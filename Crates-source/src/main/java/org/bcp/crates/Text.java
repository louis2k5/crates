package org.bcp.crates;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;

/** Colour codes: "&a" style codes and "&#RRGGBB" hex colours. */
public final class Text {
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    private Text() {
    }

    public static String color(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = HEX.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            StringBuilder hex = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) {
                hex.append('§').append(Character.toLowerCase(c));
            }
            matcher.appendReplacement(out, hex.toString());
        }
        matcher.appendTail(out);
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }

    public static String strip(String text) {
        return text == null ? "" : ChatColor.stripColor(color(text));
    }

    /** A timer: "42:17", a full hour as "60:00", and anything longer as "1:05:09". */
    public static String clock(long seconds) {
        long s = Math.max(0L, seconds);
        if (s <= 3600L) {
            return String.format("%02d:%02d", s / 60L, s % 60L);
        }
        return String.format("%d:%02d:%02d", s / 3600L, s / 60L % 60L, s % 60L);
    }

    /** A time span in its two biggest units: "1d 23h", "5h 12m", "3m 20s", "45s"; a unit of zero is left out ("2d", "5m"). */
    public static String duration(long millis) {
        long s = Math.max(0L, (millis + 999L) / 1000L);
        long d = s / 86400L;
        long h = s / 3600L % 24L;
        long m = s / 60L % 60L;
        long sec = s % 60L;
        if (d > 0L) {
            return d + "d" + (h > 0L ? " " + h + "h" : "");
        }
        if (h > 0L) {
            return h + "h" + (m > 0L ? " " + m + "m" : "");
        }
        if (m > 0L) {
            return m + "m" + (sec > 0L ? " " + sec + "s" : "");
        }
        return sec + "s";
    }
}
