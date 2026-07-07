package io.github.openskyblock.island;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.IslandTeleportPad;
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
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class IslandService {
    private static final String PAD_ITEM_MARKER = "teleport_pad";

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final VoidChunkGenerator generator = new VoidChunkGenerator();
    private final Map<UUID, CoopInvite> coopInvites = new HashMap<>();
    private final Map<ResetConfirmationKey, Long> resetConfirmations = new HashMap<>();
    private final Map<UUID, Long> teleportPadCooldowns = new HashMap<>();
    private final NamespacedKey teleportPadItemKey;

    public IslandService(JavaPlugin plugin, ConfigService configService, TextService text, ProfileManager profiles) {
        this.plugin = plugin;
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.teleportPadItemKey = new NamespacedKey(plugin, "teleport_pad_item");
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
        SkyBlockProfile owner = islandContext(player);
        setVisitors(player, owner == null || !owner.islandVisitorsEnabled());
    }

    public void setVisitors(Player player, boolean visitorsEnabled) {
        if (!enabled()) {
            text.send(player, "commands.island-disabled");
            return;
        }
        SkyBlockProfile profile = editableIslandOwner(player, IslandPermission.MANAGE_COOP);
        if (profile == null || !hasIsland(profile)) {
            text.send(player, profile == null ? "commands.island-protected" : "commands.island-no-own-island");
            return;
        }
        profile.islandVisitorsEnabled(visitorsEnabled);
        profiles.save(profile);
        text.send(player, "commands.island-visitors-updated", islandPlaceholders(profile));
    }

    public void inviteCoop(Player ownerPlayer, Player target) {
        if (!enabled()) {
            text.send(ownerPlayer, "commands.island-disabled");
            return;
        }
        SkyBlockProfile owner = editableIslandOwner(ownerPlayer, IslandPermission.MANAGE_COOP);
        if (owner == null || !hasIsland(owner)) {
            text.send(ownerPlayer, owner == null ? "commands.island-protected" : "commands.island-no-own-island");
            return;
        }
        expireCoopInvites();
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
        owner.setIslandCoopRole(target.getUniqueId(), defaultCoopRoleId());
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
        SkyBlockProfile owner = editableIslandOwner(ownerPlayer, IslandPermission.MANAGE_COOP);
        if (owner == null || !hasIsland(owner)) {
            text.send(ownerPlayer, owner == null ? "commands.island-protected" : "commands.island-no-own-island");
            return;
        }
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
        SkyBlockProfile owner = islandContext(player);
        if (owner == null || !hasIsland(owner)) {
            text.send(player, "commands.island-no-own-island");
            return;
        }
        text.send(player, "commands.island-coop-members-header", islandPlaceholders(owner));
        text.send(player, "commands.island-coop-member-line", List.of(
                TextService.raw("player", owner.playerName()),
                TextService.parsed("role", text.rawMessage("islands.coop-role-owner"))
        ));
        for (UUID memberId : owner.islandCoopMembers().stream().sorted().toList()) {
            text.send(player, "commands.island-coop-member-line", List.of(
                    TextService.raw("player", profiles.name(memberId)),
                    TextService.parsed("role", coopRoleDisplay(coopRoleId(owner, memberId)))
            ));
        }
    }

    public void sendCoopPermissions(Player player) {
        sendCoopPermissions(player, "");
    }

    public void sendCoopPermissions(Player player, String roleId) {
        SkyBlockProfile owner = islandContext(player);
        if (owner == null || !hasIsland(owner)) {
            text.send(player, "commands.island-no-own-island");
            return;
        }
        String normalizedRole = normalizeCoopRoleId(roleId == null || roleId.isBlank() ? defaultCoopRoleId() : roleId);
        if (!coopRoleExists(normalizedRole)) {
            text.send(player, "commands.island-coop-role-unknown", List.of(TextService.raw("role", roleId == null ? "" : roleId)));
            return;
        }
        text.send(player, "commands.island-coop-permissions-header", List.of(
                TextService.raw("role", normalizedRole),
                TextService.parsed("role_display", coopRoleDisplay(normalizedRole))
        ));
        for (IslandPermission permission : IslandPermission.values()) {
            text.send(player, "commands.island-coop-permissions-line", List.of(
                    TextService.parsed("permission", permissionDisplay(permission)),
                    TextService.parsed("status", permissionStatus(coopPermissionEnabled(normalizedRole, permission))),
                    TextService.raw("permission_key", permission.configKey())
            ));
        }
    }

    public void sendCoopRoles(Player player) {
        SkyBlockProfile owner = islandContext(player);
        if (owner == null || !hasIsland(owner)) {
            text.send(player, "commands.island-no-own-island");
            return;
        }
        text.send(player, "commands.island-coop-roles-header");
        for (String roleId : coopRoleIds()) {
            text.send(player, "commands.island-coop-role-line", List.of(
                    TextService.raw("role", roleId),
                    TextService.parsed("role_display", coopRoleDisplay(roleId))
            ));
        }
    }

    public void setCoopRole(Player player, String targetName, String roleId) {
        if (!enabled()) {
            text.send(player, "commands.island-disabled");
            return;
        }
        SkyBlockProfile owner = editableIslandOwner(player, IslandPermission.MANAGE_COOP);
        if (owner == null || !hasIsland(owner)) {
            text.send(player, owner == null ? "commands.island-protected" : "commands.island-no-own-island");
            return;
        }
        String normalizedRole = normalizeCoopRoleId(roleId);
        if (!coopRoleExists(normalizedRole)) {
            text.send(player, "commands.island-coop-role-unknown", List.of(TextService.raw("role", roleId == null ? "" : roleId)));
            return;
        }
        SkyBlockProfile target = profiles.profileByName(targetName);
        if (target == null || !owner.isIslandCoopMember(target.uniqueId())) {
            text.send(player, "commands.island-coop-not-member", List.of(TextService.raw("player", targetName == null ? "" : targetName)));
            return;
        }
        owner.setIslandCoopRole(target.uniqueId(), normalizedRole);
        profiles.save(owner);
        text.send(player, "commands.island-coop-role-set", List.of(
                TextService.raw("player", target.playerName()),
                TextService.raw("role", normalizedRole),
                TextService.parsed("role_display", coopRoleDisplay(normalizedRole))
        ));
        Player targetPlayer = Bukkit.getPlayer(target.uniqueId());
        if (targetPlayer != null) {
            text.send(targetPlayer, "commands.island-coop-role-updated", List.of(
                    TextService.raw("player", owner.playerName()),
                    TextService.raw("role", normalizedRole),
                    TextService.parsed("role_display", coopRoleDisplay(normalizedRole))
            ));
        }
    }

    public ItemStack createTeleportPadItem() {
        ItemStack itemStack = new ItemStack(teleportPadMaterial());
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(text.message("islands.teleport-pad-item-name"));
        meta.lore(configService.messages().getStringList("islands.teleport-pad-item-lore").stream()
                .map(text::deserialize)
                .toList());
        meta.getPersistentDataContainer().set(teleportPadItemKey, PersistentDataType.STRING, PAD_ITEM_MARKER);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public boolean isTeleportPadItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return false;
        }
        String marker = itemStack.getItemMeta().getPersistentDataContainer().get(teleportPadItemKey, PersistentDataType.STRING);
        return PAD_ITEM_MARKER.equals(marker);
    }

    public boolean placeTeleportPad(Player player, Location location) {
        if (!enabled()) {
            text.send(player, "commands.island-disabled");
            return false;
        }
        SkyBlockProfile owner = ownerForCurrentIsland(player);
        if (owner == null || !canModify(player, location.getWorld(), IslandPermission.MANAGE_TELEPORT_PADS)) {
            text.send(player, "commands.island-teleport-pad-place-invalid");
            return false;
        }
        if (findTeleportPad(owner, location).isPresent()) {
            text.send(player, "commands.island-teleport-pad-location-used", teleportPadPlaceholders(owner, null));
            return false;
        }
        if (owner.islandTeleportPads().size() >= maxTeleportPads()) {
            text.send(player, "commands.island-teleport-pad-limit", islandPlaceholders(owner));
            return false;
        }
        IslandTeleportPad pad = new IslandTeleportPad(
                nextTeleportPadId(owner),
                defaultTeleportPadGroup(owner),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                player.getLocation().getYaw(),
                player.getLocation().getPitch()
        );
        owner.setIslandTeleportPad(pad);
        location.getBlock().setType(teleportPadMaterial(), false);
        profiles.save(owner);
        text.send(player, "commands.island-teleport-pad-placed", teleportPadPlaceholders(owner, pad));
        return true;
    }

    public boolean breakTeleportPad(Player player, Location location) {
        SkyBlockProfile owner = profiles.profileByIslandWorld(location.getWorld().getName());
        if (owner == null) {
            return false;
        }
        IslandTeleportPad pad = findTeleportPad(owner, location).orElse(null);
        if (pad == null) {
            return false;
        }
        if (!canModify(player, location.getWorld(), IslandPermission.MANAGE_TELEPORT_PADS)) {
            text.send(player, "commands.island-protected");
            return true;
        }
        owner.removeIslandTeleportPad(pad.id());
        location.getBlock().setType(Material.AIR, false);
        giveOrDrop(player, createTeleportPadItem());
        profiles.save(owner);
        text.send(player, "commands.island-teleport-pad-removed", teleportPadPlaceholders(owner, pad));
        return true;
    }

    public boolean setTeleportPadGroup(Player player, String padName, String groupName) {
        SkyBlockProfile owner = editableIslandOwner(player, IslandPermission.MANAGE_TELEPORT_PADS);
        if (owner == null || !hasIsland(owner)) {
            text.send(player, "commands.island-no-own-island");
            return false;
        }
        IslandTeleportPad pad = owner.islandTeleportPad(normalizePadName(padName));
        if (pad == null) {
            text.send(player, "commands.island-teleport-pad-unknown", teleportPadPlaceholders(owner, padName, groupName));
            return false;
        }
        String group = normalizePadGroup(groupName);
        if (group.isBlank()) {
            text.send(player, "commands.island-teleport-pad-group-invalid", teleportPadPlaceholders(owner, padName, groupName));
            return false;
        }
        IslandTeleportPad updated = new IslandTeleportPad(pad.id(), group, pad.x(), pad.y(), pad.z(), pad.yaw(), pad.pitch());
        owner.setIslandTeleportPad(updated);
        profiles.save(owner);
        text.send(player, "commands.island-teleport-pad-linked", teleportPadPlaceholders(owner, updated));
        return true;
    }

    public void sendTeleportPads(Player player) {
        SkyBlockProfile owner = islandContext(player);
        if (owner == null || !hasIsland(owner)) {
            text.send(player, "commands.island-no-own-island");
            return;
        }
        if (owner.islandTeleportPads().isEmpty()) {
            text.send(player, "commands.island-teleport-pads-empty", islandPlaceholders(owner));
            return;
        }
        text.send(player, "commands.island-teleport-pads-header", islandPlaceholders(owner));
        sortedTeleportPads(owner).forEach(pad -> text.send(player, "commands.island-teleport-pads-line", teleportPadPlaceholders(owner, pad)));
    }

    public List<String> teleportPadIds(Player player) {
        if (player == null) {
            return List.of();
        }
        SkyBlockProfile owner = islandContext(player);
        if (owner == null) {
            return List.of();
        }
        return sortedTeleportPads(owner).stream().map(IslandTeleportPad::id).toList();
    }

    public void handleTeleportPadMove(Player player) {
        if (!enabled() || !configService.main().getBoolean("islands.teleport-pads.enabled", true) || !isIslandWorld(player.getWorld())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (teleportPadCooldowns.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        SkyBlockProfile owner = profiles.profileByIslandWorld(player.getWorld().getName());
        if (owner == null || owner.islandTeleportPads().size() < 2) {
            return;
        }
        IslandTeleportPad source = currentTeleportPad(owner, player.getLocation()).orElse(null);
        if (source == null) {
            return;
        }
        IslandTeleportPad target = nextTeleportPad(owner, source).orElse(null);
        if (target == null) {
            text.send(player, "commands.island-teleport-pad-unlinked", teleportPadPlaceholders(owner, source));
            teleportPadCooldowns.put(player.getUniqueId(), now + teleportPadCooldownMillis());
            return;
        }
        teleportPadCooldowns.put(player.getUniqueId(), now + teleportPadCooldownMillis());
        player.teleport(target.location(player.getWorld()));
        text.send(player, "commands.island-teleport-pad-teleported", teleportPadPlaceholders(owner, target));
    }

    public void setHome(Player player) {
        if (!enabled()) {
            text.send(player, "commands.island-disabled");
            return;
        }
        SkyBlockProfile owner = ownerForCurrentIsland(player);
        if (owner == null || !canModify(player, player.getWorld(), IslandPermission.SET_HOME)) {
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
        if (owner == null || !canModify(player, player.getWorld(), IslandPermission.MANAGE_WARPS)) {
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
        SkyBlockProfile owner = editableIslandOwner(player, IslandPermission.MANAGE_WARPS);
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

    public List<String> coopMemberNames(Player player) {
        if (player == null) {
            return List.of();
        }
        SkyBlockProfile owner = islandContext(player);
        if (owner == null) {
            return List.of();
        }
        return owner.islandCoopMembers().stream()
                .map(profiles::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public List<String> coopRoleIds() {
        return configuredCoopRoleIds();
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
        SkyBlockProfile profile = editableIslandOwner(player, IslandPermission.RESET);
        if (profile == null || !hasIsland(profile)) {
            text.send(player, profile == null ? "commands.island-protected" : "commands.island-reset-no-island");
            return;
        }
        long seconds = resetConfirmationSeconds();
        resetConfirmations.put(new ResetConfirmationKey(player.getUniqueId(), profile.uniqueId()), System.currentTimeMillis() + seconds * 1000L);
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
        SkyBlockProfile profile = editableIslandOwner(player, IslandPermission.RESET);
        if (profile == null || !hasIsland(profile)) {
            text.send(player, profile == null ? "commands.island-protected" : "commands.island-reset-no-island");
            return;
        }
        ResetConfirmationKey key = new ResetConfirmationKey(player.getUniqueId(), profile.uniqueId());
        Long expiresAt = resetConfirmations.get(key);
        if (expiresAt == null || expiresAt <= System.currentTimeMillis()) {
            resetConfirmations.remove(key);
            text.send(player, "commands.island-reset-missing", resetPlaceholders(profile));
            return;
        }
        if (!resetConfirmationWord().equalsIgnoreCase(confirmationWord == null ? "" : confirmationWord)) {
            text.send(player, "commands.island-reset-token-invalid", resetPlaceholders(profile));
            return;
        }
        resetConfirmations.remove(key);
        resetIsland(player, profile);
    }

    public List<TextService.TextPlaceholder> islandPlaceholders(Player player) {
        return islandPlaceholders(islandContext(player));
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
                TextService.raw("teleport_pads", text.formatNumber(profile.islandTeleportPads().size())),
                TextService.raw("teleport_pad_limit", text.formatNumber(maxTeleportPads())),
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
        return canModify(player, world, IslandPermission.BUILD);
    }

    public boolean canInteract(Player player, World world) {
        return canModify(player, world, IslandPermission.INTERACT);
    }

    public boolean canManageTeleportPads(Player player, World world) {
        return canModify(player, world, IslandPermission.MANAGE_TELEPORT_PADS);
    }

    public boolean canModify(Player player, World world, IslandPermission permission) {
        if (!isIslandWorld(world) || player.hasPermission("openskyblock.admin")) {
            return true;
        }
        SkyBlockProfile owner = profiles.profileByIslandWorld(world.getName());
        return hasIslandPermission(player, owner, permission);
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

    private boolean coopPermissionEnabled(SkyBlockProfile owner, UUID memberId, IslandPermission permission) {
        return coopPermissionEnabled(coopRoleId(owner, memberId), permission);
    }

    private boolean coopPermissionEnabled(String roleId, IslandPermission permission) {
        ConfigurationSection role = coopRoleSection(roleId);
        if (role != null && role.contains("permissions." + permission.configKey())) {
            return role.getBoolean("permissions." + permission.configKey(), true);
        }
        return configService.main().getBoolean("islands.coop.permissions." + permission.configKey(), true);
    }

    private String coopRoleId(SkyBlockProfile owner, UUID memberId) {
        if (owner == null || memberId == null) {
            return defaultCoopRoleId();
        }
        String roleId = normalizeCoopRoleId(owner.islandCoopRole(memberId));
        return coopRoleExists(roleId) ? roleId : defaultCoopRoleId();
    }

    private String defaultCoopRoleId() {
        List<String> configuredRoles = configuredCoopRoleIds();
        String configuredDefault = normalizeCoopRoleId(configService.main().getString("islands.coop.default-role", "member"));
        if (!configuredDefault.isBlank() && configuredRoles.contains(configuredDefault)) {
            return configuredDefault;
        }
        if (configuredRoles.contains("member")) {
            return "member";
        }
        return configuredRoles.isEmpty() ? "member" : configuredRoles.get(0);
    }

    private boolean coopRoleExists(String roleId) {
        return configuredCoopRoleIds().contains(normalizeCoopRoleId(roleId));
    }

    private List<String> configuredCoopRoleIds() {
        ConfigurationSection roles = configService.main().getConfigurationSection("islands.coop.roles");
        if (roles == null) {
            return List.of("member");
        }
        List<String> roleIds = roles.getKeys(false).stream()
                .map(this::normalizeCoopRoleId)
                .filter(roleId -> !roleId.isBlank())
                .distinct()
                .sorted()
                .toList();
        return roleIds.isEmpty() ? List.of("member") : roleIds;
    }

    private ConfigurationSection coopRoleSection(String roleId) {
        String normalized = normalizeCoopRoleId(roleId);
        return normalized.isBlank() ? null : configService.main().getConfigurationSection("islands.coop.roles." + normalized);
    }

    private String coopRoleDisplay(String roleId) {
        String normalized = normalizeCoopRoleId(roleId);
        ConfigurationSection role = coopRoleSection(normalized);
        if (role != null) {
            String display = role.getString("display-name", "");
            if (!display.isBlank()) {
                return display;
            }
        }
        String legacy = configService.messages().getString("islands.coop-role-" + normalized, "");
        if (!legacy.isBlank()) {
            return legacy;
        }
        if (normalized.isBlank()) {
            return text.rawMessage("islands.coop-role-member");
        }
        return "<green>" + readableRoleName(normalized) + "</green>";
    }

    private String normalizeCoopRoleId(String raw) {
        if (raw == null) {
            return "";
        }
        String input = raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            if (Character.isLetterOrDigit(character) || character == '_' || character == '-') {
                normalized.append(character);
            }
        }
        return normalized.toString();
    }

    private String readableRoleName(String roleId) {
        String normalized = roleId.replace('-', ' ').replace('_', ' ').trim();
        if (normalized.isBlank()) {
            return "Member";
        }
        StringBuilder readable = new StringBuilder();
        for (String part : normalized.split(" ")) {
            if (part.isBlank()) {
                continue;
            }
            if (readable.length() > 0) {
                readable.append(' ');
            }
            readable.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return readable.toString();
    }

    private int maxIslandWarps() {
        return Math.max(1, configService.main().getInt("islands.warps.max", 56));
    }

    private int maxWarpNameLength() {
        return Math.max(3, configService.main().getInt("islands.warps.max-name-length", 24));
    }

    private int maxTeleportPads() {
        return Math.max(0, configService.main().getInt("islands.teleport-pads.max", 12));
    }

    private long teleportPadCooldownMillis() {
        return Math.max(1L, configService.main().getLong("islands.teleport-pads.cooldown-ticks", 20L)) * 50L;
    }

    private Material teleportPadMaterial() {
        Material material = Material.matchMaterial(configService.main().getString("islands.teleport-pads.material", "LIGHT_WEIGHTED_PRESSURE_PLATE"));
        if (material == null || material.isAir() || !material.isBlock()) {
            return Material.LIGHT_WEIGHTED_PRESSURE_PLATE;
        }
        return material;
    }

    private String normalizePadName(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        int maxLength = maxWarpNameLength();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private String normalizePadGroup(String value) {
        String normalized = normalizePadName(value);
        return normalized.isBlank() ? "" : normalized;
    }

    private String defaultTeleportPadGroup(SkyBlockProfile owner) {
        int group = owner.islandTeleportPads().size() / 2 + 1;
        return "group_" + group;
    }

    private String nextTeleportPadId(SkyBlockProfile owner) {
        int index = owner.islandTeleportPads().size() + 1;
        String candidate = "pad_" + index;
        while (owner.islandTeleportPad(candidate) != null) {
            index++;
            candidate = "pad_" + index;
        }
        return candidate;
    }

    private Optional<IslandTeleportPad> findTeleportPad(SkyBlockProfile owner, Location location) {
        if (owner == null || location == null) {
            return Optional.empty();
        }
        return owner.islandTeleportPads().values().stream()
                .filter(pad -> pad.matches(location))
                .findFirst();
    }

    private Optional<IslandTeleportPad> currentTeleportPad(SkyBlockProfile owner, Location location) {
        Optional<IslandTeleportPad> footBlock = findTeleportPad(owner, location.getBlock().getLocation());
        if (footBlock.isPresent()) {
            return footBlock;
        }
        return findTeleportPad(owner, location.clone().subtract(0.0D, 1.0D, 0.0D));
    }

    private Optional<IslandTeleportPad> nextTeleportPad(SkyBlockProfile owner, IslandTeleportPad source) {
        List<IslandTeleportPad> linked = sortedTeleportPads(owner).stream()
                .filter(pad -> pad.group().equalsIgnoreCase(source.group()))
                .toList();
        if (linked.size() < 2) {
            return Optional.empty();
        }
        int sourceIndex = linked.indexOf(source);
        if (sourceIndex < 0) {
            return Optional.empty();
        }
        return Optional.of(linked.get((sourceIndex + 1) % linked.size()));
    }

    private List<IslandTeleportPad> sortedTeleportPads(SkyBlockProfile profile) {
        return profile.islandTeleportPads().values().stream()
                .sorted((first, second) -> first.id().compareTo(second.id()))
                .toList();
    }

    private void giveOrDrop(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
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
        if (configService.main().getBoolean("islands.reset.clear-teleport-pads", true)) {
            profile.islandTeleportPads().clear();
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

    private List<TextService.TextPlaceholder> teleportPadPlaceholders(SkyBlockProfile profile, IslandTeleportPad pad) {
        return teleportPadPlaceholders(profile, pad == null ? "" : pad.id(), pad == null ? "" : pad.group(),
                pad == null ? "" : text.formatNumber(pad.x()),
                pad == null ? "" : text.formatNumber(pad.y()),
                pad == null ? "" : text.formatNumber(pad.z()));
    }

    private List<TextService.TextPlaceholder> teleportPadPlaceholders(SkyBlockProfile profile, String padName, String groupName) {
        return teleportPadPlaceholders(profile, padName, groupName, "", "", "");
    }

    private List<TextService.TextPlaceholder> teleportPadPlaceholders(SkyBlockProfile profile, String padName, String groupName, String x, String y, String z) {
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(islandPlaceholders(profile));
        placeholders.add(TextService.raw("pad", padName == null ? "" : padName));
        placeholders.add(TextService.raw("pad_group", groupName == null ? "" : groupName));
        placeholders.add(TextService.raw("pad_x", x));
        placeholders.add(TextService.raw("pad_y", y));
        placeholders.add(TextService.raw("pad_z", z));
        placeholders.add(TextService.raw("teleport_pads", text.formatNumber(profile == null ? 0 : profile.islandTeleportPads().size())));
        placeholders.add(TextService.raw("teleport_pad_limit", text.formatNumber(maxTeleportPads())));
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
        return editableIslandOwner(player, IslandPermission.BUILD);
    }

    private SkyBlockProfile editableIslandOwner(Player player, IslandPermission permission) {
        if (!isIslandWorld(player.getWorld())) {
            return profiles.profile(player);
        }
        SkyBlockProfile owner = ownerForCurrentIsland(player);
        return owner != null && canModify(player, player.getWorld(), permission) ? owner : null;
    }

    private boolean hasIslandPermission(Player player, SkyBlockProfile owner, IslandPermission permission) {
        if (player == null || owner == null) {
            return false;
        }
        if (player.hasPermission("openskyblock.admin") || owner.uniqueId().equals(player.getUniqueId())) {
            return true;
        }
        return owner.isIslandCoopMember(player.getUniqueId()) && coopPermissionEnabled(owner, player.getUniqueId(), permission);
    }

    private String permissionDisplay(IslandPermission permission) {
        return text.rawMessage("islands.permissions." + permission.configKey());
    }

    private String permissionStatus(boolean enabled) {
        return text.rawMessage(enabled ? "islands.permission-enabled" : "islands.permission-disabled");
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
