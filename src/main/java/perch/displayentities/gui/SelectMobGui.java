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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SelectMobGui implements PagedGui {

    private enum SortMode {
        DISTANCE("Distance (Nearest)"),
        NEWEST("Age (Newest)"),
        TYPE("Type (Block/Item/Text)");

        final String displayName;
        SortMode(String displayName) { this.displayName = displayName; }
    }

    private final Inventory inv;
    private final Player player;
    private int page = 1;
    private final List<Entity> entities;
    private final List<Entity> selected;
    private final boolean copy;
    private SortMode currentSort = SortMode.DISTANCE;

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

        // Initial sort
        sortEntities();
    }

    private void sortEntities() {
        switch (currentSort) {
            case DISTANCE:
                entities.sort((e1, e2) -> {
                    Location pLoc = player.getLocation();
                    Location l1 = e1.getLocation();
                    Location l2 = e2.getLocation();
                    boolean sameWorld1 = l1.getWorld() != null && l1.getWorld().equals(pLoc.getWorld());
                    boolean sameWorld2 = l2.getWorld() != null && l2.getWorld().equals(pLoc.getWorld());

                    if (sameWorld1 && !sameWorld2) return -1;
                    if (!sameWorld1 && sameWorld2) return 1;
                    if (!sameWorld1 && !sameWorld2) return 0;
                    return Double.compare(l1.distanceSquared(pLoc), l2.distanceSquared(pLoc));
                });
                break;
            case NEWEST:
                // Higher Entity ID usually means newer entity
                entities.sort(Comparator.comparingInt(Entity::getEntityId).reversed());
                break;
            case TYPE:
                entities.sort((e1, e2) -> {
                    String type1 = e1.getType().name();
                    String type2 = e2.getType().name();
                    // Secondary sort by distance so types are grouped but still ordered by proximity
                    int typeCompare = type1.compareTo(type2);
                    if (typeCompare != 0) return typeCompare;

                    Location pLoc = player.getLocation();
                    return Double.compare(e1.getLocation().distanceSquared(pLoc), e2.getLocation().distanceSquared(pLoc));
                });
                break;
        }
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

        // Previous Page
        if (event.getSlot() == 46) {
            page = Math.max(1, page - 1);
            updateInventory();
            return;
        }

        // Next Page
        if (event.getSlot() == 52) {
            page = Math.min(page + 1, 1 + entities.size() / 45 + (entities.size() % 45 == 0 ? -1 : 0));
            updateInventory();
            return;
        }

        // Sort Button
        if (event.getSlot() == 53) {
            cycleSortMode();
            SoundUtil.playSoundUIClick(player);
            return;
        }

        // Entity Selection Logic
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

    private void cycleSortMode() {
        int nextOrdinal = (currentSort.ordinal() + 1) % SortMode.values().length;
        currentSort = SortMode.values()[nextOrdinal];
        sortEntities();
        // Reset to page 1 when sorting changes so the user doesn't get lost
        page = 1;
        updateInventory();
    }

    private ItemStack getSortButton() {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Sort Mode: §e" + currentSort.displayName);
        List<String> lore = new ArrayList<>();
        lore.add("§7Click to cycle sorting method:");
        for (SortMode mode : SortMode.values()) {
            if (mode == currentSort) {
                lore.add("§8» " + mode.displayName);
            } else {
                lore.add("§f  " + mode.displayName);
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void updateInventory() {
        inv.setItem(46, page > 1 ? getPreviousPageItem() : null);
        inv.setItem(52, page < entities.size() / 45 + (entities.size() % 45 == 0 ? 0 : 1) ? getNextPageItem() : null);
        inv.setItem(53, getSortButton()); // Add the sort button

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