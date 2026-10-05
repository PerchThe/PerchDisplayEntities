package ever.green.displayentities;

import ever.green.displayentities.command.DisplayEntitiesCommand;
import ever.green.displayentities.command.DisplayEntitiesInfoCommand;
import ever.green.displayentities.command.ReloadCommand;
import ever.green.displayentities.gui.Gui;
import ever.green.displayentities.gui.GuiHandler;
import ever.green.displayentities.selection.editorListener;
import ever.green.displayentities.selection.SelectionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public final class DisplayEntities extends APlugin {
    private final static Integer PROJECT_ID = 113254;
    private static final int BSTATS_PLUGIN_ID = 19646;
    private static DisplayEntities plugin = null;

    @NotNull
    public static DisplayEntities get() {
        return plugin;
    }

    public void onLoad() {
        plugin = this;
    }

    @Override
    @NotNull
    public Integer getProjectId() {
        return PROJECT_ID;
    }

    @Override
    public void enable() {

        ConfigurationUpdater.update();
        C.reload();
        Bukkit.getPluginManager().registerEvents(new GuiHandler(), this);
        Bukkit.getPluginManager().registerEvents(new editorListener(), this);

        registerCommand(new DisplayEntitiesCommand(), Collections.singletonList("de"));
        new DisplayEntitiesInfoCommand().register();
        new ReloadCommand(this).register();

        registerMetrics(BSTATS_PLUGIN_ID);
    }

    @Override
    public void reload() {
        C.reload();
        DisplayEntitiesCommand.get().reload();
    }

    @Override
    public void disable() {
        for (Player p : Bukkit.getOnlinePlayers())
            SelectionManager.seteditor(p, null);
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Gui)
                p.closeInventory();
    }
}
