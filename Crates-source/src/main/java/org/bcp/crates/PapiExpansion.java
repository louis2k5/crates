package org.bcp.crates;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI placeholders (only loaded when PlaceholderAPI is installed):
 * %crates_keyall%, %crates_keyall_time%, %crates_keyall_status% and %crates_cooldown_&lt;crate&gt;%.
 */
public final class PapiExpansion extends PlaceholderExpansion {
    private final CratesPlugin plugin;

    private PapiExpansion(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    static void hook(CratesPlugin plugin) {
        new PapiExpansion(plugin).register();
    }

    @Override
    public String getIdentifier() {
        return "crates";
    }

    @Override
    public String getAuthor() {
        String authors = String.join(", ", this.plugin.getDescription().getAuthors());
        return authors.isEmpty() ? "Crates" : authors;
    }

    @Override
    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // stay registered when PlaceholderAPI reloads
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        switch (key) {
            case "keyall":
                return this.plugin.keyAllDisplay();
            case "keyall_time":
                return Text.clock(this.plugin.keyAllSeconds());
            case "keyall_status":
                return this.plugin.keyAllRunning() ? "running" : "paused";
            default:
                break;
        }
        if (key.startsWith("cooldown_") && player != null) {
            Crate crate = this.plugin.crates().get(key.substring("cooldown_".length()));
            if (crate == null) {
                return "";
            }
            long wait = this.plugin.cooldowns().remaining(player.getUniqueId(), crate.id());
            return wait > 0L ? Text.duration(wait) : "ready";
        }
        return null;
    }
}
