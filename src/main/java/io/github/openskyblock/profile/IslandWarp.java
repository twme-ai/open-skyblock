package io.github.openskyblock.profile;

import org.bukkit.Location;
import org.bukkit.World;

public final class IslandWarp {
    private final String id;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public IslandWarp(String id, double x, double y, double z, float yaw, float pitch) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String id() {
        return id;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public Location location(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }
}
