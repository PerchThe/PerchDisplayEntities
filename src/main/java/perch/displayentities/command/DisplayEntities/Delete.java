package perch.displayentities.command.DisplayEntities;

import perch.displayentities.command.AbstractCommand;
import perch.displayentities.command.SubCmd;
import perch.displayentities.selection.Editor;
import perch.displayentities.selection.SelectionManager;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Delete extends SubCmd {
    public Delete(@NotNull AbstractCommand cmd) {
        super("delete", cmd, true, false);
    }

    @Override
    public void onCommand(CommandSender sender, String alias, String[] args) {
        Player player = (Player) sender;
        Collection<Display> selections = SelectionManager.getSelections(player);
        if (selections == null || selections.isEmpty()) {
            Display single = SelectionManager.getSelection(player);
            if (single == null) {
                sendLanguageString("none-selected", null, player);
                return;
            }
            if (!canEditHere(player, single.getLocation())) {
                player.sendMessage("§cYou need to be trusted to use this command here.");
                return;
            }
            single.remove();
        } else {
            int removed = 0;
            for (Display sel : selections) {
                if (canEditHere(player, sel.getLocation())) {
                    sel.remove();
                    removed++;
                }
            }
            if (removed == 0) {
                player.sendMessage("§cYou need to be trusted to use this command here.");
                return;
            }
        }
        SelectionManager.deselectAll(player);
        Editor mode = SelectionManager.geteditor(player);
        if (mode != null) mode.setup(player);
        sendLanguageString("success", null, player);
    }

    @Override
    public List<String> onComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    private boolean canEditHere(Player player, Location location) {
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("GriefPrevention") == null) return true;
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
        if (claim == null) return true;
        return claim.allowBuild(player, location.getBlock().getType()) == null;
    }
}
