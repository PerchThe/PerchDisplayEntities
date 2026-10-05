package ever.green.displayentities.selection;

import ever.green.displayentities.C;
import ever.green.displayentities.SoundUtil;
import ever.green.displayentities.Util;
import ever.green.displayentities.gui.SelectMobGui;
import ever.green.displayentities.selection.blockdata.BlockDataInteractor;
import ever.green.displayentities.selection.blockdata.BlockDataUtil;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Paper Dialog & Kyori Imports
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.*;

public class editorListener implements Listener {

    private final HashMap<UUID, Long> lastPlayerInteraction = new HashMap<>();
    private final HashMap<UUID, SpamTracker> shopSpamTrackers = new HashMap<>();

    public static final Color[] PRESET_COLORS = {
            Color.fromARGB(255, 125, 125, 125), // Default Gray
            Color.BLACK,
            Color.WHITE,
            Color.RED,
            Color.ORANGE,
            Color.YELLOW,
            Color.LIME,
            Color.GREEN,
            Color.AQUA,
            Color.BLUE,
            Color.PURPLE,
            Color.FUCHSIA
    };

    private static class SpamTracker {
        int count = 0;
        long lastAttempt = 0;
    }

    private boolean shouldSendShopWarning(Player player) {
        long now = System.currentTimeMillis();
        SpamTracker tracker = shopSpamTrackers.computeIfAbsent(player.getUniqueId(), k -> new SpamTracker());

        // Reset cache if it's been more than 5 minutes (300,000 ms)
        if (now - tracker.lastAttempt > 300_000L) {
            tracker.count = 0;
        }

        tracker.lastAttempt = now;
        tracker.count++;

        // Return true on the 1st, 11th, 21st, etc. interaction
        return tracker.count % 10 == 1;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCrossPluginCommand(PlayerCommandPreprocessEvent event) {
        if (SelectionManager.isOneditor(event.getPlayer())) {
            String cmd = event.getMessage().split(" ")[0].toLowerCase();
            // Block EntityEditor commands from being run while in DisplayEntities
            if (cmd.equals("/ee") || cmd.equals("/entityeditor")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou must exit the Display Editor (Slot 8) first!");
            }
        }
    }

    @EventHandler
    public void event(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player && SelectionManager.isOneditor((Player) event.getEntity()))
            event.setCancelled(true);
    }

