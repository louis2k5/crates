package org.bcp.crates;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Crates in the world. A placed crate is not a real block: it is a shulker box display that floats above the ground
 * and turns, an invisible click box around it, and a name above it. All three are created again whenever the chunk
 * loads and are never saved into the world, so nothing is left behind when the plugin is removed.
 */
public final class Placements implements Listener {
    private static final String FILE = "placed.yml";

    private final CratesPlugin plugin;
    private final NamespacedKey entityTag;
    private final Map<String, Placed> placed = new LinkedHashMap<>();
    /** player -> server tick of the last handled click, so one click is never handled twice */
    private final Map<UUID, Long> lastClick = new HashMap<>();

    /** One crate standing in the world. */
    public static final class Placed {
        final String crateId;
        final String world;
        final int x;
        final int y;
        final int z;
        ItemDisplay box;
        Interaction clickBox;
        TextDisplay label;
        float yaw;
        long retryAt;

        Placed(String crateId, String world, int x, int y, int z) {
            this.crateId = crateId;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        String key() {
            return key(this.world, this.x, this.y, this.z);
        }

        static String key(String world, int x, int y, int z) {
            return world + ";" + x + ";" + y + ";" + z;
        }

        /** @param withLabel true when the name tag has to be there as well */
        boolean alive(boolean withLabel) {
            return this.box != null && this.box.isValid() && this.clickBox != null && this.clickBox.isValid()
                && (!withLabel || (this.label != null && this.label.isValid()));
        }
    }

    public Placements(CratesPlugin plugin) {
        this.plugin = plugin;
        this.entityTag = new NamespacedKey(plugin, "placed");
    }

    // ------------------------------------------------------------------ settings

    private double hover() {
        return this.plugin.getConfig().getDouble("crate.hover-height", 1.0);
    }

    private boolean showName() {
        return this.plugin.getConfig().getBoolean("crate.show-name", true);
    }

    private float scale() {
        return (float) Math.max(0.3, Math.min(3.0, this.plugin.getConfig().getDouble("crate.scale", 1.0)));
    }

    // ------------------------------------------------------------------ storage

