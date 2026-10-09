package org.bcp.crates;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/** Draws the particle animations around a placed crate. Called every 2 ticks with a rising step counter. */
public final class ParticleFx {
    private ParticleFx() {
    }

    /**
     * @param center middle of the floating crate
     * @param size   edge length of the crate
     */
    public static void play(ParticleEffect effect, World world, Location center, CrateColor color, long step, double size) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(color.rgb()), 1.0f);
        double r = 0.75 * size + 0.15;
        double x = center.getX();
        double y = center.getY();
        double z = center.getZ();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        switch (effect) {
            case HELIX: {
                // two strands, half a turn apart, climbing from below the crate to above it
                double angle = step * 0.28;
                double height = (step % 40L) / 40.0 * (size + 0.8) - (size * 0.5 + 0.4);
                for (int strand = 0; strand < 2; strand++) {
                    double a = angle + strand * Math.PI;
                    world.spawnParticle(Particle.DUST, x + Math.cos(a) * r, y + height, z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0, dust);
                }
                break;
            }
            case RING: {
                double offset = step * 0.12;
                for (int i = 0; i < 6; i++) {
                    double a = offset + i * Math.PI / 3.0;
                    world.spawnParticle(Particle.DUST, x + Math.cos(a) * r, y, z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0, dust);
                }
                break;
            }
            case ORBIT: {
                double a = step * 0.22;
                world.spawnParticle(Particle.END_ROD, x + Math.cos(a) * r, y + Math.sin(a) * 0.45 * size, z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
                world.spawnParticle(Particle.DUST, x - Math.cos(a) * r, y - Math.sin(a) * 0.45 * size, z - Math.sin(a) * r, 2, 0.02, 0.02, 0.02, 0.0, dust);
                break;
            }
            case FOUNTAIN: {
                double top = y + size * 0.5 + 0.1;
                // a jet straight up ...
                world.spawnParticle(Particle.DUST, x, top + (step % 6L) * 0.14, z, 2, 0.04, 0.05, 0.04, 0.0, dust);
                // ... and drops falling in a widening circle
                double a = random.nextDouble(Math.PI * 2.0);
                double d = 0.25 + random.nextDouble(0.55) * size;
                world.spawnParticle(Particle.DUST, x + Math.cos(a) * d, top + 0.75 - d * 0.9, z + Math.sin(a) * d, 1, 0.0, 0.0, 0.0, 0.0, dust);
                break;
            }
            case SPARKLE: {
                if (step % 2L == 0L) {
                    world.spawnParticle(Particle.END_ROD, x + random.nextDouble(-r, r), y + random.nextDouble(-0.6, 0.8) * size, z + random.nextDouble(-r, r), 1, 0.0, 0.0, 0.0, 0.0);
                }
                world.spawnParticle(Particle.DUST, x + random.nextDouble(-r, r), y + random.nextDouble(-0.6, 0.8) * size, z + random.nextDouble(-r, r), 1, 0.0, 0.0, 0.0, 0.0, dust);
                break;
            }
            case FLAMES: {
                double offset = step * 0.1;
                double base = y - size * 0.5 - 0.15;
                for (int i = 0; i < 3; i++) {
                    double a = offset + i * Math.PI * 2.0 / 3.0;
                    world.spawnParticle(Particle.FLAME, x + Math.cos(a) * r, base, z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
                }
                break;
            }
            case ENCHANT:
                // the glyphs fly towards the point they are spawned at
                world.spawnParticle(Particle.ENCHANT, x, y + size * 0.5 + 0.6, z, 4, 0.6, 0.4, 0.6, 0.9);
                break;
            case NONE:
            default:
                break;
        }
    }

    /** A burst in the crate's colour when somebody wins. */
    public static void burst(World world, Location center, CrateColor color) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(color.rgb()), 1.4f);
        world.spawnParticle(Particle.DUST, center, 40, 0.6, 0.6, 0.6, 0.0, dust);
        world.spawnParticle(Particle.END_ROD, center, 20, 0.3, 0.3, 0.3, 0.08);
    }
}
