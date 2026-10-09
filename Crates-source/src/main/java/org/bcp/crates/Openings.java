package org.bcp.crates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Opening a crate: the window with the moving items and the prize under the nether star.
 * The prize is decided the moment the crate is opened; the window only shows the way there. So closing the window
 * early or leaving the server never loses the prize.
 */
public final class Openings implements Listener {
    /** The seven item slots of the middle row, left to right. Slot 13 (index 3) is under the nether star. */
    static final int[] ROW = {10, 11, 12, 13, 14, 15, 16};
    static final int STAR_SLOT = 4;
    private static final int CENTER = 3;
    private static final int REVEAL_TICKS = 40;

    private final CratesPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    /** player -> server tick of the last "you can't open this" message */
    private final Map<UUID, Long> lastDenied = new HashMap<>();

    /** Marks the opening window. */
    static final class Holder implements InventoryHolder {
        Inventory inventory;
        final UUID player;

        Holder(UUID player) {
            this.player = player;
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }

    static final class Session {
        final Player player;
        final Crate crate;
        final Crate.Reward prize;
        final Placements.Placed source;
        final Holder holder;
        /** frames[step][0-6]: what the seven slots show, null = a glass pane */
        ItemStack[][] frames;
        /** frame colour per step */
        CrateColor[] colors;
        /** ticks to wait before each step */
        int[] delays;
        int step;
        long nextAt;
        long revealUntil = -1L;
        boolean done;

        Session(Player player, Crate crate, Crate.Reward prize, Placements.Placed source) {
            this.player = player;
            this.crate = crate;
            this.prize = prize;
            this.source = source;
            this.holder = new Holder(player.getUniqueId());
        }

        int steps() {
            return this.frames.length - 1;
        }
    }

    public Openings(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isOpening(Player player) {
        return this.sessions.containsKey(player.getUniqueId());
    }

    // ------------------------------------------------------------------ starting

    /** Right-click on a placed crate. Checks key, cooldown and inventory space, takes one key and starts. */
    public void tryOpen(Player player, Crate crate, Placements.Placed source) {
        UUID id = player.getUniqueId();
        if (this.sessions.containsKey(id)) {
            return;
        }
        if (!crate.hasRewards()) {
            this.plugin.msg(player, "§cThis crate is empty.");
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int keySlot = findKey(inventory, crate);
        if (keySlot < 0) {
            boolean inHand = this.plugin.getConfig().getBoolean("opening.key-must-be-in-hand", false);
            deny(player, "§cYou need a " + crate.keyName() + (inHand ? " §cin your hand" : "")
                + " §cto open this crate. §7Left-click to see what is inside.", true);
            return;
        }
        long wait = this.cooldownUntil.getOrDefault(id, 0L) - System.currentTimeMillis();
        if (wait > 0L) {
            deny(player, "§cPlease wait §e" + Text.duration(wait) + " §cbefore you open the next crate.", false);
            return;
        }
        if (inventory.firstEmpty() == -1) {
            deny(player, "§cYour inventory is full. Make some room before you open a crate.", true);
            return;
        }
        ItemStack key = inventory.getItem(keySlot);
        if (key.getAmount() > 1) {
            key.setAmount(key.getAmount() - 1);
            inventory.setItem(keySlot, key);
        } else {
            inventory.setItem(keySlot, null);
        }
        start(player, crate, source);
    }

    private boolean opens(ItemStack item, Crate crate) {
        Crate keyCrate = this.plugin.crates().crateOfKey(item);
        return keyCrate != null && keyCrate.id().equals(crate.id());
    }

    /**
     * Where the player carries a key of this crate: the hand first, then the rest of the inventory
     * (only the hand when the config asks for it).
     *
     * @return the inventory slot, or -1 without a key
     */
    private int findKey(PlayerInventory inventory, Crate crate) {
        int held = inventory.getHeldItemSlot();
        if (opens(inventory.getItem(held), crate)) {
            return held;
        }
        if (this.plugin.getConfig().getBoolean("opening.key-must-be-in-hand", false)) {
            return -1;
        }
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (opens(contents[slot], crate)) {
                return slot;
            }
        }
        return -1;
    }

    /** A "no" answer, at most once per second: holding the mouse button down repeats the click several times a second. */
    private void deny(Player player, String text, boolean sound) {
        long now = this.plugin.currentTick();
        Long last = this.lastDenied.put(player.getUniqueId(), now);
        if (last != null && now >= last && now - last < 20L) {
            this.lastDenied.put(player.getUniqueId(), last);
            return;
        }
        this.plugin.msg(player, text);
        if (sound) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
        }
    }

    /** Starts the opening window without asking for a key (also used by the "test opening" button of the editor). */
    public boolean start(Player player, Crate crate, Placements.Placed source) {
        if (this.sessions.containsKey(player.getUniqueId())) {
            return false;
        }
        Crate.Reward prize = crate.pick(this.random);
        if (prize == null) {
            this.plugin.msg(player, "§cThis crate is empty.");
            return false;
        }
        Session session = new Session(player, crate, prize, source);
        build(session);
        session.holder.inventory = Bukkit.createInventory(session.holder, InventoryType.SHULKER_BOX, crate.displayName());
        render(session);
        session.nextAt = this.plugin.currentTick() + session.delays[0];
        this.sessions.put(player.getUniqueId(), session);
        player.openInventory(session.holder.inventory);
        return true;
    }

