package org.bcp.crates;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.persistence.PersistentDataType;

/** All crates: storage (crates.yml), the key and crate items, and the per-crate permission of /key. */
public final class CrateManager {
    public static final String KEY_PERMISSION = "crates.key.";

    private final CratesPlugin plugin;
    private final Map<String, Crate> crates = new LinkedHashMap<>();
    private final NamespacedKey keyTag;
    private final NamespacedKey crateItemTag;

    public CrateManager(CratesPlugin plugin) {
        this.plugin = plugin;
        this.keyTag = new NamespacedKey(plugin, "key");
        this.crateItemTag = new NamespacedKey(plugin, "crate_item");
    }

    // ------------------------------------------------------------------ lookup

    public Collection<Crate> all() {
        return Collections.unmodifiableCollection(this.crates.values());
    }

    public Crate get(String id) {
        return id == null ? null : this.crates.get(id.toLowerCase(Locale.ROOT));
    }

    public List<String> ids() {
        return new ArrayList<>(this.crates.keySet());
    }

    /** Crate names: 1-24 characters, letters, digits, "_" and "-". */
    public static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,24}");
    }

    // ------------------------------------------------------------------ create / delete

    public Crate create(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        if (!validId(id) || this.crates.containsKey(key)) {
            return null;
        }
        Crate crate = new Crate(key);
        this.crates.put(key, crate);
        registerPermission(crate);
        save(false);
        return crate;
    }

    public void delete(Crate crate) {
        this.crates.remove(crate.id());
        try {
            Bukkit.getPluginManager().removePermission(KEY_PERMISSION + crate.id());
        } catch (Throwable ignored) {
            // was not registered
        }
        save(false);
    }

    /** Every crate gets its own permission crates.key.&lt;name&gt; for the /key command. */
    private void registerPermission(Crate crate) {
        String name = KEY_PERMISSION + crate.id();
        try {
            if (Bukkit.getPluginManager().getPermission(name) != null) {
                return;
            }
            Permission permission = new Permission(name, "Use /key " + crate.id() + " to get a key of this crate", PermissionDefault.FALSE);
            Bukkit.getPluginManager().addPermission(permission);
            permission.addParent(KEY_PERMISSION + "*", true); // crates.key.* covers every crate
        } catch (Throwable t) {
            this.plugin.getLogger().warning("Could not register the permission " + name + ": " + t);
        }
    }

    // ------------------------------------------------------------------ storage

    public void load() throws InvalidConfigurationException {
        this.crates.clear();
        YamlConfiguration yaml = this.plugin.storage().read("crates.yml");
        if (yaml == null) {
            return;
        }
        ConfigurationSection root = yaml.getConfigurationSection("crates");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            if (!validId(id)) {
                this.plugin.getLogger().warning("Skipping the crate '" + id + "' in crates.yml: invalid name");
                continue;
            }
            Crate crate = new Crate(id.toLowerCase(Locale.ROOT));
            String base = id + ".";
            String display = root.getString(base + "display-name");
            if (display != null && !display.isBlank()) {
                crate.displayName(display);
            }
            crate.keyName(root.getString(base + "key-name"));
            crate.color(CrateColor.parse(root.getString(base + "color"), CrateColor.PURPLE));
            crate.animation(OpenAnimation.parse(root.getString(base + "open-animation")));
            crate.particles(ParticleEffect.parse(root.getString(base + "particles")));
            crate.sound(OpenSound.parse(root.getString(base + "sound")));
            ConfigurationSection rewards = root.getConfigurationSection(base + "rewards");
            if (rewards != null) {
                for (String slotKey : rewards.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(slotKey);
                        ItemStack item = rewards.getItemStack(slotKey + ".item");
                        if (item != null && slot >= 0 && slot < Crate.MAX_REWARDS) {
                            crate.reward(slot, new Crate.Reward(item, rewards.getInt(slotKey + ".chance", 50)));
                        }
                    } catch (RuntimeException ex) {
                        this.plugin.getLogger().warning("Skipping reward '" + slotKey + "' of the crate '" + id + "': " + ex.getMessage());
                    }
                }
            }
            this.crates.put(crate.id(), crate);
            registerPermission(crate);
        }
        this.plugin.getLogger().info("Loaded " + this.crates.size() + " crate(s).");
    }

    public void save(boolean now) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Crate crate : this.crates.values()) {
            String base = "crates." + crate.id() + ".";
            yaml.set(base + "display-name", crate.rawDisplayName());
            yaml.set(base + "key-name", crate.rawKeyName());
            yaml.set(base + "color", crate.color().name());
            yaml.set(base + "open-animation", crate.animation().name());
            yaml.set(base + "particles", crate.particles().name());
            yaml.set(base + "sound", crate.sound().name());
            for (int slot = 0; slot < Crate.MAX_REWARDS; slot++) {
                Crate.Reward reward = crate.reward(slot);
                if (reward != null) {
                    yaml.set(base + "rewards." + slot + ".item", reward.item());
                    yaml.set(base + "rewards." + slot + ".chance", reward.chance());
                }
            }
        }
        this.plugin.storage().write("crates.yml", yaml.saveToString(), now);
    }

    // ------------------------------------------------------------------ items

    /** The key: an enchanted candle in the crate's colour. */
    public ItemStack keyItem(Crate crate, int amount) {
        ItemStack item = new ItemStack(crate.color().candle(), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(crate.keyName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Right-click the " + crate.displayName());
        lore.add("§7with this key to open it.");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(this.keyTag, PersistentDataType.STRING, crate.id());
        item.setItemMeta(meta);
        return item;
    }

    /** The shulker box an admin places to set the crate up in the world. */
    public ItemStack crateItem(Crate crate) {
        ItemStack item = new ItemStack(crate.color().shulker());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(crate.displayName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Place this to set up the crate.");
        lore.add("§8Sneak + left-click a placed crate to remove it.");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(this.crateItemTag, PersistentDataType.STRING, crate.id());
        item.setItemMeta(meta);
        return item;
    }

    private String tag(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /** True for every key item, also for keys of crates that no longer exist. */
    public boolean isKey(ItemStack item) {
        return tag(item, this.keyTag) != null;
    }

    /** The crate this key opens, or null. */
    public Crate crateOfKey(ItemStack item) {
        return get(tag(item, this.keyTag));
    }

    public boolean isCrateItem(ItemStack item) {
        return tag(item, this.crateItemTag) != null;
    }

    public Crate crateOfCrateItem(ItemStack item) {
        return get(tag(item, this.crateItemTag));
    }

    /**
     * Brings the keys a player carries up to date after a crate got a new colour or name
     * (the candle changes its colour, the amount stays the same).
     */
    public void syncKeys(Player player) {
        syncKeys(player.getInventory());
    }

    /** The same for any inventory, for example a chest a player opens. */
    public void syncKeys(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            Crate crate = crateOfKey(item);
            if (crate == null) {
                continue;
            }
            ItemStack fresh = keyItem(crate, item.getAmount());
            if (!fresh.isSimilar(item)) {
                inventory.setItem(slot, fresh);
            }
        }
    }

    /** Call after the look of a crate changed (name, key name, colour, particles): saves and updates everything live. */
    public void appearanceChanged(Crate crate) {
        save(false);
        this.plugin.placements().refresh(crate);
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncKeys(player);
        }
    }
}
