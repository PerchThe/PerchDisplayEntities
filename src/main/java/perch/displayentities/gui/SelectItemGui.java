package perch.displayentities.gui;

import perch.displayentities.PerchDisplayEntities;
import perch.displayentities.SoundUtil;
import perch.displayentities.selection.Editor;
import perch.displayentities.selection.SelectionManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class SelectItemGui implements Gui {

    private final Inventory inv;
    private final Player player;

    public SelectItemGui(@NotNull Player player) {
        inv = Bukkit.createInventory(this, 5 * 9, getLanguageMessage("gui.select_item.title"));
        this.player = player;
    }

    @Override
    public void onClose(@NotNull InventoryCloseEvent event) {
        // No action needed on close
    }

    @Override
    public void onClick(@NotNull InventoryClickEvent event) {
        if (event.getClickedInventory() != inv || event.getSlot() >= 45) {
            SoundUtil.playSoundNo(player);
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            SoundUtil.playSoundNo(player);
            return;
        }
        Display display = SelectionManager.getSelection(player);
        if (display == null) {
            player.closeInventory();
            SoundUtil.playSoundNo(player);
            return;
        }
        if (display instanceof ItemDisplay itemDisplay) {
            itemDisplay.setItemStack(item.clone());
        } else if (display instanceof BlockDisplay blockDisplay && item.getType().isBlock()) {
            // Only set if the material is a valid block and can be used as block data
            try {
                blockDisplay.setBlock(item.getType().createBlockData());
            } catch (Exception e) {
                SoundUtil.playSoundNo(player);
                return;
            }
        } else {
            SoundUtil.playSoundNo(player);
            return;
        }
        SoundUtil.playSoundUIClick(player);
        Editor mode = SelectionManager.geteditor(player);
        if (mode != null)
            mode.setup(player);
        event.getWhoClicked().closeInventory();
    }

    @Override
    public void onDrag(@NotNull InventoryDragEvent event) {
        // No drag actions needed
    }

    @Override
    public void onOpen(@NotNull InventoryOpenEvent event) {
        ItemStack[] items = SelectionManager.getInventoryBackup((Player) event.getPlayer());
        if (items != null) {
            for (int i = 0; i < items.length && i < inv.getSize(); i++) {
                inv.setItem(i, items[i]);
            }
        }
        // Place info item in the center slot (slot 40)
        inv.setItem(40, this.loadLanguageDescription(
                getGuiItem("select_item.info", Material.PAPER), "gui.select_item.info"));
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inv;
    }

    @NotNull
    @Override
    public Player getTargetPlayer() {
        return player;
    }

    @NotNull
    @Override
    public PerchDisplayEntities getPlugin() {
        return PerchDisplayEntities.get();
    }
}