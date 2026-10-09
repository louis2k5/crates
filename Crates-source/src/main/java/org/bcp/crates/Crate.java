package org.bcp.crates;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.inventory.ItemStack;

/** One crate with its look, its sounds and animations and up to 30 rewards. */
public final class Crate {
    public static final int MAX_REWARDS = 30;

    /** One reward: the item a player gets and how likely it is (1-100, higher = more often). */
    public static final class Reward {
        private final ItemStack item;
        private int chance;

        public Reward(ItemStack item, int chance) {
            this.item = item;
            this.chance = clamp(chance);
        }

        public ItemStack item() {
            return this.item;
        }

        public int chance() {
            return this.chance;
        }

        public void chance(int chance) {
            this.chance = clamp(chance);
        }

        public Rarity rarity() {
            return Rarity.of(this.chance);
        }

        private static int clamp(int chance) {
            return Math.max(1, Math.min(100, chance));
        }
    }

    private final String id;
    private String displayName;
    /** null = "<display name> Key" */
    private String keyName;
    private CrateColor color = CrateColor.PURPLE;
    private OpenAnimation animation = OpenAnimation.ROULETTE;
    private ParticleEffect particles = ParticleEffect.HELIX;
    private OpenSound sound = OpenSound.CLASSIC;
    /** indexed by the slot number of the reward editor (0-29), null = empty */
    private final Reward[] rewards = new Reward[MAX_REWARDS];

    public Crate(String id) {
        this.id = id;
        this.displayName = "&5&l" + Character.toUpperCase(id.charAt(0)) + id.substring(1) + " Crate";
    }

    public String id() {
        return this.id;
    }

    /** The name as typed, with & codes. */
    public String rawDisplayName() {
        return this.displayName;
    }

    /** The name with colours applied. */
    public String displayName() {
        return Text.color(this.displayName);
    }

    public void displayName(String name) {
        this.displayName = name;
    }

    public String rawKeyName() {
        return this.keyName;
    }

    public String keyName() {
        if (this.keyName == null || this.keyName.isBlank()) {
            return Text.color(this.displayName) + " §7Key";
        }
        return Text.color(this.keyName);
    }

    public void keyName(String name) {
        this.keyName = name == null || name.isBlank() ? null : name;
    }

    public CrateColor color() {
        return this.color;
    }

    public void color(CrateColor color) {
        this.color = color;
    }

    public OpenAnimation animation() {
        return this.animation;
    }

    public void animation(OpenAnimation animation) {
        this.animation = animation;
    }

    public ParticleEffect particles() {
        return this.particles;
    }

    public void particles(ParticleEffect particles) {
        this.particles = particles;
    }

    public OpenSound sound() {
        return this.sound;
    }

    public void sound(OpenSound sound) {
        this.sound = sound;
    }

    public Reward reward(int slot) {
        return slot < 0 || slot >= MAX_REWARDS ? null : this.rewards[slot];
    }

    public void reward(int slot, Reward reward) {
        if (slot >= 0 && slot < MAX_REWARDS) {
            this.rewards[slot] = reward;
        }
    }

    /** All rewards that are set, in slot order. */
    public List<Reward> rewards() {
        List<Reward> list = new ArrayList<>();
        for (Reward reward : this.rewards) {
            if (reward != null) {
                list.add(reward);
            }
        }
        return list;
    }

    public boolean hasRewards() {
        for (Reward reward : this.rewards) {
            if (reward != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Picks a reward. The chances work as weights: an item with 100 comes up twenty times as often as one with 5.
     * They do not have to add up to 100.
     */
    public Reward pick(Random random) {
        int total = 0;
        for (Reward reward : this.rewards) {
            if (reward != null) {
                total += reward.chance();
            }
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (Reward reward : this.rewards) {
            if (reward == null) {
                continue;
            }
            roll -= reward.chance();
            if (roll < 0) {
                return reward;
            }
        }
        return null;
    }
}
