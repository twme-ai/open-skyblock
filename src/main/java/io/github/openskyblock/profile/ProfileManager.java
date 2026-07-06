package io.github.openskyblock.profile;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.service.SkillType;
import io.github.openskyblock.wardrobe.WardrobeSet;
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
        ConfigurationSection wardrobe = section.getConfigurationSection("wardrobe");
        if (wardrobe != null) {
            for (String key : wardrobe.getKeys(false)) {
                ConfigurationSection slotSection = wardrobe.getConfigurationSection(key);
                if (slotSection == null) {
                    continue;
                }
                try {
                    int slot = Integer.parseInt(key);
                    WardrobeSet set = WardrobeSet.of(
                            slotSection.getItemStack("helmet"),
                            slotSection.getItemStack("chestplate"),
                            slotSection.getItemStack("leggings"),
                            slotSection.getItemStack("boots")
                    );
                    if (!set.empty()) {
                        profile.wardrobe().put(slot, set);
                    }
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Skipping invalid wardrobe slot in profiles.yml: " + key);
                }
            }
        }
        ConfigurationSection storagePages = section.getConfigurationSection("storage.pages");
        if (storagePages != null) {
            for (String pageKey : storagePages.getKeys(false)) {
                ConfigurationSection pageSection = storagePages.getConfigurationSection(pageKey);
                if (pageSection == null) {
                    continue;
                }
                try {
                    int page = Integer.parseInt(pageKey);
                    ItemStack[] contents = new ItemStack[54];
                    for (String slotKey : pageSection.getKeys(false)) {
                        int slot = Integer.parseInt(slotKey);
                        if (slot < 0 || slot >= contents.length) {
                            continue;
                        }
                        ItemStack itemStack = pageSection.getItemStack(slotKey);
                        if (itemStack != null && !itemStack.getType().isAir()) {
                            contents[slot] = itemStack;
                        }
                    }
                    profile.storagePages().put(page, contents);
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Skipping invalid storage page or slot in profiles.yml: " + pageKey);
                }
            }
        }
        ConfigurationSection backpacks = section.getConfigurationSection("backpacks");
        if (backpacks != null) {
            for (String slotKey : backpacks.getKeys(false)) {
                ConfigurationSection backpackSection = backpacks.getConfigurationSection(slotKey);
                if (backpackSection == null) {
                    continue;
                }
                try {
                    int backpackSlot = Integer.parseInt(slotKey);
                    String id = backpackSection.getString("id", "");
                    if (id.isBlank()) {
                        continue;
                    }
                    ItemStack[] contents = new ItemStack[54];
                    ConfigurationSection items = backpackSection.getConfigurationSection("items");
                    if (items != null) {
                        for (String itemSlotKey : items.getKeys(false)) {
                            int itemSlot = Integer.parseInt(itemSlotKey);
                            if (itemSlot < 0 || itemSlot >= contents.length) {
                                continue;
                            }
                            ItemStack itemStack = items.getItemStack(itemSlotKey);
                            if (itemStack != null && !itemStack.getType().isAir()) {
                                contents[itemSlot] = itemStack;
                            }
                        }
                    }
                    profile.backpacks().put(backpackSlot, new OwnedBackpack(backpackSlot, id.toUpperCase(), contents));
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Skipping invalid backpack slot in profiles.yml: " + slotKey);
                }
            }
        }
        ConfigurationSection sacks = section.getConfigurationSection("sacks");
        if (sacks != null) {
            for (String sackId : sacks.getKeys(false)) {
                ConfigurationSection sackSection = sacks.getConfigurationSection(sackId);
                if (sackSection == null) {
                    continue;
                }
                Map<String, Long> storage = profile.sacks().computeIfAbsent(sackId.toUpperCase(), ignored -> new HashMap<>());
                for (String itemId : sackSection.getKeys(false)) {
                    long amount = sackSection.getLong(itemId, 0L);
                    if (amount > 0L) {
                        storage.put(itemId.toUpperCase(), amount);
                    }
                }
                if (storage.isEmpty()) {
                    profile.sacks().remove(sackId.toUpperCase());
                }
            }
        }
        profile.selectedQuiverItem(section.getString("quiver.selected", null));
        ConfigurationSection quiver = section.getConfigurationSection("quiver.items");
        if (quiver != null) {
            for (String itemId : quiver.getKeys(false)) {
                long amount = quiver.getLong(itemId, 0L);
                if (amount > 0L) {
                    profile.quiver().put(itemId.toUpperCase(), amount);
                }
            }
        }
        ConfigurationSection potions = section.getConfigurationSection("potions.active");
        if (potions != null) {
            for (String effectId : potions.getKeys(false)) {
                long seconds = potions.getLong(effectId, 0L);
                if (seconds > 0L) {
                    profile.potionEffects().put(effectId.toUpperCase(), seconds);
                }
            }
        }
        ConfigurationSection cakeBuffs = section.getConfigurationSection("cakes.active");
        if (cakeBuffs != null) {
            for (String cakeId : cakeBuffs.getKeys(false)) {
                long expiresAt = cakeBuffs.getLong(cakeId, 0L);
                if (expiresAt > System.currentTimeMillis()) {
                    profile.cakeBuffs().put(cakeId.toUpperCase(), expiresAt);
                }
            }
        }
        ConfigurationSection placedCakes = section.getConfigurationSection("cakes.placed");
        if (placedCakes != null) {
            for (String key : placedCakes.getKeys(false)) {
                ConfigurationSection cake = placedCakes.getConfigurationSection(key);
                if (cake != null) {
                    profile.placedCakes().add(new PlacedCake(
                            cake.getString("id", ""),
                            cake.getString("world", null),
                            cake.getInt("x", 0),
                            cake.getInt("y", 0),
                            cake.getInt("z", 0)
                    ));
                }
            }
        }
        ConfigurationSection upgrades = section.getConfigurationSection("upgrades");
        if (upgrades != null) {
            for (String upgradeId : upgrades.getKeys(false)) {
                int level = upgrades.getInt(upgradeId, 0);
                if (level > 0) {
                    profile.upgrades().put(upgradeId.toUpperCase(), level);
                }
            }
        }
        ConfigurationSection bestiaryKills = section.getConfigurationSection("bestiary.kills");
        if (bestiaryKills != null) {
            for (String familyId : bestiaryKills.getKeys(false)) {
                long kills = bestiaryKills.getLong(familyId, 0L);
                if (kills > 0L) {
                    profile.bestiaryKills().put(familyId.toUpperCase(), kills);
                }
            }
        }
        ConfigurationSection bestiaryTiers = section.getConfigurationSection("bestiary.tiers");
        if (bestiaryTiers != null) {
            for (String familyId : bestiaryTiers.getKeys(false)) {
                int tier = bestiaryTiers.getInt(familyId, 0);
                if (tier > 0) {
                    profile.bestiaryTiers().put(familyId.toUpperCase(), tier);
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
        profileData.set(base + ".wardrobe", null);
        for (Map.Entry<Integer, WardrobeSet> entry : profile.wardrobe().entrySet()) {
            WardrobeSet set = entry.getValue();
            if (set == null || set.empty()) {
                continue;
            }
            String wardrobeBase = base + ".wardrobe." + entry.getKey();
            profileData.set(wardrobeBase + ".helmet", set.helmet());
            profileData.set(wardrobeBase + ".chestplate", set.chestplate());
            profileData.set(wardrobeBase + ".leggings", set.leggings());
            profileData.set(wardrobeBase + ".boots", set.boots());
        }
        profileData.set(base + ".storage", null);
        for (Map.Entry<Integer, ItemStack[]> entry : profile.storagePages().entrySet()) {
            ItemStack[] contents = entry.getValue();
            if (contents == null) {
                continue;
            }
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack itemStack = contents[slot];
                if (itemStack != null && !itemStack.getType().isAir()) {
                    profileData.set(base + ".storage.pages." + entry.getKey() + "." + slot, itemStack);
                }
            }
        }
        profileData.set(base + ".backpacks", null);
        for (Map.Entry<Integer, OwnedBackpack> entry : profile.backpacks().entrySet()) {
            OwnedBackpack backpack = entry.getValue();
            if (backpack == null) {
                continue;
            }
            String backpackBase = base + ".backpacks." + entry.getKey();
            profileData.set(backpackBase + ".id", backpack.id());
            ItemStack[] contents = backpack.contents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack itemStack = contents[slot];
                if (itemStack != null && !itemStack.getType().isAir()) {
                    profileData.set(backpackBase + ".items." + slot, itemStack);
                }
            }
        }
        profileData.set(base + ".sacks", null);
        for (Map.Entry<String, Map<String, Long>> sackEntry : profile.sacks().entrySet()) {
            for (Map.Entry<String, Long> itemEntry : sackEntry.getValue().entrySet()) {
                if (itemEntry.getValue() > 0L) {
                    profileData.set(base + ".sacks." + sackEntry.getKey() + "." + itemEntry.getKey(), itemEntry.getValue());
                }
            }
        }
        profileData.set(base + ".quiver.selected", profile.selectedQuiverItem());
        profileData.set(base + ".quiver.items", null);
        for (Map.Entry<String, Long> entry : profile.quiver().entrySet()) {
            if (entry.getValue() > 0L) {
                profileData.set(base + ".quiver.items." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".potions.active", null);
        for (Map.Entry<String, Long> entry : profile.potionEffects().entrySet()) {
            if (entry.getValue() > 0L) {
                profileData.set(base + ".potions.active." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".cakes.active", null);
        for (Map.Entry<String, Long> entry : profile.cakeBuffs().entrySet()) {
            if (entry.getValue() > System.currentTimeMillis()) {
                profileData.set(base + ".cakes.active." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".cakes.placed", null);
        for (int index = 0; index < profile.placedCakes().size(); index++) {
            PlacedCake cake = profile.placedCakes().get(index);
            String cakeBase = base + ".cakes.placed." + index;
            profileData.set(cakeBase + ".id", cake.id());
            profileData.set(cakeBase + ".world", cake.worldName());
            profileData.set(cakeBase + ".x", cake.x());
            profileData.set(cakeBase + ".y", cake.y());
            profileData.set(cakeBase + ".z", cake.z());
        }
        profileData.set(base + ".upgrades", null);
        for (Map.Entry<String, Integer> entry : profile.upgrades().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".upgrades." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".bestiary", null);
        for (Map.Entry<String, Long> entry : profile.bestiaryKills().entrySet()) {
            if (entry.getValue() > 0L) {
                profileData.set(base + ".bestiary.kills." + entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Integer> entry : profile.bestiaryTiers().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".bestiary.tiers." + entry.getKey(), entry.getValue());
            }
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
