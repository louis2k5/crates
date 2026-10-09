package org.bcp.crates;

/** What players see in the crate preview instead of the percentage. */
public enum Rarity {
    COMMON(80, "Common", "§7"),
    RARE(60, "Rare", "§a"),
    SUPERRARE(40, "Superrare", "§b"),
    EPIC(20, "Epic", "§5"),
    LEGENDARY(8, "Legendary", "§6"),
    MYTHIC(1, "Mythic", "§c");

    private final int minChance;
    private final String label;
    private final String color;

    Rarity(int minChance, String label, String color) {
        this.minChance = minChance;
        this.label = label;
        this.color = color;
    }

    public String label() {
        return this.label;
    }

    /** Coloured and bold, e.g. "§6§lLegendary". */
    public String display() {
        return this.color + "§l" + this.label;
    }

    /** 100-80 Common, 79-60 Rare, 59-40 Superrare, 39-20 Epic, 19-8 Legendary, 7-1 Mythic. */
    public static Rarity of(int chance) {
        for (Rarity rarity : values()) {
            if (chance >= rarity.minChance) {
                return rarity;
            }
        }
        return MYTHIC;
    }
}
