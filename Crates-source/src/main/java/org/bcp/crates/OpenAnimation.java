package org.bcp.crates;

import org.bukkit.Material;

/** The seven ways the items move through the opening window. */
public enum OpenAnimation {
    ROULETTE("Roulette", Material.MINECART, "Items roll from right to left", "and slow down on your prize."),
    REVERSE("Reverse Roulette", Material.CHEST_MINECART, "Items roll from left to right", "and slow down on your prize."),
    PENDULUM("Pendulum", Material.CLOCK, "Items swing back and forth", "before they settle."),
    SHUFFLE("Shuffle", Material.ENDER_EYE, "All items change at random", "until the prize appears."),
    CLOSING_IN("Closing In", Material.IRON_BARS, "The row gets narrower until", "only your prize is left."),
    RAINBOW("Rainbow Roulette", Material.PRISMARINE_CRYSTALS, "A roulette with a frame that", "runs through all colours."),
    PULSE("Pulse", Material.REDSTONE, "Items jump forward in quick", "bursts with short breaks.");

    private final String label;
    private final Material icon;
    private final String[] description;

    OpenAnimation(String label, Material icon, String... description) {
        this.label = label;
        this.icon = icon;
        this.description = description;
    }

    public String label() {
        return this.label;
    }

    public Material icon() {
        return this.icon;
    }

    public String[] description() {
        return this.description;
    }

    public static OpenAnimation parse(String name) {
        if (name != null) {
            for (OpenAnimation a : values()) {
                if (a.name().equalsIgnoreCase(name.trim())) {
                    return a;
                }
            }
        }
        return ROULETTE;
    }
}
