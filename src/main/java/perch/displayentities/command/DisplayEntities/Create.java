package perch.displayentities.command.DisplayEntities;

import perch.displayentities.C;
import perch.displayentities.Util;
import perch.displayentities.UtilsString;
import perch.displayentities.command.AbstractCommand;
import perch.displayentities.command.SubCmd;
import perch.displayentities.selection.Editor;
import perch.displayentities.selection.SelectionManager;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class Create extends SubCmd {
    public Create(@NotNull AbstractCommand cmd) {
        super("create", cmd, true, false);
    }

    @Override
    public void onCommand(CommandSender sender, String alias, String[] args) {
        if (args.length < 2) {
            onFail(sender, alias);
            return;
        }
        switch (args[1].toLowerCase(Locale.ENGLISH)) {
            case "item" -> {
                item((Player) sender, alias, args);
                return;
            }
            case "block" -> {
                block((Player) sender, alias, args);
                return;
            }
            case "text" -> {
                text((Player) sender, alias, args);
                return;
            }
        }
        onFail(sender, alias);
    }

    // create block [type=STONE/HAND]
    private void block(Player player, String alias, String[] args) {
        Material type = Material.STONE;
        if (args.length > 2) {
            try {
                type = Material.valueOf(args[2].toUpperCase(Locale.ENGLISH));
            } catch (Exception e) {
                sendLanguageString("wrong-block-type", null, player);
                return;
            }
        } else if (getItemInHand(player) != null
                && !getItemInHand(player).getType().isAir()
                && getItemInHand(player).getType().isBlock()) {
            type = getItemInHand(player).getType();
        }
        if (!type.isBlock()) {
            sendLanguageString("wrong-block-type", null, player);
            return;
        }

        Location loc = player.getLocation().clone();
        loc.setYaw(0);
        loc.setPitch(0);

        // --- GriefPrevention check ---
        if (!canEditHere(player, loc)) {
            player.sendMessage("§cYou need to be trusted to use this command here.");
            return;
        }

        // Workaround for vanilla rendering quirk: use ItemDisplay for hanging signs
        if (isHangingSign(type)) {
            ItemDisplay display = (ItemDisplay) player.getWorld().spawnEntity(loc, EntityType.ITEM_DISPLAY);
            display.setItemStack(new ItemStack(type)); // hanging sign has an item form
            applyItemDisplayDefaults(display);
            setOwner(display, player);
            SelectionManager.select(player, display);
        } else {
            BlockDisplay display = (BlockDisplay) player.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY);
            display.setBlock(Bukkit.createBlockData(type));
            applyBlockDisplayDefaults(display);
            setOwner(display, player);
            SelectionManager.select(player, display);
        }

        if (!SelectionManager.isOneditor(player))
            SelectionManager.seteditor(player, Editor.POSITION);
        else
            SelectionManager.geteditor(player).setup(player);
        sendLanguageString("success-block", null, player);
    }

    // create item [item=HAND/STONE]
    private void item(Player player, String alias, String[] args) {
        ItemStack type = new ItemStack(Material.STONE);
        if (args.length > 2) {
            try {
                Material mat = Material.valueOf(args[2].toUpperCase(Locale.ENGLISH));
                if (!mat.isItem()) {
                    sendLanguageString("wrong-item-type", null, player);
                    return;
                }
                type = new ItemStack(mat);
            } catch (Exception e) {
                sendLanguageString("wrong-item-type", null, player);
                return;
            }
        } else if (getItemInHand(player) != null && !getItemInHand(player).getType().isAir()) {
            type = new ItemStack(getItemInHand(player));
        }

        Location loc = player.getLocation().clone();
        loc.setYaw(0);
        loc.setPitch(0);

        // --- GriefPrevention check ---
        if (!canEditHere(player, loc)) {
            player.sendMessage("§cYou need to be trusted to use this command here.");
            return;
        }

        ItemDisplay item = (ItemDisplay) player.getWorld().spawnEntity(loc, EntityType.ITEM_DISPLAY);
        item.setItemStack(type);
        applyItemDisplayDefaults(item);

        setOwner(item, player);
        SelectionManager.select(player, item);
        if (!SelectionManager.isOneditor(player))
            SelectionManager.seteditor(player, Editor.POSITION);
        else
            SelectionManager.geteditor(player).setup(player);
        sendLanguageString("success-item", null, player);
    }

    // create text [text=Hologram]
    private void text(Player player, String alias, String[] args) {
        String text = "Hologram";
        if (args.length > 2) {
            text = String.join(" ", Arrays.asList(args).subList(2, args.length));
        }
        // TODO fix text
        // TODO apply censure or bypass it
        text = UtilsString.fix(text, null, true);

        Location loc = player.getLocation().clone();
        loc.setYaw(0);
        loc.setPitch(0);

        // --- GriefPrevention check ---
        if (!canEditHere(player, loc)) {
            player.sendMessage("§cYou need to be trusted to use this command here.");
            return;
        }

        TextDisplay textDisplay = (TextDisplay) player.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY, false);
        textDisplay.setBillboard(Display.Billboard.CENTER);
        textDisplay.setSeeThrough(false);   // supported on TextDisplay
        textDisplay.setGlowing(false);
        textDisplay.setBrightness(null);

        textDisplay.setText(text);
        setOwner(textDisplay, player);
        SelectionManager.select(player, textDisplay);
        if (!SelectionManager.isOneditor(player))
            SelectionManager.seteditor(player, Editor.POSITION);
        else
            SelectionManager.geteditor(player).setup(player);
        sendLanguageString("success-text", null, player);
    }

    @Override
    public List<String> onComplete(CommandSender sender, String[] args) {
        return switch (args.length) {
            case 2 -> Util.complete(args[1], "text", "item", "block");
            case 3 -> switch (args[1].toLowerCase(Locale.ENGLISH)) {
                case "item" -> Util.complete(args[2], Material.class, Material::isItem);
                case "block" -> Util.complete(args[2], Material.class, Material::isBlock);
                default -> Collections.emptyList();
            };
            default -> Collections.emptyList();
        };
    }

    private void setOwner(Display display, Player player) {
        display.getPersistentDataContainer().set(C.OWNER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
    }

    // --- GriefPrevention check utility ---
    private boolean canEditHere(Player player, Location location) {
        if (Bukkit.getPluginManager().getPlugin("GriefPrevention") == null) return true;
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
        if (claim == null) return true;
        return claim.allowBuild(player, location.getBlock().getType()) == null;
    }

    // ---------- helpers ----------
    private static boolean isHangingSign(Material mat) {
        String n = mat.name();
        return n.endsWith("_HANGING_SIGN") || n.endsWith("_WALL_HANGING_SIGN");
    }

    // ---------- Display defaults ----------
    private void applyBlockDisplayDefaults(BlockDisplay d) {
        d.setBillboard(Display.Billboard.FIXED);
        d.setGlowing(false);
        d.setBrightness(null);
        d.setShadowRadius(0f);
        d.setShadowStrength(0f);
        d.setInterpolationDuration(0);
        // No setCullingBox in your API; leave default culling
    }

    private void applyItemDisplayDefaults(ItemDisplay d) {
        d.setBillboard(Display.Billboard.FIXED);
        d.setGlowing(false);
        d.setBrightness(null);
        d.setShadowRadius(0f);
        d.setShadowStrength(0f);
        d.setInterpolationDuration(0);
    }
}
