package perch.displayentities;

import org.bukkit.NamespacedKey;

public class C {

    public static final NamespacedKey OWNER_KEY = new NamespacedKey(PerchDisplayEntities.get(), "owner");

    public static double MAX_EDIT_RADIUS = 64;
    public static double DEFAULT_SELECT_RADIUS = 16;
    public static int MAX_VIEW_RANGE = 128;
    public static double MAX_EDIT_RADIUS_SQUARED = MAX_EDIT_RADIUS * MAX_EDIT_RADIUS;
    public static double MOVE_COARSE = 0.5;
    public static double MOVE_FINE = 0.025;
    public static double SCALE_COARSE = 0.5;
    public static double SCALE_FINE = 0.025;
    public static double ROTATE_COARSE = 22.5;
    public static double ROTATE_FINE = 1.25;
    public static double MAX_SCALE = 10.0;
    public static boolean EXIT_ON_HIT = true;

    public static void reload() {
        MAX_EDIT_RADIUS = Math.max(8, PerchDisplayEntities.get().getConfig().loadDouble("editor.max_edit_distance", 64D));
        MAX_EDIT_RADIUS_SQUARED = MAX_EDIT_RADIUS * MAX_EDIT_RADIUS;
        DEFAULT_SELECT_RADIUS = Math.max(4, PerchDisplayEntities.get().getConfig().loadDouble("editor.default_selection_radius", 16D));
        MAX_VIEW_RANGE = Math.max(1, PerchDisplayEntities.get().getConfig().loadInteger("editor.max_view_range", 128));
        MOVE_COARSE = Math.max(0.001, PerchDisplayEntities.get().getConfig().loadDouble("editor.move.coarse", 0.5D));
        MOVE_FINE = Math.max(0.001, PerchDisplayEntities.get().getConfig().loadDouble("editor.move.fine", 0.025D));
        SCALE_COARSE = Math.max(0.001, PerchDisplayEntities.get().getConfig().loadDouble("editor.scale.coarse", 0.5D));
        SCALE_FINE = Math.max(0.001, PerchDisplayEntities.get().getConfig().loadDouble("editor.scale.fine", 0.025D));
        ROTATE_COARSE = Math.max(0.001, PerchDisplayEntities.get().getConfig().loadDouble("editor.rotation.coarse_degrees", 22.5D));
        ROTATE_FINE = Math.max(0.001, PerchDisplayEntities.get().getConfig().loadDouble("editor.rotation.fine_degrees", 1.25D));
        EXIT_ON_HIT = PerchDisplayEntities.get().getConfig().loadBoolean("editor.exit_editor_when_hit", true);
    }
}
