package io.github.openskyblock.profile;

import org.bukkit.Location;

public final class PlacedMinion {
    private final String id;
    private long generatedAmount;
    private long lastActionMillis;
    private String worldName;
    private int x;
    private int y;
    private int z;

    public PlacedMinion(String id, long generatedAmount, long lastActionMillis) {
        this(id, generatedAmount, lastActionMillis, null, 0, 0, 0);
    }

    public PlacedMinion(String id, long generatedAmount, long lastActionMillis, String worldName, int x, int y, int z) {
        this.id = id;
        this.generatedAmount = Math.max(0L, generatedAmount);
        this.lastActionMillis = lastActionMillis;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String id() {
        return id;
    }

    public long generatedAmount() {
        return generatedAmount;
    }

    public void generatedAmount(long generatedAmount) {
        this.generatedAmount = Math.max(0L, generatedAmount);
    }

    public long lastActionMillis() {
        return lastActionMillis;
    }

    public void lastActionMillis(long lastActionMillis) {
        this.lastActionMillis = lastActionMillis;
    }

    public boolean hasLocation() {
        return worldName != null && !worldName.isBlank();
    }

    public boolean matches(Location location) {
        return hasLocation()
                && location.getWorld() != null
                && worldName.equals(location.getWorld().getName())
                && x == location.getBlockX()
                && y == location.getBlockY()
                && z == location.getBlockZ();
    }

    public String worldName() {
        return worldName;
    }

    public void worldName(String worldName) {
        this.worldName = worldName;
    }

    public int x() {
        return x;
    }

    public void x(int x) {
        this.x = x;
    }

    public int y() {
        return y;
    }

    public void y(int y) {
        this.y = y;
    }

    public int z() {
        return z;
    }

    public void z(int z) {
        this.z = z;
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
