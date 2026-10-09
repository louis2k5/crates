package org.bcp.crates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** The /crate gui editor (admins) and the crate preview (everybody). */
public final class Menus implements Listener {
    /** Where the 30 rewards sit in the reward editor, the chance editor and the preview: rows 1-3 and the middle of row 4. */
    static final int[] REWARD_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        30, 31, 32};

    private enum Kind { LIST, CRATE, SELECT, ITEMS, CHANCES, PREVIEW }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final Kind kind;
        private final String crateId;
        private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

        Holder(Kind kind, String crateId) {
            this.kind = kind;
            this.crateId = crateId;
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }

    private final CratesPlugin plugin;

    public Menus(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ helpers

    static ItemStack item(ItemStack base, String name, List<String> lore, boolean glow) {
        ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            if (glow) {
                meta.setEnchantmentGlintOverride(Boolean.TRUE);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            base.setItemMeta(meta);
        }
        return base;
    }

    private static List<String> lore(String... lines) {
        List<String> list = new ArrayList<>();
        for (String line : lines) {
            list.add(line);
        }
        return list;
    }

    private static ItemStack filler(Material material) {
        return item(new ItemStack(material), " ", null, false);
    }

    private static void fill(Inventory inv, Material material) {
        ItemStack pane = filler(material);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    private static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    private static int rewardIndex(int slot) {
        for (int i = 0; i < REWARD_SLOTS.length; i++) {
            if (REWARD_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private void give(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    private Inventory create(Holder holder, int size, String title) {
        holder.inventory = Bukkit.createInventory(holder, size, title);
        return holder.inventory;
    }

    // ------------------------------------------------------------------ list of all crates

    public void openList(Player player) {
        this.plugin.chatInput().cancel(player);
        openList(player, 0);
    }

    /** Draws the crate list again for everybody who has it open (after a crate was created or deleted). */
    public void refreshLists() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Holder holder = holderOf(player.getOpenInventory().getTopInventory());
            if (holder != null && holder.kind == Kind.LIST) {
                openList(player, 0);
            }
        }
    }

    private void openList(Player player, int page) {
        Holder holder = new Holder(Kind.LIST, null);
        Inventory inv = create(holder, 54, "§8Crates");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        List<Crate> crates = new ArrayList<>(this.plugin.crates().all());
        int pages = Math.max(1, (crates.size() + 44) / 45);
        int current = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < 45; i++) {
            int index = current * 45 + i;
            if (index >= crates.size()) {
                break;
            }
            Crate crate = crates.get(index);
            inv.setItem(i, item(new ItemStack(crate.color().shulker()), crate.displayName(), lore(
                    "§8Name: " + crate.id(),
                    "§7Rewards: §e" + crate.rewards().size() + "§7/§e" + Crate.MAX_REWARDS,
                    "§7Placed: §e" + this.plugin.placements().count(crate),
                    "§7Key: " + crate.keyName(),
                    "",
                    "§e» Click to edit"), false));
            holder.actions.put(i, e -> {
                click(player);
                // the list may be older than the crate: somebody else can have deleted it in the meantime
                if (this.plugin.crates().get(crate.id()) == crate) {
                    openCrate(player, crate);
                } else {
                    openList(player, current);
                }
            });
        }
        inv.setItem(49, item(new ItemStack(Material.BOOK), "§6§lCrates", lore(
                "§7Create a crate with",
                "§e/crate create <name>",
                "",
                "§7Page §e" + (current + 1) + "§7/§e" + pages), false));
        if (current > 0) {
            inv.setItem(45, item(new ItemStack(Material.ARROW), "§e« Previous page", null, false));
            holder.actions.put(45, e -> openList(player, current - 1));
        }
        if (current < pages - 1) {
            inv.setItem(53, item(new ItemStack(Material.ARROW), "§eNext page »", null, false));
            holder.actions.put(53, e -> openList(player, current + 1));
        }
        player.openInventory(inv);
    }

    // ------------------------------------------------------------------ one crate

    public void openCrate(Player player, Crate crate) {
        this.plugin.chatInput().cancel(player);
        Holder holder = new Holder(Kind.CRATE, crate.id());
        Inventory inv = create(holder, 36, "§8Crate » " + crate.id());
        fill(inv, Material.GRAY_STAINED_GLASS_PANE);

        inv.setItem(4, item(new ItemStack(crate.color().shulker()), crate.displayName(), lore(
                "§8Name: " + crate.id(),
                "§7Rewards: §e" + crate.rewards().size() + "§7/§e" + Crate.MAX_REWARDS,
                "§7Placed: §e" + this.plugin.placements().count(crate),
                "§7/key permission: §f" + CrateManager.KEY_PERMISSION + crate.id()), true));

        inv.setItem(10, item(new ItemStack(Material.NAME_TAG), "§6§lCrate Name", lore(
                "§7Now: " + crate.displayName(), "", "§e» Click to type a new name", "§8Colour codes like &6&l work."), false));
        holder.actions.put(10, e -> rename(player, crate, false));

        inv.setItem(11, item(new ItemStack(crate.color().candle()), "§6§lKey Name", lore(
                "§7Now: " + crate.keyName(), "", "§e» Click to type a new name"), true));
        holder.actions.put(11, e -> rename(player, crate, true));

        inv.setItem(12, item(new ItemStack(crate.color().shulker()), "§6§lColour", lore(
                "§7Now: " + crate.color().chat() + crate.color().label(),
                "§7The placed crate, the key and the",
                "§7opening window use this colour.", "", "§e» Click to change"), false));
        holder.actions.put(12, e -> openColors(player, crate));

        inv.setItem(13, item(new ItemStack(Material.CHEST), "§6§lRewards", lore(
                "§7Items: §e" + crate.rewards().size() + "§7/§e" + Crate.MAX_REWARDS, "", "§e» Click to add or remove items"), false));
        holder.actions.put(13, e -> openItems(player, crate));

        inv.setItem(14, item(new ItemStack(Material.EMERALD), "§6§lChances", lore(
                "§7How often each item comes up.", "", "§e» Click to set the chances"), false));
        holder.actions.put(14, e -> openChances(player, crate));

        inv.setItem(15, item(new ItemStack(crate.animation().icon()), "§6§lOpening Animation", lore(
                "§7Now: §f" + crate.animation().label(), "", "§e» Click to change"), false));
        holder.actions.put(15, e -> openAnimations(player, crate));

        inv.setItem(16, item(new ItemStack(crate.particles().icon()), "§6§lParticles", lore(
                "§7Now: §f" + crate.particles().label(), "§7Shown around the placed crate.", "", "§e» Click to change"), false));
        holder.actions.put(16, e -> openParticles(player, crate));

        inv.setItem(19, item(new ItemStack(crate.sound().icon()), "§6§lOpening Sound", lore(
                "§7Now: §f" + crate.sound().label(), "", "§e» Click to change"), false));
        holder.actions.put(19, e -> openSounds(player, crate));

        inv.setItem(21, item(new ItemStack(Material.SPYGLASS), "§b§lPreview", lore(
                "§7What players see with a left-click."), false));
        holder.actions.put(21, e -> openPreview(player, crate));

        inv.setItem(22, item(new ItemStack(Material.ENDER_EYE), "§b§lTest Opening", lore(
                "§7Opens the crate once without a key.", "§7You keep the prize."), false));
        holder.actions.put(22, e -> {
            if (!crate.hasRewards()) {
                this.plugin.msg(player, "§cThis crate has no rewards yet.");
                return;
            }
            this.plugin.openings().start(player, crate, null);
        });

        inv.setItem(23, item(this.plugin.crates().keyItem(crate, 1), "§a§lGet Keys", lore(
                "§e» Click for 1 key", "§e» Shift-click for 16 keys"), true));
        holder.actions.put(23, e -> {
            int amount = e.isShiftClick() ? 16 : 1;
            give(player, this.plugin.crates().keyItem(crate, amount));
            this.plugin.msg(player, "You received §e" + amount + "x " + crate.keyName() + "§7.");
        });

        inv.setItem(24, item(this.plugin.crates().crateItem(crate), "§a§lGet Crate", lore(
                "§7Gives you the shulker box.", "§7Place it to set the crate up."), true));
        holder.actions.put(24, e -> {
            give(player, this.plugin.crates().crateItem(crate));
            this.plugin.msg(player, "You received the " + crate.displayName() + "§7. Place it where the crate should stand.");
        });

        inv.setItem(27, item(new ItemStack(Material.ARROW), "§e« Back", lore("§7All crates"), false));
        holder.actions.put(27, e -> openList(player));

        player.openInventory(inv);
    }

    private void rename(Player player, Crate crate, boolean key) {
        player.closeInventory();
        this.plugin.msg(player, key
                ? "Type the new §ekey name §7in the chat (you have " + ChatInput.TIMEOUT_SECONDS + " seconds). Colour codes like §e&6&l §7work. §ereset §7= name of the crate + \"Key\", §ecancel §7= keep it."
                : "Type the new §ecrate name §7in the chat (you have " + ChatInput.TIMEOUT_SECONDS + " seconds). Colour codes like §e&6&l §7work. §ecancel §7= keep it.");
        this.plugin.chatInput().ask(player, input -> {
            Crate current = this.plugin.crates().get(crate.id());
            if (current == null) {
                return;
            }
            String text = input.trim();
            if (text.equalsIgnoreCase("cancel") || text.isEmpty()) {
                this.plugin.msg(player, "Nothing changed.");
            } else if (text.length() > 64) {
                this.plugin.msg(player, "§cThat name is too long (64 characters at most).");
            } else if (key) {
                current.keyName(text.equalsIgnoreCase("reset") ? null : text);
                this.plugin.crates().appearanceChanged(current);
                this.plugin.msg(player, "The key is now called " + current.keyName() + "§7.");
            } else {
                current.displayName(text);
                this.plugin.crates().appearanceChanged(current);
                this.plugin.msg(player, "The crate is now called " + current.displayName() + "§7.");
            }
            openCrate(player, current);
        });
    }

    // ------------------------------------------------------------------ pick-one menus

    private interface Option {
        ItemStack icon();

        String name();

        String[] description();

        boolean selected();

        void choose();
    }

    private void openSelect(Player player, Crate crate, String title, List<Option> options) {
        Holder holder = new Holder(Kind.SELECT, crate.id());
        Inventory inv = create(holder, 27, "§8" + title + " » " + crate.id());
        renderSelect(holder, player, crate, title, options);
        player.openInventory(inv);
    }

    private void renderSelect(Holder holder, Player player, Crate crate, String title, List<Option> options) {
        Inventory inv = holder.inventory;
        holder.actions.clear();
        fill(inv, Material.GRAY_STAINED_GLASS_PANE);
        int n = options.size();
        int start = n > 9 ? 0 : 9 + (9 - n) / 2;
        for (int i = 0; i < n; i++) {
            Option option = options.get(i);
            List<String> text = new ArrayList<>();
            for (String line : option.description()) {
                text.add("§7" + line);
            }
            if (!text.isEmpty()) {
                text.add("");
            }
            text.add(option.selected() ? "§a» Selected" : "§e» Click to select");
            inv.setItem(start + i, item(option.icon(), (option.selected() ? "§a§l" : "§6§l") + option.name(), text, option.selected()));
            holder.actions.put(start + i, e -> {
                option.choose();
                click(player);
                renderSelect(holder, player, crate, title, options);
            });
        }
        inv.setItem(22, item(new ItemStack(Material.ARROW), "§e« Back", null, false));
        holder.actions.put(22, e -> openCrate(player, crate));
    }

    private void openColors(Player player, Crate crate) {
        List<Option> options = new ArrayList<>();
        for (CrateColor color : CrateColor.values()) {
            options.add(new Option() {
                public ItemStack icon() { return new ItemStack(color.shulker()); }
                public String name() { return color.label(); }
                public String[] description() { return new String[0]; }
                public boolean selected() { return crate.color() == color; }
                public void choose() {
                    crate.color(color);
                    plugin.crates().appearanceChanged(crate);
                }
            });
        }
        openSelect(player, crate, "Colour", options);
    }

    private void openAnimations(Player player, Crate crate) {
        List<Option> options = new ArrayList<>();
        for (OpenAnimation animation : OpenAnimation.values()) {
            options.add(new Option() {
                public ItemStack icon() { return new ItemStack(animation.icon()); }
                public String name() { return animation.label(); }
                public String[] description() { return animation.description(); }
                public boolean selected() { return crate.animation() == animation; }
                public void choose() {
                    crate.animation(animation);
                    plugin.crates().save(false);
                }
            });
        }
        openSelect(player, crate, "Opening Animation", options);
    }

    private void openParticles(Player player, Crate crate) {
        List<Option> options = new ArrayList<>();
        for (ParticleEffect effect : ParticleEffect.values()) {
            options.add(new Option() {
                public ItemStack icon() { return new ItemStack(effect.icon()); }
                public String name() { return effect.label(); }
                public String[] description() { return effect.description(); }
                public boolean selected() { return crate.particles() == effect; }
                public void choose() {
                    crate.particles(effect);
                    plugin.crates().save(false);
                }
            });
        }
        openSelect(player, crate, "Particles", options);
    }

    private void openSounds(Player player, Crate crate) {
        List<Option> options = new ArrayList<>();
        for (OpenSound sound : OpenSound.values()) {
            options.add(new Option() {
                public ItemStack icon() { return new ItemStack(sound.icon()); }
                public String name() { return sound.label(); }
                public String[] description() { return new String[]{"Click to hear it."}; }
                public boolean selected() { return crate.sound() == sound; }
                public void choose() {
                    crate.sound(sound);
                    plugin.crates().save(false);
                    player.playSound(player.getLocation(), sound.tick(), 0.8f, sound.tickPitch());
                    player.playSound(player.getLocation(), sound.win(), 0.8f, sound.winPitch());
                }
            });
        }
        openSelect(player, crate, "Opening Sound", options);
    }

    // ------------------------------------------------------------------ rewards: which items

    private void openItems(Player player, Crate crate) {
        Holder holder = new Holder(Kind.ITEMS, crate.id());
        Inventory inv = create(holder, 54, "§8Rewards » " + crate.id());
        fill(inv, Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < REWARD_SLOTS.length; i++) {
            Crate.Reward reward = crate.reward(i);
            inv.setItem(REWARD_SLOTS[i], reward == null ? null : reward.item().clone());
        }
        inv.setItem(45, item(new ItemStack(Material.BOOK), "§6§lHow it works", lore(
                "§7Put items from your inventory into",
                "§7the 30 free slots, or take them out.",
                "§7Everything is saved when you close.",
                "",
                "§7New items start with a chance of §e" + defaultChance() + "%§7.",
                "§7Change it under §eChances§7."), false));
        inv.setItem(49, item(new ItemStack(Material.LIME_DYE), "§a§lSave", lore("§7Back to the crate"), false));
        holder.actions.put(49, e -> {
            click(player);
            saveItems(holder); // first, so that the crate's menu already shows the new number of rewards
            Crate current = this.plugin.crates().get(crate.id());
            if (current != null) {
                openCrate(player, current);
            } else {
                player.closeInventory();
            }
        });
        inv.setItem(53, item(new ItemStack(Material.EMERALD), "§6§lChances", lore("§7Save and set the chances"), false));
        holder.actions.put(53, e -> {
            click(player);
            saveItems(holder);
            Crate current = this.plugin.crates().get(crate.id());
            if (current != null) {
                openChances(player, current);
            }
        });
        player.openInventory(inv);
    }

    private int defaultChance() {
        return Math.max(1, Math.min(100, this.plugin.getConfig().getInt("default-chance", 50)));
    }

    /**
     * Takes over what lies in the 30 slots. The chance belongs to the item, not to the slot: an item keeps its
     * chance when it stays where it is or is moved to another slot, and an item that is new gets the default chance.
     */
    private void saveItems(Holder holder) {
        Crate crate = this.plugin.crates().get(holder.crateId);
        if (crate == null) {
            return;
        }
        int n = REWARD_SLOTS.length;
        Crate.Reward[] old = new Crate.Reward[n];
        ItemStack[] now = new ItemStack[n];
        for (int i = 0; i < n; i++) {
            old[i] = crate.reward(i);
            ItemStack item = holder.inventory.getItem(REWARD_SLOTS[i]);
            now[i] = item == null || item.getType() == Material.AIR || item.getAmount() <= 0 ? null : item;
        }
        int[] chance = new int[n]; // 0 = not known yet
        boolean[] taken = new boolean[n];
        for (int i = 0; i < n; i++) { // items that stayed in their slot
            if (now[i] != null && old[i] != null && old[i].item().isSimilar(now[i])) {
                chance[i] = old[i].chance();
                taken[i] = true;
            }
        }
        for (int i = 0; i < n; i++) { // items that were moved
            if (now[i] == null || chance[i] != 0) {
                continue;
            }
            for (int j = 0; j < n; j++) {
                if (!taken[j] && old[j] != null && old[j].item().isSimilar(now[i])) {
                    chance[i] = old[j].chance();
                    taken[j] = true;
                    break;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            crate.reward(i, now[i] == null ? null : new Crate.Reward(now[i].clone(), chance[i] != 0 ? chance[i] : defaultChance()));
        }
        this.plugin.crates().save(false);
    }

    // ------------------------------------------------------------------ rewards: how likely

    private void openChances(Player player, Crate crate) {
        Holder holder = new Holder(Kind.CHANCES, crate.id());
        Inventory inv = create(holder, 54, "§8Chances » " + crate.id());
        renderChances(holder, player, crate);
        player.openInventory(inv);
    }

    private void renderChances(Holder holder, Player player, Crate crate) {
        Inventory inv = holder.inventory;
        holder.actions.clear();
        fill(inv, Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < REWARD_SLOTS.length; i++) {
            int index = i;
            Crate.Reward reward = crate.reward(i);
            if (reward == null) {
                inv.setItem(REWARD_SLOTS[i], null);
                continue;
            }
            inv.setItem(REWARD_SLOTS[i], chanceIcon(reward));
            holder.actions.put(REWARD_SLOTS[i], e -> {
                Crate.Reward r = crate.reward(index);
                if (r == null) {
                    return;
                }
                int delta = (e.isShiftClick() ? 10 : 1) * (e.isRightClick() ? -1 : 1);
                r.chance(r.chance() + delta);
                this.plugin.crates().save(false);
                click(player);
                holder.inventory.setItem(REWARD_SLOTS[index], chanceIcon(r));
            });
        }
        inv.setItem(45, item(new ItemStack(Material.BOOK), "§6§lHow it works", lore(
                "§eLeft-click §7+1    §eRight-click §7-1",
                "§eShift §7changes it by 10.",
                "",
                "§7100 = comes up very often, 1 = almost never.",
                "§7The numbers are weights: an item with 100",
                "§7comes up 20 times as often as one with 5.",
                "",
                "§7Players only see the rarity:",
                "§8100-80 " + Rarity.COMMON.display() + "  §879-60 " + Rarity.RARE.display(),
                "§859-40 " + Rarity.SUPERRARE.display() + "  §839-20 " + Rarity.EPIC.display(),
                "§819-8 " + Rarity.LEGENDARY.display() + "  §87-1 " + Rarity.MYTHIC.display()), false));
        inv.setItem(49, item(new ItemStack(Material.ARROW), "§e« Back", lore("§7Back to the crate"), false));
        holder.actions.put(49, e -> openCrate(player, crate));
        inv.setItem(53, item(new ItemStack(Material.CHEST), "§6§lRewards", lore("§7Add or remove items"), false));
        holder.actions.put(53, e -> openItems(player, crate));
    }

    private ItemStack chanceIcon(Crate.Reward reward) {
        ItemStack icon = reward.item().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<String> text = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            text.add("");
            text.add("§7Chance: §e" + reward.chance() + "%");
            text.add("§7Rarity: " + reward.rarity().display());
            text.add("");
            text.add("§eLeft §7+1  §eRight §7-1  §eShift §7x10");
            meta.setLore(text);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    // ------------------------------------------------------------------ preview for players

    /** Left-click on a crate: all rewards with their rarity (never the percentage). */
    public void openPreview(Player player, Crate crate) {
        Holder holder = new Holder(Kind.PREVIEW, crate.id());
        Inventory inv = create(holder, 45, crate.displayName() + " §8» Preview");
        for (int i = 27; i < 45; i++) {
            inv.setItem(i, filler(crate.color().pane()));
        }
        for (int i = 0; i < REWARD_SLOTS.length; i++) {
            Crate.Reward reward = crate.reward(i);
            if (reward == null) {
                inv.setItem(REWARD_SLOTS[i], null);
                continue;
            }
            ItemStack icon = reward.item().clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<String> text = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                text.add("");
                text.add("§7Rarity: " + reward.rarity().display());
                meta.setLore(text);
                icon.setItemMeta(meta);
            }
            inv.setItem(REWARD_SLOTS[i], icon);
        }
        inv.setItem(40, item(this.plugin.crates().keyItem(crate, 1), crate.keyName(), lore(
                "§7Right-click the crate while you",
                "§7carry this key to open it."), true));
        player.openInventory(inv);
    }

    // ------------------------------------------------------------------ clicks

    /** Our menu behind this window, or null. (The "false" keeps the server from copying a chest just to answer.) */
    private Holder holderOf(Inventory top) {
        InventoryHolder holder = top.getHolder(false);
        return holder instanceof Holder ? (Holder) holder : null;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Holder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        int slot = event.getRawSlot();
        int size = holder.inventory.getSize();
        HumanEntity who = event.getWhoClicked();
        if (holder.kind != Kind.PREVIEW && !who.hasPermission(CratesPlugin.ADMIN)) {
            event.setCancelled(true);
            who.closeInventory();
            return;
        }
        if (holder.kind == Kind.ITEMS) {
            // the admin's own inventory and the 30 reward slots can be used freely, everything else is locked
            if (slot >= size || slot < 0 || rewardIndex(slot) >= 0) {
                return;
            }
        }
        event.setCancelled(true);
        if (!(who instanceof Player) || slot < 0 || slot >= size) {
            return;
        }
        Consumer<InventoryClickEvent> action = holder.actions.get(slot);
        if (action == null) {
            return;
        }
        // Opening or closing a window from inside the click itself is not safe, so the button runs right after it,
        // and only if the player still looks at the same, unchanged menu.
        Player player = (Player) who;
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()
                    && holderOf(player.getOpenInventory().getTopInventory()) == holder
                    && holder.actions.get(slot) == action) {
                action.accept(event);
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Holder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        if (holder.kind != Kind.PREVIEW && !event.getWhoClicked().hasPermission(CratesPlugin.ADMIN)) {
            event.setCancelled(true);
            return;
        }
        if (holder.kind == Kind.ITEMS) {
            int size = holder.inventory.getSize();
            for (int slot : event.getRawSlots()) {
                if (slot < size && rewardIndex(slot) < 0) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Holder holder = holderOf(event.getInventory());
        if (holder != null && holder.kind == Kind.ITEMS) {
            saveItems(holder);
        }
    }

    // ------------------------------------------------------------------ closing from outside

    /** Closes every open menu of this crate (used when the crate is deleted). Nothing of it is saved any more. */
    public void closeFor(String crateId) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Holder holder = holderOf(player.getOpenInventory().getTopInventory());
            if (holder != null && crateId.equals(holder.crateId)) {
                player.closeInventory();
            }
        }
    }

    /** Plugin shutdown: events no longer reach us, so an open reward editor is saved by hand before it is closed. */
    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Holder holder = holderOf(player.getOpenInventory().getTopInventory());
            if (holder == null) {
                continue;
            }
            if (holder.kind == Kind.ITEMS) {
                saveItems(holder);
            }
            player.closeInventory();
        }
    }
}
