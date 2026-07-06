package io.github.openskyblock.profile;

import org.bukkit.Location;

public final class PlacedCake {
    private final String id;
    private String worldName;
    private int x;
    private int y;
    private int z;

    public PlacedCake(String id, String worldName, int x, int y, int z) {
        this.id = id;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String id() {
        return id;
    }

    public boolean matches(Location location) {
        return worldName != null
                && !worldName.isBlank()
                && location.getWorld() != null
                && worldName.equals(location.getWorld().getName())
                && x == location.getBlockX()
                && y == location.getBlockY()
                && z == location.getBlockZ();
    }

    public String worldName() {
        return worldName;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public void location(Location location) {
        if (location.getWorld() == null) {
            return;
        }
        this.worldName = location.getWorld().getName();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
    }
}