    public void load() throws InvalidConfigurationException {
        this.placed.clear();
        YamlConfiguration yaml = this.plugin.storage().read(FILE);
        if (yaml == null) {
            return;
        }
        for (String entry : yaml.getStringList("placed")) {
            String[] parts = entry.split(";");
            if (parts.length != 5) {
                continue;
            }
            try {
                Placed p = new Placed(parts[4], parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                if (this.plugin.crates().get(p.crateId) != null) {
                    this.placed.put(p.key(), p);
                }
            } catch (NumberFormatException ignored) {
                // broken line
            }
        }
    }

    public void save(boolean now) {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (Placed p : this.placed.values()) {
            list.add(p.key() + ";" + p.crateId);
        }
        yaml.set("placed", list);
        this.plugin.storage().write(FILE, yaml.saveToString(), now);
    }

    // ------------------------------------------------------------------ place / remove

    public int count(Crate crate) {
        int n = 0;
        for (Placed p : this.placed.values()) {
            if (p.crateId.equals(crate.id())) {
                n++;
            }
        }
        return n;
    }

    /** @return false when another crate already stands there */
    public boolean place(Crate crate, Block block) {
        Placed p = new Placed(crate.id(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (this.placed.containsKey(p.key())) {
            return false;
        }
        this.placed.put(p.key(), p);
        save(false);
        spawn(p, crate, block.getWorld());
        return true;
    }

    public void remove(Placed p) {
        despawn(p);
        this.placed.remove(p.key());
        save(false);
    }

    /** Removes every placed copy of a crate (used when the crate is deleted). */
    public void removeAll(Crate crate) {
        List<Placed> gone = new ArrayList<>();
        for (Placed p : this.placed.values()) {
            if (p.crateId.equals(crate.id())) {
                gone.add(p);
            }
        }
        for (Placed p : gone) {
            despawn(p);
            this.placed.remove(p.key());
        }
        if (!gone.isEmpty()) {
            save(false);
        }
    }

    /** Applies a new colour or name to the placed copies right away. */
    public void refresh(Crate crate) {
        for (Placed p : this.placed.values()) {
            if (!p.crateId.equals(crate.id())) {
                continue;
            }
            despawn(p);
            World world = Bukkit.getWorld(p.world);
            if (world != null && world.isChunkLoaded(p.x >> 4, p.z >> 4)) {
                spawn(p, crate, world);
            }
        }
    }

    public void shutdown() {
        for (Placed p : this.placed.values()) {
            despawn(p);
        }
    }

    private void despawn(Placed p) {
        if (p.box != null) {
            p.box.remove();
            p.box = null;
        }
        if (p.clickBox != null) {
            p.clickBox.remove();
            p.clickBox = null;
        }
        if (p.label != null) {
            p.label.remove();
            p.label = null;
        }
    }

    /** Middle of the bottom side of the floating crate. */
    private Location base(Placed p, World world) {
        return new Location(world, p.x + 0.5, p.y + hover(), p.z + 0.5);
    }

    /** Middle of the floating crate (for particles and effects). */
    public Location center(Placed p) {
        World world = Bukkit.getWorld(p.world);
        return world == null ? null : base(p, world).add(0.0, scale() * 0.5, 0.0);
    }

    private void spawn(Placed p, Crate crate, World world) {
        float s = scale();
        Location base = base(p, world);
        String tag = p.key();
        try {
            p.box = world.spawn(base, ItemDisplay.class, e -> {
                e.getPersistentDataContainer().set(this.entityTag, PersistentDataType.STRING, tag);
                e.setPersistent(false);
                e.setItemStack(new ItemStack(crate.color().shulker()));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                // the shulker box is drawn around the entity's position: lift it by half its height so that it
                // stands on that point, and it turns around its own middle
                e.setTransformation(new Transformation(new Vector3f(0.0f, 0.5f * s, 0.0f), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
                e.setTeleportDuration(2);
                e.setShadowRadius(0.45f * s);
                e.setShadowStrength(0.6f);
            });
            p.clickBox = world.spawn(base, Interaction.class, e -> {
                e.getPersistentDataContainer().set(this.entityTag, PersistentDataType.STRING, tag);
                e.setPersistent(false);
                e.setInteractionWidth(1.05f * s);
                e.setInteractionHeight(1.05f * s);
                e.setResponsive(true);
            });
            boolean showName = showName();
            if (showName) {
                p.label = world.spawn(base.clone().add(0.0, s + 0.35, 0.0), TextDisplay.class, e -> {
                    e.getPersistentDataContainer().set(this.entityTag, PersistentDataType.STRING, tag);
                    e.setPersistent(false);
                    e.setText(crate.displayName());
                    e.setBillboard(Display.Billboard.CENTER);
                    e.setShadowed(true);
                });
            }
            if (!p.alive(showName)) {
                // something stopped the spawn: clean up and try again later
                despawn(p);
                p.retryAt = System.currentTimeMillis() + 5000L;
            }
        } catch (Throwable t) {
            despawn(p);
            p.retryAt = System.currentTimeMillis() + 30000L;
            this.plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not show the crate '" + crate.id() + "' at " + tag, t);
        }
    }

    // ------------------------------------------------------------------ per tick

    /** Called every 2 ticks: keeps the displays alive, turns them and draws the particles. */
    public void tick(long tick) {
        if (this.placed.isEmpty()) {
            return;
        }
        float turn = (float) this.plugin.getConfig().getDouble("crate.rotation-speed", 3.0) * 2.0f; // this runs every 2 ticks
        boolean showName = showName();
        float s = scale();
        long step = tick / 2L;
        for (Placed p : this.placed.values()) {
            World world = Bukkit.getWorld(p.world);
            if (world == null || !world.isChunkLoaded(p.x >> 4, p.z >> 4)) {
                despawn(p); // the server drops the entities with the chunk, they come back when it loads again
                continue;
            }
            Crate crate = this.plugin.crates().get(p.crateId);
            if (crate == null) {
                despawn(p);
                continue;
            }
            if (!p.alive(showName)) {
                // a part is missing (chunk was reloaded, something killed it): build the whole crate again
                despawn(p);
                if (tick % 20L == 0L && System.currentTimeMillis() >= p.retryAt) {
                    spawn(p, crate, world);
                }
                continue;
            }
            Location base = base(p, world);
            if (!playerNear(world, base)) {
                continue;
            }
            p.yaw = (p.yaw + turn) % 360.0f;
            Location spot = base.clone().add(0.0, Math.sin(step * 0.08) * 0.06, 0.0);
            spot.setYaw(p.yaw);
            p.box.teleport(spot);
            ParticleFx.play(crate.particles(), world, base.clone().add(0.0, s * 0.5, 0.0), crate.color(), step, s);
        }
    }

    private boolean playerNear(World world, Location where) {
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(where) < 48.0 * 48.0) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ clicks

    private Placed of(Entity entity) {
        if (entity == null) {
            return null;
        }
        String tag = entity.getPersistentDataContainer().get(this.entityTag, PersistentDataType.STRING);
        return tag == null ? null : this.placed.get(tag);
    }

    private boolean ours(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(this.entityTag, PersistentDataType.STRING);
    }

    /** True once per click: the client can report the same click through more than one event. */
    private boolean firstThisClick(Player player) {
        long now = this.plugin.currentTick();
        Long last = this.lastClick.put(player.getUniqueId(), now);
        return last == null || now - last >= 3L || now < last;
    }

    /** Right-click: open the crate with a key. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEntityEvent event) {
        Placed p = of(event.getRightClicked());
        if (p == null) {
            if (ours(event.getRightClicked())) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || !firstThisClick(event.getPlayer())) {
            return;
        }
        Crate crate = this.plugin.crates().get(p.crateId);
        if (crate != null) {
            this.plugin.openings().tryOpen(event.getPlayer(), crate, p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClickAt(PlayerInteractAtEntityEvent event) {
        if (ours(event.getRightClicked())) {
            event.setCancelled(true); // handled by the event above
        }
    }

    /** Left-click: show what is inside. Admins who sneak remove the crate instead. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onLeftClick(PrePlayerAttackEntityEvent event) {
        Placed p = of(event.getAttacked());
        if (p == null) {
            if (ours(event.getAttacked())) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        leftClick(event.getPlayer(), p);
    }

    /** Nothing can damage the click box. A hit by a player counts as a left-click too (the server reports it both ways). */
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Interaction || event.getEntity() instanceof Display) || !ours(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        Placed p = of(event.getEntity());
        if (p != null && event instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
            if (damager instanceof Player) {
                leftClick((Player) damager, p);
            }
        }
    }

    private void leftClick(Player player, Placed p) {
        if (!firstThisClick(player)) {
            return;
        }
        Crate crate = this.plugin.crates().get(p.crateId);
        if (crate == null) {
            return;
        }
        if (player.isSneaking() && player.hasPermission(CratesPlugin.ADMIN)) {
            remove(p);
            this.plugin.msg(player, "Removed this " + crate.displayName() + "§7.");
            return;
        }
        this.plugin.menus().openPreview(player, crate);
    }

    /**
     * Placing the shulker box from /crate place sets the crate up instead of putting down a real block.
     * This also runs when a protection plugin has already cancelled the placement: no block is built here,
     * and crates usually stand in protected areas like the spawn. The crates.admin permission decides.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!this.plugin.crates().isCrateItem(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission(CratesPlugin.ADMIN)) {
            this.plugin.msg(player, "§cYou don't have permission to place crates.");
            return;
        }
        Crate crate = this.plugin.crates().crateOfCrateItem(item);
        if (crate == null) {
            this.plugin.msg(player, "§cThis crate no longer exists.");
            return;
        }
        Block block = event.getBlockPlaced();
        if (!place(crate, block)) {
            this.plugin.msg(player, "§cThere is already a crate at this spot.");
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            // the server puts the item back when a placement is cancelled, so take it one tick later
            EquipmentSlot hand = event.getHand();
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                ItemStack inHand = player.getInventory().getItem(hand);
                if (!this.plugin.crates().isCrateItem(inHand)) {
                    return;
                }
                if (inHand.getAmount() > 1) {
                    inHand.setAmount(inHand.getAmount() - 1);
                    player.getInventory().setItem(hand, inHand);
                } else {
                    player.getInventory().setItem(hand, null);
                }
            });
        }
        this.plugin.msg(player, "Placed the " + crate.displayName() + "§7. Change it any time with §e/crate gui§7.");
    }
}
