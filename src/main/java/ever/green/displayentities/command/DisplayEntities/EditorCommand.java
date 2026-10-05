package ever.green.displayentities.command.DisplayEntities;

import ever.green.displayentities.Util;
import ever.green.displayentities.command.AbstractCommand;
import ever.green.displayentities.command.SubCmd;
import ever.green.displayentities.selection.SelectionManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ever.green.displayentities.selection.Editor;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class EditorCommand extends SubCmd {
    public EditorCommand(@NotNull AbstractCommand cmd) {
        super("Editor", cmd, true, false);
    }

    @Override
    public void onCommand(CommandSender sender, String alias, String[] args) {
        Editor target;
        if (args.length == 1) {
            target = SelectionManager.isOneditor((Player) sender) ? null : Editor.POSITION;
        } else {
            try {
                target = Editor.valueOf(args[1].toUpperCase(Locale.ENGLISH));
            } catch (Exception e) {
                sendLanguageString("wrong-type", null, sender);
                return;
            }
        }
        SelectionManager.seteditor((Player) sender, target);
        if (target == null)
            sendLanguageString("success-disabled", null, sender);
        else
            sendLanguageString("success-enabled", null, sender);
    }

    @Override
    public List<String> onComplete(CommandSender sender, String[] args) {
        return (args.length == 2) ? Util.complete(args[1], Editor.class) : Collections.emptyList();
    }
}
