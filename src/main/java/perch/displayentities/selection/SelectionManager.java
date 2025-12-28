package perch.displayentities.selection;

import perch.displayentities.PerchDisplayEntities;
import perch.displayentities.Util;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SelectionManager {

    // Multi-selection support: each player can have a set of selected Displays
    private static final HashMap<Player, Set<Display>> selections = new HashMap<>();
    private static final HashMap<Player, CopyPasteOption> pasteOptions = new HashMap<>();
    private static final HashMap<Player, Editor> editor = new HashMap<>();
    private static final HashMap<Player, ItemStack[]> inventoryBackup = new HashMap<>();
    private static final HashMap<Player, ItemStack[]> offhandBackup = new HashMap<>();
    private static final HashMap<Player, ItemStack[]> equipmentBackup = new HashMap<>();
    private static BukkitTask cornerFlash;

    private static final int MAX_SELECTION = 100;

    @Nullable
    public static ItemStack[] getInventoryBackup(@NotNull Player player) {
        return inventoryBackup.get(player);
    }

    /**
     * Add a Display to the player's selection set.
     * Use this for multi-selection (e.g., radius command).
     */
    public static void select(@NotNull Player player, @NotNull Display display) {
        Set<Display> set = selections.computeIfAbsent(player, k -> new HashSet<>());
        if (set.size() >= MAX_SELECTION) {
            player.sendMessage("§cYou cannot select more than " + MAX_SELECTION + " entities at once.");
            return;
        }
        set.add(display);
        Editor mode = SelectionManager.geteditor(player);
        if (mode != null)
            mode.setup(player);
        player.playSound(player, Sound.UI_BUTTON_CLICK, 1F, 0.9F);
        Util.flashEntities(player, display);
    }

    /**
     * Remove a specific Display from the player's selection set.
     * Removes by UUID to avoid instance issues.
     */
    public static boolean deselect(@NotNull Player player, @NotNull Display display) {
        Set<Display> set = selections.get(player);
        if (set != null) {
            boolean removed = set.removeIf(e -> e.getUniqueId().equals(display.getUniqueId()));
            if (removed) {
                player.playSound(player, Sound.UI_BUTTON_CLICK, 1F, 0.1F);
                if (set.isEmpty()) {
                    selections.remove(player);
                    Editor mode = SelectionManager.geteditor(player);
                    if (mode != null)
                        mode.setup(player);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Remove all selections for the player.
     * Use this before single-selection commands to preserve legacy behavior.
     */
    public static boolean deselectAll(@NotNull Player player) {
        boolean hadSelection = selections.containsKey(player);
        if (hadSelection)
            player.playSound(player, Sound.UI_BUTTON_CLICK, 1F, 0.1F);
        if (selections.remove(player) != null) {
            Editor mode = SelectionManager.geteditor(player);
            if (mode != null)
                mode.setup(player);
            return true;
        }
        return false;
    }

    /**
     * Legacy compatibility: Remove all selections for the player.
     * This allows old code calling deselect(player) to still work.
     */
    public static boolean deselect(@NotNull Player player) {
        return deselectAll(player);
    }

    /**
     * Get the first selected Display for legacy compatibility.
     * Old code using getSelection() will still work.
     */
    @Nullable
    public static Display getSelection(@NotNull Player player) {
        Set<Display> set = selections.get(player);
        if (set != null && !set.isEmpty())
            return set.iterator().next();
        return null;
    }

    /**
     * Get all selected Displays for a player.
     * Use this for multi-selection features.
     */
    @NotNull
    public static Set<Display> getSelections(@NotNull Player player) {
        return selections.getOrDefault(player, Collections.emptySet());
    }

    /**
     * Legacy: true if at least one selection.
     */
    public static boolean hasSelection(@NotNull Player player) {
        return getSelection(player) != null;
    }

    public static boolean isOneditor(@NotNull Player player) {
        return geteditor(player) != null;
    }

    public static void seteditor(@NotNull Player player, @Nullable Editor mode) {
        Editor now = geteditor(player);
        if (now == mode)
            return;

        if (!editor.containsKey(player)) {
            inventoryBackup.put(player, player.getInventory().getStorageContents());
            offhandBackup.put(player, player.getInventory().getExtraContents());
            equipmentBackup.put(player, player.getInventory().getArmorContents());
            player.getInventory().clear();
            if (!selections.containsKey(player))
                player.playSound(player, Sound.UI_BUTTON_CLICK, 1F, 0.9F);
        }

        if (mode == null) {
            editor.remove(player);
            selections.remove(player);
            player.getInventory().setStorageContents(inventoryBackup.remove(player));
            player.getInventory().setExtraContents(offhandBackup.remove(player));
            player.getInventory().setArmorContents(equipmentBackup.remove(player));
            player.playSound(player, Sound.UI_BUTTON_CLICK, 1F, 0.1F);
            if (PerchDisplayEntities.get().getConfig().loadBoolean("editor.actionbar_reminder", true))
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new ComponentBuilder().create());
        } else {
            editor.put(player, mode);
            mode.setup(player);
        }

        if (editor.isEmpty() && cornerFlash != null) {
            cornerFlash.cancel();
            cornerFlash = null;
        }
        if (!editor.isEmpty() && cornerFlash == null) {
            cornerFlash = new BukkitRunnable() {
                long counter = 0;

                @Override
                public void run() {
                    counter++;
                    editor.keySet().forEach(p -> {
                        if (!(p.isValid() && p.isOnline()))
                            return;
                        if (PerchDisplayEntities.get().getConfig().loadBoolean("editor.actionbar_reminder", true) && counter % 5 == 0)
                            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new ComponentBuilder(
                                    PerchDisplayEntities.get().getLanguageConfig(p).getMessage("editor.reminder",
                                            "&6You are on (Display) Editor Mode")).create());
                    });
                    // Show particles for all selected displays
                    selections.forEach((p, dispSet) -> {
                        if (!(p.isValid() && p.isOnline()))
                            return;
                        for (Display disp : dispSet) {
                            if (disp.isValid() && p.getWorld().equals(disp.getWorld()))
                                p.spawnParticle(Util.isVersionAfter(1,20,5)?
                                                Particle.DUST:Particle.valueOf("REDSTONE"), disp.getLocation(),
                                        1, 0, 0, 0, 0,
                                        new Particle.DustOptions(Color.RED, 0.5F));
                        }
                    });
                }
            }.runTaskTimer(PerchDisplayEntities.get(), 2L, 2L);
        }
    }

    @Nullable
    public static Editor geteditor(@NotNull Player player) {
        return editor.get(player);
    }

    public static void swapeditor(@NotNull Player player) {
        swapeditor(player, 1);
    }

    public static void swapeditor(@NotNull Player player, int amount) {
        Editor mode = geteditor(player);
        if (mode == null)
            seteditor(player, Editor.POSITION);
        else
            seteditor(player, Editor.values()[(Editor.values().length + mode.ordinal() + amount) % Editor.values().length]);
    }

    public static CopyPasteOption getCopyPasteOption(@NotNull Player player) {
        pasteOptions.putIfAbsent(player, new CopyPasteOption());
        return pasteOptions.get(player);
    }
}