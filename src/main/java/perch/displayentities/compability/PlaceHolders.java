package perch.displayentities.compability;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import perch.displayentities.PerchDisplayEntities;
import perch.displayentities.selection.SelectionManager;

/**
 * This class will automatically register as a placeholder expansion when a jar
 * including this class is added to the directory
 * {@code /plugins/PlaceholderAPI/expansions} on your server. <br>
 * <br>
 * If you create such a class inside your own plugin, you have to register it
 * manually in your plugin's {@code onEnable()} by using
 * {@code new YourExpansionClass().register();}
 */
public class PlaceHolders extends PlaceholderExpansion {
    public PlaceHolders() {
        PerchDisplayEntities.get().log("Hooked into PlaceHolderAPI:");
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "emanon";
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "itemedit";
    }

    @Override
    public String getRequiredPlugin() {
        return PerchDisplayEntities.get().getName();
    }

    @Override
    @NotNull
    public String getVersion() {
        return "1.0";
    }

    /**
     * This is the method called when a placeholder with our identifier is found and
     * needs a value. <br>
     * We specify the value identifier in this method. <br>
     * Since version 2.9.1 you can use OfflinePlayers in your requests.
     *
     * @param player A {@link Player Player}.
     * @param value  A String containing the identifier/value.
     * @return possibly-null String of the requested identifier.
     */
    @Override
    public String onPlaceholderRequest(Player player, @NotNull String value) {
        if (player == null) return "";

        if (value.equalsIgnoreCase("in_editor")) {
            boolean inEditor = SelectionManager.isOneditor(player);
            return inEditor ? "yes" : "no";
        }

        // Add more placeholders here if needed

        return null;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }
}