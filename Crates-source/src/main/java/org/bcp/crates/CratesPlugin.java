package org.bcp.crates;

import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Crates: floating, rotating crates that are set up completely in a menu, opened with keys
 * (enchanted candles), a /key command with one permission per crate and an hourly Key All.
 */
public final class CratesPlugin extends JavaPlugin {
    /** Everything an admin does: /crate, the editor, placing and removing crates. */
    public static final String ADMIN = "crates.admin";

    private Storage storage;
    private CrateManager crates;
    private Placements placements;
    private Openings openings;
    private Menus menus;
    private ChatInput chatInput;
    private KeyAll keyAll;
    private Cooldowns cooldowns;
    private Deliveries deliveries;
    private boolean dataUnreadable;

    private String prefix = "";
    private long tick;
    private int tickErrors;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPrefix();

        this.storage = new Storage(this);
        this.crates = new CrateManager(this);
        this.placements = new Placements(this);
        this.openings = new Openings(this);
        this.menus = new Menus(this);
        this.chatInput = new ChatInput(this);
        this.keyAll = new KeyAll(this);
        this.cooldowns = new Cooldowns(this);

        this.deliveries = new Deliveries(this);

        try {
            this.crates.load();
            this.placements.load();
            this.keyAll.load();
            this.cooldowns.load();
        } catch (Exception ex) {
            // Never carry on with half the data: the next save would overwrite the file with what little was read.
            this.dataUnreadable = true;
            getLogger().severe("==================================================================");
            getLogger().severe("Crates is NOT running: " + ex.getMessage());
            getLogger().severe("Nothing was changed, no crate is lost. Repair that file in plugins/" + getName()
                + " (or put a backup there) and restart the server.");
            getLogger().severe("==================================================================");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this.placements, this);
        pm.registerEvents(this.openings, this);
        pm.registerEvents(this.menus, this);
        pm.registerEvents(this.chatInput, this);
        pm.registerEvents(this.deliveries, this);
        pm.registerEvents(new KeyListener(this), this);

        CrateCommand crateCommand = new CrateCommand(this);
        bind("crate", crateCommand, crateCommand);
        KeyCommand keyCommand = new KeyCommand(this);
        bind("key", keyCommand, keyCommand);

        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                PapiExpansion.hook(this);
                getLogger().info("PlaceholderAPI found: %crates_keyall% is available.");
            } catch (Throwable t) {
                getLogger().warning("Could not register the PlaceholderAPI placeholders: " + t);
            }
        }

        // after a plugin reload players are already online
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.crates.syncKeys(player);
        }
    }

    @Override
    public void onDisable() {
        if (this.dataUnreadable) {
            return; // the files were not read, so they must not be written either
        }
        // every step on its own: a problem in one of them must not keep the others from saving
        if (this.openings != null) {
            closing("the crate openings", this.openings::shutdown); // running openings hand out their prize now
        }
        if (this.menus != null) {
            closing("the menus", this.menus::shutdown);
        }
        if (this.placements != null) {
            closing("the placed crates", this.placements::shutdown);
            closing("placed.yml", () -> this.placements.save(true));
        }
        if (this.crates != null) {
            closing("crates.yml", () -> this.crates.save(true));
        }
        if (this.keyAll != null) {
            closing("keyall.yml", () -> this.keyAll.save(true));
        }
        if (this.cooldowns != null) {
            closing("cooldowns.yml", () -> this.cooldowns.save(true));
        }
    }

    private void closing(String what, Runnable step) {
        try {
            step.run();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Error while shutting down " + what, t);
        }
    }

    private void bind(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("The command /" + name + " is missing in plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    // ------------------------------------------------------------------ timer

    private void tick() {
        this.tick++;
        try {
            this.openings.tick();
        } catch (Throwable t) {
            report("the crate openings", t);
        }
        if (this.tick % 2L == 0L) {
            try {
                this.placements.tick(this.tick);
            } catch (Throwable t) {
                report("the placed crates", t);
            }
        }
        if (this.tick % 20L == 0L) {
            try {
                this.keyAll.second();
            } catch (Throwable t) {
                report("Key All", t);
            }
        }
    }

    private void report(String what, Throwable t) {
        this.tickErrors++;
        // the first few in full, afterwards one reminder per minute at most
        if (this.tickErrors <= 5 || this.tick % 1200L == 0L) {
            getLogger().log(Level.SEVERE, "Error while updating " + what + " (" + this.tickErrors + " so far)", t);
        }
    }

    /** Number of errors the timer has run into since the plugin started. */
    public int tickErrors() {
        return this.tickErrors;
    }

    /** Server ticks since the plugin started. */
    public long currentTick() {
        return this.tick;
    }

    // ------------------------------------------------------------------ settings and messages

    private void loadPrefix() {
        this.prefix = Text.color(getConfig().getString("prefix", "&8[&6Crates&8] &7"));
    }

    /** /crate reload */
    public void reloadSettings() {
        reloadConfig();
        loadPrefix();
        for (Crate crate : this.crates.all()) {
            this.placements.refresh(crate); // hover height, size and name tag may have changed
        }
    }

    public void msg(CommandSender to, String text) {
        to.sendMessage(this.prefix + text);
    }

    // ------------------------------------------------------------------ parts

    public Storage storage() {
        return this.storage;
    }

    public CrateManager crates() {
        return this.crates;
    }

    public Placements placements() {
        return this.placements;
    }

    public Openings openings() {
        return this.openings;
    }

    public Menus menus() {
        return this.menus;
    }

    public ChatInput chatInput() {
        return this.chatInput;
    }

    public KeyAll keyAll() {
        return this.keyAll;
    }

    public Cooldowns cooldowns() {
        return this.cooldowns;
    }

    public Deliveries deliveries() {
        return this.deliveries;
    }

    /** True when a data file could not be read at startup; the plugin is switched off then. */
    public boolean dataUnreadable() {
        return this.dataUnreadable;
    }

    // ------------------------------------------------------------------ for other plugins (the scoreboard reads these)

    /** True while the Key All timer runs. */
    public boolean keyAllRunning() {
        return this.keyAll != null && this.keyAll.running();
    }

    /** Seconds until the next Key All. */
    public long keyAllSeconds() {
        return this.keyAll == null ? 0L : this.keyAll.remaining();
    }

    /** The timer ("42:17") or the paused text, as set in config.yml under keyall.display. */
    public String keyAllDisplay() {
        return this.keyAll == null ? "" : this.keyAll.display();
    }
}
