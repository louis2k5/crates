package org.bcp.crates;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Keys are candles, but they must never be used as candles: no placing, no cake, no crafting, no renaming. */
public final class KeyListener implements Listener {
    private final CratesPlugin plugin;

    public KeyListener(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean special(ItemStack item) {
        return this.plugin.crates().isKey(item) || this.plugin.crates().isCrateItem(item);
    }

    /** Placing a key as a candle (the crate item itself is handled by Placements). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (this.plugin.crates().isKey(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    /**
     * A candle goes onto a cake without being "placed", so the rule above does not see it: cancel that click.
     * (Cancelling also makes the server send the player's real inventory again, so no key seems to vanish.)
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
            || event.getClickedBlock().getType() != Material.CAKE || !this.plugin.crates().isKey(event.getItem())) {
            return;
        }
        event.setCancelled(true);
    }

    /** No recipe accepts a key or a crate item (dyeing, firework stars, ...). */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (special(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (special(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** The crafter block must not use them either. */
    @EventHandler(ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        BlockState state = event.getBlock().getState();
        if (!(state instanceof InventoryHolder)) {
            return;
        }
        for (ItemStack item : ((InventoryHolder) state).getInventory().getContents()) {
            if (special(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** No renaming on an anvil: the name belongs to the crate. */
    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        if (special(event.getInventory().getItem(0)) || special(event.getInventory().getItem(1))) {
            event.setResult(null);
        }
    }

    /** A dispenser would place the crate item as a normal shulker box. */
    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (this.plugin.crates().isCrateItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** Keys lying in a chest, barrel, shulker box or ender chest get the crate's current colour and name when it is opened. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        Inventory top = event.getInventory();
        InventoryHolder holder = top.getHolder(false);
        if (holder instanceof BlockInventoryHolder || holder instanceof DoubleChest || top.getType() == InventoryType.ENDER_CHEST) {
            this.plugin.crates().syncKeys(top);
        }
    }

    /** The same for a key that is picked up from the ground. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player) || !this.plugin.crates().isKey(event.getItem().getItemStack())) {
            return;
        }
        Player player = (Player) event.getEntity();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
                this.plugin.crates().syncKeys(player);
            }
        });
    }

    /** Keys a player still carries get the current colour and name of their crate. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
                this.plugin.crates().syncKeys(player);
            }
        });
    }
}
