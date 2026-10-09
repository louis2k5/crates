package org.bcp.crates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Items for players who are dead at the moment (on the death screen). Whatever is put into a dead player's
 * inventory is wiped by the respawn, so prizes and keys wait here and are handed over right after the respawn.
 */
public final class Deliveries implements Listener {
    private static final class Waiting {
        final ItemStack item;
        final boolean dropIfFull;

        Waiting(ItemStack item, boolean dropIfFull) {
            this.item = item;
            this.dropIfFull = dropIfFull;
        }
    }

    private final CratesPlugin plugin;
    private final Map<UUID, List<Waiting>> waiting = new HashMap<>();

    public Deliveries(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param dropIfFull true: what does not fit is dropped at the player's feet. false: it is not handed out at all
     *                   (the rule of Key All for full inventories)
     */
    public void afterRespawn(Player player, ItemStack item, boolean dropIfFull) {
        this.waiting.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(new Waiting(item.clone(), dropIfFull));
    }

    /** How many items wait for this player. */
    public int waitingFor(UUID player) {
        List<Waiting> list = this.waiting.get(player);
        return list == null ? 0 : list.size();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!this.waiting.containsKey(id)) {
            return;
        }
        // one tick later the respawn is complete; the player is looked up again because the server may use a new object
        Bukkit.getScheduler().runTask(this.plugin, () -> hand(id));
    }

    private void hand(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player == null || player.isDead()) {
            return; // left or died again: the items keep waiting
        }
        List<Waiting> list = this.waiting.remove(id);
        if (list == null) {
            return;
        }
        boolean missed = false;
        for (Waiting w : list) {
            if (!w.dropIfFull && player.getInventory().firstEmpty() == -1) {
                missed = true;
                continue;
            }
            for (ItemStack left : player.getInventory().addItem(w.item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
        this.plugin.msg(player, "You received what was waiting for you while you were dead."
            + (missed ? " §cYour inventory was full, so not everything could be handed out." : ""));
    }
}
