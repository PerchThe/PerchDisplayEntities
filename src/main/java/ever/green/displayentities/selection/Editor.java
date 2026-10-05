package ever.green.displayentities.selection;

import ever.green.displayentities.C;
import ever.green.displayentities.DisplayEntities;
import ever.green.displayentities.Util;
import ever.green.displayentities.selection.blockdata.BlockDataInteractor;
import ever.green.displayentities.selection.blockdata.BlockDataUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public enum Editor {

    POSITION,
    ROTATION,
    TRANSLATION,
    SCALE,
    SHADOW,
    ENTITY_SPECIFIC,
    COPY_PASTE;

    private final DecimalFormat optional3Digit = new DecimalFormat("0.###");
    public static final Map<UUID, Boolean> rightRotationMap = new HashMap<>(); // Tracks toggle state

    Editor() {
        optional3Digit.setRoundingMode(RoundingMode.HALF_DOWN);
    }

    public static boolean isEntitySpecificEmpty(Display display) {
        if (display instanceof BlockDisplay bd) {
            List<BlockDataInteractor> datas = BlockDataUtil.getBlockDataValues(bd.getBlock());
            return datas == null || datas.isEmpty();
        }
        return false;
    }

    private String getColorName(Color color) {
        if (color == null) return "Default (Gray)";
        int r = color.getRed(), g = color.getGreen(), b = color.getBlue();
        if (r == 125 && g == 125 && b == 125) return "Gray";
        if (r == 0 && g == 0 && b == 0) return "Black";
        if (r == 255 && g == 255 && b == 255) return "White";
        if (r == 255 && g == 0 && b == 0) return "Red";
        if (r == 255 && g == 200 && b == 0) return "Orange";
        if (r == 255 && g == 255 && b == 0) return "Yellow";
        if (r == 0 && g == 255 && b == 0) return "Lime";
        if (r == 0 && g == 128 && b == 0) return "Green";
        if (r == 0 && g == 255 && b == 255) return "Aqua";
        if (r == 0 && g == 0 && b == 255) return "Blue";
        if (r == 128 && g == 0 && b == 128) return "Purple";
        if (r == 255 && g == 0 && b == 255) return "Fuchsia";

        return String.format("Custom (#%02X%02X%02X)", r, g, b);
    }

    private Material getColorMaterial(Color color) {
        if (color == null) return Material.GRAY_STAINED_GLASS;
        int r = color.getRed(), g = color.getGreen(), b = color.getBlue();
        if (r == 125 && g == 125 && b == 125) return Material.GRAY_STAINED_GLASS;
        if (r == 0 && g == 0 && b == 0) return Material.BLACK_STAINED_GLASS;
        if (r == 255 && g == 255 && b == 255) return Material.WHITE_STAINED_GLASS;
        if (r == 255 && g == 0 && b == 0) return Material.RED_STAINED_GLASS;
        if (r == 255 && g == 200 && b == 0) return Material.ORANGE_STAINED_GLASS;
        if (r == 255 && g == 255 && b == 0) return Material.YELLOW_STAINED_GLASS;
        if (r == 0 && g == 255 && b == 0) return Material.LIME_STAINED_GLASS;
        if (r == 0 && g == 128 && b == 0) return Material.GREEN_STAINED_GLASS;
        if (r == 0 && g == 255 && b == 255) return Material.LIGHT_BLUE_STAINED_GLASS;
        if (r == 0 && g == 0 && b == 255) return Material.BLUE_STAINED_GLASS;
        if (r == 128 && g == 0 && b == 128) return Material.PURPLE_STAINED_GLASS;
        if (r == 255 && g == 0 && b == 255) return Material.MAGENTA_STAINED_GLASS; // Fuchsia visually

        return Material.BEACON; // Custom hex color fallback!
    }

    public void setup(Player player) {
        Inventory inv = player.getInventory();
        Display display = SelectionManager.getSelection(player);

        // Page is strictly Slot 8
        inv.setItem(8, setDesc(craftItem(Material.PAPER, ordinal() + 1), player, "editor.all.page"));

        // Exit is strictly Slot 7, and only on the first page
        if (this == Editor.POSITION) {
            inv.setItem(7, setDesc(craftItem(Material.RED_STAINED_GLASS_PANE), player, "editor.all.exit"));
        } else {
            inv.setItem(7, null);
        }

        if (display == null && this != COPY_PASTE) {
            for (int i = 0; i < 7; i++)
                inv.setItem(i, setDesc(craftItem(Material.STRUCTURE_VOID), player, "editor.all.select"));
            fillEmptyHotbar(inv);
            return;
        }

        String[] holders;
        switch (this) {
            case POSITION -> {
                Location loc = display.getLocation();
                holders = new String[]{"%x_offset%", optional3Digit.format(loc.getX() - loc.getBlockX()),
                        "%y_offset%", optional3Digit.format(loc.getY() - loc.getBlockY()),
                        "%z_offset%", optional3Digit.format(loc.getZ() - loc.getBlockZ()),
                        "%move_coarse%", String.valueOf(C.MOVE_COARSE), "%move_fine%", String.valueOf(C.MOVE_FINE)};
                inv.setItem(0, setDesc(craftItem(Material.LAPIS_LAZULI), player, "editor.position.x", holders));
                inv.setItem(1, setDesc(craftItem(Material.REDSTONE), player, "editor.position.y", holders));
                inv.setItem(2, setDesc(craftItem(Material.EMERALD), player, "editor.position.z", holders));
                inv.setItem(3, null);
                inv.setItem(4, setDesc(craftItem(Material.ENDER_PEARL), player, "editor.position.teleport"));
                inv.setItem(5, null);
                inv.setItem(6, setDesc(craftItem(Material.ANVIL), player, "editor.position.reset"));
            }
            case ROTATION -> {
                boolean isRight = rightRotationMap.getOrDefault(player.getUniqueId(), false);
                Vector3f vect = isRight ?
                        display.getTransformation().getRightRotation().getEulerAnglesXYZ(new Vector3f()) :
                        display.getTransformation().getLeftRotation().getEulerAnglesXYZ(new Vector3f());

                double x = vect.x; x = (x / Math.PI + (x < 0 ? 2 : 0)) * 180;
                double y = vect.y; y = (y / Math.PI + (y < 0 ? 2 : 0)) * 180;
                double z = vect.z; z = (z / Math.PI + (z < 0 ? 2 : 0)) * 180;

                String rotMode = isRight ? "Right" : "Left";

                holders = new String[]{"%x_degree%", optional3Digit.format(x),
                        "%y_degree%", optional3Digit.format(y), "%z_degree%", optional3Digit.format(z),
                        "%rotate_coarse%", String.valueOf(C.ROTATE_COARSE), "%rotate_fine%", String.valueOf(C.ROTATE_FINE),
                        "%mode%", rotMode};

                Material matX = isRight ? Material.RED_CONCRETE_POWDER : Material.RED_CONCRETE;
                Material matY = isRight ? Material.LIME_CONCRETE_POWDER : Material.LIME_CONCRETE;
                Material matZ = isRight ? Material.BLUE_CONCRETE_POWDER : Material.BLUE_CONCRETE;

                inv.setItem(0, setDesc(craftItem(matX), player, "editor.rotation.x", holders));
                inv.setItem(1, setDesc(craftItem(matY), player, "editor.rotation.y", holders));
                inv.setItem(2, setDesc(craftItem(matZ), player, "editor.rotation.z", holders));

                // Toggle Button
                inv.setItem(3, setDesc(craftItem(Material.COMPASS), player, "editor.rotation.toggle", "%value%", rotMode));

                if (!isRight) {
                    inv.setItem(4, setDesc(craftItem(Material.COMPARATOR), player, "editor.rotation.mode",
                            "%value%", display.getBillboard().name().toLowerCase(Locale.ENGLISH)));
                } else {
                    inv.setItem(4, null); // Billboard mode only applies generally, hiding on right rotation reduces clutter
                }

                inv.setItem(5, null);
                inv.setItem(6, setDesc(craftItem(Material.ANVIL), player, "editor.rotation.reset"));
            }
            case TRANSLATION -> {
                Vector3f trans = display.getTransformation().getTranslation();
                holders = new String[]{"%x_trans%", optional3Digit.format(trans.x),
                        "%y_trans%", optional3Digit.format(trans.y),
                        "%z_trans%", optional3Digit.format(trans.z),
                        "%move_coarse%", String.valueOf(C.MOVE_COARSE), "%move_fine%", String.valueOf(C.MOVE_FINE)};
                inv.setItem(0, setDesc(craftItem(Material.LAPIS_BLOCK), player, "editor.translation.x", holders));
                inv.setItem(1, setDesc(craftItem(Material.REDSTONE_BLOCK), player, "editor.translation.y", holders));
                inv.setItem(2, setDesc(craftItem(Material.EMERALD_BLOCK), player, "editor.translation.z", holders));
                inv.setItem(3, null);
                inv.setItem(4, setDesc(craftItem(Material.ENDER_EYE), player, "editor.translation.origin_to_player"));
                inv.setItem(5, null);
                inv.setItem(6, setDesc(craftItem(Material.BARRIER), player, "editor.translation.reset"));
            }
            case SCALE -> {
                Vector3f scale = display.getTransformation().getScale();
                holders = new String[]{"%x_scale%", optional3Digit.format(scale.x),
                        "%y_scale%", optional3Digit.format(scale.y),
                        "%z_scale%", optional3Digit.format(scale.z), "%scale_coarse%", String.valueOf(C.SCALE_COARSE), "%scale_fine%", String.valueOf(C.SCALE_FINE)};
                inv.setItem(0, setDesc(craftItem(Material.BLUE_DYE), player, "editor.scale.x", holders));
                inv.setItem(1, setDesc(craftItem(Material.RED_DYE), player, "editor.scale.y", holders));
                inv.setItem(2, setDesc(craftItem(Material.LIME_DYE), player, "editor.scale.z", holders));
                inv.setItem(3, null);
                inv.setItem(4, setDesc(craftItem(Material.GRAY_DYE), player, "editor.scale.all", holders));
                inv.setItem(5, null);
                inv.setItem(6, setDesc(craftItem(Material.ANVIL), player, "editor.scale.reset"));
            }
            case SHADOW -> {
                Display.Brightness brightness = display.getBrightness();
                if (brightness == null) {
                    inv.setItem(0, setDesc(craftItem(Material.TORCH), player, "editor.shadow.skylight"));
                    inv.setItem(1, setDesc(craftItem(Material.DAYLIGHT_DETECTOR), player, "editor.shadow.blocklight"));
                } else {
                    ItemStack itemStack = craftItem(Material.LIGHT);
                    BlockDataMeta meta = (BlockDataMeta) itemStack.getItemMeta();
                    assert meta != null;
                    meta.setBlockData(Bukkit.createBlockData(Material.LIGHT, (data) -> ((Light) data).setLevel(brightness.getSkyLight())));
                    itemStack.setItemMeta(meta);
                    inv.setItem(0, setDesc(itemStack, player, "editor.shadow.skylight"));
                    ItemStack itemStack2 = craftItem(Material.LIGHT);
                    BlockDataMeta meta2 = (BlockDataMeta) itemStack2.getItemMeta();
                    assert meta2 != null;
                    meta2.setBlockData(Bukkit.createBlockData(Material.LIGHT, (data) -> ((Light) data).setLevel(brightness.getBlockLight())));
                    itemStack2.setItemMeta(meta2);
                    inv.setItem(1, setDesc(itemStack2, player, "editor.shadow.blocklight"));
                }
                inv.setItem(2, setDesc(craftItem(Material.ENDER_EYE, (int) (display.getViewRange() * 64)), player,
                        "editor.shadow.see_distance", "%value%", String.valueOf((int) (display.getViewRange() * 64))));
                inv.setItem(3, setDesc(craftItem(Material.GLOWSTONE_DUST), player, "editor.shadow.reset"));
                inv.setItem(4, null);
                inv.setItem(5, null);
                inv.setItem(6, null);
            }
            case ENTITY_SPECIFIC -> {
                if (display instanceof TextDisplay textDisplay) {
                    Color backGround = textDisplay.getBackgroundColor();
                    String colorName = getColorName(backGround);
                    Material colorMat = getColorMaterial(backGround);

                    inv.setItem(0, setDesc(craftItem(colorMat), player,
                            "editor.entity_specific.text_background_color", "%value%", colorName));

                    inv.setItem(1, setDesc(craftItem(Material.BLACK_STAINED_GLASS), player,
                            "editor.entity_specific.text_background_alpha",
                            "%bg_alpha%", backGround == null ? "?" : String.valueOf(backGround.getAlpha())));

                    inv.setItem(2, setDesc(craftItem(Material.LIGHT_GRAY_STAINED_GLASS), player,
                            "editor.entity_specific.text_opacity",
                            "%text_alpha%", String.valueOf(textDisplay.getTextOpacity() == -1 ? 255 : (textDisplay.getTextOpacity() & 0xFF))));

                    inv.setItem(3, setDesc(craftItem(Material.GLOBE_BANNER_PATTERN), player,
                            "editor.entity_specific.text_alignment", "%value%",
                            textDisplay.getAlignment().name().toLowerCase(Locale.ENGLISH)));

                    inv.setItem(4, setDesc(craftItem(Material.TINTED_GLASS), player,
                            "editor.entity_specific.text_see_through", "%value%", String.valueOf(textDisplay.isSeeThrough())));

                    inv.setItem(5, setDesc(craftItem(Material.STRING), player,
                            "editor.entity_specific.text_line_width", "%value%", String.valueOf(textDisplay.getLineWidth())));

                    inv.setItem(6, null);
                } else if (display instanceof BlockDisplay) {
                    BlockData data = ((BlockDisplay) display).getBlock();
                    List<BlockDataInteractor> datas = BlockDataUtil.getBlockDataValues(data);
                    for (int i = 0; i < 7; i++) {
                        if (i >= datas.size())
                            inv.setItem(i, null);
                        else {
                            BlockDataInteractor value = datas.get(i);
                            inv.setItem(i,
                                    setDesc(craftItem(value.getMaterial(data), value.getAmount(data)), player,
                                            value.getLanguagePath(data), value.getHolders(data)));
                        }
                    }
                    if (datas.size() > 6)
                        DisplayEntities.get().log("BlockData require more than 6 slots, report this message to the developer &e" + ((BlockDisplay) display).getBlock().getAsString(false));
                } else if (display instanceof ItemDisplay itemDisplay) {
                    int current = 0;
                    ItemStack item = ((ItemDisplay) display).getItemStack();
                    if (item != null) {
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null)
                            current = meta.hasCustomModelData() ? meta.getCustomModelData() : 0;
                    }
                    inv.setItem(0, setDesc(craftItem(Material.GLOBE_BANNER_PATTERN), player,
                            "editor.entity_specific.item_view", "%value%",
                            itemDisplay.getItemDisplayTransform().name().toLowerCase(Locale.ENGLISH)));
                    inv.setItem(1, setDesc(craftItem(Material.ENCHANTED_BOOK), player,
                            "editor.entity_specific.item_glow"));
                    for (int i = 1; i <= 5; i++) {
                        DecimalFormat format = new DecimalFormat("###,###");
                        int val = (int) Math.pow(10, 2 * (i - 1));
                        inv.setItem(1 + i, setDesc(craftItem(Material.PAINTING, i * 2 - 1), player,
                                "editor.entity_specific.item_modeldata", "%value%", format.format(val),
                                "%shift-value%", format.format((int) val * 10), "%current%", format.format(current)));
                    }
                }
            }
            case COPY_PASTE -> {
                inv.setItem(0, setDesc(craftItem(Material.STRUCTURE_VOID), player, "editor.copy_paste.select"));
                if (Util.isVersionAfter(1, 20, 2)) {
                    CopyPasteOption option = SelectionManager.getCopyPasteOption(player);
                    inv.setItem(1, setDesc(craftItem(Material.BUCKET), player, "editor.copy_paste.copy"));
                    inv.setItem(2, setDesc(craftItem(Material.BUCKET, 2), player, "editor.copy_paste.copyrange",
                            "%radius%", String.valueOf(option.getCopyRadius())));
                    inv.setItem(3, setDesc(craftItem(Material.WATER_BUCKET, option.getCopiedEntitiesSize()), player, "editor.copy_paste.paste",
                            "%value%", String.valueOf(option.getCopiedEntitiesSize())));
                    inv.setItem(4, setDesc(craftItem(Material.LAVA_BUCKET, option.getAvailableUndo()), player, "editor.copy_paste.undo"));
                    inv.setItem(5, setDesc(craftItem(Material.CLOCK, option.getYRotation() / 10), player, "editor.copy_paste.paste_rotate",
                            "%value%", String.valueOf(option.getYRotation())));

                    inv.setItem(6, setDesc(craftItem(Material.POWDER_SNOW_BUCKET), player, "editor.copy_paste.copy_selected"));
                }
            }
        }

        fillEmptyHotbar(inv);
    }

    private void fillEmptyHotbar(Inventory inv) {
        for (int i = 0; i <= 8; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, craftFiller());
            }
        }
    }

    @Contract("null,_,_,_->null;!null,_,_,_->!null")
    private ItemStack setDesc(@Nullable ItemStack item, Player target, @NotNull String fullPath, String... holders) {
        if (item == null) {
            return null;
        } else {
            item.setItemMeta(this.setDesc(item.getItemMeta(), target, fullPath, holders));
            return item;
        }
    }

    @Contract("null,_,_,_->null;!null,_,_,_->!null")
    private ItemMeta setDesc(@Nullable ItemMeta meta, Player target, @NotNull String fullPath, String... holders) {
        if (meta == null) {
            return null;
        } else {
            List<String> list = DisplayEntities.get().getLanguageConfig(target).loadMultiMessage(fullPath, null, target, true, holders);
            meta.setDisplayName(list != null && !list.isEmpty() ? list.get(0) : " ");
            if (list != null && !list.isEmpty()) {
                meta.setLore(list.subList(1, list.size()));
            }
            return meta;
        }
    }

    private ItemStack craftItem(Material mat) {
        return craftItem(mat, 1);
    }

    private ItemStack craftItem(Material mat, int amount) {
        ItemStack item = new ItemStack(mat, Math.max(1, Math.min(127, amount)));
        ItemMeta meta = item.getItemMeta();
        assert meta != null;
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack craftFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }
}