    @EventHandler
    public void event(PlayerTeleportEvent event) {
        if (!SelectionManager.isOneditor(event.getPlayer()))
            return;
        if (event.getTo() == null || Objects.equals(event.getFrom().getWorld(), event.getTo().getWorld())) {
            SelectionManager.deselect(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void event(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && SelectionManager.isOneditor((Player) event.getWhoClicked()))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void event(EntityDamageByEntityEvent event) {
        if (C.EXIT_ON_HIT && event.getEntity() instanceof Player p && SelectionManager.isOneditor(p))
            SelectionManager.seteditor(p, null);
    }

    @EventHandler
    public void event(EntityResurrectEvent event) {
        if (event.isCancelled() && event.getEntity() instanceof Player p && SelectionManager.isOneditor(p))
            SelectionManager.seteditor(p, null);
    }

    @EventHandler
    public void event(PlayerDropItemEvent event) {
        if (SelectionManager.isOneditor(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler
    public void event(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL)
            return;
        Editor mode = SelectionManager.geteditor(event.getPlayer());
        if (mode == null)
            return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        long nowMs = System.currentTimeMillis();
        long lastMs = this.lastPlayerInteraction.getOrDefault(event.getPlayer().getUniqueId(), nowMs - 150);

        if (lastMs + 100 >= nowMs)
            return;
        this.lastPlayerInteraction.put(event.getPlayer().getUniqueId(), nowMs);
        handleClick(event, mode);
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (SelectionManager.isOneditor(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        if (SelectionManager.isOneditor(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void event(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (SelectionManager.isOneditor(event.getPlayer()))
            SelectionManager.seteditor(event.getPlayer(), null);

        // Clean up caches to prevent memory leaks
        lastPlayerInteraction.remove(uuid);
        shopSpamTrackers.remove(uuid);
        Editor.rightRotationMap.remove(uuid);
    }

    @EventHandler
    public void event(InventoryOpenEvent event) { }

    @EventHandler
    public void event(PlayerSwapHandItemsEvent event) {
        if (SelectionManager.isOneditor(event.getPlayer())) {
            event.setCancelled(true);
            SelectionManager.swapeditor(event.getPlayer());

            // Skip the entity specific page if it will be empty
            if (SelectionManager.geteditor(event.getPlayer()) == Editor.ENTITY_SPECIFIC) {
                Display sel = SelectionManager.getSelection(event.getPlayer());
                if (Editor.isEntitySpecificEmpty(sel)) {
                    SelectionManager.swapeditor(event.getPlayer());
                }
            }
            SoundUtil.playSoundPageTurn(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void blockShopContainerClicks(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!SelectionManager.isOneditor(p)) return;
        Action a = event.getAction();
        if (a != Action.LEFT_CLICK_BLOCK && a != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Material type = event.getClickedBlock().getType();
        if (isShopContainer(type)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
            if (shouldSendShopWarning(p)) {
                p.sendMessage("§cShops are disabled while you are in editor mode.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockQuickShopCommands(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!SelectionManager.isOneditor(p)) return;

        String msg = event.getMessage().trim().toLowerCase(Locale.ENGLISH);
        if (!msg.startsWith("/")) return;
        String[] parts = msg.substring(1).split("\\s+");
        if (parts.length < 2) return;

        String base = parts[0];
        String sub  = parts[1];

        boolean quickShopAlias = base.equals("chestshop") || base.equals("quickshop") || base.equals("cs");
        if (quickShopAlias && (sub.equals("create") || sub.equals("buy") || sub.equals("sell"))) {
            event.setCancelled(true);
            if (shouldSendShopWarning(p)) {
                p.sendMessage("§cQuickShop commands are disabled while you are in editor mode.");
            }
        }
    }

    private boolean isShopContainer(Material m) {
        if (m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL) return true;
        return m.name().endsWith("_SHULKER_BOX");
    }

    private void handleClick(PlayerInteractEvent event, Editor editor) {
        int slot = event.getPlayer().getInventory().getHeldItemSlot();
        Display sel = SelectionManager.getSelection(event.getPlayer());

        // EXIT is solely on Slot 7, and ONLY when on the POSITION page.
        if (slot == 7 && editor == Editor.POSITION) {
            SelectionManager.seteditor(event.getPlayer(), null);
            return;
        }

        boolean isLeftClick = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;

        // PAGE is strictly Slot 8
        if (slot == 8) {
            int direction = isLeftClick ? -1 : 1;
            SelectionManager.swapeditor(event.getPlayer(), direction);

            // Skip the entity specific page if it will be empty
            if (SelectionManager.geteditor(event.getPlayer()) == Editor.ENTITY_SPECIFIC) {
                if (Editor.isEntitySpecificEmpty(sel)) {
                    SelectionManager.swapeditor(event.getPlayer(), direction);
                }
            }
            SoundUtil.playSoundPageTurn(event.getPlayer());
            return;
        }

        boolean sneak = event.getPlayer().isSneaking();
        Set<Display> selections = SelectionManager.getSelections(event.getPlayer());
        if (selections.isEmpty() || sel == null || !sel.isValid()) {
            if (editor == Editor.COPY_PASTE) {
                copyPasteHandleClick(event.getPlayer(), slot, isLeftClick, sel, sneak, editor);
                return;
            }
            selectNearest(event.getPlayer(), slot, isLeftClick, sel, sneak);
            return;
        }
        switch (editor) {
            case POSITION -> positionHandleClick(event.getPlayer(), slot, isLeftClick, selections, sneak, editor);
            case ROTATION -> rotationHandleClick(event.getPlayer(), slot, isLeftClick, selections, sneak, editor);
            case TRANSLATION -> translationHandleClick(event.getPlayer(), slot, isLeftClick, selections, sneak, editor);
            case SCALE -> scaleHandleClick(event.getPlayer(), slot, isLeftClick, selections, sneak, editor);
            case SHADOW -> shadowHandleClick(event.getPlayer(), slot, isLeftClick, selections, sneak, editor);
            case ENTITY_SPECIFIC -> entitySpecificHandleClick(event.getPlayer(), slot, isLeftClick, selections, sneak, editor);
            case COPY_PASTE -> copyPasteHandleClick(event.getPlayer(), slot, isLeftClick, sel, sneak, editor);
        }
    }

    private void selectNearest(Player player, int slot, boolean isLeftClick, Display sel, boolean sneak) {
        Display target = null;
        for (Entity en : player.getNearbyEntities(C.DEFAULT_SELECT_RADIUS, C.DEFAULT_SELECT_RADIUS, C.DEFAULT_SELECT_RADIUS)) {
            // Ignore GriefPrevention visualization entities
            if (en instanceof Display && !en.getScoreboardTags().contains("gp_vis")) {
                if (target == null)
                    target = (Display) en;
                else
                    target = target.getLocation().distanceSquared(player.getLocation()) > en.getLocation().distanceSquared(player.getLocation()) ?
                            (Display) en : target;
            }
        }
        if (target == null) {
            return;
        }
        SelectionManager.select(player, target);
    }

    private void copyPasteHandleClick(Player player, int slot, boolean isLeftClick, Display sel, boolean sneak, Editor editor) {
        if (slot == 0) {
            if (isLeftClick)
                selectNearest(player, slot, true, sel, sneak);
            else {
                CopyPasteOption option = SelectionManager.getCopyPasteOption(player);
                BoundingBox box = new BoundingBox().shift(player.getLocation()).expand(option.getCopyRadius());
                Collection<Entity> list = player.getWorld().getNearbyEntities(box, (en) ->
                        !(en instanceof Player) && !en.getScoreboardTags().contains("gp_vis"));
                player.openInventory(new SelectMobGui(player, list, false).getInventory());
            }
            return;
        }
        if (!Util.isVersionAfter(1, 20, 2))
            return;
        CopyPasteOption option = SelectionManager.getCopyPasteOption(player);
        switch (slot) {
            case 1 -> {
                Display disp = SelectionManager.getSelection(player);
                if (disp != null) {
                    option.copy(player, List.of(disp), player.getLocation());
                    SoundUtil.playSoundUIClick(player);
                    editor.setup(player);
                    Util.flashEntities(player, disp);
                } else {
                    SoundUtil.playSoundNo(player);
                }
            }
            case 2 -> {
                if (!isLeftClick) {
                    BoundingBox box = new BoundingBox().shift(player.getLocation()).expand(option.getCopyRadius());
                    Collection<Entity> list = player.getWorld().getNearbyEntities(box, (en) ->
                            !(en instanceof Player) && !en.getScoreboardTags().contains("gp_vis"));
                    if (!option.copy(player, list, player.getLocation())) {
                        SoundUtil.playSoundNo(player);
                        return;
                    }
                    SoundUtil.playSoundUIClick(player);
                    editor.setup(player);
                    Util.flashEntities(player, list);
                }
                else {
                    BoundingBox box = new BoundingBox().shift(player.getLocation()).expand(option.getCopyRadius());
                    Collection<Entity> list = player.getWorld().getNearbyEntities(box, (en) ->
                            !(en instanceof Player) && !en.getScoreboardTags().contains("gp_vis"));
                    player.openInventory(new SelectMobGui(player,list,true).getInventory());
                }
            }
            case 3 -> {
                if (!canEditHere(player, player.getLocation())) {
                    player.sendMessage("§cYou cannot paste entities into an untrusted claim.");
                    SoundUtil.playSoundNo(player);
                    return;
                }
                if (!option.paste(player, player.getLocation(), !sneak)) {
                    SoundUtil.playSoundNo(player);
                    return;
                }
                SoundUtil.playSoundUIClick(player);
                editor.setup(player);
                Util.flashEntities(player, option.getLastPasted());
            }
            case 4 -> {
                if (!option.undoPaste()) {
                    SoundUtil.playSoundNo(player);
                    return;
                }
                SoundUtil.playSoundUIClick(player);
                editor.setup(player);
            }
            case 5 -> {
                option.addYRotation((isLeftClick ? -1 : 1) * (sneak ? 5 : 90));
                SoundUtil.playSoundUIClick(player);
                editor.setup(player);
            }
            case 6 -> {
                Set<Display> currentSelection = SelectionManager.getSelections(player);
                if (currentSelection != null && !currentSelection.isEmpty()) {
                    List<Entity> entityList = new ArrayList<>(currentSelection);
                    option.copy(player, entityList, player.getLocation());
                    SoundUtil.playSoundUIClick(player);
                    editor.setup(player);
                    Util.flashEntities(player, entityList);
                    player.sendMessage("§aCopied " + currentSelection.size() + " selected entities.");
                } else {
                    SoundUtil.playSoundNo(player);
                }
            }
        }
    }

    private Color parseColorInput(String input, Color current) {
        if (input == null || input.isBlank()) return current;
        input = input.trim();

        // Handle legacy &a, &c, etc.
        if (input.matches("^(?i)[&§][0-9a-f]$")) {
            return getLegacyColor(input.charAt(1));
        }

        // Handle Hex formats (#FFFFFF, &#FFFFFF, FFFFFF)
        String hex = input.replaceAll("^(?i)(?:&#|#|&x|)", "");
        if (hex.length() == 6) {
            try {
                return Color.fromRGB(Integer.parseInt(hex, 16));
            } catch (NumberFormatException ignored) {}
        }

        return current;
    }

    private Color getLegacyColor(char code) {
        code = Character.toLowerCase(code);
        return switch (code) {
            case '0' -> Color.fromRGB(0, 0, 0);
            case '1' -> Color.fromRGB(0, 0, 170);
            case '2' -> Color.fromRGB(0, 170, 0);
            case '3' -> Color.fromRGB(0, 170, 170);
            case '4' -> Color.fromRGB(170, 0, 0);
            case '5' -> Color.fromRGB(170, 0, 170);
            case '6' -> Color.fromRGB(255, 170, 0);
            case '7' -> Color.fromRGB(170, 170, 170);
            case '8' -> Color.fromRGB(85, 85, 85);
            case '9' -> Color.fromRGB(85, 85, 255);
            case 'a' -> Color.fromRGB(85, 255, 85);
            case 'b' -> Color.fromRGB(85, 255, 255);
            case 'c' -> Color.fromRGB(255, 85, 85);
            case 'd' -> Color.fromRGB(255, 85, 255);
            case 'e' -> Color.fromRGB(255, 255, 85);
            case 'f' -> Color.fromRGB(255, 255, 255);
            default -> Color.fromARGB(125, 125, 125, 125);
        };
    }

    private void entitySpecificHandleClick(Player player, int slot, boolean isLeftClick, Set<Display> selections, boolean sneak, Editor editor) {
        for (Display sel : selections) {
            if (sel instanceof TextDisplay display) {
                Color color = display.getBackgroundColor() == null ? Color.fromARGB(125, 125, 125, 125) : display.getBackgroundColor();
                switch (slot) {
                    case 0 -> {
                        if (sneak) {
                            Dialog dialog = Dialog.create(builder -> builder.empty()
                                    .base(DialogBase.builder(Component.text("Background Color", TextColor.color(0x48ab76)))
                                            .canCloseWithEscape(true)
                                            .inputs(List.of(
                                                    DialogInput.text("hex_color", Component.text("Hex (#RRGGBB) or Legacy (&a)")).build()
                                            ))
                                            .build()
                                    )
                                    .type(DialogType.confirmation(
                                            ActionButton.builder(Component.text("Save", NamedTextColor.GREEN))
                                                    .action(DialogAction.customClick(
                                                            (view, audience) -> {
                                                                if (!(audience instanceof Player p)) return;
                                                                String text = view.getText("hex_color");
                                                                Color newColor = parseColorInput(text, color);

                                                                // Run sync task to update the display entity and refresh the hotbar UI
                                                                org.bukkit.Bukkit.getScheduler().runTask(ever.green.displayentities.DisplayEntities.get(), () -> {
                                                                    display.setBackgroundColor(Color.fromARGB(color.getAlpha(), newColor.getRed(), newColor.getGreen(), newColor.getBlue()));
                                                                    SoundUtil.playSoundUIClick(p);
                                                                    editor.setup(p);
                                                                    sendActionBarFeedback(p, " §8| §aColor Updated!");
                                                                });
                                                            },
                                                            ClickCallback.Options.builder().uses(1).build()
                                                    ))
                                                    .build(),
                                            ActionButton.builder(Component.text("Cancel", NamedTextColor.RED))
                                                    .action(DialogAction.customClick(
                                                            (view, audience) -> {
                                                                if (!(audience instanceof Player p)) return;
                                                                org.bukkit.Bukkit.getScheduler().runTask(ever.green.displayentities.DisplayEntities.get(), () -> {
                                                                    editor.setup(p);
                                                                });
                                                            },
                                                            ClickCallback.Options.builder().uses(1).build()
                                                    ))
                                                    .build()
                                    ))
                            );
                            player.showDialog(dialog);
                        } else {
                            int currentIndex = -1;
                            for (int i = 0; i < PRESET_COLORS.length; i++) {
                                if (PRESET_COLORS[i].getRed() == color.getRed() &&
                                        PRESET_COLORS[i].getGreen() == color.getGreen() &&
                                        PRESET_COLORS[i].getBlue() == color.getBlue()) {
                                    currentIndex = i;
                                    break;
                                }
                            }
                            int nextIndex = (currentIndex + (isLeftClick ? -1 : 1) + PRESET_COLORS.length) % PRESET_COLORS.length;
                            Color nextColor = PRESET_COLORS[nextIndex];
                            edit(display, player, () -> display.setBackgroundColor(Color.fromARGB(color.getAlpha(), nextColor.getRed(), nextColor.getGreen(), nextColor.getBlue())), editor);
                        }
                    }
                    case 1 ->
                            edit(display, player, () -> display.setBackgroundColor(color.setAlpha(Math.max(0, Math.min(255,
                                    color.getAlpha() + (isLeftClick ? -1 : 1) * (sneak ? 3 : 15))))), editor);
                    case 2 -> {
                        int currentOpacity = display.getTextOpacity() == -1 ? 255 : (display.getTextOpacity() & 0xFF);
                        int newOpacity = Math.max(0, Math.min(255, currentOpacity + (isLeftClick ? -1 : 1) * (sneak ? 3 : 15)));
                        edit(display, player, () -> display.setTextOpacity((byte) newOpacity), editor);
                    }
                    case 3 ->
                            edit(display, player, () -> display.setAlignment(TextDisplay.TextAlignment.values()[(TextDisplay.TextAlignment.values().length
                                    + display.getAlignment().ordinal() + (isLeftClick ? -1 : 1)) % TextDisplay.TextAlignment.values().length]), editor);
                    case 4 ->
                            edit(display, player, () -> display.setSeeThrough(!display.isSeeThrough()), editor);
                    case 5 -> {
                        int currentWidth = display.getLineWidth();
                        int adjustment = (isLeftClick ? -10 : 10) * (sneak ? 5 : 1);
                        edit(display, player, () -> display.setLineWidth(Math.max(1, currentWidth + adjustment)), editor);
                    }
                }
                continue;
            }
            if (sel instanceof BlockDisplay) {
                BlockData data = ((BlockDisplay) sel).getBlock();
                List<BlockDataInteractor> values = BlockDataUtil.getBlockDataValues(data);
                if (values.size() > slot)
                    edit(sel, player, () -> values.get(slot).handleClick((BlockDisplay) sel, player, isLeftClick), editor);
                continue;
            }
            if (sel instanceof ItemDisplay display) {
                switch (slot) {
                    case 0 ->
                            edit(display, player, () -> display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.values()[(ItemDisplay.ItemDisplayTransform.values().length
                                    + display.getItemDisplayTransform().ordinal() + (isLeftClick ? -1 : 1)) % ItemDisplay.ItemDisplayTransform.values().length]), editor);

                    case 1 -> edit(display, player, () -> {
                        ItemStack item = display.getItemStack();
                        if (item == null)
                            item = new ItemStack(Material.STONE);

                        if (item.getEnchantments().isEmpty())
                            item.addUnsafeEnchantment(Enchantment.LURE, 1);
                        else
                            item.getEnchantments().keySet().forEach(item::removeEnchantment);
                        display.setItemStack(item);
                    }, editor);
                    case 2, 3, 4, 5, 6 -> edit(display, player, () -> {
                        ItemStack item = display.getItemStack();
                        if (item == null)
                            item = new ItemStack(Material.STONE);
                        ItemMeta meta = item.getItemMeta();
                        assert meta != null;
                        int value = meta.hasCustomModelData() ? meta.getCustomModelData() : 0;
                        value += (Math.pow(10, 2 * (slot - 2) + (sneak ? 1 : 0)) * (isLeftClick ? -1 : 1));
                        meta.setCustomModelData(value);
                        item.setItemMeta(meta);
                        display.setItemStack(item);
                    }, editor);
                }
            }
        }
    }

    private void shadowHandleClick(Player player, int slot, boolean isLeftClick, Set<Display> selections, boolean sneak, Editor mode) {
        for (Display sel : selections) {
            switch (slot) {
                case 0:
                    if (isLeftClick)
                        edit(sel, player, () -> {
                            if (sneak) {
                                sel.setBrightness(null);
                            } else {
                                Display.Brightness bright = sel.getBrightness();
                                if (bright == null)
                                    bright = new Display.Brightness(0, 0);
                                sel.setBrightness(new Display.Brightness(bright.getBlockLight(), Math.max(0, bright.getSkyLight() - 1)));
                            }
                        }, mode);
                    else
                        edit(sel, player, () -> {
                            if (sneak) {
                                sel.setBrightness(null);
                            } else {
                                Display.Brightness bright = sel.getBrightness();
                                if (bright == null)
                                    bright = new Display.Brightness(0, 0);
                                sel.setBrightness(new Display.Brightness(bright.getBlockLight(), Math.min(15, bright.getSkyLight() + 1)));
                            }
                        }, mode);
                    break;
                case 1:
                    if (isLeftClick)
                        edit(sel, player, () -> {
                            if (sneak) {
                                sel.setBrightness(null);
                            } else {
                                Display.Brightness bright = sel.getBrightness();
                                if (bright == null)
                                    bright = new Display.Brightness(0, 0);
                                sel.setBrightness(new Display.Brightness(Math.max(0, bright.getBlockLight() - 1), bright.getSkyLight()));
                            }
                        }, mode);
                    else
                        edit(sel, player, () -> {
                            if (sneak) {
                                sel.setBrightness(null);
                            } else {
                                Display.Brightness bright = sel.getBrightness();
                                if (bright == null)
                                    bright = new Display.Brightness(0, 0);
                                sel.setBrightness(new Display.Brightness(Math.min(15, bright.getBlockLight() + 1), bright.getSkyLight()));
                            }
                        }, mode);
                    break;
                case 2:
                    if (isLeftClick)
                        edit(sel, player, () -> {
                            float range = Math.min(C.MAX_VIEW_RANGE, Math.max(0, sel.getViewRange() * 64 + (sneak ? 1 : 4))) / 64;
                            sel.setViewRange(range);
                        }, mode);
                    else
                        edit(sel, player, () -> {
                            float range = Math.min(C.MAX_VIEW_RANGE, Math.max(0, sel.getViewRange() * 64 - (sneak ? 1 : 4))) / 64;
                            sel.setViewRange(range);
                        }, mode);
                    break;
                case 3:
                    edit(sel, player, () -> sel.setBrightness(null), mode);
                    break;
            }
        }
    }

    private void scaleHandleClick(Player player, int slot, boolean isLeftClick, Set<Display> selections, boolean sneak, Editor mode) {
        float scale = (float) ((isLeftClick ? -1 : 1) * (sneak ? C.SCALE_FINE : C.SCALE_COARSE));
        for (Display sel : selections) {
            switch (slot) {
                case 4 -> edit(sel, player, () -> {
                    Transformation transf = sel.getTransformation();
                    Vector3f s = transf.getScale();
                    float newX = clamp(s.x + scale, 0.00f, (float) C.MAX_SCALE);
                    float newY = clamp(s.y + scale, 0.00f, (float) C.MAX_SCALE);
                    float newZ = clamp(s.z + scale, 0.00f, (float) C.MAX_SCALE);
                    s.set(newX, newY, newZ);
                    sel.setTransformation(new Transformation(transf.getTranslation(), transf.getLeftRotation(), s, transf.getRightRotation()));
                }, mode);
                case 0 -> edit(sel, player, () -> {
                    Transformation transf = sel.getTransformation();
                    Vector3f s = transf.getScale();
                    float newX = clamp(s.x + scale, 0.00f, (float) C.MAX_SCALE);
                    s.set(newX, s.y, s.z);
                    sel.setTransformation(new Transformation(transf.getTranslation(), transf.getLeftRotation(), s, transf.getRightRotation()));
                }, mode);
                case 1 -> edit(sel, player, () -> {
                    Transformation transf = sel.getTransformation();
                    Vector3f s = transf.getScale();
                    float newY = clamp(s.y + scale, 0.00f, (float) C.MAX_SCALE);
                    s.set(s.x, newY, s.z);
                    sel.setTransformation(new Transformation(transf.getTranslation(), transf.getLeftRotation(), s, transf.getRightRotation()));
                }, mode);
                case 2 -> edit(sel, player, () -> {
                    Transformation transf = sel.getTransformation();
                    Vector3f s = transf.getScale();
                    float newZ = clamp(s.z + scale, 0.00f, (float) C.MAX_SCALE);
                    s.set(s.x, s.y, newZ);
                    sel.setTransformation(new Transformation(transf.getTranslation(), transf.getLeftRotation(), s, transf.getRightRotation()));
                }, mode);
                case 6 -> edit(sel, player, () -> {
                    Transformation transf = sel.getTransformation();
                    sel.setTransformation(new Transformation(transf.getTranslation(), transf.getLeftRotation(), new Vector3f(1, 1, 1), transf.getRightRotation()));
                }, mode);
            }
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void translationHandleClick(Player player, int slot, boolean isLeftClick, Set<Display> selections, boolean sneak, Editor mode) {
        float move = (float) ((isLeftClick ? -1 : 1) * (sneak ? C.MOVE_FINE : C.MOVE_COARSE));
        for (Display sel : selections) {
            switch (slot) {
                case 0 -> edit(sel, player, () -> {
                    Transformation transf = sel.getTransformation();
                    Vector3f t = transf.getTranslation();
                    t.add(move, 0, 0);
                    sel.setTransformation(new Transformation(t, transf.getLeftRotation(), transf.getScale(), transf.getRightRotation()));
                }, mode);
                case 1 -> edit(sel, player, () -> {
                    Transformation transf = sel.getTransformation();
                    Vector3f t = transf.getTranslation();
                    t.add(0, move, 0);
                    sel.setTransformation(new Transformation(t, transf.getLeftRotation(), transf.getScale(), transf.getRightRotation()));
                }, mode);
                case 2 -> edit(sel, player, () -> {
                    Transformation transf = sel.getTransformation();
                    Vector3f t = transf.getTranslation();
                    t.add(0, 0, move);
                    sel.setTransformation(new Transformation(t, transf.getLeftRotation(), transf.getScale(), transf.getRightRotation()));
                }, mode);
                case 4 -> {
                    if (!canEditHere(player, player.getLocation())) {
                        player.sendMessage("§cYou cannot teleport the origin into an untrusted claim.");
                        SoundUtil.playSoundNo(player);
                        continue;
                    }
                    edit(sel, player, () -> {
                        Location pLoc = player.getLocation();
                        Location sLoc = sel.getLocation();

                        Location target = pLoc.clone();
                        target.setYaw(sLoc.getYaw());
                        target.setPitch(sLoc.getPitch());

                        Vector3f diff = new Vector3f(
                                (float) (target.getX() - sLoc.getX()),
                                (float) (target.getY() - sLoc.getY()),
                                (float) (target.getZ() - sLoc.getZ())
                        );

                        Transformation transf = sel.getTransformation();
                        Vector3f t = transf.getTranslation();
                        t.sub(diff);
                        sel.setTransformation(new Transformation(t, transf.getLeftRotation(), transf.getScale(), transf.getRightRotation()));
                        sel.teleport(target);
                    }, mode);
                }
                case 6 -> edit(sel, player, () -> {
                    Transformation transf = sel.getTransformation();
                    sel.setTransformation(new Transformation(new Vector3f(0, 0, 0), transf.getLeftRotation(), transf.getScale(), transf.getRightRotation()));
                }, mode);
            }
        }
    }

    private void rotationHandleClick(Player player, int slot, boolean isLeftClick, Set<Display> selections, boolean sneak, Editor mode) {
        if (slot == 3) { // Toggle Mode
            boolean isRight = Editor.rightRotationMap.getOrDefault(player.getUniqueId(), false);
            Editor.rightRotationMap.put(player.getUniqueId(), !isRight);

            SoundUtil.playSoundUIClick(player);
            mode.setup(player);

            sendActionBarFeedback(player, !isRight ? " §8| §c(Warping may occur!)" : null);
            return;
        }

        float rotationDegrees = (float) ((isLeftClick ? -1 : 1) * (Math.PI / 180 * (sneak ? C.ROTATE_FINE : C.ROTATE_COARSE)));
        boolean isRight = Editor.rightRotationMap.getOrDefault(player.getUniqueId(), false);

        for (Display sel : selections) {
            switch (slot) {
                case 0 -> edit(sel, player, () -> {
                    Transformation transf = sel.getTransformation();
                    if (isRight) {
                        Quaternionf deltaRot = new Quaternionf().rotationX(rotationDegrees);
                        Quaternionf rRot = transf.getRightRotation();
                        rRot.set(rRot.mul(deltaRot));
                        sel.setTransformation(new Transformation(transf.getTranslation(), transf.getLeftRotation(), transf.getScale(), rRot));
                    } else {
                        Quaternionf deltaRot = new Quaternionf().rotationX(rotationDegrees);
                        Vector3f trans = transf.getTranslation();
                        trans.rotate(deltaRot);
                        Quaternionf lRot = transf.getLeftRotation();
                        lRot.set(deltaRot.mul(lRot));
                        sel.setTransformation(new Transformation(trans, lRot, transf.getScale(), transf.getRightRotation()));
                    }
                }, mode);
                case 1 -> edit(sel, player, () -> {
                    Transformation transf = sel.getTransformation();
                    if (isRight) {
                        Quaternionf deltaRot = new Quaternionf().rotationY(rotationDegrees);
                        Quaternionf rRot = transf.getRightRotation();
                        rRot.set(rRot.mul(deltaRot));
                        sel.setTransformation(new Transformation(transf.getTranslation(), transf.getLeftRotation(), transf.getScale(), rRot));
                    } else {
                        Quaternionf deltaRot = new Quaternionf().rotationY(rotationDegrees);
                        Vector3f trans = transf.getTranslation();
                        trans.rotate(deltaRot);
                        Quaternionf lRot = transf.getLeftRotation();
                        lRot.set(deltaRot.mul(lRot));
                        sel.setTransformation(new Transformation(trans, lRot, transf.getScale(), transf.getRightRotation()));
                    }
                }, mode);
                case 2 -> edit(sel, player, () -> {
                    Transformation transf = sel.getTransformation();
                    if (isRight) {
                        Quaternionf deltaRot = new Quaternionf().rotationZ(rotationDegrees);
                        Quaternionf rRot = transf.getRightRotation();
                        rRot.set(rRot.mul(deltaRot));
                        sel.setTransformation(new Transformation(transf.getTranslation(), transf.getLeftRotation(), transf.getScale(), rRot));
                    } else {
                        Quaternionf deltaRot = new Quaternionf().rotationZ(rotationDegrees);
                        Vector3f trans = transf.getTranslation();
                        trans.rotate(deltaRot);
                        Quaternionf lRot = transf.getLeftRotation();
                        lRot.set(deltaRot.mul(lRot));
                        sel.setTransformation(new Transformation(trans, lRot, transf.getScale(), transf.getRightRotation()));
                    }
                }, mode);
                case 4 -> {
                    if (!isRight) {
                        edit(sel, player, () -> sel.setBillboard(Display.Billboard.values()[(sel.getBillboard().ordinal()
                                + Display.Billboard.values().length + (isLeftClick ? -1 : 1)) % Display.Billboard.values().length]), mode);
                    }
                }
                case 6 -> edit(sel, player, () -> {
                    Transformation transf = sel.getTransformation();
                    if (isRight) {
                        sel.setTransformation(new Transformation(transf.getTranslation(), transf.getLeftRotation(), transf.getScale(), new Quaternionf()));
                    } else {
                        sel.setTransformation(new Transformation(transf.getTranslation(), new Quaternionf(), transf.getScale(), transf.getRightRotation()));
                    }
                }, mode);
            }
        }
    }

    private void positionHandleClick(Player player, int slot, boolean isLeftClick, Set<Display> selections, boolean sneak, Editor mode) {
        double move = (isLeftClick ? -1 : 1) * (sneak ? C.MOVE_FINE : C.MOVE_COARSE);
        for (Display sel : selections) {
            Location current = sel.getLocation();
            Location target = current.clone();

            switch (slot) {
                case 0 -> target.add(move, 0, 0);
                case 1 -> target.add(0, move, 0);
                case 2 -> target.add(0, 0, move);
                case 4 -> {
                    target = isLeftClick ? player.getEyeLocation().clone() : player.getLocation().clone();
                    target.setYaw(current.getYaw());
                    target.setPitch(current.getPitch());
                }
                case 6 -> {
                    target = new Location(current.getWorld(), current.getBlockX(), current.getBlockY(), current.getBlockZ(), current.getYaw(), current.getPitch());
                }
            }

            if (!canEditHere(player, target)) {
                player.sendMessage("§cYou cannot move this entity into an untrusted claim.");
                SoundUtil.playSoundNo(player);
                continue;
            }

            final Location finalTarget = target;
            edit(sel, player, () -> sel.teleport(finalTarget), mode);
        }
    }

    private boolean canEditHere(Player player, Location location) {
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("GriefPrevention") == null) return true;
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
        if (claim == null) return true;
        return claim.allowBuild(player, location.getBlock().getType()) == null;
    }

    private void edit(Display selection, Player player, Runnable consumer, Editor mode) {
        edit(selection, player, consumer, mode, false, true);
    }

    private void edit(Display selection, Player player, Runnable consumer, Editor mode, boolean bypassDistanceCheck, boolean reloadBar) {
        if (!bypassDistanceCheck && selection.getLocation().distanceSquared(player.getLocation()) > C.MAX_EDIT_RADIUS_SQUARED) {
            return;
        }
        if (!canEditHere(player, selection.getLocation())) {
            player.sendMessage("§cYou are not trusted in this claim.");
            return;
        }
        consumer.run();
        SoundUtil.playSoundUIClick(player);
        if (reloadBar) {
            mode.setup(player);
            sendActionBarFeedback(player);
        }
    }

    private void sendActionBarFeedback(Player player, String suffix) {
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack item = player.getInventory().getItem(slot);
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            String msg = meta.getDisplayName();
            if (meta.hasLore() && !meta.getLore().isEmpty()) {
                // Combine the name with the first line of the lore
                msg += " §8| " + meta.getLore().get(0);
            }
            if (suffix != null) {
                msg += suffix;
            }
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg));
        }
    }

    private void sendActionBarFeedback(Player player) {
        sendActionBarFeedback(player, null);
    }
}