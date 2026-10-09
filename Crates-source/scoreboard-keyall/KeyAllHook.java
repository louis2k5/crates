package de.maximilian.scoreboardpl.hook;

import de.maximilian.scoreboardpl.ScoreboardPL;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Key All of the Crates plugin on the scoreboard: the placeholders %keyall%, %keyall_time% and
 * %keyall_status%, and the line that is added automatically (see "key-all" in config.yml).
 * The Crates plugin is looked up while the server runs, so neither plugin needs the other to start.
 */
public final class KeyAllHook {
    /** The sidebar shows 15 lines at most. */
    private static final int MAX_LINES = 15;

    private static Plugin crates;
    private static Method running;
    private static Method seconds;

    private KeyAllHook() {
    }

    /** Finds the Crates plugin (again after it was reloaded). @return false when it is not installed or too old */
    private static boolean lookup() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Crates");
        if (plugin == null || !plugin.isEnabled()) {
            crates = null;
            return false;
        }
        if (plugin != crates) {
            crates = null;
            try {
                Method r = plugin.getClass().getMethod("keyAllRunning");
                Method s = plugin.getClass().getMethod("keyAllSeconds");
                running = r;
                seconds = s;
                crates = plugin;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }
        return true;
    }

    public static boolean available() {
        try {
            return lookup();
        } catch (Throwable t) {
            return false;
        }
    }

    private static String clock(long totalSeconds) {
        long s = Math.max(0L, totalSeconds);
        if (s <= 3600L) {
            return String.format("%02d:%02d", s / 60L, s % 60L);
        }
        return String.format("%d:%02d:%02d", s / 3600L, s / 60L % 60L, s % 60L);
    }

    /** Fills in the Key All placeholders of a title or line. */
    public static String apply(ScoreboardPL plugin, String line) {
        if (line == null || !line.contains("%keyall")) {
            return line;
        }
        FileConfiguration config = plugin.getConfig();
        String text;
        String time;
        String status;
        try {
            if (!lookup()) {
                throw new IllegalStateException("Crates is not installed");
            }
            boolean isRunning = (Boolean) running.invoke(crates);
            time = clock(((Number) seconds.invoke(crates)).longValue());
            status = isRunning ? "running" : "paused";
            text = isRunning
                ? config.getString("key-all.running", "&e%timer%")
                : config.getString("key-all.paused", "&cpaused");
        } catch (Throwable t) {
            time = "--:--";
            status = "unavailable";
            text = config.getString("key-all.unavailable", "&8-");
        }
        if (text == null) {
            text = "";
        }
        return line.replace("%keyall%", text.replace("%timer%", time))
            .replace("%keyall_time%", time)
            .replace("%keyall_status%", status);
    }

    /**
     * The scoreboard lines, with the Key All line added when it is switched on, the Crates plugin is installed
     * and no line shows %keyall% yet.
     */
    public static List<String> lines(ScoreboardPL plugin, List<String> lines) {
        try {
            FileConfiguration config = plugin.getConfig();
            if (!config.getBoolean("key-all.auto-line.enabled", true) || lines.size() >= MAX_LINES) {
                return lines;
            }
            for (String line : lines) {
                if (line != null && line.contains("%keyall")) {
                    return lines;
                }
            }
            if (!lookup()) {
                return lines;
            }
            String text = config.getString("key-all.auto-line.text", " &7Key all&8: %keyall%");
            if (text == null || text.isEmpty()) {
                return lines;
            }
            // 1 = first line, 2 = second line ...; -1 = last line, -2 = above the last line ...
            int position = config.getInt("key-all.auto-line.position", -2);
            int index = position > 0 ? position - 1 : lines.size() + (position == 0 ? -1 : position) + 1;
            index = Math.max(0, Math.min(lines.size(), index));
            List<String> out = new ArrayList<String>(lines);
            out.add(index, text);
            return out;
        } catch (Throwable t) {
            return lines;
        }
    }
}
