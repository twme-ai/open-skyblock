package io.github.openskyblock.profile;

import org.bukkit.Location;
import org.bukkit.World;

public record IslandTeleportPad(
        String id,
        String group,
        int x,
        int y,
        int z,
        float yaw,
        float pitch
) {
    public Location location(World world) {
        return new Location(world, x + 0.5D, y + 1.0D, z + 0.5D, yaw, pitch);
    }

    public boolean matches(Location location) {
        return location != null
                && x == location.getBlockX()
                && y == location.getBlockY()
                && z == location.getBlockZ();
    }
}
