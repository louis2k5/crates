package org.bcp.crates;

import org.bukkit.Material;
import org.bukkit.Sound;

/** The seven opening sounds: a tick for every item that passes and a sound for the win. */
public enum OpenSound {
    CLASSIC("Classic", Material.NOTE_BLOCK, Sound.UI_BUTTON_CLICK, 1.0f, Sound.ENTITY_PLAYER_LEVELUP, 1.0f),
    PLING("Pling", Material.GOLD_BLOCK, Sound.BLOCK_NOTE_BLOCK_PLING, 1.4f, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f),
    CHIME("Chime", Material.AMETHYST_SHARD, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.9f),
    BELL("Bell", Material.BELL, Sound.BLOCK_NOTE_BLOCK_BELL, 1.3f, Sound.BLOCK_BELL_USE, 1.0f),
    FIREWORK("Firework", Material.FIREWORK_ROCKET, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f),
    DRAGON("Dragon", Material.DRAGON_HEAD, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f),
    TREASURE("Treasure", Material.CHEST, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.1f, Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f);

    private final String label;
    private final Material icon;
    private final Sound tick;
    private final float tickPitch;
    private final Sound win;
    private final float winPitch;

    OpenSound(String label, Material icon, Sound tick, float tickPitch, Sound win, float winPitch) {
        this.label = label;
        this.icon = icon;
        this.tick = tick;
        this.tickPitch = tickPitch;
        this.win = win;
        this.winPitch = winPitch;
    }

    public String label() {
        return this.label;
    }

    public Material icon() {
        return this.icon;
    }

    public Sound tick() {
        return this.tick;
    }

    public float tickPitch() {
        return this.tickPitch;
    }

    public Sound win() {
        return this.win;
    }

    public float winPitch() {
        return this.winPitch;
    }

    public static OpenSound parse(String name) {
        if (name != null) {
            for (OpenSound s : values()) {
                if (s.name().equalsIgnoreCase(name.trim())) {
                    return s;
                }
            }
        }
        return CLASSIC;
    }
}
