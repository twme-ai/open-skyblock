package io.github.openskyblock.island;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import java.util.List;
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
        text.send(player, "commands.island-info", islandPlaceholders(player));
    }

    public void visit(Player visitor, String ownerName) {
        if (!enabled()) {
            text.send(visitor, "commands.island-disabled");
            return;
        }
        SkyBlockProfile owner = profiles.profileByName(ownerName);
        if (owner == null) {
            text.send(visitor, "commands.island-unknown-player", List.of(TextService.raw("player", ownerName == null ? "" : ownerName)));
            return;
        }
        if (owner.uniqueId().equals(visitor.getUniqueId())) {
            teleportHome(visitor);
            return;
        }
        if (owner.islandWorldName() == null || owner.islandWorldName().isBlank()) {
            text.send(visitor, "commands.island-no-island", List.of(TextService.raw("player", owner.playerName())));
            return;
        }
        if (!owner.islandVisitorsEnabled() && !visitor.hasPermission("openskyblock.admin")) {
            text.send(visitor, "commands.island-visitors-closed", List.of(TextService.raw("player", owner.playerName())));
            return;
        }
        boolean alreadyVisiting = visitor.getWorld().getName().equals(owner.islandWorldName());
        if (!alreadyVisiting && !visitor.hasPermission("openskyblock.admin") && visitorCount(owner) >= visitorLimit(owner)) {
            text.send(visitor, "commands.island-visitors-full", islandPlaceholders(owner));
            return;
        }
        World world = worldFor(owner, owner.uniqueId());
        ensureStarterIsland(world);
        visitor.teleport(homeLocation(world));
        text.send(visitor, "commands.island-visited", List.of(TextService.raw("player", owner.playerName())));
        Player ownerPlayer = Bukkit.getPlayer(owner.uniqueId());
        if (ownerPlayer != null) {
            text.send(ownerPlayer, "commands.island-visitor-arrived", List.of(TextService.raw("player", visitor.getName())));
        }
    }

    public void toggleVisitors(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        setVisitors(player, !profile.islandVisitorsEnabled());
    }

    public void setVisitors(Player player, boolean visitorsEnabled) {
        if (!enabled()) {
            text.send(player, "commands.island-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        profile.islandVisitorsEnabled(visitorsEnabled);
        profiles.save(player);
        text.send(player, "commands.island-visitors-updated", islandPlaceholders(player));
    }

    public List<TextService.TextPlaceholder> islandPlaceholders(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        return islandPlaceholders(profile);
    }

    public List<TextService.TextPlaceholder> islandPlaceholders(SkyBlockProfile profile) {
        return List.of(
                TextService.raw("owner", profile.playerName()),
                TextService.raw("world", profile.islandWorldName() == null || profile.islandWorldName().isBlank() ? text.rawMessage("islands.no-world") : profile.islandWorldName()),
                TextService.parsed("visitors_status", visitorStatus(profile)),
                TextService.raw("visitors", text.formatNumber(visitorCount(profile))),
                TextService.raw("visitor_limit", text.formatNumber(visitorLimit(profile))),
                TextService.raw("minions", text.formatNumber(profile.minions().size()))
        );
    }

    public String visitorStatus(SkyBlockProfile profile) {
        return text.rawMessage(profile.islandVisitorsEnabled() ? "islands.visitors-open" : "islands.visitors-closed");
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

    private int visitorCount(SkyBlockProfile profile) {
        if (profile.islandWorldName() == null || profile.islandWorldName().isBlank()) {
            return 0;
        }
        World world = Bukkit.getWorld(profile.islandWorldName());
        if (world == null) {
            return 0;
        }
        int count = 0;
        for (Player player : world.getPlayers()) {
            if (!player.getUniqueId().equals(profile.uniqueId())) {
                count++;
            }
        }
        return count;
    }

    private int visitorLimit(SkyBlockProfile profile) {
        return Math.max(1, configService.main().getInt("islands.max-visitors", 1));
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
