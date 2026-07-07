package io.github.openskyblock.service;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MinionService {
    private static final long MILLIS_PER_TICK = 50L;

    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CollectionService collections;
    private final UpgradeService upgrades;
    private final CustomItemService customItems;
    private final NamespacedKey minionIdKey;
    private final Map<String, MinionDefinition> definitions = new HashMap<>();
    private final Map<String, MinionFuelDefinition> fuels = new HashMap<>();
    private final Map<String, MinionUpgradeDefinition> minionUpgrades = new HashMap<>();
    private MayorService mayors;

    public MinionService(JavaPlugin plugin, ConfigService configService, TextService text, ProfileManager profiles, CollectionService collections, UpgradeService upgrades, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.collections = collections;
        this.upgrades = upgrades;
        this.customItems = customItems;
        this.minionIdKey = new NamespacedKey(plugin, "minion_id");
    }

    public void mayorService(MayorService mayors) {
        this.mayors = mayors;
    }

    public void reload() {
        definitions.clear();
        fuels.clear();
        minionUpgrades.clear();
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
                    Math.max(0L, sectionUpgrade.getLong("storage-bonus", 0L))
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

    public long claim(Player player, int slot) {
        SkyBlockProfile profile = profiles.profile(player);
        if (slot < 0 || slot >= profile.minions().size()) {
            return 0L;
        }
        PlacedMinion placedMinion = profile.minions().get(slot);
        MinionDefinition definition = definition(placedMinion.id()).orElse(null);
        if (definition == null) {
            return 0L;
        }
        return claim(player, new MinionPlacement(profile, slot, placedMinion, definition));
    }

    public long claim(Player player, MinionPlacement placement) {
        updateMinion(placement.placedMinion(), placement.definition(), System.currentTimeMillis());
        if (placement.placedMinion().generatedAmount() <= 0L) {
            return 0L;
        }
        PlacedMinion placedMinion = placement.placedMinion();
        MinionDefinition definition = placement.definition();
        long generated = placedMinion.generatedAmount();
        placedMinion.generatedAmount(0L);
        collections.addProgress(player, definition.generatedCollection(), generated);
        addGeneratedItems(player, definition, generated);
        return generated;
    }

    public long claimAll(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        long claimed = 0L;
        for (int slot = 0; slot < profile.minions().size(); slot++) {
            claimed += claim(player, slot);
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
        text.send(player, "commands.minion-picked-up", List.of(TextService.parsed("minion_name", definition.displayName())));
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
        long generated = Math.min(capacity, generatedAmount(definition, placedMinion, cycles));
        if (generated > 0L) {
            placedMinion.generatedAmount(placedMinion.generatedAmount() + generated);
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
        long bonus = activeUpgrades(placedMinion).stream()
                .mapToLong(MinionUpgradeDefinition::storageBonus)
                .sum();
        return Math.max(1L, definition.storageSize() + bonus);
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
                TextService.raw("upgrade_storage", text.formatNumber(Math.max(0L, storage - definition.storageSize())))
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

    private List<TextService.TextPlaceholder> upgradePlaceholders(MinionDefinition definition, PlacedMinion placedMinion, MinionUpgradeDefinition upgrade) {
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(minionPlaceholders(definition, placedMinion));
        placeholders.add(TextService.parsed("upgrade", upgrade == null ? text.rawMessage("minions.upgrade-unknown") : upgrade.displayName()));
        return placeholders;
    }

    private void addGeneratedItems(Player player, MinionDefinition definition, long amount) {
        Material material = collections.definition(definition.generatedCollection())
                .map(CollectionDefinition::material)
                .orElse(definition.material());
        long remaining = amount;
        int stackSize = Math.max(1, material.getMaxStackSize());
        while (remaining > 0L) {
            int next = (int) Math.min(stackSize, remaining);
            giveOrDrop(player, new ItemStack(material, next));
            remaining -= next;
        }
    }

    private void giveOrDrop(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
