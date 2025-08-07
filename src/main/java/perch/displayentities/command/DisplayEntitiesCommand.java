package perch.displayentities.command;

import perch.displayentities.PerchDisplayEntities;
import perch.displayentities.command.DisplayEntities.*;

public class DisplayEntitiesCommand extends AbstractCommand {
    public static DisplayEntitiesCommand instance;

    public DisplayEntitiesCommand() {
        super("DisplayEntities", PerchDisplayEntities.get());
        instance = this;

        this.registerSubCommand(new Create(this));
        this.registerSubCommand(new Select(this));
        this.registerSubCommand(new Deselect(this));
        this.registerSubCommand(new EditorCommand(this));
        this.registerSubCommand(new Delete(this));
        this.registerSubCommand(new Settext(this));
        this.registerSubCommand(new Setitem(this));
        this.registerSubCommand(new Setblock(this));
    }

    public static DisplayEntitiesCommand get() {
        return instance;
    }

}
