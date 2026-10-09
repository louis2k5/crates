package org.bcp.crates;

import org.bukkit.Material;

/** The seven particle animations around a placed crate (plus "off"). */
public enum ParticleEffect {
    NONE("Off", Material.BARRIER, "No particles."),
    HELIX("Helix", Material.STRING, "Two spirals climb around the crate."),
    RING("Ring", Material.GOLD_NUGGET, "A ring of particles circles the crate."),
    ORBIT("Orbit", Material.ENDER_PEARL, "Two sparks orbit the crate", "on tilted paths."),
    FOUNTAIN("Fountain", Material.PRISMARINE_SHARD, "Particles shoot up from the crate", "and rain back down."),
    SPARKLE("Sparkle", Material.GLOWSTONE_DUST, "Glittering sparks appear all around."),
    FLAMES("Flames", Material.BLAZE_POWDER, "A circle of small flames burns", "under the crate."),
    ENCHANT("Enchant", Material.ENCHANTING_TABLE, "Magic letters fly into the crate.");

    private final String label;
    private final Material icon;
    private final String[] description;

    ParticleEffect(String label, Material icon, String... description) {
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

    public static ParticleEffect parse(String name) {
        if (name != null) {
            for (ParticleEffect e : values()) {
                if (e.name().equalsIgnoreCase(name.trim())) {
                    return e;
                }
            }
        }
        return HELIX;
    }
}
