package perch.displayentities.gui;

import perch.displayentities.PerchDisplayEntities;
import perch.displayentities.SoundUtil;
import perch.displayentities.selection.SelectionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class SelectMobGui implements PagedGui {

    private final Inventory inv;
    private final Player player;
    private int page = 1;
    private final List<Entity> entities;
    private final List<Entity> selected;
    private final boolean copy;

    // For double-click detection
    private int lastClickedSlot = -1;
    private long lastClickTime = 0;
    private static final long DOUBLE_CLICK_THRESHOLD_MS = 500;

    public SelectMobGui(@NotNull Player player, Collection<Entity> entities, boolean copy) {
        inv = Bukkit.createInventory(this, 6 * 9, getLanguageMessage("gui.select_entity.title"));
        this.player = player;
        this.entities = new ArrayList<>();
        if (copy) {
            this.entities.addAll(entities);
            selected = new ArrayList<>(entities);
        } else {
            for (Entity en : entities) {
                if (en instanceof Display) {
                    this.entities.add(en);
                }
            }
            selected = new ArrayList<>();
        }
        this.copy = copy;
    }

    @Override
    public void onClose(@NotNull InventoryCloseEvent event) {}

    @Override
    public void onClick(@NotNull InventoryClickEvent event) {
        if (event.getClickedInventory() != inv) {
            SoundUtil.playSoundNo(player);
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            SoundUtil.playSoundNo(player);
            return;
        }
        if (event.getSlot() == 46) { //page change
            page = Math.max(1, page - 1);
            updateInventory();
            return;
        }
        if (event.getSlot() == 52) { //page change
            page = Math.min(page + 1, 1 + entities.size() / 45 + (entities.size() % 45 == 0 ? -1 : 0));
            updateInventory();
            return;
        }
        int index = event.getSlot() + (page - 1) * 45;
        if (index < 0 || index >= entities.size()) {
            SoundUtil.playSoundNo(player);
            return;
        }
        Entity entity = entities.get(index);

        boolean rightClick = event.isRightClick();
        boolean leftClick = event.isLeftClick();

        long now = System.currentTimeMillis();
        boolean isDoubleClick = leftClick && lastClickedSlot == event.getSlot() && (now - lastClickTime) <= DOUBLE_CLICK_THRESHOLD_MS;

        if (rightClick) {
            // Deselect on right click
            if (selected.removeIf(e -> e.getUniqueId().equals(entity.getUniqueId()))) {
                if (entity instanceof Display) {
                    SelectionManager.deselect(player, (Display) entity);
                }
                SoundUtil.playSoundUIClick(player);
            } else {
                SoundUtil.playSoundNo(player);
            }
        } else if (leftClick) {
            if (isDoubleClick) {
                // Double left click: select only this entity
                selected.clear();
                selected.add(entity);
                if (entity instanceof Display) {
                    SelectionManager.deselectAll(player);
                    SelectionManager.select(player, (Display) entity);
                }
                SoundUtil.playSoundUIClick(player);
            } else {
                // Single left click: add to selection if not already present
                boolean alreadySelected = selected.stream().anyMatch(e -> e.getUniqueId().equals(entity.getUniqueId()));
                if (!alreadySelected) {
                    selected.add(entity);
                    if (entity instanceof Display) {
                        SelectionManager.select(player, (Display) entity);
                    }
                    SoundUtil.playSoundUIClick(player);
                } else {
                    SoundUtil.playSoundNo(player);
                }
            }
            lastClickedSlot = event.getSlot();
            lastClickTime = now;
        }

        // For copy mode, update the copy-paste option
        if (copy) {
            SelectionManager.getCopyPasteOption(player).copy(selected, player.getLocation());
        }

        updateInventory();
    }

    private void updateInventory() {
        inv.setItem(46, page > 1 ? getPreviousPageItem() : null);
        inv.setItem(52, page < entities.size() / 45 + (entities.size() % 45 == 0 ? 0 : 1) ? getNextPageItem() : null);
        for (int i = 0; i < 45; i++) {
            int entityIndex = i + (page - 1) * 45;
            if (entityIndex >= entities.size()) {
                inv.setItem(i, null);
                continue;
            }
            Entity entity = entities.get(entityIndex);
            ItemStack item;

            boolean isSelected = selected.stream().anyMatch(e -> e.getUniqueId().equals(entity.getUniqueId()));

            if (entity instanceof ItemDisplay itemDisplay) {
                ItemStack stack = itemDisplay.getItemStack();
                if (stack == null || stack.getType().isAir()) {
                    item = new ItemStack(Material.BARRIER);
                } else {
                    item = stack.clone();
                }
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§eItemDisplay: " + (entity.getCustomName() != null ? entity.getCustomName() : entity.getUniqueId().toString()));
                List<String> lore = new ArrayList<>();
                addPositionLore(lore, entity);
                lore.add("§7Left click to select, right click to deselect");
                lore.add("§7Double left click to select only this entity");
                if (isSelected) {
                    lore.add("§f[SELECTED]");
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
                inv.setItem(i, item);

            } else if (entity instanceof BlockDisplay blockDisplay) {
                Material mat = Material.BARRIER;
                if (blockDisplay.getBlock() != null && blockDisplay.getBlock().getMaterial() != Material.AIR) {
                    Material candidate = blockDisplay.getBlock().getMaterial();
                    if (candidate.isItem()) {
                        mat = candidate;
                    }
                }
                item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§aBlockDisplay: " + (entity.getCustomName() != null ? entity.getCustomName() : entity.getUniqueId().toString()));
                List<String> lore = new ArrayList<>();
                addPositionLore(lore, entity);
                lore.add("§eLeft click §7to select, right click to deselect");
                lore.add("§eDouble left click §7to select only this entity");
                if (isSelected) {
                    lore.add("§f[SELECTED]");
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
                inv.setItem(i, item);

            } else if (entity instanceof TextDisplay textDisplay) {
                item = new ItemStack(Material.NAME_TAG);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§bTextDisplay: " + (textDisplay.getText() != null ? textDisplay.getText() : "No Text"));
                List<String> lore = new ArrayList<>();
                addPositionLore(lore, entity);
                lore.add("§7Left click to select, right click to deselect");
                lore.add("§7Double left click to select only this entity");
                if (isSelected) {
                    lore.add("§f[SELECTED]");
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
                inv.setItem(i, item);

            } else {
                item = new ItemStack(Material.ARMOR_STAND);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(entity.getType().name());
                List<String> lore = new ArrayList<>();
                addPositionLore(lore, entity);
                lore.add("§7Left click to select, right click to deselect");
                lore.add("§7Double left click to select only this entity");
                if (isSelected) {
                    lore.add("§f[SELECTED]");
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
                inv.setItem(i, item);
            }
        }
    }

    private void addPositionLore(List<String> lore, Entity entity) {
        Location eloc = entity.getLocation();
        Location ploc = player.getLocation();

        int bx = eloc.getBlockX();
        int by = eloc.getBlockY();
        int bz = eloc.getBlockZ();

        lore.add("§7Pos: §fX: " + bx + " Y: " + by + " Z: " + bz);

        String distanceStr;
        if (eloc.getWorld() != null && ploc.getWorld() != null && eloc.getWorld().equals(ploc.getWorld())) {
            double dist = eloc.distance(ploc);
            distanceStr = format2(dist) + " blocks";
        } else {
            distanceStr = "N/A (different world)";
        }
        lore.add("§7Dist: §f" + distanceStr);
    }

    private String format2(double d) {
        return String.format(Locale.US, "%.2f", d);
    }

    @Override
    public void onDrag(@NotNull InventoryDragEvent event) {}

    @Override
    public void onOpen(@NotNull InventoryOpenEvent event) {
        updateInventory();
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

    @Override
    public int getPage() {
        return page;
    }
}
