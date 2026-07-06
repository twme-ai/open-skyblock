package io.github.openskyblock.profile;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.service.SkillType;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProfileManager {
    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final Map<UUID, SkyBlockProfile> profiles = new HashMap<>();
    private File profileFile;
    private YamlConfiguration profileData;

    public ProfileManager(JavaPlugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    public void loadAll() {
        this.profileFile = new File(plugin.getDataFolder(), "profiles.yml");
        if (!profileFile.exists()) {
            try {
                File parent = profileFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                profileFile.createNewFile();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to create profiles.yml", exception);
            }
        }
        this.profileData = YamlConfiguration.loadConfiguration(profileFile);
        ConfigurationSection section = profileData.getConfigurationSection("profiles");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uniqueId = UUID.fromString(key);
                profiles.put(uniqueId, loadProfile(uniqueId, section.getConfigurationSection(key)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping invalid profile UUID in profiles.yml: " + key);
            }
        }
    }

    public SkyBlockProfile profile(Player player) {
        SkyBlockProfile profile = profiles.computeIfAbsent(player.getUniqueId(), uniqueId -> newProfile(uniqueId, player.getName()));
        profile.playerName(player.getName());
        return profile;
    }

    public SkyBlockProfile profile(UUID uniqueId) {
        return profiles.get(uniqueId);
    }

    public Collection<SkyBlockProfile> loadedProfiles() {
        return profiles.values();
    }

    public SkyBlockProfile profileByIslandWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        for (SkyBlockProfile profile : profiles.values()) {
            if (worldName.equals(profile.islandWorldName())) {
                return profile;
            }
        }
        return null;
    }

    public void save(Player player) {
        SkyBlockProfile profile = profiles.get(player.getUniqueId());
        if (profile != null) {
            writeProfile(profile);
            saveFile();
        }
    }

    public void saveAll() {
        for (SkyBlockProfile profile : profiles.values()) {
            writeProfile(profile);
        }
        saveFile();
    }

    private SkyBlockProfile newProfile(UUID uniqueId, String playerName) {
        double purse = configService.main().getDouble("settings.default-purse", 0.0D);
        double bank = configService.main().getDouble("settings.default-bank", 0.0D);
        return new SkyBlockProfile(uniqueId, playerName, purse, bank);
    }

    private SkyBlockProfile loadProfile(UUID uniqueId, ConfigurationSection section) {
        if (section == null) {
            return newProfile(uniqueId, "Unknown");
        }
        SkyBlockProfile profile = new SkyBlockProfile(
                uniqueId,
                section.getString("name", "Unknown"),
                section.getDouble("purse", 0.0D),
                section.getDouble("bank", 0.0D)
        );
        profile.islandWorldName(section.getString("island-world", null));
        ConfigurationSection skills = section.getConfigurationSection("skills");
        if (skills != null) {
            for (String key : skills.getKeys(false)) {
                SkillType.fromKey(key).ifPresent(type -> profile.setSkillXp(type, skills.getDouble(key + ".xp", 0.0D)));
            }
        }
        ConfigurationSection collections = section.getConfigurationSection("collections");
        if (collections != null) {
            for (String key : collections.getKeys(false)) {
                profile.setCollectionAmount(key.toUpperCase(), collections.getLong(key, 0L));
            }
        }
        profile.shopPurchaseDay(section.getString("shop-purchases.day", null));
        ConfigurationSection shopPurchases = section.getConfigurationSection("shop-purchases.items");
        if (shopPurchases != null) {
            for (String key : shopPurchases.getKeys(false)) {
                profile.setDailyShopPurchases(key.toUpperCase(), shopPurchases.getInt(key, 0));
            }
        }
        for (String itemId : section.getStringList("accessory-bag")) {
            if (itemId != null && !itemId.isBlank()) {
                profile.addAccessory(itemId);
            }
        }
        ConfigurationSection tuning = section.getConfigurationSection("tuning");
        if (tuning != null) {
            for (String key : tuning.getKeys(false)) {
                profile.setTuning(key.toLowerCase(), tuning.getInt(key, 0));
            }
        }
        ConfigurationSection equipment = section.getConfigurationSection("equipment");
        if (equipment != null) {
            for (String key : equipment.getKeys(false)) {
                ItemStack itemStack = equipment.getItemStack(key);
                if (itemStack != null) {
                    profile.equipment().put(key.toUpperCase(), itemStack);
                }
            }
        }
        profile.activePetInstanceId(section.getString("pets.active", null));
        ConfigurationSection pets = section.getConfigurationSection("pets.owned");
        if (pets != null) {
            for (String key : pets.getKeys(false)) {
                ConfigurationSection pet = pets.getConfigurationSection(key);
                if (pet != null) {
                    profile.pets().add(new OwnedPet(
                            pet.getString("instance-id", null),
                            pet.getString("id", ""),
                            pet.getDouble("xp", 0.0D)
                    ));
                }
            }
        }
        ConfigurationSection minions = section.getConfigurationSection("minions");
        if (minions != null) {
            for (String key : minions.getKeys(false)) {
                ConfigurationSection minion = minions.getConfigurationSection(key);
                if (minion != null) {
                    profile.minions().add(new PlacedMinion(
                            minion.getString("id", ""),
                            minion.getLong("generated", 0L),
                            minion.getLong("last-action-millis", System.currentTimeMillis()),
                            minion.getString("world", null),
                            minion.getInt("x", 0),
                            minion.getInt("y", 0),
                            minion.getInt("z", 0)
                    ));
                }
            }
        }
        return profile;
    }

    private void writeProfile(SkyBlockProfile profile) {
        String base = "profiles." + profile.uniqueId();
        profileData.set(base + ".name", profile.playerName());
        profileData.set(base + ".purse", profile.purse());
        profileData.set(base + ".bank", profile.bank());
        profileData.set(base + ".island-world", profile.islandWorldName());
        for (Map.Entry<SkillType, Double> entry : profile.skillXp().entrySet()) {
            profileData.set(base + ".skills." + entry.getKey().key() + ".xp", entry.getValue());
        }
        for (Map.Entry<String, Long> entry : profile.collections().entrySet()) {
            profileData.set(base + ".collections." + entry.getKey(), entry.getValue());
        }
        profileData.set(base + ".shop-purchases.day", profile.shopPurchaseDay());
        profileData.set(base + ".shop-purchases.items", null);
        for (Map.Entry<String, Integer> entry : profile.dailyShopPurchases().entrySet()) {
            profileData.set(base + ".shop-purchases.items." + entry.getKey(), entry.getValue());
        }
        profileData.set(base + ".accessory-bag", profile.accessoryBag());
        profileData.set(base + ".tuning", null);
        for (Map.Entry<String, Integer> entry : profile.tuning().entrySet()) {
            profileData.set(base + ".tuning." + entry.getKey(), entry.getValue());
        }
        profileData.set(base + ".equipment", null);
        for (Map.Entry<String, ItemStack> entry : profile.equipment().entrySet()) {
            profileData.set(base + ".equipment." + entry.getKey(), entry.getValue());
        }
        profileData.set(base + ".pets.active", profile.activePetInstanceId());
        profileData.set(base + ".pets.owned", null);
        for (int index = 0; index < profile.pets().size(); index++) {
            OwnedPet pet = profile.pets().get(index);
            String petBase = base + ".pets.owned." + index;
            profileData.set(petBase + ".instance-id", pet.instanceId());
            profileData.set(petBase + ".id", pet.petId());
            profileData.set(petBase + ".xp", pet.xp());
        }
        profileData.set(base + ".minions", null);
        for (int index = 0; index < profile.minions().size(); index++) {
            PlacedMinion minion = profile.minions().get(index);
            String minionBase = base + ".minions." + index;
            profileData.set(minionBase + ".id", minion.id());
            profileData.set(minionBase + ".generated", minion.generatedAmount());
            profileData.set(minionBase + ".last-action-millis", minion.lastActionMillis());
            profileData.set(minionBase + ".world", minion.worldName());
            profileData.set(minionBase + ".x", minion.x());
            profileData.set(minionBase + ".y", minion.y());
            profileData.set(minionBase + ".z", minion.z());
        }
    }

    private void saveFile() {
        try {
            profileData.save(profileFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Unable to save profiles.yml: " + exception.getMessage());
        }
    }
}
