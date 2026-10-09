package org.bcp.crates;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** The cooldown of /key: per player and per crate, kept in cooldowns.yml so it survives restarts. */
public final class Cooldowns {
    private static final String FILE = "cooldowns.yml";

    private final CratesPlugin plugin;
    /**
     * player -> crate -> time (epoch millis) of the last /key. The start is stored rather than the end, so a
     * different cooldown length in the config applies to cooldowns that are already running.
     */
    private final Map<UUID, Map<String, Long>> usedAt = new HashMap<>();

    public Cooldowns(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    /** Length of the cooldown in milliseconds (48 hours unless the config says otherwise). */
    public long length() {
        double hours = this.plugin.getConfig().getDouble("key-command.cooldown-hours", 48.0);
        return Math.max(0L, Math.round(hours * 3_600_000.0));
    }

    /** Milliseconds this player still has to wait for this crate's key, 0 when /key is ready. */
    public long remaining(UUID player, String crateId) {
        Map<String, Long> map = this.usedAt.get(player);
        if (map == null) {
            return 0L;
        }
        Long time = map.get(crateId);
        if (time == null) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        if (time > now) {
            time = now; // the server clock was set back
        }
        return Math.max(0L, time + length() - now);
    }

    public void start(UUID player, String crateId) {
        if (length() <= 0L) {
            return;
        }
        this.usedAt.computeIfAbsent(player, k -> new HashMap<>()).put(crateId, System.currentTimeMillis());
        save(false);
    }

    /**
     * Ends cooldowns early.
     *
     * @param crateId one crate, or null for all of them
     * @return true when there was something to clear
     */
    public boolean clear(UUID player, String crateId) {
        Map<String, Long> map = this.usedAt.get(player);
        if (map == null) {
            return false;
        }
        boolean changed;
        if (crateId == null) {
            changed = !map.isEmpty();
            this.usedAt.remove(player);
        } else {
            changed = map.remove(crateId.toLowerCase(Locale.ROOT)) != null;
        }
        if (changed) {
            save(false);
        }
        return changed;
    }

    /** A crate was deleted: its cooldowns go with it. */
    public void forget(String crateId) {
        boolean changed = false;
        for (Map<String, Long> map : this.usedAt.values()) {
            changed |= map.remove(crateId) != null;
        }
        if (changed) {
            save(false);
        }
    }

    public void load() throws InvalidConfigurationException {
        this.usedAt.clear();
        YamlConfiguration yaml = this.plugin.storage().read(FILE);
        if (yaml == null) {
            return;
        }
        ConfigurationSection root = yaml.getConfigurationSection("cooldowns");
        if (root == null) {
            return;
        }
        long expired = System.currentTimeMillis() - length();
        for (String playerKey : root.getKeys(false)) {
            UUID player;
            try {
                player = UUID.fromString(playerKey);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(playerKey);
            if (section == null) {
                continue;
            }
            for (String crateId : section.getKeys(false)) {
                long time = section.getLong(crateId, 0L);
                if (time > expired) {
                    this.usedAt.computeIfAbsent(player, k -> new HashMap<>()).put(crateId, time);
                }
            }
        }
    }

    public void save(boolean now) {
        long expired = System.currentTimeMillis() - length();
        YamlConfiguration yaml = new YamlConfiguration();
        Iterator<Map.Entry<UUID, Map<String, Long>>> players = this.usedAt.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, Map<String, Long>> entry = players.next();
            entry.getValue().values().removeIf(used -> used <= expired);
            if (entry.getValue().isEmpty()) {
                players.remove();
                continue;
            }
            for (Map.Entry<String, Long> cooldown : entry.getValue().entrySet()) {
                yaml.set("cooldowns." + entry.getKey() + "." + cooldown.getKey(), cooldown.getValue());
            }
        }
        this.plugin.storage().write(FILE, yaml.saveToString(), now);
    }
}
