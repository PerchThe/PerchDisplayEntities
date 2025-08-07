package perch.displayentities.command.DisplayEntities;

import perch.displayentities.command.AbstractCommand;
import perch.displayentities.command.SubCmd;
import perch.displayentities.selection.Editor;
import perch.displayentities.selection.SelectionManager;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class Delete extends SubCmd {
    public Delete(@NotNull AbstractCommand cmd) {
        super("delete", cmd, true, false);
    }

    @Override
    public void onCommand(CommandSender sender, String alias, String[] args) {

        Player player = (Player) sender;
        //TODO check permissions
        Display sel = SelectionManager.getSelection(player);
        if (sel != null) {
            // --- GriefPrevention check ---
            if (!canEditHere(player, sel.getLocation())) {
                player.sendMessage("§cYou need to be trusted to use this command here.");
                return;
            }
            sel.remove();
            SelectionManager.deselect(player);
            Editor mode = SelectionManager.geteditor(player);
            if (mode != null)
                mode.setup(player);
            sendLanguageString("success", null, player);
        } else
            sendLanguageString("none-selected", null, player);
    }

    @Override
    public List<String> onComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    // --- GriefPrevention check utility ---
    private boolean canEditHere(Player player, Location location) {
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("GriefPrevention") == null) return true;
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
        if (claim == null) return true;
        return claim.allowBuild(player, location.getBlock().getType()) == null;
    }
}