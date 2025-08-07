package perch.displayentities.command.DisplayEntities;

import perch.displayentities.command.AbstractCommand;
import perch.displayentities.command.SubCmd;
import perch.displayentities.selection.SelectionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class Deselect extends SubCmd {
    public Deselect(@NotNull AbstractCommand cmd) {
        super("deselect", cmd, true, false);
    }

    @Override
    public void onCommand(CommandSender sender, String alias, String[] args) {
        Player p = (Player) sender;
        if (SelectionManager.deselect(p)) {
            sendLanguageString("success", null, sender);
            return;
        }
        sendLanguageString("none-selected", null, sender);
    }

    @Override
    public List<String> onComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
