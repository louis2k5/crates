package org.bcp.crates;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Lets the editor ask an admin to type something (a new name) in the chat. The line is not sent to other players. */
public final class ChatInput implements Listener {
    /** How long the plugin waits for the answer. After that the next chat line is normal chat again. */
    public static final int TIMEOUT_SECONDS = 60;

    private static final class Pending {
        final Consumer<String> answer;
        final long until;

        Pending(Consumer<String> answer) {
            this.answer = answer;
            this.until = System.currentTimeMillis() + TIMEOUT_SECONDS * 1000L;
        }
    }

    private final CratesPlugin plugin;
    private final Map<UUID, Pending> waiting = new ConcurrentHashMap<>();

    public ChatInput(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    public void ask(Player player, Consumer<String> answer) {
        this.waiting.put(player.getUniqueId(), new Pending(answer));
    }

    /** The admin did something else instead of answering: the next chat line is normal chat again. */
    public void cancel(Player player) {
        this.waiting.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Pending pending = this.waiting.remove(event.getPlayer().getUniqueId());
        if (pending == null || System.currentTimeMillis() > pending.until) {
            return; // nothing was asked, or the question is too old: this is normal chat
        }
        event.setCancelled(true);
        String message = event.getMessage();
        // chat runs on another thread: go back to the server thread before touching anything
        Bukkit.getScheduler().runTask(this.plugin, () -> pending.answer.accept(message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.waiting.remove(event.getPlayer().getUniqueId());
    }
}
