package org.bcp.crates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Key All: while it runs, every online player gets a free key each time the timer reaches zero
 * (once per hour unless the config says otherwise). It can only be started after at least one
 * crate was added with /crate keyall add.
 */
public final class KeyAll {
    private static final String FILE = "keyall.yml";

    private final CratesPlugin plugin;
    private final List<String> crateIds = new ArrayList<>();
    private boolean running;
    /** seconds until the next keys are handed out */
    private long remaining;
    private int secondsSinceSave;

    public KeyAll(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ settings

    /** Length of one round in seconds. */
    public long interval() {
        return Math.max(1L, this.plugin.getConfig().getLong("keyall.interval-minutes", 60L)) * 60L;
    }

    private int amount() {
        return Math.max(1, Math.min(64, this.plugin.getConfig().getInt("keyall.amount", 1)));
    }

    // ------------------------------------------------------------------ storage

    public void load() throws InvalidConfigurationException {
        this.crateIds.clear();
        this.running = false;
        this.remaining = interval();
        YamlConfiguration yaml = this.plugin.storage().read(FILE);
        if (yaml == null) {
            return;
        }
        for (String id : yaml.getStringList("crates")) {
            Crate crate = this.plugin.crates().get(id);
            if (crate != null && !this.crateIds.contains(crate.id())) {
                this.crateIds.add(crate.id());
            }
        }
        long saved = yaml.getLong("remaining-seconds", interval());
        this.remaining = saved <= 0L || saved > interval() ? interval() : saved;
        this.running = yaml.getBoolean("running", false) && !this.crateIds.isEmpty();
    }

    public void save(boolean now) {
        this.secondsSinceSave = 0;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("crates", new ArrayList<>(this.crateIds));
        yaml.set("running", this.running);
        yaml.set("remaining-seconds", this.remaining);
        this.plugin.storage().write(FILE, yaml.saveToString(), now);
    }

    // ------------------------------------------------------------------ state

    public List<String> crates() {
        return Collections.unmodifiableList(this.crateIds);
    }

    public boolean running() {
        return this.running;
    }

    /** Seconds until the next keys (the timer stands still while Key All is paused). */
    public long remaining() {
        return this.remaining;
    }

    /** @return false when this crate's key is already part of Key All */
    public boolean add(Crate crate) {
        if (this.crateIds.contains(crate.id())) {
            return false;
        }
        this.crateIds.add(crate.id());
        save(false);
        return true;
    }

    /** @return false when this crate's key was not part of Key All */
    public boolean remove(String crateId) {
        if (!this.crateIds.remove(crateId.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (this.crateIds.isEmpty()) {
            this.running = false;
        }
        save(false);
        return true;
    }

    /** @return false when no key was added yet */
    public boolean start() {
        if (this.crateIds.isEmpty()) {
            return false;
        }
        if (this.remaining <= 0L || this.remaining > interval()) {
            this.remaining = interval();
        }
        this.running = true;
        save(false);
        return true;
    }

    public void stop() {
        this.running = false;
        save(false);
    }

    /** Sets the timer back to a full round. */
    public void reset() {
        this.remaining = interval();
        save(false);
    }

    /** Text for the scoreboard: the timer while Key All runs, otherwise the "paused" text. */
    public String display() {
        String format = this.running
            ? this.plugin.getConfig().getString("keyall.display.running", "&e{time}")
            : this.plugin.getConfig().getString("keyall.display.paused", "&cpaused");
        if (format == null) {
            format = this.running ? "{time}" : "paused";
        }
        return Text.color(format.replace("{time}", Text.clock(this.remaining)));
    }

    // ------------------------------------------------------------------ timer

    /** Called once per second. */
    public void second() {
        if (!this.running) {
            return;
        }
        if (this.crateIds.isEmpty()) {
            this.running = false;
            save(false);
            return;
        }
        if (this.remaining > interval()) {
            this.remaining = interval(); // the interval was shortened in the config
        }
        this.remaining--;
        if (this.remaining <= 0L) {
            this.remaining = interval();
            save(false);
            distribute();
            return;
        }
        if (this.plugin.getConfig().getIntegerList("keyall.announce-seconds").contains((int) this.remaining)
            && !Bukkit.getOnlinePlayers().isEmpty()) {
            String in = Text.duration(this.remaining * 1000L);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage("§6§lKEY ALL §8» §7Free keys for everyone in §e" + in + "§7!");
            }
        }
        if (++this.secondsSinceSave >= 60) {
            save(false); // so a crash costs a minute of the timer at most
        }
    }

    /**
     * Hands the keys out right now.
     *
     * @return how many players got at least one key
     */
    public int distribute() {
        List<Crate> list = new ArrayList<>();
        for (String id : this.crateIds) {
            Crate crate = this.plugin.crates().get(id);
            if (crate != null) {
                list.add(crate);
            }
        }
        if (list.isEmpty()) {
            return 0;
        }
        int amount = amount();
        int receivers = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            List<String> got = new ArrayList<>();
            boolean full = false;
            if (player.isDead()) {
                // the respawn wipes the inventory: the keys are handed over right after it
                for (Crate crate : list) {
                    this.plugin.deliveries().afterRespawn(player, this.plugin.crates().keyItem(crate, amount), false);
                    got.add("§e" + amount + "x " + crate.keyName());
                }
                receivers++;
                player.sendMessage("§6§lKEY ALL §8» §7You get " + String.join("§7, ", got) + " §7as soon as you respawn!");
                continue;
            }
            for (Crate crate : list) {
                // a full inventory gets no key: nothing is dropped and nothing is kept for later
                if (player.getInventory().firstEmpty() == -1) {
                    full = true;
                    break;
                }
                player.getInventory().addItem(this.plugin.crates().keyItem(crate, amount));
                got.add("§e" + amount + "x " + crate.keyName());
            }
            if (!got.isEmpty()) {
                receivers++;
                player.sendMessage("§6§lKEY ALL §8» §7You received " + String.join("§7, ", got) + "§7!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            }
            if (full) {
                player.sendMessage("§6§lKEY ALL §8» §cYour inventory is full, so you "
                    + (got.isEmpty() ? "did not get a key" : "did not get every key") + " this time.");
            }
        }
        return receivers;
    }
}