    // ------------------------------------------------------------------ building the animation

    private ItemStack randomItem(Crate crate) {
        Crate.Reward reward = crate.pick(this.random);
        return reward == null ? null : reward.item().clone();
    }

    private void build(Session session) {
        Crate crate = session.crate;
        OpenAnimation animation = crate.animation();
        int min = Math.max(1, this.plugin.getConfig().getInt("opening.min-seconds", 6));
        int max = Math.max(min, this.plugin.getConfig().getInt("opening.max-seconds", 12));
        int total = (min + this.random.nextInt(max - min + 1)) * 20;

        // ---- timing: fast at first, slower towards the end
        int steps = Math.max(10, Math.round(total / (animation == OpenAnimation.PULSE ? 4.8f : 5.4f)));
        double[] weight = new double[steps];
        double sum = 0.0;
        for (int i = 0; i < steps; i++) {
            double progress = steps == 1 ? 1.0 : (double) i / (steps - 1);
            if (animation == OpenAnimation.PULSE) {
                // three quick steps, then a break; everything gets slower over time
                weight[i] = (i % 4 == 3 ? 6.0 : 1.2) * (1.0 + 2.0 * progress);
            } else {
                weight[i] = 2.0 + 10.0 * progress * progress;
            }
            sum += weight[i];
        }
        session.delays = new int[steps];
        int used = 0;
        for (int i = 0; i < steps; i++) {
            session.delays[i] = Math.max(1, (int) Math.round(weight[i] * total / sum));
            used += session.delays[i];
        }
        session.delays[steps - 1] = Math.max(1, session.delays[steps - 1] + (total - used));

        // ---- what the seven slots show at every step
        ItemStack prize = session.prize.item().clone();
        ItemStack[][] frames = new ItemStack[steps + 1][7];
        CrateColor[] colors = new CrateColor[steps + 1];
        for (int i = 0; i <= steps; i++) {
            colors[i] = animation == OpenAnimation.RAINBOW && i < steps ? CrateColor.values()[i % CrateColor.values().length] : crate.color();
        }
        switch (animation) {
            case SHUFFLE:
                for (int i = 0; i <= steps; i++) {
                    for (int j = 0; j < 7; j++) {
                        frames[i][j] = randomItem(crate);
                    }
                }
                frames[steps][CENTER] = prize;
                break;
            case CLOSING_IN:
                for (int i = 0; i <= steps; i++) {
                    // from 40% on, the outer slots close one pair after the other
                    int closed = 0;
                    if (i >= steps) {
                        closed = 3;
                    } else if (i >= steps * 0.4) {
                        closed = Math.min(3, 1 + (int) ((i - steps * 0.4) / (steps * 0.6) * 3.0));
                    }
                    for (int j = 0; j < 7; j++) {
                        frames[i][j] = j < closed || j > 6 - closed ? null : randomItem(crate);
                    }
                }
                frames[steps][CENTER] = prize;
                break;
            case PENDULUM: {
                // the strip moves a few steps one way, then back, and ends on the prize
                int[] offset = new int[steps + 1];
                int position = 0;
                int direction = 1;
                int run = 4 + this.random.nextInt(5);
                int highest = 0;
                for (int i = 1; i <= steps; i++) {
                    if (position + direction < 0) {
                        direction = 1;
                    }
                    position += direction;
                    offset[i] = position;
                    highest = Math.max(highest, position);
                    if (--run <= 0) {
                        direction = -direction;
                        run = 4 + this.random.nextInt(5);
                    }
                }
                ItemStack[] strip = new ItemStack[highest + 7];
                for (int k = 0; k < strip.length; k++) {
                    strip[k] = randomItem(crate);
                }
                strip[offset[steps] + CENTER] = prize;
                for (int i = 0; i <= steps; i++) {
                    for (int j = 0; j < 7; j++) {
                        frames[i][j] = strip[offset[i] + j];
                    }
                }
                break;
            }
            case ROULETTE:
            case REVERSE:
            case RAINBOW:
            case PULSE:
            default: {
                ItemStack[] strip = new ItemStack[steps + 7];
                for (int k = 0; k < strip.length; k++) {
                    strip[k] = randomItem(crate);
                }
                strip[steps + CENTER] = prize;
                boolean reverse = animation == OpenAnimation.REVERSE;
                for (int i = 0; i <= steps; i++) {
                    for (int j = 0; j < 7; j++) {
                        frames[i][j] = strip[i + (reverse ? 6 - j : j)];
                    }
                }
                break;
            }
        }
        session.frames = frames;
        session.colors = colors;
    }

    // ------------------------------------------------------------------ drawing

