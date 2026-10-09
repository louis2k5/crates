package org.bcp.crates;

import org.bukkit.Material;

/** The 16 colours a crate can have. Shulker box, key candle and glass panes always share the crate's colour. */
public enum CrateColor {
    WHITE("White", Material.WHITE_SHULKER_BOX, Material.WHITE_CANDLE, Material.WHITE_STAINED_GLASS_PANE, "§f", 0xF9FFFE),
    ORANGE("Orange", Material.ORANGE_SHULKER_BOX, Material.ORANGE_CANDLE, Material.ORANGE_STAINED_GLASS_PANE, "§6", 0xF9801D),
    MAGENTA("Magenta", Material.MAGENTA_SHULKER_BOX, Material.MAGENTA_CANDLE, Material.MAGENTA_STAINED_GLASS_PANE, "§d", 0xC74EBD),
    LIGHT_BLUE("Light Blue", Material.LIGHT_BLUE_SHULKER_BOX, Material.LIGHT_BLUE_CANDLE, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§b", 0x3AB3DA),
    YELLOW("Yellow", Material.YELLOW_SHULKER_BOX, Material.YELLOW_CANDLE, Material.YELLOW_STAINED_GLASS_PANE, "§e", 0xFED83D),
    LIME("Lime", Material.LIME_SHULKER_BOX, Material.LIME_CANDLE, Material.LIME_STAINED_GLASS_PANE, "§a", 0x80C71F),
    PINK("Pink", Material.PINK_SHULKER_BOX, Material.PINK_CANDLE, Material.PINK_STAINED_GLASS_PANE, "§d", 0xF38BAA),
    GRAY("Gray", Material.GRAY_SHULKER_BOX, Material.GRAY_CANDLE, Material.GRAY_STAINED_GLASS_PANE, "§8", 0x474F52),
    LIGHT_GRAY("Light Gray", Material.LIGHT_GRAY_SHULKER_BOX, Material.LIGHT_GRAY_CANDLE, Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7", 0x9D9D97),
    CYAN("Cyan", Material.CYAN_SHULKER_BOX, Material.CYAN_CANDLE, Material.CYAN_STAINED_GLASS_PANE, "§3", 0x169C9C),
    PURPLE("Purple", Material.PURPLE_SHULKER_BOX, Material.PURPLE_CANDLE, Material.PURPLE_STAINED_GLASS_PANE, "§5", 0x8932B8),
    BLUE("Blue", Material.BLUE_SHULKER_BOX, Material.BLUE_CANDLE, Material.BLUE_STAINED_GLASS_PANE, "§9", 0x3C44AA),
    BROWN("Brown", Material.BROWN_SHULKER_BOX, Material.BROWN_CANDLE, Material.BROWN_STAINED_GLASS_PANE, "§6", 0x835432),
    GREEN("Green", Material.GREEN_SHULKER_BOX, Material.GREEN_CANDLE, Material.GREEN_STAINED_GLASS_PANE, "§2", 0x5E7C16),
    RED("Red", Material.RED_SHULKER_BOX, Material.RED_CANDLE, Material.RED_STAINED_GLASS_PANE, "§c", 0xB02E26),
    BLACK("Black", Material.BLACK_SHULKER_BOX, Material.BLACK_CANDLE, Material.BLACK_STAINED_GLASS_PANE, "§8", 0x1D1D21);

    private final String label;
    private final Material shulker;
    private final Material candle;
    private final Material pane;
    private final String chat;
    private final int rgb;

    CrateColor(String label, Material shulker, Material candle, Material pane, String chat, int rgb) {
        this.label = label;
        this.shulker = shulker;
        this.candle = candle;
        this.pane = pane;
        this.chat = chat;
        this.rgb = rgb;
    }

    public String label() {
        return this.label;
    }

    public Material shulker() {
        return this.shulker;
    }

    public Material candle() {
        return this.candle;
    }

    public Material pane() {
        return this.pane;
    }

    /** Legacy colour code that comes closest to this colour. */
    public String chat() {
        return this.chat;
    }

    public int rgb() {
        return this.rgb;
    }

    public static CrateColor parse(String name, CrateColor fallback) {
        if (name != null) {
            for (CrateColor color : values()) {
                if (color.name().equalsIgnoreCase(name.trim())) {
                    return color;
                }
            }
        }
        return fallback;
    }
}
