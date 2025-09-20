package perch.displayentities.selection;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CopyPasteOption {

    private final List<List<Entity>> lastPasted = new ArrayList<>();
    private static final int MAX_HISTORY = 50;
    private static final int MAX_COPY = 200;
    private final List<EntitySnapshot> copiedEntities = new ArrayList<>();
    private final List<Location> copiedEntitiesOffsets = new ArrayList<>();
    private int yRotation = 0;
    private int copyRadius = 5;
    private Location copyPos;

    public int getYRotation() {
        return yRotation;
    }

    public void setYRotation(int yRotation) {
        this.yRotation = yRotation % 360;
        if (this.yRotation < 0)
            this.yRotation += 360;
    }

    public void addYRotation(int yRotation) {
        setYRotation(this.yRotation + yRotation);
    }

    public int getCopyRadius() {
        return copyRadius;
    }

    public boolean copy(@NotNull Collection<Entity> entities, @NotNull Location from) {
        return copy(null, entities, from);
    }

    public boolean copy(@Nullable Player player, @NotNull Collection<Entity> entities, @NotNull Location from) {
        if (entities.isEmpty() || entities.size() > MAX_COPY)
            return false;
        copiedEntities.clear();
        copiedEntitiesOffsets.clear();
        entities.stream()
                .filter(en -> en instanceof Display)
                .filter(en -> player == null || canEditHere(player, en.getLocation()))
                .forEach(en -> {
                    this.copiedEntities.add(en.createSnapshot());
                    Location loc = en.getLocation();
                    loc.setWorld(null);
                    this.copiedEntitiesOffsets.add(loc);
                });
        if (copiedEntities.isEmpty())
            return false;
        from = from.clone();
        from.setWorld(null);
        copyPos = from;
        setYRotation(0);
        return true;
    }

    public boolean paste(@NotNull Location loc, boolean round, double rotationDegreesY) {
        return paste(null, loc, round, rotationDegreesY);
    }

    public boolean paste(@Nullable Player player, @NotNull Location loc, boolean round, double rotationDegreesY) {
        if (copiedEntities.isEmpty())
            return false;
        World w = loc.getWorld();
        if (w == null)
            return false;
        Location from = copyPos.clone();
        if (round) {
            from.setX(from.getBlockX());
            from.setY(from.getBlockY());
            from.setZ(from.getBlockZ());
            loc.setX(loc.getBlockX());
            loc.setY(loc.getBlockY());
            loc.setZ(loc.getBlockZ());
        }
        loc.setWorld(null);
        List<Location> locations = new ArrayList<>(copiedEntitiesOffsets.size());
        for (Location offset : copiedEntitiesOffsets) {
            Location to = offset.clone().subtract(from);
            if (rotationDegreesY != 0) {
                Vector toVector = to.toVector().rotateAroundY(Math.PI * -rotationDegreesY / 180D);
                to = new Location(null, toVector.getX(), toVector.getY(), toVector.getZ(),
                        (float) (to.getYaw() + rotationDegreesY), to.getPitch());
            }
            to.add(loc);
            to.setWorld(w);
            locations.add(to);
        }
        List<Integer> allowedIndexes = new ArrayList<>(locations.size());
        if (player == null) {
            for (int i = 0; i < locations.size(); i++) allowedIndexes.add(i);
        } else {
            for (int i = 0; i < locations.size(); i++) {
                if (canEditHere(player, locations.get(i))) {
                    allowedIndexes.add(i);
                }
            }
            if (allowedIndexes.isEmpty())
                return false;
        }
        List<Entity> results = new ArrayList<>(allowedIndexes.size());
        for (int idx : allowedIndexes) {
            Entity en = copiedEntities.get(idx).createEntity(locations.get(idx));
            en.teleport(locations.get(idx));
            results.add(en);
        }
        if (results.isEmpty())
            return false;
        lastPasted.add(results);
        if (lastPasted.size() > MAX_HISTORY)
            lastPasted.remove(0);
        return true;
    }

    public boolean paste(@NotNull Location location, boolean roundLocation) {
        return paste(null, location, roundLocation, yRotation);
    }

    public boolean paste(@Nullable Player player, @NotNull Location location, boolean roundLocation) {
        return paste(player, location, roundLocation, yRotation);
    }

    public boolean undoPaste() {
        if (lastPasted.isEmpty())
            return false;
        lastPasted.remove(lastPasted.size() - 1).forEach(Entity::remove);
        return true;
    }

    public boolean hasCopiedEntities() {
        return !copiedEntities.isEmpty();
    }

    public int getCopiedEntitiesSize() {
        return copiedEntities.size();
    }

    public boolean hasAvailableUndo() {
        return !lastPasted.isEmpty();
    }

    public int getAvailableUndo() {
        return lastPasted.size();
    }

    public List<Entity> getLastPasted() {
        return hasAvailableUndo() ? lastPasted.get(lastPasted.size() - 1) : Collections.emptyList();
    }

    private boolean canEditHere(@NotNull Player player, @NotNull Location location) {
        if (Bukkit.getPluginManager().getPlugin("GriefPrevention") == null) return true;
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
        if (claim == null) return true;
        return claim.allowBuild(player, location.getBlock().getType()) == null;
    }
}
