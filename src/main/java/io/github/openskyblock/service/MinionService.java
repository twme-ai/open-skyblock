package io.github.openskyblock.service;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.mayor.MayorService;
import io.github.openskyblock.profile.PlacedMinion;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.upgrade.UpgradeService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MinionService {
    private static final long MILLIS_PER_TICK = 50L;
    private static final List<BlockFace> STORAGE_FACES = List.of(
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST,
            BlockFace.UP,
            BlockFace.DOWN
    );

    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CollectionService collections;
    private final UpgradeService upgrades;
    private final CustomItemService customItems;
    private final EconomyService economy;
    private final NamespacedKey minionIdKey;
    private final NamespacedKey minionStorageIdKey;
    private final Map<String, MinionDefinition> definitions = new HashMap<>();
    private final Map<String, MinionFuelDefinition> fuels = new HashMap<>();
    private final Map<String, MinionUpgradeDefinition> minionUpgrades = new HashMap<>();
    private final Map<String, MinionStorageDefinition> storages = new HashMap<>();
    private MayorService mayors;

    public MinionService(JavaPlugin plugin, ConfigService configService, TextService text, ProfileManager profiles, CollectionService collections, UpgradeService upgrades, CustomItemService customItems, EconomyService economy) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.collections = collections;
        this.upgrades = upgrades;
        this.customItems = customItems;
        this.economy = economy;
        this.minionIdKey = new NamespacedKey(plugin, "minion_id");
        this.minionStorageIdKey = new NamespacedKey(plugin, "minion_storage_id");
    }

    public void mayorService(MayorService mayors) {
        this.mayors = mayors;
    }

    public void reload() {
        definitions.clear();
        fuels.clear();
        minionUpgrades.clear();
        storages.clear();
        ConfigurationSection section = configService.minions().getConfigurationSection("minions");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection minionSection = section.getConfigurationSection(id);
                if (minionSection == null) {
                    continue;
                }
                Material material = Material.matchMaterial(minionSection.getString("material", "STONE"));
                definitions.put(id.toUpperCase(Locale.ROOT), new MinionDefinition(
                        id.toUpperCase(Locale.ROOT),
                        minionSection.getString("display-name", id),
                        material == null ? Material.STONE : material,
                        minionSection.getString("generated-collection", "").toUpperCase(Locale.ROOT),
                        minionSection.getLong("generated-amount", 1L),
                        minionSection.getLong("interval-ticks", 400L),
                        minionSection.getLong("storage-size", 64L),
                        minionSection.getInt("required-collection-tier", 0)
                ));
            }
        }
        ConfigurationSection fuelSection = configService.minions().getConfigurationSection("fuels");
        if (fuelSection == null) {
            loadUpgrades();
            loadStorages();
            return;
        }
        for (String id : fuelSection.getKeys(false)) {
            ConfigurationSection sectionFuel = fuelSection.getConfigurationSection(id);
            if (sectionFuel == null) {
                continue;
            }
            Material material = Material.matchMaterial(sectionFuel.getString("material", ""));
            String customItemId = sectionFuel.getString("custom-item", "").toUpperCase(Locale.ROOT);
            if (material == null && customItemId.isBlank()) {
                continue;
            }
            fuels.put(id.toUpperCase(Locale.ROOT), new MinionFuelDefinition(
                    id.toUpperCase(Locale.ROOT),
                    sectionFuel.getString("display-name", id),
                    material == null ? Material.AIR : material,
                    customItemId,
                    Math.max(1.0D, sectionFuel.getDouble("speed-multiplier", 1.0D)),
                    sectionFuel.getLong("duration-seconds", 0L)
            ));
        }
        loadUpgrades();
        loadStorages();
    }

    private void loadUpgrades() {
        ConfigurationSection upgradeSection = configService.minions().getConfigurationSection("upgrades");
        if (upgradeSection == null) {
            return;
        }
        for (String id : upgradeSection.getKeys(false)) {
            ConfigurationSection sectionUpgrade = upgradeSection.getConfigurationSection(id);
            if (sectionUpgrade == null) {
                continue;
            }
            Material material = Material.matchMaterial(sectionUpgrade.getString("material", ""));
            String customItemId = sectionUpgrade.getString("custom-item", "").toUpperCase(Locale.ROOT);
            if (material == null && customItemId.isBlank()) {
                continue;
            }
            minionUpgrades.put(id.toUpperCase(Locale.ROOT), new MinionUpgradeDefinition(
                    id.toUpperCase(Locale.ROOT),
                    sectionUpgrade.getString("display-name", id),
                    material == null ? Material.AIR : material,
                    customItemId,
                    Math.max(1.0D, sectionUpgrade.getDouble("speed-multiplier", 1.0D)),
                    Math.max(1.0D, sectionUpgrade.getDouble("output-multiplier", 1.0D)),
                    Math.max(0L, sectionUpgrade.getLong("storage-bonus", 0L)),
                    Math.max(0.0D, Math.min(1.0D, sectionUpgrade.getDouble("sell-percentage", 0.0D))),
                    loadCompactions(sectionUpgrade)
            ));
        }
    }

    private Map<String, MinionCompactionDefinition> loadCompactions(ConfigurationSection upgradeSection) {
        ConfigurationSection compact = upgradeSection.getConfigurationSection("compact");
        if (compact == null) {
            return Map.of();
        }
        ConfigurationSection outputs = compact.getConfigurationSection("outputs");
        if (outputs == null) {
            return Map.of();
        }
        Map<String, MinionCompactionDefinition> compactions = new HashMap<>();
        long defaultInput = Math.max(1L, compact.getLong("input-amount", 9L));
        long defaultOutput = Math.max(1L, compact.getLong("output-amount", 1L));
        for (String collectionId : outputs.getKeys(false)) {
            ConfigurationSection output = outputs.getConfigurationSection(collectionId);
            if (output == null) {
                continue;
            }
            Material material = Material.matchMaterial(output.getString("material", ""));
            String customItemId = output.getString("custom-item", "").toUpperCase(Locale.ROOT);
            if (material == null && customItemId.isBlank()) {
                continue;
            }
            String normalized = collectionId.toUpperCase(Locale.ROOT);
            compactions.put(normalized, new MinionCompactionDefinition(
                    normalized,
                    Math.max(1L, output.getLong("input-amount", defaultInput)),
                    Math.max(1L, output.getLong("output-amount", defaultOutput)),
                    material == null ? Material.AIR : material,
                    customItemId
            ));
        }
        return Map.copyOf(compactions);
    }

    private void loadStorages() {
        ConfigurationSection storageSection = configService.minions().getConfigurationSection("storages");
        if (storageSection == null) {
            return;
        }
        for (String id : storageSection.getKeys(false)) {
            ConfigurationSection sectionStorage = storageSection.getConfigurationSection(id);
            if (sectionStorage == null) {
                continue;
            }
            Material material = Material.matchMaterial(sectionStorage.getString("material", ""));
            String customItemId = sectionStorage.getString("custom-item", "").toUpperCase(Locale.ROOT);
            if (material == null && customItemId.isBlank()) {
                continue;
            }
            storages.put(id.toUpperCase(Locale.ROOT), new MinionStorageDefinition(
                    id.toUpperCase(Locale.ROOT),
                    sectionStorage.getString("display-name", id),
                    material == null ? Material.CHEST : material,
                    customItemId,
                    Math.max(0L, sectionStorage.getLong("storage-bonus", 0L))
            ));
        }
    }

    public Optional<MinionDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<MinionDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(MinionDefinition::id))
                .toList();
    }

    public Optional<MinionFuelDefinition> fuel(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(fuels.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<MinionFuelDefinition> fuels() {
        return fuels.values().stream()
                .sorted(Comparator.comparing(MinionFuelDefinition::id))
                .toList();
    }

    public Optional<MinionUpgradeDefinition> minionUpgrade(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(minionUpgrades.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<MinionUpgradeDefinition> minionUpgrades() {
        return minionUpgrades.values().stream()
                .sorted(Comparator.comparing(MinionUpgradeDefinition::id))
                .toList();
    }

    public Optional<MinionStorageDefinition> storage(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(storages.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<MinionStorageDefinition> storages() {
        return storages.values().stream()
                .sorted(Comparator.comparing(MinionStorageDefinition::id))
                .toList();
    }

    public Optional<MinionDefinition> definition(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return Optional.empty();
        }
        String id = itemStack.getItemMeta().getPersistentDataContainer().get(minionIdKey, PersistentDataType.STRING);
        return definition(id);
    }

    public ItemStack createMinionItem(MinionDefinition definition) {
        ItemStack itemStack = new ItemStack(definition.material());
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(text.deserialize(definition.displayName()));
        meta.lore(minionItemLore(definition));
        meta.getPersistentDataContainer().set(minionIdKey, PersistentDataType.STRING, definition.id());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public ItemStack createUpgradeItem(MinionUpgradeDefinition definition) {
        ItemStack itemStack;
        if (definition.customItem()) {
            itemStack = customItems.definition(definition.customItemId())
                    .map(customItems::createItem)
                    .orElseGet(() -> new ItemStack(Material.STONE));
        } else {
            itemStack = new ItemStack(definition.material());
        }
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(text.deserialize(definition.displayName()));
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public ItemStack createStorageItem(MinionStorageDefinition definition) {
        ItemStack itemStack;
        if (definition.customItem()) {
            itemStack = customItems.definition(definition.customItemId())
                    .map(customItems::createItem)
                    .orElseGet(() -> new ItemStack(storageBlockMaterial(definition)));
        } else {
            itemStack = new ItemStack(storageBlockMaterial(definition));
        }
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(text.deserialize(definition.displayName()));
        meta.getPersistentDataContainer().set(minionStorageIdKey, PersistentDataType.STRING, definition.id());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public void addMinion(Player player, MinionDefinition definition) {
        SkyBlockProfile profile = profiles.profile(player);
        if (!hasMinionCapacity(player, profile)) {
            return;
        }
        profile.minions().add(new PlacedMinion(definition.id(), 0L, System.currentTimeMillis()));
        text.send(player, "commands.minion-added", List.of(TextService.parsed("minion_name", definition.displayName())));
    }

    public boolean placeMinion(Player player, MinionDefinition definition, Location location) {
        SkyBlockProfile profile = profiles.profile(player);
        if (!hasMinionCapacity(player, profile)) {
            return false;
        }
        if (find(location).isPresent()) {
            text.send(player, "commands.minion-location-used");
            return false;
        }
        PlacedMinion placedMinion = new PlacedMinion(definition.id(), 0L, System.currentTimeMillis());
        placedMinion.location(location);
        profile.minions().add(placedMinion);
        location.getBlock().setType(definition.material(), false);
        text.send(player, "commands.minion-placed", List.of(TextService.parsed("minion_name", definition.displayName())));
        return true;
    }

    public boolean placeStorage(Player player, MinionStorageDefinition storage, Location location) {
        if (findStorage(location).isPresent()) {
            text.send(player, "commands.minion-storage-location-used");
            return false;
        }
        AdjacentStorageTarget target = adjacentStorageTarget(location);
        MinionPlacement placement = target.available().orElse(null);
        if (placement == null) {
            text.send(player, target.blockedByAttachedStorage() ? "commands.minion-storage-already-adjacent" : "commands.minion-storage-no-minion");
            return false;
        }
        updateMinion(placement.placedMinion(), placement.definition(), System.currentTimeMillis());
        placement.placedMinion().storage(storage.id(), location);
        location.getBlock().setType(storageBlockMaterial(storage), false);
        profiles.save(placement.profile());
        text.send(player, "commands.minion-storage-attached", storagePlaceholders(placement.definition(), placement.placedMinion(), storage));
        return true;
    }

    public Optional<MinionPlacement> find(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        for (SkyBlockProfile profile : profiles.loadedProfiles()) {
            for (int slot = 0; slot < profile.minions().size(); slot++) {
                PlacedMinion placedMinion = profile.minions().get(slot);
                if (!placedMinion.matches(location)) {
                    continue;
                }
                MinionDefinition definition = definition(placedMinion.id()).orElse(null);
                if (definition == null) {
                    continue;
                }
                updateMinion(placedMinion, definition, System.currentTimeMillis());
                return Optional.of(new MinionPlacement(profile, slot, placedMinion, definition));
            }
        }
        return Optional.empty();
    }

    public Optional<MinionPlacement> findStorage(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        for (SkyBlockProfile profile : profiles.loadedProfiles()) {
            for (int slot = 0; slot < profile.minions().size(); slot++) {
                PlacedMinion placedMinion = profile.minions().get(slot);
                if (!placedMinion.storageMatches(location)) {
                    continue;
                }
                MinionDefinition definition = definition(placedMinion.id()).orElse(null);
                if (definition == null) {
                    continue;
                }
                updateMinion(placedMinion, definition, System.currentTimeMillis());
                return Optional.of(new MinionPlacement(profile, slot, placedMinion, definition));
            }
        }
        return Optional.empty();
    }

    public Optional<MinionPlacement> placement(UUID owner, int slot) {
        SkyBlockProfile profile = profiles.profile(owner);
        if (profile == null || slot < 0 || slot >= profile.minions().size()) {
            return Optional.empty();
        }
        PlacedMinion placedMinion = profile.minions().get(slot);
        MinionDefinition definition = definition(placedMinion.id()).orElse(null);
        if (definition == null) {
            return Optional.empty();
        }
        updateMinion(placedMinion, definition, System.currentTimeMillis());
        return Optional.of(new MinionPlacement(profile, slot, placedMinion, definition));
    }

    public MinionClaimResult claim(Player player, int slot) {
        SkyBlockProfile profile = profiles.profile(player);
        if (slot < 0 || slot >= profile.minions().size()) {
            return MinionClaimResult.empty();
        }
        PlacedMinion placedMinion = profile.minions().get(slot);
        MinionDefinition definition = definition(placedMinion.id()).orElse(null);
        if (definition == null) {
            return MinionClaimResult.empty();
        }
        return claim(player, new MinionPlacement(profile, slot, placedMinion, definition));
    }

    public MinionClaimResult claim(Player player, MinionPlacement placement) {
        updateMinion(placement.placedMinion(), placement.definition(), System.currentTimeMillis());
        PlacedMinion placedMinion = placement.placedMinion();
        MinionDefinition definition = placement.definition();
        long generated = placedMinion.generatedAmount();
        double soldCoins = placedMinion.soldCoins();
        if (generated <= 0L && soldCoins <= 0.0D) {
            return MinionClaimResult.empty();
        }
        placedMinion.generatedAmount(0L);
        placedMinion.soldCoins(0.0D);
        if (generated > 0L) {
            collections.addProgress(player, definition.generatedCollection(), generated);
            addGeneratedItems(player, definition, placedMinion, generated);
        }
        if (soldCoins > 0.0D) {
            economy.addPurse(player, soldCoins);
        }
        return new MinionClaimResult(generated, soldCoins);
    }

    public MinionClaimResult claimAll(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        MinionClaimResult claimed = MinionClaimResult.empty();
        for (int slot = 0; slot < profile.minions().size(); slot++) {
            claimed = claimed.add(claim(player, slot));
        }
        return claimed;
    }

    public boolean applyHeldFuel(Player player, int slot) {
        SkyBlockProfile profile = profiles.profile(player);
        if (slot < 0 || slot >= profile.minions().size()) {
            text.send(player, "commands.minion-not-found");
            return false;
        }
        PlacedMinion placedMinion = profile.minions().get(slot);
        MinionDefinition definition = definition(placedMinion.id()).orElse(null);
        if (definition == null) {
            text.send(player, "commands.minion-not-found");
            return false;
        }
        return applyHeldFuel(player, new MinionPlacement(profile, slot, placedMinion, definition));
    }

    public boolean applyHeldFuel(Player player, MinionPlacement placement) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            text.send(player, "commands.minion-fuel-missing");
            return false;
        }
        MinionFuelDefinition fuel = fuelForItem(held).orElse(null);
        if (fuel == null) {
            text.send(player, "commands.minion-fuel-invalid");
            return false;
        }
        updateMinion(placement.placedMinion(), placement.definition(), System.currentTimeMillis());
        if (!placement.placedMinion().fuelId().isBlank()) {
            text.send(player, "commands.minion-fuel-active", minionPlaceholders(placement.definition(), placement.placedMinion()));
            return false;
        }
        long expiresAt = fuel.permanent() ? 0L : System.currentTimeMillis() + fuel.durationSeconds() * 1000L;
        placement.placedMinion().fuel(fuel.id(), expiresAt);
        consumeOneMainHand(player, held);
        profiles.save(placement.profile());
        text.send(player, "commands.minion-fuel-applied", minionPlaceholders(placement.definition(), placement.placedMinion()));
        return true;
    }

    public boolean clearFuel(Player player, int slot) {
        SkyBlockProfile profile = profiles.profile(player);
        if (slot < 0 || slot >= profile.minions().size()) {
            text.send(player, "commands.minion-not-found");
            return false;
        }
        PlacedMinion placedMinion = profile.minions().get(slot);
        MinionDefinition definition = definition(placedMinion.id()).orElse(null);
        if (definition == null) {
            text.send(player, "commands.minion-not-found");
            return false;
        }
        updateMinion(placedMinion, definition, System.currentTimeMillis());
        if (placedMinion.fuelId().isBlank()) {
            text.send(player, "commands.minion-fuel-none");
            return false;
        }
        placedMinion.clearFuel();
        profiles.save(profile);
        text.send(player, "commands.minion-fuel-cleared");
        return true;
    }

    public boolean applyHeldUpgrade(Player player, int slot) {
        SkyBlockProfile profile = profiles.profile(player);
        if (slot < 0 || slot >= profile.minions().size()) {
            text.send(player, "commands.minion-not-found");
            return false;
        }
        PlacedMinion placedMinion = profile.minions().get(slot);
        MinionDefinition definition = definition(placedMinion.id()).orElse(null);
        if (definition == null) {
            text.send(player, "commands.minion-not-found");
            return false;
        }
        return applyHeldUpgrade(player, new MinionPlacement(profile, slot, placedMinion, definition));
    }

    public boolean applyHeldUpgrade(Player player, MinionPlacement placement) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            text.send(player, "commands.minion-upgrade-missing");
            return false;
        }
        MinionUpgradeDefinition upgrade = upgradeForItem(held).orElse(null);
        if (upgrade == null) {
            text.send(player, "commands.minion-upgrade-invalid");
            return false;
        }
        updateMinion(placement.placedMinion(), placement.definition(), System.currentTimeMillis());
        if (placement.placedMinion().upgradeIds().stream().anyMatch(id -> id.equalsIgnoreCase(upgrade.id()))) {
            text.send(player, "commands.minion-upgrade-duplicate", upgradePlaceholders(placement.definition(), placement.placedMinion(), upgrade));
            return false;
        }
        int upgradeSlots = maxUpgradeSlots();
        if (placement.placedMinion().upgradeIds().size() >= upgradeSlots) {
            text.send(player, "commands.minion-upgrade-full", minionPlaceholders(placement.definition(), placement.placedMinion()));
            return false;
        }
        placement.placedMinion().upgradeIds().add(upgrade.id());
        consumeOneMainHand(player, held);
        profiles.save(placement.profile());
        text.send(player, "commands.minion-upgrade-applied", upgradePlaceholders(placement.definition(), placement.placedMinion(), upgrade));
        return true;
    }

    public boolean removeUpgrade(Player player, int minionSlot, int upgradeSlot) {
        SkyBlockProfile profile = profiles.profile(player);
        if (minionSlot < 0 || minionSlot >= profile.minions().size()) {
            text.send(player, "commands.minion-not-found");
            return false;
        }
        PlacedMinion placedMinion = profile.minions().get(minionSlot);
        MinionDefinition definition = definition(placedMinion.id()).orElse(null);
        if (definition == null) {
            text.send(player, "commands.minion-not-found");
            return false;
        }
        updateMinion(placedMinion, definition, System.currentTimeMillis());
        if (upgradeSlot < 0 || upgradeSlot >= placedMinion.upgradeIds().size()) {
            text.send(player, "commands.minion-upgrade-not-found");
            return false;
        }
        String removedId = placedMinion.upgradeIds().remove(upgradeSlot);
        MinionUpgradeDefinition upgrade = minionUpgrade(removedId).orElse(null);
        if (upgrade != null) {
            giveOrDrop(player, createUpgradeItem(upgrade));
        }
        profiles.save(profile);
        text.send(player, "commands.minion-upgrade-removed", upgradePlaceholders(definition, placedMinion, upgrade));
        return true;
    }

    public boolean pickup(Player player, int slot) {
        SkyBlockProfile profile = profiles.profile(player);
        if (slot < 0 || slot >= profile.minions().size()) {
            text.send(player, "commands.minion-not-found");
            return false;
        }
        PlacedMinion placedMinion = profile.minions().get(slot);
        MinionDefinition definition = definition(placedMinion.id()).orElse(null);
        if (definition == null) {
            text.send(player, "commands.minion-not-found");
            return false;
        }
        return pickup(player, new MinionPlacement(profile, slot, placedMinion, definition));
    }

    public boolean pickup(Player player, MinionPlacement placement) {
        claim(player, placement);
        PlacedMinion placedMinion = placement.profile().minions().remove(placement.slot());
        MinionDefinition definition = placement.definition();
        pickupAttachedStorage(player, placedMinion);
        if (placedMinion.hasLocation()) {
            Block block = Bukkit.getWorld(placedMinion.worldName()) == null
                    ? null
                    : Bukkit.getWorld(placedMinion.worldName()).getBlockAt(placedMinion.x(), placedMinion.y(), placedMinion.z());
            if (block != null) {
                block.setType(Material.AIR, false);
            }
        }
        giveOrDrop(player, createMinionItem(definition));
        for (String upgradeId : placedMinion.upgradeIds()) {
            minionUpgrade(upgradeId).ifPresent(upgrade -> giveOrDrop(player, createUpgradeItem(upgrade)));
        }
        profiles.save(placement.profile());
        text.send(player, "commands.minion-picked-up", List.of(TextService.parsed("minion_name", definition.displayName())));
        return true;
    }

    public boolean pickupStorage(Player player, MinionPlacement placement) {
        updateMinion(placement.placedMinion(), placement.definition(), System.currentTimeMillis());
        MinionStorageDefinition storage = activeStorage(placement.placedMinion()).orElse(null);
        if (storage == null) {
            placement.placedMinion().clearStorage();
            profiles.save(placement.profile());
            text.send(player, "commands.minion-storage-not-found");
            return false;
        }
        pickupAttachedStorage(player, placement.placedMinion());
        profiles.save(placement.profile());
        text.send(player, "commands.minion-storage-picked-up", storagePlaceholders(placement.definition(), placement.placedMinion(), storage));
        return true;
    }

    public void tickLoadedMinions() {
        if (!configService.main().getBoolean("features.minions", true)) {
            return;
        }
        long now = System.currentTimeMillis();
        for (SkyBlockProfile profile : profiles.loadedProfiles()) {
            tickProfile(profile, now);
        }
    }

    public String locationLabel(PlacedMinion placedMinion) {
        if (!placedMinion.hasLocation()) {
            return text.rawMessage("minions.location-missing");
        }
        return text.rawMessage("minions.location-present")
                .replace("<world>", placedMinion.worldName())
                .replace("<x>", Integer.toString(placedMinion.x()))
                .replace("<y>", Integer.toString(placedMinion.y()))
                .replace("<z>", Integer.toString(placedMinion.z()));
    }

    public String collectionDisplayName(MinionDefinition definition) {
        return collections.definition(definition.generatedCollection())
                .map(CollectionDefinition::displayName)
                .orElse("<white>" + definition.generatedCollection() + "</white>");
    }

    private void tickProfile(SkyBlockProfile profile, long now) {
        for (PlacedMinion placedMinion : profile.minions()) {
            definition(placedMinion.id()).ifPresent(definition -> updateMinion(placedMinion, definition, now));
        }
    }

    private void updateMinion(PlacedMinion placedMinion, MinionDefinition definition, long now) {
        MinionFuelDefinition fuel = activeFuel(placedMinion, now).orElse(null);
        long intervalMillis = intervalMillis(definition, fuel, placedMinion);
        long elapsed = now - placedMinion.lastActionMillis();
        long cycles = elapsed / intervalMillis;
        if (cycles <= 0L) {
            return;
        }
        long capacity = Math.max(0L, effectiveStorage(definition, placedMinion) - placedMinion.generatedAmount());
        long produced = generatedAmount(definition, placedMinion, cycles);
        long stored = Math.min(capacity, produced);
        long overflow = produced - stored;
        if (stored > 0L) {
            placedMinion.generatedAmount(placedMinion.generatedAmount() + stored);
        }
        if (overflow > 0L) {
            sellOverflow(placedMinion, definition, overflow);
        }
        placedMinion.lastActionMillis(placedMinion.lastActionMillis() + cycles * intervalMillis);
    }

    private long intervalMillis(MinionDefinition definition, MinionFuelDefinition fuel, PlacedMinion placedMinion) {
        double multiplier = fuel == null ? 1.0D : Math.max(1.0D, fuel.speedMultiplier());
        multiplier *= upgradeSpeedMultiplier(placedMinion);
        return Math.max(1L, (long) Math.floor(Math.max(1L, definition.intervalTicks()) * MILLIS_PER_TICK / multiplier));
    }

    private Optional<MinionFuelDefinition> activeFuel(PlacedMinion placedMinion, long now) {
        if (placedMinion.fuelId().isBlank()) {
            return Optional.empty();
        }
        MinionFuelDefinition fuel = fuel(placedMinion.fuelId()).orElse(null);
        if (fuel == null) {
            placedMinion.clearFuel();
            return Optional.empty();
        }
        long expiresAt = placedMinion.fuelExpiresAtMillis();
        if (expiresAt > 0L && expiresAt <= now) {
            placedMinion.clearFuel();
            return Optional.empty();
        }
        return Optional.of(fuel);
    }

    private long generatedAmount(MinionDefinition definition, PlacedMinion placedMinion, long cycles) {
        double amount = cycles * (double) Math.max(1L, definition.generatedAmount()) * minionOutputMultiplier() * upgradeOutputMultiplier(placedMinion);
        return Math.max(0L, (long) Math.floor(amount));
    }

    private long effectiveStorage(MinionDefinition definition, PlacedMinion placedMinion) {
        long upgradeBonus = activeUpgrades(placedMinion).stream()
                .mapToLong(MinionUpgradeDefinition::storageBonus)
                .sum();
        return Math.max(1L, definition.storageSize() + upgradeBonus + activeStorageBonus(placedMinion));
    }

    private double upgradeSpeedMultiplier(PlacedMinion placedMinion) {
        return activeUpgrades(placedMinion).stream()
                .mapToDouble(MinionUpgradeDefinition::speedMultiplier)
                .reduce(1.0D, (left, right) -> left * Math.max(1.0D, right));
    }

    private double upgradeOutputMultiplier(PlacedMinion placedMinion) {
        return activeUpgrades(placedMinion).stream()
                .mapToDouble(MinionUpgradeDefinition::outputMultiplier)
                .reduce(1.0D, (left, right) -> left * Math.max(1.0D, right));
    }

    private List<MinionUpgradeDefinition> activeUpgrades(PlacedMinion placedMinion) {
        if (placedMinion == null) {
            return List.of();
        }
        return placedMinion.upgradeIds().stream()
                .map(this::minionUpgrade)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<ActiveCompaction> activeCompaction(MinionDefinition definition, PlacedMinion placedMinion) {
        String collectionId = definition.generatedCollection().toUpperCase(Locale.ROOT);
        return activeUpgrades(placedMinion).stream()
                .map(upgrade -> new ActiveCompaction(upgrade, upgrade.compactions().get(collectionId)))
                .filter(active -> active.compaction() != null && compactionOutputAvailable(active.compaction()))
                .max(Comparator.comparingLong(active -> active.compaction().inputAmount()));
    }

    private Optional<MinionUpgradeDefinition> activeSeller(PlacedMinion placedMinion) {
        return activeUpgrades(placedMinion).stream()
                .filter(upgrade -> upgrade.sellPercentage() > 0.0D)
                .max(Comparator.comparingDouble(MinionUpgradeDefinition::sellPercentage));
    }

    private Optional<MinionStorageDefinition> activeStorage(PlacedMinion placedMinion) {
        if (placedMinion == null || !placedMinion.hasStorage()) {
            return Optional.empty();
        }
        return storage(placedMinion.storageId());
    }

    private long activeStorageBonus(PlacedMinion placedMinion) {
        return activeStorage(placedMinion)
                .map(MinionStorageDefinition::storageBonus)
                .orElse(0L);
    }

    private void sellOverflow(PlacedMinion placedMinion, MinionDefinition definition, long amount) {
        MinionUpgradeDefinition seller = activeSeller(placedMinion).orElse(null);
        if (seller == null) {
            return;
        }
        double price = sellPrice(definition.generatedCollection());
        if (price <= 0.0D) {
            return;
        }
        placedMinion.addSoldCoins(amount * price * seller.sellPercentage());
    }

    private double sellPrice(String collectionId) {
        if (collectionId == null || collectionId.isBlank()) {
            return 0.0D;
        }
        return Math.max(0.0D, configService.minions().getDouble("settings.sell-prices." + collectionId.toUpperCase(Locale.ROOT), 0.0D));
    }

    private boolean compactionOutputAvailable(MinionCompactionDefinition compaction) {
        if (compaction.customItem()) {
            return customItems.definition(compaction.outputCustomItemId()).isPresent();
        }
        return compaction.outputMaterial() != null && !compaction.outputMaterial().isAir();
    }

    private int maxUpgradeSlots() {
        return Math.max(0, configService.minions().getInt("settings.upgrade-slots", 2));
    }

    private double minionOutputMultiplier() {
        if (mayors == null) {
            return 1.0D;
        }
        double multiplier = mayors.modifier("minion_output_multiplier");
        return multiplier <= 0.0D ? 1.0D : multiplier;
    }

    private boolean hasMinionCapacity(Player player, SkyBlockProfile profile) {
        int limit = Math.max(1, configService.main().getInt("minions.max-per-profile", 5) + upgrades.capacityBonus(profile, "minion_slots"));
        if (profile.minions().size() < limit) {
            return true;
        }
        text.send(player, "commands.minion-limit", List.of(TextService.raw("limit", Integer.toString(limit))));
        return false;
    }

    private List<Component> minionItemLore(MinionDefinition definition) {
        List<Component> lore = new ArrayList<>();
        List<TextService.TextPlaceholder> placeholders = minionPlaceholders(definition, 0L);
        for (String line : configService.messages().getStringList("minions.item-lore")) {
            lore.add(text.deserialize(line, placeholders));
        }
        lore.add(Component.empty());
        lore.add(text.message("minions.item-rarity-line"));
        return lore;
    }

    public List<TextService.TextPlaceholder> minionPlaceholders(MinionDefinition definition, long generated) {
        return minionPlaceholders(definition, generated, null);
    }

    public List<TextService.TextPlaceholder> minionPlaceholders(MinionDefinition definition, PlacedMinion placedMinion) {
        return minionPlaceholders(definition, placedMinion.generatedAmount(), placedMinion);
    }

    private List<TextService.TextPlaceholder> minionPlaceholders(MinionDefinition definition, long generated, PlacedMinion placedMinion) {
        long now = System.currentTimeMillis();
        MinionFuelDefinition fuel = placedMinion == null ? null : activeFuel(placedMinion, now).orElse(null);
        double fuelSpeedMultiplier = fuel == null ? 1.0D : fuel.speedMultiplier();
        double upgradeSpeedMultiplier = placedMinion == null ? 1.0D : upgradeSpeedMultiplier(placedMinion);
        double outputMultiplier = placedMinion == null ? 1.0D : upgradeOutputMultiplier(placedMinion);
        long storage = placedMinion == null ? definition.storageSize() : effectiveStorage(definition, placedMinion);
        long upgradeStorage = placedMinion == null ? 0L : activeUpgrades(placedMinion).stream()
                .mapToLong(MinionUpgradeDefinition::storageBonus)
                .sum();
        MinionStorageDefinition externalStorage = placedMinion == null ? null : activeStorage(placedMinion).orElse(null);
        long externalStorageBonus = externalStorage == null ? 0L : externalStorage.storageBonus();
        ActiveCompaction compaction = placedMinion == null ? null : activeCompaction(definition, placedMinion).orElse(null);
        MinionUpgradeDefinition seller = placedMinion == null ? null : activeSeller(placedMinion).orElse(null);
        return List.of(
                TextService.parsed("minion_name", definition.displayName()),
                TextService.parsed("collection", collectionDisplayName(definition)),
                TextService.raw("generated", text.formatNumber(generated)),
                TextService.raw("storage", text.formatNumber(storage)),
                TextService.raw("interval_seconds", text.formatNumber(intervalMillis(definition, fuel, placedMinion) / 1000.0D)),
                TextService.parsed("fuel", fuel == null ? text.rawMessage("minions.fuel-empty") : fuel.displayName()),
                TextService.raw("fuel_speed", text.formatNumber((fuelSpeedMultiplier - 1.0D) * 100.0D)),
                TextService.raw("fuel_remaining", fuelRemaining(placedMinion, fuel, now)),
                TextService.raw("upgrade_slots", text.formatNumber(maxUpgradeSlots())),
                TextService.parsed("upgrades", upgradeList(placedMinion)),
                TextService.raw("upgrade_speed", text.formatNumber((upgradeSpeedMultiplier - 1.0D) * 100.0D)),
                TextService.raw("upgrade_output", text.formatNumber((outputMultiplier - 1.0D) * 100.0D)),
                TextService.raw("upgrade_storage", text.formatNumber(Math.max(0L, upgradeStorage))),
                TextService.parsed("external_storage", externalStorage == null ? text.rawMessage("minions.storage-empty") : externalStorage.displayName()),
                TextService.raw("external_storage_bonus", text.formatNumber(externalStorageBonus)),
                TextService.parsed("compaction", compactionLabel(compaction)),
                TextService.parsed("hopper", seller == null ? text.rawMessage("minions.hopper-none") : seller.displayName()),
                TextService.raw("hopper_percentage", text.formatNumber((seller == null ? 0.0D : seller.sellPercentage()) * 100.0D)),
                TextService.raw("hopper_coins", text.formatNumber(placedMinion == null ? 0.0D : placedMinion.soldCoins()))
        );
    }

    private Optional<MinionFuelDefinition> fuelForItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return Optional.empty();
        }
        for (MinionFuelDefinition fuel : fuels()) {
            if (fuelMatches(fuel, itemStack)) {
                return Optional.of(fuel);
            }
        }
        return Optional.empty();
    }

    private boolean fuelMatches(MinionFuelDefinition fuel, ItemStack itemStack) {
        if (fuel.customItem()) {
            return customItems.definition(itemStack)
                    .map(CustomItemDefinition::id)
                    .filter(id -> id.equalsIgnoreCase(fuel.customItemId()))
                    .isPresent();
        }
        return itemStack.getType() == fuel.material() && customItems.definition(itemStack).isEmpty();
    }

    private Optional<MinionUpgradeDefinition> upgradeForItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return Optional.empty();
        }
        for (MinionUpgradeDefinition upgrade : minionUpgrades()) {
            if (upgradeMatches(upgrade, itemStack)) {
                return Optional.of(upgrade);
            }
        }
        return Optional.empty();
    }

    public Optional<MinionStorageDefinition> storageForItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return Optional.empty();
        }
        if (itemStack.hasItemMeta()) {
            String id = itemStack.getItemMeta().getPersistentDataContainer().get(minionStorageIdKey, PersistentDataType.STRING);
            if (id != null && !id.isBlank()) {
                return storage(id);
            }
        }
        for (MinionStorageDefinition storage : storages()) {
            if (storage.customItem() && storageMatches(storage, itemStack)) {
                return Optional.of(storage);
            }
        }
        return Optional.empty();
    }

    private boolean storageMatches(MinionStorageDefinition storage, ItemStack itemStack) {
        if (storage.customItem()) {
            return customItems.definition(itemStack)
                    .map(CustomItemDefinition::id)
                    .filter(id -> id.equalsIgnoreCase(storage.customItemId()))
                    .isPresent();
        }
        return itemStack.getType() == storageBlockMaterial(storage) && customItems.definition(itemStack).isEmpty();
    }

    private boolean upgradeMatches(MinionUpgradeDefinition upgrade, ItemStack itemStack) {
        if (upgrade.customItem()) {
            return customItems.definition(itemStack)
                    .map(CustomItemDefinition::id)
                    .filter(id -> id.equalsIgnoreCase(upgrade.customItemId()))
                    .isPresent();
        }
        return itemStack.getType() == upgrade.material() && customItems.definition(itemStack).isEmpty();
    }

    private void consumeOneMainHand(Player player, ItemStack held) {
        int amount = held.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            return;
        }
        held.setAmount(amount - 1);
    }

    private String fuelRemaining(PlacedMinion placedMinion, MinionFuelDefinition fuel, long now) {
        if (placedMinion == null || fuel == null) {
            return text.rawMessage("minions.fuel-remaining-none");
        }
        if (fuel.permanent() || placedMinion.fuelExpiresAtMillis() <= 0L) {
            return text.rawMessage("minions.fuel-remaining-permanent");
        }
        long seconds = Math.max(0L, (placedMinion.fuelExpiresAtMillis() - now) / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainingSeconds = seconds % 60L;
        return text.rawMessage("minions.fuel-remaining-format")
                .replace("<hours>", text.formatNumber(hours))
                .replace("<minutes>", text.formatNumber(minutes))
                .replace("<seconds>", text.formatNumber(remainingSeconds));
    }

    private String upgradeList(PlacedMinion placedMinion) {
        if (placedMinion == null || placedMinion.upgradeIds().isEmpty()) {
            return text.rawMessage("minions.upgrades-empty");
        }
        List<String> names = new ArrayList<>();
        for (String upgradeId : placedMinion.upgradeIds()) {
            names.add(minionUpgrade(upgradeId)
                    .map(MinionUpgradeDefinition::displayName)
                    .orElse("<dark_gray>" + upgradeId + "</dark_gray>"));
        }
        return String.join(text.rawMessage("minions.upgrades-separator"), names);
    }

    private List<TextService.TextPlaceholder> storagePlaceholders(MinionDefinition definition, PlacedMinion placedMinion, MinionStorageDefinition storage) {
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(minionPlaceholders(definition, placedMinion));
        placeholders.add(TextService.parsed("storage_name", storage == null ? text.rawMessage("minions.storage-empty") : storage.displayName()));
        placeholders.add(TextService.raw("storage_bonus", text.formatNumber(storage == null ? 0L : storage.storageBonus())));
        return placeholders;
    }

    private AdjacentStorageTarget adjacentStorageTarget(Location storageLocation) {
        if (storageLocation == null || storageLocation.getWorld() == null) {
            return new AdjacentStorageTarget(Optional.empty(), false);
        }
        boolean blockedByAttachedStorage = false;
        for (BlockFace face : STORAGE_FACES) {
            Optional<MinionPlacement> placement = find(storageLocation.getBlock().getRelative(face).getLocation());
            if (placement.isEmpty()) {
                continue;
            }
            if (!placement.get().placedMinion().hasStorage()) {
                return new AdjacentStorageTarget(placement, false);
            }
            blockedByAttachedStorage = true;
        }
        return new AdjacentStorageTarget(Optional.empty(), blockedByAttachedStorage);
    }

    private void pickupAttachedStorage(Player player, PlacedMinion placedMinion) {
        if (!placedMinion.hasStorage()) {
            return;
        }
        MinionStorageDefinition storage = activeStorage(placedMinion).orElse(null);
        Block block = Bukkit.getWorld(placedMinion.storageWorldName()) == null
                ? null
                : Bukkit.getWorld(placedMinion.storageWorldName()).getBlockAt(placedMinion.storageX(), placedMinion.storageY(), placedMinion.storageZ());
        if (block != null) {
            block.setType(Material.AIR, false);
        }
        placedMinion.clearStorage();
        if (storage != null) {
            giveOrDrop(player, createStorageItem(storage));
        }
    }

    private Material storageBlockMaterial(MinionStorageDefinition storage) {
        if (storage == null || storage.material() == null || storage.material().isAir() || !storage.material().isBlock()) {
            return Material.CHEST;
        }
        return storage.material();
    }

    private String compactionLabel(ActiveCompaction compaction) {
        if (compaction == null) {
            return text.rawMessage("minions.compaction-none");
        }
        return text.rawMessage("minions.compaction-format")
                .replace("<upgrade>", compaction.upgrade().displayName())
                .replace("<output>", compactionOutputName(compaction.compaction()));
    }

    private String compactionOutputName(MinionCompactionDefinition compaction) {
        if (compaction.customItem()) {
            return customItems.definition(compaction.outputCustomItemId())
                    .map(CustomItemDefinition::displayName)
                    .orElse(compaction.outputCustomItemId());
        }
        return readableMaterialName(compaction.outputMaterial());
    }

    private String readableMaterialName(Material material) {
        if (material == null || material.isAir()) {
            return "";
        }
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            words.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return String.join(" ", words);
    }

    private List<TextService.TextPlaceholder> upgradePlaceholders(MinionDefinition definition, PlacedMinion placedMinion, MinionUpgradeDefinition upgrade) {
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(minionPlaceholders(definition, placedMinion));
        placeholders.add(TextService.parsed("upgrade", upgrade == null ? text.rawMessage("minions.upgrade-unknown") : upgrade.displayName()));
        return placeholders;
    }

    private record ActiveCompaction(MinionUpgradeDefinition upgrade, MinionCompactionDefinition compaction) {
    }

    private record AdjacentStorageTarget(Optional<MinionPlacement> available, boolean blockedByAttachedStorage) {
    }

    private void addGeneratedItems(Player player, MinionDefinition definition, PlacedMinion placedMinion, long amount) {
        ActiveCompaction compaction = activeCompaction(definition, placedMinion).orElse(null);
        if (compaction != null && compaction.compaction().inputAmount() > 0L) {
            long compacted = amount / compaction.compaction().inputAmount();
            long remainder = amount % compaction.compaction().inputAmount();
            if (compacted > 0L) {
                addCompactedItems(player, compaction.compaction(), compacted * compaction.compaction().outputAmount());
            }
            if (remainder <= 0L) {
                return;
            }
            amount = remainder;
        }
        Material material = collections.definition(definition.generatedCollection())
                .map(CollectionDefinition::material)
                .orElse(definition.material());
        addMaterialItems(player, material, amount);
    }

    private void addCompactedItems(Player player, MinionCompactionDefinition compaction, long amount) {
        if (compaction.customItem()) {
            CustomItemDefinition definition = customItems.definition(compaction.outputCustomItemId()).orElse(null);
            if (definition == null) {
                return;
            }
            addCustomItems(player, definition, amount);
            return;
        }
        addMaterialItems(player, compaction.outputMaterial(), amount);
    }

    private void addMaterialItems(Player player, Material material, long amount) {
        if (material == null || material.isAir()) {
            return;
        }
        long remaining = amount;
        int stackSize = Math.max(1, material.getMaxStackSize());
        while (remaining > 0L) {
            int next = (int) Math.min(stackSize, remaining);
            giveOrDrop(player, new ItemStack(material, next));
            remaining -= next;
        }
    }

    private void addCustomItems(Player player, CustomItemDefinition definition, long amount) {
        ItemStack template = customItems.createItem(definition);
        long remaining = amount;
        int stackSize = Math.max(1, template.getMaxStackSize());
        while (remaining > 0L) {
            int next = (int) Math.min(stackSize, remaining);
            ItemStack itemStack = template.clone();
            itemStack.setAmount(next);
            giveOrDrop(player, itemStack);
            remaining -= next;
        }
    }

    private void giveOrDrop(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