    private ItemStack pane(CrateColor color) {
        ItemStack item = new ItemStack(color.pane());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack star(boolean glow) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color(this.plugin.getConfig().getString("opening.prize-title", "&6&lYour Prize")));
        if (glow) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void render(Session session) {
        Inventory inv = session.holder.inventory;
        boolean finished = session.step >= session.steps();
        CrateColor color = session.colors[Math.min(session.step, session.colors.length - 1)];
        if (finished && session.revealUntil >= 0L) {
            // the frame blinks while the prize is shown
            long left = session.revealUntil - this.plugin.currentTick();
            color = (left / 5L) % 2L == 0L ? session.crate.color() : CrateColor.WHITE;
        }
        // top row and the two side panes follow the animation, the bottom row always has the crate's colour
        ItemStack pane = pane(color);
        ItemStack bottom = pane(session.crate.color());
        for (int slot = 0; slot < 27; slot++) {
            inv.setItem(slot, slot >= 18 ? bottom : pane);
        }
        inv.setItem(STAR_SLOT, star(finished));
        ItemStack[] frame = session.frames[Math.min(session.step, session.steps())];
        for (int j = 0; j < 7; j++) {
            if (frame[j] != null) {
                inv.setItem(ROW[j], frame[j]);
            }
        }
    }

    // ------------------------------------------------------------------ per tick

    public void tick() {
        long now = this.plugin.currentTick();
        if (now % 1200L == 0L) {
            long time = System.currentTimeMillis();
            this.cooldownUntil.values().removeIf(until -> until < time);
        }
        if (this.sessions.isEmpty()) {
            return;
        }
        for (Session session : new ArrayList<>(this.sessions.values())) {
            if (session.done) {
                continue;
            }
            Player player = session.player;
            if (session.revealUntil >= 0L) {
                if (now >= session.revealUntil) {
                    finish(session, true);
                } else if ((session.revealUntil - now) % 5L == 0L) {
                    render(session);
                }
                continue;
            }
            if (now < session.nextAt) {
                continue;
            }
            session.step++;
            render(session);
            OpenSound sound = session.crate.sound();
            if (session.step >= session.steps()) {
                session.revealUntil = now + REVEAL_TICKS;
                player.playSound(player.getLocation(), sound.win(), 1.0f, sound.winPitch());
            } else {
                session.nextAt = now + session.delays[session.step];
                player.playSound(player.getLocation(), sound.tick(), 0.6f, sound.tickPitch());
            }
        }
    }

    // ------------------------------------------------------------------ finishing

    /** Gives the prize. Safe to call more than once. */
    private void finish(Session session, boolean closeWindow) {
        if (session.done) {
            return;
        }
        session.done = true;
        Player player = session.player;
        this.sessions.remove(player.getUniqueId());
        long cooldown = Math.max(0L, this.plugin.getConfig().getLong("opening.cooldown-seconds", 5L));
        this.cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + cooldown * 1000L);

        ItemStack prize = session.prize.item().clone();
        String won = "You won " + describe(session.prize.item()) + " §8(" + session.prize.rarity().display() + "§8)§7!";
        if (player.isDead()) {
            // dying closes the window, and the server empties the inventory right after: keep the prize for the respawn
            this.plugin.deliveries().afterRespawn(player, prize, true);
            this.plugin.msg(player, won + " You get it as soon as you respawn.");
        } else {
            for (ItemStack left : player.getInventory().addItem(prize).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left); // inventory filled up in the meantime
            }
            this.plugin.msg(player, won);
        }
        if (session.source != null) {
            Location center = this.plugin.placements().center(session.source);
            if (center != null && center.getWorld() != null) {
                ParticleFx.burst(center.getWorld(), center, session.crate.color());
            }
        }
        if (closeWindow) {
            player.closeInventory();
        }
    }

    /** "3x Diamond" or the custom name of the item. */
    static String describe(ItemStack item) {
        String name = null;
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                name = meta.getDisplayName();
            }
        }
        if (name == null) {
            StringBuilder pretty = new StringBuilder();
            for (String word : item.getType().name().toLowerCase(java.util.Locale.ROOT).split("_")) {
                if (pretty.length() > 0) {
                    pretty.append(' ');
                }
                pretty.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            name = "§f" + pretty;
        }
        return (item.getAmount() > 1 ? "§f" + item.getAmount() + "x " : "") + name;
    }

    public void shutdown() {
        for (Session session : new ArrayList<>(this.sessions.values())) {
            finish(session, true);
        }
    }

    private Session sessionOf(Inventory top) {
        InventoryHolder holder = top.getHolder(false);
        if (!(holder instanceof Holder)) {
            return null;
        }
        return this.sessions.get(((Holder) holder).player);
    }

    // ------------------------------------------------------------------ the window cannot be changed

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }

    /** Closing the window early hands out the prize at once. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Session session = sessionOf(event.getInventory());
        HumanEntity who = event.getPlayer();
        if (session != null && !session.done && who.getUniqueId().equals(session.player.getUniqueId())) {
            finish(session, false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.lastDenied.remove(event.getPlayer().getUniqueId());
        Session session = this.sessions.get(event.getPlayer().getUniqueId());
        if (session != null) {
            finish(session, false);
        }
    }
}
