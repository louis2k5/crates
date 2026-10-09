package org.bcp.crates;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /key &lt;crate&gt;: a free key for players who have the crate's own permission
 * (crates.key.&lt;crate&gt;), with a long cooldown per crate.
 */
public final class KeyCommand implements CommandExecutor, TabCompleter {
    /** Players with this permission skip the cooldown. Nobody has it by default, not even operators. */
    public static final String BYPASS = "crates.bypass.cooldown";

    private final CratesPlugin plugin;

    public KeyCommand(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean allowed(CommandSender sender, Crate crate) {
        return sender.hasPermission(CrateManager.KEY_PERMISSION + crate.id());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /" + label + ". Use \"crate key <crate> <amount> <player>\" from the console.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            list(player, label);
            return true;
        }
        Crate crate = this.plugin.crates().get(args[0]);
        // an unknown crate and a missing permission give the same answer, so the names of other ranks' crates stay private
        if (crate == null || !allowed(player, crate)) {
            this.plugin.msg(player, "§cYou don't have access to a key called §e" + args[0] + "§c.");
            return true;
        }
        if (player.isDead()) {
            this.plugin.msg(player, "§cYou can't get a key while you are dead.");
            return true;
        }
        boolean bypass = player.hasPermission(BYPASS);
        long wait = bypass ? 0L : this.plugin.cooldowns().remaining(player.getUniqueId(), crate.id());
        if (wait > 0L) {
            this.plugin.msg(player, "§cYou can get your next " + crate.keyName() + " §cin §e" + Text.duration(wait) + "§c.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return true;
        }
        if (player.getInventory().firstEmpty() == -1) {
            this.plugin.msg(player, "§cYour inventory is full. Make some room first, your key is not lost.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return true;
        }
        int amount = Math.max(1, Math.min(64, this.plugin.getConfig().getInt("key-command.amount", 1)));
        player.getInventory().addItem(this.plugin.crates().keyItem(crate, amount));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        long length = this.plugin.cooldowns().length();
        if (bypass || length <= 0L) {
            this.plugin.msg(player, "You received §e" + amount + "x " + crate.keyName() + "§7.");
        } else {
            this.plugin.cooldowns().start(player.getUniqueId(), crate.id());
            this.plugin.msg(player, "You received §e" + amount + "x " + crate.keyName() + "§7. Your next one is ready in §e" + Text.duration(length) + "§7.");
        }
        return true;
    }

    private void list(Player player, String label) {
        List<String> lines = new ArrayList<>();
        boolean bypass = player.hasPermission(BYPASS);
        for (Crate crate : this.plugin.crates().all()) {
            if (!allowed(player, crate)) {
                continue;
            }
            long wait = bypass ? 0L : this.plugin.cooldowns().remaining(player.getUniqueId(), crate.id());
            lines.add("§8 » §e/" + label + " " + crate.id() + " §8- " + crate.keyName() + " §8("
                + (wait > 0L ? "§c" + Text.duration(wait) : "§aready") + "§8)");
        }
        if (lines.isEmpty()) {
            this.plugin.msg(player, "§cYou don't have access to a free key.");
            return;
        }
        this.plugin.msg(player, "Your free keys:");
        for (String line : lines) {
            player.sendMessage(line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String start = args[0].toLowerCase(Locale.ROOT);
            for (Crate crate : this.plugin.crates().all()) {
                if (crate.id().startsWith(start) && allowed(sender, crate)) {
                    out.add(crate.id());
                }
            }
        }
        return out;
    }
}
