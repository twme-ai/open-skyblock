package io.github.openskyblock.island;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class IslandService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final VoidChunkGenerator generator = new VoidChunkGenerator();

    public IslandService(ConfigService configService, TextService text, ProfileManager profiles) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.private-islands", true);
    }

    public void createOrTeleport(Player player) {
        if (!enabled()) {
            text.send(player, "commands.island-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        boolean existed = profile.islandWorldName() != null && !profile.islandWorldName().isBlank();
        World world = worldFor(profile, player.getUniqueId());
        if (ensureStarterIsland(world) && !existed) {
            text.send(player, "commands.island-created");
        }
        teleportHome(player, world);
    }

    public void teleportHome(Player player) {
        if (!enabled()) {
            text.send(player, "commands.island-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        boolean existed = profile.islandWorldName() != null && !profile.islandWorldName().isBlank();
        World world = worldFor(profile, player.getUniqueId());
        if (ensureStarterIsland(world) && !existed) {
            text.send(player, "commands.island-created");
        }
        teleportHome(player, world);
    }

    public void sendInfo(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.islandWorldName() == null || profile.islandWorldName().isBlank()) {
            createOrTeleport(player);
            return;
        }
        text.send(player, "commands.island-info", java.util.List.of(TextService.raw("world", profile.islandWorldName())));
    }

    public boolean isIslandWorld(World world) {
        return world != null && world.getName().startsWith(worldPrefix());
    }

    public boolean canModify(Player player, World world) {
        if (!isIslandWorld(world) || player.hasPermission("openskyblock.admin")) {
            return true;
        }
        SkyBlockProfile profile = profiles.profile(player);
        return world.getName().equals(profile.islandWorldName());
    }

    private World worldFor(SkyBlockProfile profile, UUID owner) {
        String worldName = profile.islandWorldName();
        if (worldName == null || worldName.isBlank()) {
            worldName = worldPrefix() + owner.toString().replace("-", "");
            profile.islandWorldName(worldName);
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return world;
        }
        WorldCreator creator = new WorldCreator(worldName)
                .generator(generator)
                .environment(World.Environment.NORMAL)
                .generateStructures(false);
        World created = Bukkit.createWorld(creator);
        if (created == null) {
            throw new IllegalStateException("Unable to create island world: " + worldName);
        }
        created.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        created.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        created.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        created.setSpawnLocation(homeLocation(created));
        return created;
    }

    private void teleportHome(Player player, World world) {
        player.teleport(homeLocation(world));
        text.send(player, "commands.island-home");
    }

    private Location homeLocation(World world) {
        double y = configService.main().getDouble("islands.spawn-y", 80.0D);
        float yaw = (float) configService.main().getDouble("islands.home-yaw", 180.0D);
        float pitch = (float) configService.main().getDouble("islands.home-pitch", 0.0D);
        return new Location(world, 0.5D, y + 2.0D, 0.5D, yaw, pitch);
    }

    private boolean ensureStarterIsland(World world) {
        int y = configService.main().getInt("islands.spawn-y", 80);
        if (world.getBlockAt(0, y - 1, 0).getType() == Material.GRASS_BLOCK) {
            return false;
        }
        buildStarterIsland(world, y);
        return true;
    }

    private void buildStarterIsland(World world, int y) {
        int radius = Math.max(2, configService.main().getInt("islands.starter-radius", 3));
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Block base = world.getBlockAt(x, y - 2, z);
                base.setType(Material.DIRT, false);
                Block top = world.getBlockAt(x, y - 1, z);
                top.setType(Material.GRASS_BLOCK, false);
            }
        }
        world.getBlockAt(0, y, 0).setType(Material.AIR, false);
        world.getBlockAt(1, y, 0).setType(Material.CHEST, false);
        if (world.getBlockAt(1, y, 0).getState() instanceof Chest chest) {
            chest.getInventory().addItem(
                    new ItemStack(Material.LAVA_BUCKET),
                    new ItemStack(Material.ICE),
                    new ItemStack(Material.OAK_SAPLING),
                    new ItemStack(Material.BONE_MEAL, 8),
                    new ItemStack(Material.WHEAT_SEEDS, 8)
            );
            chest.update();
        }
        world.getBlockAt(-2, y, -2).setType(Material.OAK_SAPLING, false);
    }

    private String worldPrefix() {
        return configService.main().getString("islands.world-prefix", "openskyblock_island_");
    }
}
