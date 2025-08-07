package perch.displayentities.command.DisplayEntities;

import perch.displayentities.C;
import perch.displayentities.Util;
import perch.displayentities.command.AbstractCommand;
import perch.displayentities.command.SubCmd;
import perch.displayentities.selection.Editor;
import perch.displayentities.selection.SelectionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Select extends SubCmd {
    public Select(@NotNull AbstractCommand cmd) {
        super("select", cmd, true, false);
    }

    @Override
    public void onCommand(CommandSender sender, String alias, String[] args) {
        double radius = C.DEFAULT_SELECT_RADIUS;
        Player player = ((Player) sender);

        if (args.length > 1) {
            String arg = args[1];
            // Try radius
            try {
                radius = Double.parseDouble(arg);
                if (radius <= 0) {
                    sendLanguageString("invalid-radius", null, sender);
                    return;
                }
                if (radius > 10) {
                    radius = 10;
                    sendLanguageString("radius-clamped", "10", sender); // Fixed: pass String, not Map
                }
                // MULTI-SELECTION: Do NOT clear previous selections
                selectAllDisplays(player, radius, sender);
                return;
            } catch (NumberFormatException ignored) {
                // Not a number, try UUID
            }
            // SINGLE-SELECTION: Clear previous selection
            SelectionManager.deselectAll(player);
            try {
                UUID uuid = UUID.fromString(arg);
                List<Entity> entities = player.getNearbyEntities(48, 800, 48);
                Display target = null;
                for (Entity entity : entities) {
                    if (entity instanceof Display disp && entity.getUniqueId().equals(uuid)) {
                        target = disp;
                        break;
                    }
                }
                if (target == null) {
                    sendLanguageString("entity-uuid-not-found", null, sender);
                    return;
                }
                SelectionManager.select(player, target);
                Editor mode = SelectionManager.geteditor(player);
                if (mode == null)
                    SelectionManager.seteditor(player, Editor.POSITION);
                else
                    mode.setup(player);
                sendLanguageString("success", null, sender);
                return;
            } catch (IllegalArgumentException e2) {
                sendLanguageString("invalid-radius-or-uuid", null, sender);
                return;
            }
        }

        // SINGLE-SELECTION: Clear previous selection
        SelectionManager.deselectAll(player);
        selectClosestDisplay(player, radius, sender);
    }

    // Helper to select all Displays within a radius (multi-select)
    private void selectAllDisplays(Player player, double radius, CommandSender sender) {
        List<Display> found = new ArrayList<>();
        for (Entity en : player.getNearbyEntities(radius, radius, radius)) {
            if (en instanceof Display) {
                found.add((Display) en);
            }
        }
        if (found.isEmpty()) {
            sendLanguageString("none-found", null, sender);
            return;
        }
        for (Display disp : found) {
            SelectionManager.select(player, disp);
        }
        Editor mode = SelectionManager.geteditor(player);
        if (mode == null)
            SelectionManager.seteditor(player, Editor.POSITION);
        else
            mode.setup(player);
        sendLanguageString("selected-multiple", String.valueOf(found.size()), sender); // Fixed: pass String, not Map
    }

    // Helper to select the closest Display within a radius (single-select)
    private void selectClosestDisplay(Player player, double radius, CommandSender sender) {
        Display target = null;
        double minDistSq = Double.MAX_VALUE;
        for (Entity en : player.getNearbyEntities(Math.min(200, radius), Math.min(200, radius), Math.min(200, radius))) {
            if (en instanceof Display) {
                double distSq = en.getLocation().distanceSquared(player.getLocation());
                if (target == null || distSq < minDistSq) {
                    target = (Display) en;
                    minDistSq = distSq;
                }
            }
        }
        if (target == null) {
            sendLanguageString("none-found", null, sender);
            return;
        }
        SelectionManager.select(player, target);
        Editor mode = SelectionManager.geteditor(player);
        if (mode == null)
            SelectionManager.seteditor(player, Editor.POSITION);
        else
            mode.setup(player);
        sendLanguageString("success", null, sender);
    }

    @Override
    public List<String> onComplete(CommandSender sender, String[] args) {
        if (args.length == 2 && sender instanceof Player p) {
            Collection<Entity> entities = p.getWorld().getNearbyEntities(p.getLocation(), 5, 8, 5, (entity -> entity instanceof Display));
            List<String> completions = Util.complete(args[1], entities, entity -> entity.getUniqueId().toString(), entity -> true);
            completions.addAll(List.of("5", "10"));
            return completions;
        }
        return Collections.emptyList();
    }
}