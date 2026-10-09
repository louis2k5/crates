package org.bcp.crates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** /crate: everything an admin does with crates. */
public final class CrateCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = Arrays.asList(
        "create", "delete", "gui", "key", "place", "keyall", "list", "resetcooldown", "reload");
    private static final List<String> KEYALL = Arrays.asList("start", "stop", "add", "remove", "status", "now", "reset");

    private final CratesPlugin plugin;

    public CrateCommand(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(CratesPlugin.ADMIN)) {
            this.plugin.msg(sender, "§cYou don't have permission to do this.");
            return true;
        }
        if (sender instanceof Player) {
            this.plugin.chatInput().cancel((Player) sender); // a pending "type the new name" question is over
        }
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, label, args);
            case "delete" -> delete(sender, label, args);
            case "gui" -> gui(sender, args);
            case "key" -> key(sender, label, args);
            case "place" -> place(sender, label, args);
            case "keyall" -> keyAll(sender, label, args);
            case "list" -> list(sender);
            case "resetcooldown" -> resetCooldown(sender, label, args);
            case "reload" -> {
                this.plugin.reloadSettings();
                this.plugin.msg(sender, "The configuration was reloaded.");
            }
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        this.plugin.msg(sender, "Commands:");
        String p = "§8 » §e/" + label + " ";
        sender.sendMessage(p + "create <name> §8- §7create a crate");
        sender.sendMessage(p + "delete <name> §8- §7delete a crate");
        sender.sendMessage(p + "gui §8- §7edit all crates");
        sender.sendMessage(p + "key <crate> [amount] [player|all] §8- §7give keys");
        sender.sendMessage(p + "place <name> §8- §7get the crate to place it");
        sender.sendMessage(p + "keyall <start|stop|add|remove|status|now|reset> §8- §7free keys for everyone");
        sender.sendMessage(p + "list §8- §7all crates");
        sender.sendMessage(p + "resetcooldown <player> [crate] §8- §7end a /key cooldown");
        sender.sendMessage(p + "reload §8- §7reload config.yml");
    }

    /** Looks the crate up and tells the sender when it does not exist. */
    private Crate crate(CommandSender sender, String name) {
        Crate crate = this.plugin.crates().get(name);
        if (crate == null) {
            this.plugin.msg(sender, "§cThere is no crate called §e" + name + "§c.");
        }
        return crate;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        this.plugin.msg(sender, "§cOnly players can do this.");
        return null;
    }

    // ------------------------------------------------------------------ create / delete / gui / list

    private void create(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            this.plugin.msg(sender, "§cUsage: /" + label + " create <name>");
            return;
        }
        String name = args[1];
        if (!CrateManager.validId(name)) {
            this.plugin.msg(sender, "§cA crate name has 1 to 24 characters: letters, numbers, _ and -. §7You can give it a coloured name in the menu.");
            return;
        }
        if (this.plugin.crates().get(name) != null) {
            this.plugin.msg(sender, "§cThere already is a crate called §e" + name + "§c.");
            return;
        }
        Crate crate = this.plugin.crates().create(name);
        if (crate == null) {
            this.plugin.msg(sender, "§cThe crate could not be created.");
            return;
        }
        this.plugin.msg(sender, "Created the crate §e" + crate.id() + "§7. Players with the permission §e"
            + CrateManager.KEY_PERMISSION + crate.id() + " §7can use §e/key " + crate.id() + "§7.");
        this.plugin.menus().refreshLists();
        if (sender instanceof Player) {
            this.plugin.menus().openCrate((Player) sender, crate);
        } else {
            this.plugin.msg(sender, "Set it up in the game with §e/crate gui§7.");
        }
    }

    private void delete(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            this.plugin.msg(sender, "§cUsage: /" + label + " delete <name>");
            return;
        }
        Crate crate = crate(sender, args[1]);
        if (crate == null) {
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            int placed = this.plugin.placements().count(crate);
            this.plugin.msg(sender, "§cThis deletes the crate §e" + crate.id() + " §cwith its " + crate.rewards().size()
                + " reward(s)" + (placed > 0 ? " and removes it from " + placed + " place(s) in the world" : "")
                + ". Type §e/" + label + " delete " + crate.id() + " confirm §cto do it.");
            return;
        }
        boolean wasRunning = this.plugin.keyAll().running();
        this.plugin.menus().closeFor(crate.id());
        this.plugin.placements().removeAll(crate);
        this.plugin.keyAll().remove(crate.id());
        this.plugin.cooldowns().forget(crate.id());
        this.plugin.crates().delete(crate);
        this.plugin.menus().refreshLists();
        this.plugin.msg(sender, "Deleted the crate §e" + crate.id() + "§7. Its keys open nothing any more "
            + "§8(they would fit again if you create a crate with the same name).");
        if (wasRunning && !this.plugin.keyAll().running()) {
            this.plugin.msg(sender, "§cKey All was stopped, because its last key belonged to this crate.");
        }
    }

    private void gui(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length >= 2) {
            Crate crate = crate(sender, args[1]);
            if (crate != null) {
                this.plugin.menus().openCrate(player, crate);
            }
            return;
        }
        this.plugin.menus().openList(player);
    }

    private void list(CommandSender sender) {
        Collection<Crate> crates = this.plugin.crates().all();
        if (crates.isEmpty()) {
            this.plugin.msg(sender, "There are no crates yet. Create one with §e/crate create <name>§7.");
            return;
        }
        this.plugin.msg(sender, "Crates (" + crates.size() + "):");
        for (Crate crate : crates) {
            sender.sendMessage("§8 » §e" + crate.id() + " §8- " + crate.displayName() + " §8(§7" + crate.rewards().size()
                + "/" + Crate.MAX_REWARDS + " rewards, placed " + this.plugin.placements().count(crate) + "x§8)");
        }
    }

    // ------------------------------------------------------------------ key / place

    private void key(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            this.plugin.msg(sender, "§cUsage: /" + label + " key <crate> [amount] [player|all]");
            return;
        }
        Crate crate = crate(sender, args[1]);
        if (crate == null) {
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                amount = -1;
            }
            if (amount < 1 || amount > 64) {
                this.plugin.msg(sender, "§cThe amount has to be a number from 1 to 64.");
                return;
            }
        }
        List<Player> targets = new ArrayList<>();
        boolean everyone = false;
        if (args.length >= 4) {
            if (args[3].equalsIgnoreCase("all") || args[3].equals("*")) {
                everyone = true;
                targets.addAll(Bukkit.getOnlinePlayers());
            } else {
                Player target = Bukkit.getPlayerExact(args[3]);
                if (target == null) {
                    this.plugin.msg(sender, "§cThe player §e" + args[3] + " §cis not online.");
                    return;
                }
                targets.add(target);
            }
        } else if (sender instanceof Player) {
            targets.add((Player) sender);
        } else {
            this.plugin.msg(sender, "§cUsage from the console: " + label + " key <crate> <amount> <player|all>");
            return;
        }
        String what = "§e" + amount + "x " + crate.keyName();
        for (Player target : targets) {
            if (target.isDead()) {
                // the respawn would wipe it: hand it over right after
                this.plugin.deliveries().afterRespawn(target, this.plugin.crates().keyItem(crate, amount), true);
                this.plugin.msg(target, "You get " + what + " §7as soon as you respawn.");
                continue;
            }
            boolean dropped = false;
            for (ItemStack left : target.getInventory().addItem(this.plugin.crates().keyItem(crate, amount)).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), left);
                dropped = true;
            }
            if (target != sender) {
                this.plugin.msg(target, "You received " + what + "§7." + (dropped ? " §cYour inventory is full, it lies at your feet." : ""));
            }
        }
        if (everyone) {
            this.plugin.msg(sender, "Gave " + what + " §7to all " + targets.size() + " online player(s).");
        } else if (targets.get(0) == sender) {
            this.plugin.msg(sender, "You received " + what + "§7.");
        } else {
            this.plugin.msg(sender, "Gave " + what + " §7to §e" + targets.get(0).getName() + "§7.");
        }
    }

    private void place(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            this.plugin.msg(sender, "§cUsage: /" + label + " place <name>");
            return;
        }
        Crate crate = crate(sender, args[1]);
        if (crate == null) {
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            this.plugin.msg(player, "§cYour inventory is full.");
            return;
        }
        player.getInventory().addItem(this.plugin.crates().crateItem(crate));
        this.plugin.msg(player, "You received the " + crate.displayName() + "§7. Place it where the crate should float. "
            + "§8Sneak + left-click removes a placed crate again.");
    }

    // ------------------------------------------------------------------ key all

    private void keyAll(CommandSender sender, String label, String[] args) {
        KeyAll keyAll = this.plugin.keyAll();
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "start" -> {
                if (keyAll.running()) {
                    this.plugin.msg(sender, "§cKey All is already running. §7Next keys in §e" + Text.clock(keyAll.remaining()) + "§7.");
                } else if (!keyAll.start()) {
                    this.plugin.msg(sender, "§cKey All has no key yet. Add one first: §e/" + label + " keyall add <crate>");
                } else {
                    this.plugin.msg(sender, "Key All is running. Next keys in §e" + Text.clock(keyAll.remaining()) + "§7.");
                }
            }
            case "stop" -> {
                if (!keyAll.running()) {
                    this.plugin.msg(sender, "§cKey All is not running.");
                } else {
                    keyAll.stop();
                    this.plugin.msg(sender, "Key All is paused at §e" + Text.clock(keyAll.remaining()) + "§7. §e/" + label + " keyall start §7continues from there.");
                }
            }
            case "add" -> {
                if (args.length < 3) {
                    this.plugin.msg(sender, "§cUsage: /" + label + " keyall add <crate>");
                    return;
                }
                Crate crate = crate(sender, args[2]);
                if (crate == null) {
                    return;
                }
                if (!keyAll.add(crate)) {
                    this.plugin.msg(sender, "§cThe " + crate.keyName() + " §cis already part of Key All.");
                    return;
                }
                this.plugin.msg(sender, "Added the " + crate.keyName() + " §7to Key All."
                    + (keyAll.running() ? "" : " Start it with §e/" + label + " keyall start§7."));
            }
            case "remove" -> {
                if (args.length < 3) {
                    this.plugin.msg(sender, "§cUsage: /" + label + " keyall remove <crate>");
                    return;
                }
                boolean wasRunning = keyAll.running();
                if (!keyAll.remove(args[2])) {
                    this.plugin.msg(sender, "§cThe key of §e" + args[2] + " §cis not part of Key All.");
                    return;
                }
                this.plugin.msg(sender, "Removed the key of §e" + args[2].toLowerCase(Locale.ROOT) + " §7from Key All.");
                if (wasRunning && !keyAll.running()) {
                    this.plugin.msg(sender, "§cKey All was stopped, because no key is left.");
                }
            }
            case "now" -> {
                if (keyAll.crates().isEmpty()) {
                    this.plugin.msg(sender, "§cKey All has no key yet. Add one first: §e/" + label + " keyall add <crate>");
                    return;
                }
                int receivers = keyAll.distribute();
                this.plugin.msg(sender, "Handed the keys out to " + receivers + " player(s). The timer was not changed.");
            }
            case "reset" -> {
                keyAll.reset();
                this.plugin.msg(sender, "The Key All timer is back at §e" + Text.clock(keyAll.remaining()) + "§7.");
            }
            case "status" -> {
                List<String> keys = new ArrayList<>();
                for (String id : keyAll.crates()) {
                    Crate crate = this.plugin.crates().get(id);
                    keys.add(crate == null ? "§7" + id : crate.keyName());
                }
                this.plugin.msg(sender, "Key All: " + (keyAll.running() ? "§arunning" : "§cpaused")
                    + "§7, next keys in §e" + Text.clock(keyAll.remaining())
                    + "§7, every §e" + Text.duration(keyAll.interval() * 1000L) + "§7.");
                this.plugin.msg(sender, "Keys: " + (keys.isEmpty() ? "§cnone" : String.join("§7, ", keys)));
            }
            default -> this.plugin.msg(sender, "§cUsage: /" + label + " keyall <start|stop|add <crate>|remove <crate>|status|now|reset>");
        }
    }

    // ------------------------------------------------------------------ cooldown of /key

    private void resetCooldown(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            this.plugin.msg(sender, "§cUsage: /" + label + " resetcooldown <player> [crate]");
            return;
        }
        UUID id;
        String name;
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null) {
            id = online.getUniqueId();
            name = online.getName();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (offline == null) {
                this.plugin.msg(sender, "§cThe player §e" + args[1] + " §cwas never on this server.");
                return;
            }
            id = offline.getUniqueId();
            name = offline.getName() == null ? args[1] : offline.getName();
        }
        String crateId = null;
        if (args.length >= 3) {
            Crate crate = crate(sender, args[2]);
            if (crate == null) {
                return;
            }
            crateId = crate.id();
        }
        if (this.plugin.cooldowns().clear(id, crateId)) {
            this.plugin.msg(sender, "§e" + name + " §7can use §e/key" + (crateId == null ? "" : " " + crateId) + " §7again right away.");
        } else {
            this.plugin.msg(sender, "§e" + name + " §7has no cooldown" + (crateId == null ? "" : " for §e" + crateId) + "§7.");
        }
    }

    // ------------------------------------------------------------------ tab completion

    private static List<String> filter(Collection<String> options, String typed) {
        String start = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(start)) {
                out.add(option);
            }
        }
        return out;
    }

    private static List<String> playerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(CratesPlugin.ADMIN) || args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> ids = this.plugin.crates().ids();
        if (args.length == 2) {
            switch (sub) {
                case "delete", "gui", "key", "place":
                    return filter(ids, args[1]);
                case "keyall":
                    return filter(KEYALL, args[1]);
                case "resetcooldown":
                    return filter(playerNames(), args[1]);
                default:
                    return Collections.emptyList();
            }
        }
        if (args.length == 3) {
            switch (sub) {
                case "delete":
                    return filter(Collections.singletonList("confirm"), args[2]);
                case "key":
                    return filter(Arrays.asList("1", "5", "10", "32", "64"), args[2]);
                case "resetcooldown":
                    return filter(ids, args[2]);
                case "keyall":
                    if (args[1].equalsIgnoreCase("add")) {
                        ids.removeAll(this.plugin.keyAll().crates());
                        return filter(ids, args[2]);
                    }
                    if (args[1].equalsIgnoreCase("remove")) {
                        return filter(this.plugin.keyAll().crates(), args[2]);
                    }
                    return Collections.emptyList();
                default:
                    return Collections.emptyList();
            }
        }
        if (args.length == 4 && sub.equals("key")) {
            List<String> names = playerNames();
            names.add("all");
            return filter(names, args[3]);
        }
        return Collections.emptyList();
    }
}
