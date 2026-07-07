package io.github.openskyblock.island;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.IslandWarp;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
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
    private final Map<UUID, CoopInvite> coopInvites = new HashMap<>();
    private final Map<UUID, Long> resetConfirmations = new HashMap<>();

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
        visit(visitor, ownerName, "");
    }

    public void visit(Player visitor, String ownerName, String warpName) {
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
        IslandWarp warp = null;
        if (warpName != null && !warpName.isBlank()) {
            String id = normalizeWarpName(warpName);
            if (id.isBlank()) {
                text.send(visitor, "commands.island-warp-name-invalid", warpPlaceholders(owner, warpName));
                return;
            }
            warp = owner.islandWarp(id);
            if (warp == null) {
                text.send(visitor, "commands.island-warp-unknown", warpPlaceholders(owner, warpName));
                return;
            }
        }
        boolean coopMember = owner.isIslandCoopMember(visitor.getUniqueId());
        if (!coopMember && !owner.islandVisitorsEnabled() && !visitor.hasPermission("openskyblock.admin")) {
            text.send(visitor, "commands.island-visitors-closed", List.of(TextService.raw("player", owner.playerName())));
            return;
        }
        boolean alreadyVisiting = visitor.getWorld().getName().equals(owner.islandWorldName());
        if (!coopMember && !alreadyVisiting && !visitor.hasPermission("openskyblock.admin") && visitorCount(owner) >= visitorLimit(owner)) {
            text.send(visitor, "commands.island-visitors-full", islandPlaceholders(owner));
            return;
        }
        World world = worldFor(owner, owner.uniqueId());
        ensureStarterIsland(world);
        visitor.teleport(warp == null ? homeLocation(owner, world) : warp.location(world));
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

    public void inviteCoop(Player ownerPlayer, Player target) {
        if (!enabled()) {
            text.send(ownerPlayer, "commands.island-disabled");
            return;
        }
        expireCoopInvites();
        SkyBlockProfile owner = profiles.profile(ownerPlayer);
        if (target.getUniqueId().equals(owner.uniqueId())) {
            text.send(ownerPlayer, "commands.island-coop-self");
            return;
        }
        if (owner.isIslandCoopMember(target.getUniqueId())) {
            text.send(ownerPlayer, "commands.island-coop-already-member", List.of(TextService.raw("player", target.getName())));
            return;
        }
        if (owner.islandCoopMembers().size() >= maxCoopMembers()) {
            text.send(ownerPlayer, "commands.island-coop-full", islandPlaceholders(owner));
            return;
        }
        long expiresAt = System.currentTimeMillis() + inviteExpireSeconds() * 1000L;
        coopInvites.put(target.getUniqueId(), new CoopInvite(owner.uniqueId(), owner.playerName(), expiresAt));
        text.send(ownerPlayer, "commands.island-coop-invite-sent", List.of(
                TextService.raw("player", target.getName()),
                TextService.raw("seconds", Long.toString(inviteExpireSeconds()))
        ));
        text.send(target, "commands.island-coop-invite-received", List.of(
                TextService.raw("player", owner.playerName()),
                TextService.raw("seconds", Long.toString(inviteExpireSeconds()))
        ));
    }

    public void acceptCoopInvite(Player target, String ownerName) {
        if (!enabled()) {
            text.send(target, "commands.island-disabled");
            return;
        }
        expireCoopInvites();
        CoopInvite invite = coopInvites.get(target.getUniqueId());
        if (invite == null || (ownerName != null && !ownerName.isBlank() && !invite.ownerName().equalsIgnoreCase(ownerName))) {
            text.send(target, "commands.island-coop-invite-missing");
            return;
        }
        SkyBlockProfile owner = profiles.profile(invite.ownerId());
        if (owner == null) {
            text.send(target, "commands.island-coop-invite-missing");
            coopInvites.remove(target.getUniqueId());
            return;
        }
        if (owner.isIslandCoopMember(target.getUniqueId())) {
            text.send(target, "commands.island-coop-already-joined", List.of(TextService.raw("player", owner.playerName())));
            coopInvites.remove(target.getUniqueId());
            return;
        }
        if (owner.islandCoopMembers().size() >= maxCoopMembers()) {
            text.send(target, "commands.island-coop-full", islandPlaceholders(owner));
            coopInvites.remove(target.getUniqueId());
            return;
        }
        owner.addIslandCoopMember(target.getUniqueId());
        profiles.save(owner);
        coopInvites.remove(target.getUniqueId());
        text.send(target, "commands.island-coop-accepted", List.of(TextService.raw("player", owner.playerName())));
        Player ownerPlayer = Bukkit.getPlayer(owner.uniqueId());
        if (ownerPlayer != null) {
            text.send(ownerPlayer, "commands.island-coop-joined", List.of(TextService.raw("player", target.getName())));
        }
    }

    public void removeCoopMember(Player ownerPlayer, String targetName) {
        if (!enabled()) {
            text.send(ownerPlayer, "commands.island-disabled");
            return;
        }
        SkyBlockProfile owner = profiles.profile(ownerPlayer);
        SkyBlockProfile target = profiles.profileByName(targetName);
        if (target == null || !owner.isIslandCoopMember(target.uniqueId())) {
            text.send(ownerPlayer, "commands.island-coop-not-member", List.of(TextService.raw("player", targetName == null ? "" : targetName)));
            return;
        }
        owner.removeIslandCoopMember(target.uniqueId());
        profiles.save(owner);
        text.send(ownerPlayer, "commands.island-coop-removed", List.of(TextService.raw("player", target.playerName())));
        Player targetPlayer = Bukkit.getPlayer(target.uniqueId());
        if (targetPlayer != null) {
            text.send(targetPlayer, "commands.island-coop-removed-notify", List.of(TextService.raw("player", owner.playerName())));
        }
    }

    public void sendCoopMembers(Player player) {
        SkyBlockProfile owner = profiles.profile(player);
        text.send(player, "commands.island-coop-members-header", islandPlaceholders(owner));
        text.send(player, "commands.island-coop-member-line", List.of(
                TextService.raw("player", owner.playerName()),
                TextService.parsed("role", text.rawMessage("islands.coop-role-owner"))
        ));
        for (UUID memberId : owner.islandCoopMembers().stream().sorted().toList()) {
            text.send(player, "commands.island-coop-member-line", List.of(
                    TextService.raw("player", profiles.name(memberId)),
                    TextService.parsed("role", text.rawMessage("islands.coop-role-member"))
            ));
        }
    }

    public void setHome(Player player) {
        if (!enabled()) {
            text.send(player, "commands.island-disabled");
            return;
        }
        SkyBlockProfile owner = ownerForCurrentIsland(player);
        if (owner == null || !canModify(player, player.getWorld())) {
            text.send(player, "commands.island-home-set-invalid");
            return;
        }
        Location location = player.getLocation();
        owner.islandHome(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        profiles.save(owner);
        player.getWorld().setSpawnLocation(location);
        text.send(player, "commands.island-home-set", islandPlaceholders(owner));
    }

    public void setWarp(Player player, String warpName) {
        if (!enabled()) {
            text.send(player, "commands.island-disabled");
            return;
        }
        SkyBlockProfile owner = ownerForCurrentIsland(player);
        if (owner == null || !canModify(player, player.getWorld())) {
            text.send(player, "commands.island-warp-set-invalid");
            return;
        }
        String id = normalizeWarpName(warpName);
        if (id.isBlank()) {
            text.send(player, "commands.island-warp-name-invalid", warpPlaceholders(owner, warpName));
            return;
        }
        IslandWarp existing = owner.islandWarp(id);
        if (existing == null && owner.islandWarps().size() >= maxIslandWarps()) {
            text.send(player, "commands.island-warp-limit", warpPlaceholders(owner, id));
            return;
        }
        Location location = player.getLocation();
        IslandWarp warp = new IslandWarp(id, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        owner.setIslandWarp(warp);
        profiles.save(owner);
        text.send(player, "commands.island-warp-set", warpPlaceholders(owner, warp));
    }

    public void removeWarp(Player player, String warpName) {
        if (!enabled()) {
            text.send(player, "commands.island-disabled");
            return;
        }
        SkyBlockProfile owner = editableIslandOwner(player);
        if (owner == null || !hasIsland(owner)) {
            text.send(player, "commands.island-no-own-island");
            return;
        }
        String id = normalizeWarpName(warpName);
        if (id.isBlank()) {
            text.send(player, "commands.island-warp-name-invalid", warpPlaceholders(owner, warpName));
            return;
        }
        IslandWarp removed = owner.removeIslandWarp(id);
        if (removed == null) {
            text.send(player, "commands.island-warp-unknown", warpPlaceholders(owner, id));
            return;
        }
        profiles.save(owner);
        text.send(player, "commands.island-warp-removed", warpPlaceholders(owner, removed));
    }

    public void teleportWarp(Player player, String warpName) {
        if (!enabled()) {
            text.send(player, "commands.island-disabled");
            return;
        }
        SkyBlockProfile owner = islandContext(player);
        if (owner == null || !hasIsland(owner)) {
            text.send(player, "commands.island-no-own-island");
            return;
        }
        String id = normalizeWarpName(warpName);
        if (id.isBlank()) {
            text.send(player, "commands.island-warp-name-invalid", warpPlaceholders(owner, warpName));
            return;
        }
        IslandWarp warp = owner.islandWarp(id);
        if (warp == null) {
            text.send(player, "commands.island-warp-unknown", warpPlaceholders(owner, id));
            return;
        }
        World world = worldFor(owner, owner.uniqueId());
        ensureStarterIsland(world);
        player.teleport(warp.location(world));
        text.send(player, "commands.island-warp-teleported", warpPlaceholders(owner, warp));
    }

    public void sendWarps(Player player) {
        if (!enabled()) {
            text.send(player, "commands.island-disabled");
            return;
        }
        SkyBlockProfile owner = islandContext(player);
        if (owner == null || !hasIsland(owner)) {
            text.send(player, "commands.island-no-own-island");
            return;
        }
        if (owner.islandWarps().isEmpty()) {
            text.send(player, "commands.island-warps-empty", islandPlaceholders(owner));
            return;
        }
        text.send(player, "commands.island-warps-header", islandPlaceholders(owner));
        for (IslandWarp warp : sortedWarps(owner)) {
            text.send(player, "commands.island-warps-line", warpPlaceholders(owner, warp));
        }
    }

    public List<String> warpIds(Player player) {
        if (player == null) {
            return List.of();
        }
        SkyBlockProfile owner = islandContext(player);
        if (owner == null) {
            return List.of();
        }
        return sortedWarps(owner).stream().map(IslandWarp::id).toList();
    }

    public List<String> warpIds(String ownerName) {
        SkyBlockProfile owner = profiles.profileByName(ownerName);
        if (owner == null) {
            return List.of();
        }
        return sortedWarps(owner).stream().map(IslandWarp::id).toList();
    }

    public void requestReset(Player player) {
        if (!enabled()) {
            text.send(player, "commands.island-disabled");
            return;
        }
        if (!resetEnabled()) {
            text.send(player, "commands.island-reset-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (!hasIsland(profile)) {
            text.send(player, "commands.island-reset-no-island");
            return;
        }
        long seconds = resetConfirmationSeconds();
        resetConfirmations.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        text.send(player, "commands.island-reset-requested", resetPlaceholders(profile));
    }

    public void confirmReset(Player player, String confirmationWord) {
        if (!enabled()) {
            text.send(player, "commands.island-disabled");
            return;
        }
        if (!resetEnabled()) {
            text.send(player, "commands.island-reset-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (!hasIsland(profile)) {
            text.send(player, "commands.island-reset-no-island");
            return;
        }
        Long expiresAt = resetConfirmations.get(player.getUniqueId());
        if (expiresAt == null || expiresAt <= System.currentTimeMillis()) {
            resetConfirmations.remove(player.getUniqueId());
            text.send(player, "commands.island-reset-missing", resetPlaceholders(profile));
            return;
        }
        if (!resetConfirmationWord().equalsIgnoreCase(confirmationWord == null ? "" : confirmationWord)) {
            text.send(player, "commands.island-reset-token-invalid", resetPlaceholders(profile));
            return;
        }
        resetConfirmations.remove(player.getUniqueId());
        resetIsland(player, profile);
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
                TextService.raw("coop_members", text.formatNumber(profile.islandCoopMembers().size())),
                TextService.raw("coop_member_limit", text.formatNumber(maxCoopMembers())),
                TextService.raw("border_size", text.formatNumber(borderSize())),
                TextService.raw("home_x", text.formatNumber(homeX(profile))),
                TextService.raw("home_y", text.formatNumber(homeY(profile))),
                TextService.raw("home_z", text.formatNumber(homeZ(profile))),
                TextService.raw("warps", text.formatNumber(profile.islandWarps().size())),
                TextService.raw("warp_limit", text.formatNumber(maxIslandWarps())),
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
        SkyBlockProfile owner = profiles.profileByIslandWorld(world.getName());
        return owner != null && (owner.uniqueId().equals(player.getUniqueId()) || owner.isIslandCoopMember(player.getUniqueId()));
    }

    private World worldFor(SkyBlockProfile profile, UUID owner) {
        String worldName = profile.islandWorldName();
        if (worldName == null || worldName.isBlank()) {
            worldName = worldPrefix() + owner.toString().replace("-", "");
            profile.islandWorldName(worldName);
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            configureWorld(world);
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
        configureWorld(created);
        return created;
    }

    private void teleportHome(Player player, World world) {
        SkyBlockProfile profile = profiles.profileByIslandWorld(world.getName());
        player.teleport(homeLocation(profile, world));
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

    private int borderSize() {
        return Math.max(16, configService.main().getInt("islands.border-size", 160));
    }

    private int borderWarningBlocks() {
        return Math.max(0, configService.main().getInt("islands.border-warning-blocks", 5));
    }

    private int maxCoopMembers() {
        return Math.max(1, configService.main().getInt("islands.coop.max-members", 5));
    }

    private int maxIslandWarps() {
        return Math.max(1, configService.main().getInt("islands.warps.max", 56));
    }

    private int maxWarpNameLength() {
        return Math.max(3, configService.main().getInt("islands.warps.max-name-length", 24));
    }

    private long inviteExpireSeconds() {
        return Math.max(10L, configService.main().getLong("islands.coop.invite-expire-seconds", 60L));
    }

    private void expireCoopInvites() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, CoopInvite>> iterator = coopInvites.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, CoopInvite> entry = iterator.next();
            if (entry.getValue().expiresAtMillis() > now) {
                continue;
            }
            iterator.remove();
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target != null) {
                text.send(target, "commands.island-coop-invite-expired", List.of(TextService.raw("player", entry.getValue().ownerName())));
            }
            Player owner = Bukkit.getPlayer(entry.getValue().ownerId());
            if (owner != null) {
                text.send(owner, "commands.island-coop-invite-expired", List.of(TextService.raw("player", target == null ? entry.getKey().toString() : target.getName())));
            }
        }
    }

    private Location homeLocation(World world) {
        double y = configService.main().getDouble("islands.spawn-y", 80.0D);
        float yaw = (float) configService.main().getDouble("islands.home-yaw", 180.0D);
        float pitch = (float) configService.main().getDouble("islands.home-pitch", 0.0D);
        return new Location(world, 0.5D, y + 2.0D, 0.5D, yaw, pitch);
    }

    private Location homeLocation(SkyBlockProfile profile, World world) {
        if (profile != null && profile.islandHomeSet()) {
            return new Location(world, profile.islandHomeX(), profile.islandHomeY(), profile.islandHomeZ(), profile.islandHomeYaw(), profile.islandHomePitch());
        }
        return homeLocation(world);
    }

    private double homeX(SkyBlockProfile profile) {
        return profile.islandHomeSet() ? profile.islandHomeX() : 0.5D;
    }

    private double homeY(SkyBlockProfile profile) {
        return profile.islandHomeSet() ? profile.islandHomeY() : configService.main().getDouble("islands.spawn-y", 80.0D) + 2.0D;
    }

    private double homeZ(SkyBlockProfile profile) {
        return profile.islandHomeSet() ? profile.islandHomeZ() : 0.5D;
    }

    private void configureWorld(World world) {
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        SkyBlockProfile profile = profiles.profileByIslandWorld(world.getName());
        Location home = homeLocation(profile, world);
        world.setSpawnLocation(home);
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.5D, 0.5D);
        border.setSize(borderSize());
        border.setWarningDistance(borderWarningBlocks());
    }

    private SkyBlockProfile ownerForCurrentIsland(Player player) {
        if (!isIslandWorld(player.getWorld())) {
            return null;
        }
        return profiles.profileByIslandWorld(player.getWorld().getName());
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

    private void resetIsland(Player player, SkyBlockProfile profile) {
        String worldName = profile.islandWorldName();
        if (worldName == null || worldName.isBlank() || !worldName.startsWith(worldPrefix())) {
            text.send(player, "commands.island-reset-failed");
            return;
        }
        text.send(player, "commands.island-reset-started", resetPlaceholders(profile));
        World loadedWorld = Bukkit.getWorld(worldName);
        if (loadedWorld != null && !unloadResetWorld(player, profile, loadedWorld)) {
            text.send(player, "commands.island-reset-failed");
            return;
        }
        Path worldPath = worldPath(worldName);
        if (worldPath == null) {
            text.send(player, "commands.island-reset-failed");
            return;
        }
        try {
            deleteDirectory(worldPath);
        } catch (IOException exception) {
            Bukkit.getLogger().warning("Unable to delete island world '" + worldName + "': " + exception.getMessage());
            text.send(player, "commands.island-reset-failed");
            return;
        }
        clearResetState(profile, worldName);
        profile.islandWorldName(null);
        World freshWorld = worldFor(profile, profile.uniqueId());
        ensureStarterIsland(freshWorld);
        profiles.save(profile);
        player.teleport(homeLocation(profile, freshWorld));
        text.send(player, "commands.island-reset-complete", islandPlaceholders(profile));
    }

    private boolean unloadResetWorld(Player requester, SkyBlockProfile profile, World world) {
        World fallback = resetFallbackWorld(world);
        if (fallback == null) {
            return false;
        }
        Location fallbackLocation = fallback.getSpawnLocation();
        for (Player worldPlayer : List.copyOf(world.getPlayers())) {
            worldPlayer.teleport(fallbackLocation);
            if (!worldPlayer.getUniqueId().equals(requester.getUniqueId())) {
                text.send(worldPlayer, "commands.island-reset-player-moved", List.of(TextService.raw("owner", profile.playerName())));
            }
        }
        world.setAutoSave(false);
        return Bukkit.unloadWorld(world, false);
    }

    private World resetFallbackWorld(World resetWorld) {
        for (World world : Bukkit.getWorlds()) {
            if (!world.getName().equals(resetWorld.getName()) && !isIslandWorld(world)) {
                return world;
            }
        }
        for (World world : Bukkit.getWorlds()) {
            if (!world.getName().equals(resetWorld.getName())) {
                return world;
            }
        }
        return null;
    }

    private void clearResetState(SkyBlockProfile profile, String worldName) {
        profile.clearIslandHome();
        if (configService.main().getBoolean("islands.reset.clear-warps", true)) {
            profile.islandWarps().clear();
        }
        if (configService.main().getBoolean("islands.reset.clear-minions", true)) {
            profile.minions().clear();
        }
        if (configService.main().getBoolean("islands.reset.clear-cakes", true)) {
            profile.placedCakes().removeIf(cake -> worldName.equals(cake.worldName()));
        }
    }

    private void deleteDirectory(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Path worldPath(String worldName) {
        Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path worldPath = container.resolve(worldName).normalize();
        return worldPath.startsWith(container) ? worldPath : null;
    }

    private List<TextService.TextPlaceholder> resetPlaceholders(SkyBlockProfile profile) {
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(islandPlaceholders(profile));
        placeholders.add(TextService.raw("reset_token", resetConfirmationWord()));
        placeholders.add(TextService.raw("reset_seconds", text.formatNumber(resetConfirmationSeconds())));
        return placeholders;
    }

    private List<TextService.TextPlaceholder> warpPlaceholders(SkyBlockProfile profile, IslandWarp warp) {
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(islandPlaceholders(profile));
        placeholders.add(TextService.raw("warp", warp == null ? "" : warp.id()));
        placeholders.add(TextService.raw("warp_x", warp == null ? "" : text.formatNumber(warp.x())));
        placeholders.add(TextService.raw("warp_y", warp == null ? "" : text.formatNumber(warp.y())));
        placeholders.add(TextService.raw("warp_z", warp == null ? "" : text.formatNumber(warp.z())));
        return placeholders;
    }

    private List<TextService.TextPlaceholder> warpPlaceholders(SkyBlockProfile profile, String warpName) {
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(islandPlaceholders(profile));
        placeholders.add(TextService.raw("warp", warpName == null ? "" : warpName));
        placeholders.add(TextService.raw("warp_x", ""));
        placeholders.add(TextService.raw("warp_y", ""));
        placeholders.add(TextService.raw("warp_z", ""));
        return placeholders;
    }

    private List<IslandWarp> sortedWarps(SkyBlockProfile profile) {
        return profile.islandWarps().values().stream()
                .sorted((first, second) -> first.id().compareTo(second.id()))
                .toList();
    }

    private SkyBlockProfile islandContext(Player player) {
        SkyBlockProfile owner = ownerForCurrentIsland(player);
        return owner == null ? profiles.profile(player) : owner;
    }

    private SkyBlockProfile editableIslandOwner(Player player) {
        if (!isIslandWorld(player.getWorld())) {
            return profiles.profile(player);
        }
        SkyBlockProfile owner = ownerForCurrentIsland(player);
        return owner != null && canModify(player, player.getWorld()) ? owner : null;
    }

    private String normalizeWarpName(String raw) {
        if (raw == null) {
            return "";
        }
        String input = raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder();
        int limit = maxWarpNameLength();
        for (int index = 0; index < input.length() && normalized.length() < limit; index++) {
            char character = input.charAt(index);
            boolean letter = character >= 'a' && character <= 'z';
            boolean digit = character >= '0' && character <= '9';
            if (letter || digit || character == '_' || character == '-') {
                normalized.append(character);
            }
        }
        return normalized.toString();
    }

    private boolean hasIsland(SkyBlockProfile profile) {
        return profile != null && profile.islandWorldName() != null && !profile.islandWorldName().isBlank();
    }

    private boolean resetEnabled() {
        return configService.main().getBoolean("islands.reset.enabled", true);
    }

    private long resetConfirmationSeconds() {
        return Math.max(10L, configService.main().getLong("islands.reset.confirmation-seconds", 60L));
    }

    private String resetConfirmationWord() {
        String configured = configService.main().getString("islands.reset.confirmation-word", "RESET");
        return configured == null || configured.isBlank() ? "RESET" : configured.trim();
    }

    private String worldPrefix() {
        String configured = configService.main().getString("islands.world-prefix", "openskyblock_island_");
        return configured == null || configured.isBlank() ? "openskyblock_island_" : configured;
    }

    private record CoopInvite(UUID ownerId, String ownerName, long expiresAtMillis) {
    }
}
