package io.github.openskyblock.pet;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.OwnedPet;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.service.Rarity;
import io.github.openskyblock.stats.StatSnapshot;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class PetService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CustomItemService customItems;
    private final Map<String, PetDefinition> definitions = new HashMap<>();
    private final Map<String, PetItemDefinition> petItems = new HashMap<>();
    private final Map<String, PetItemDefinition> petItemsByItem = new HashMap<>();

    public PetService(ConfigService configService, TextService text, ProfileManager profiles, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.customItems = customItems;
    }

    public void reload() {
        definitions.clear();
        petItems.clear();
        petItemsByItem.clear();
        readPetItems(configService.pets().getConfigurationSection("pet-items"));
        ConfigurationSection section = configService.pets().getConfigurationSection("pets");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection petSection = section.getConfigurationSection(id);
            if (petSection == null) {
                continue;
            }
            Material material = Material.matchMaterial(petSection.getString("material", "BONE"));
            String normalizedId = id.toUpperCase(Locale.ROOT);
            definitions.put(normalizedId, new PetDefinition(
                    normalizedId,
                    material == null ? Material.BONE : material,
                    petSection.getString("display-name", normalizedId),
                    Rarity.parse(petSection.getString("rarity", "COMMON")),
                    Math.max(1, petSection.getInt("max-level", 100)),
                    Math.max(1.0D, petSection.getDouble("xp-per-level", 100.0D)),
                    petSection.getStringList("lore"),
                    stats(petSection.getConfigurationSection("base-stats")),
                    stats(petSection.getConfigurationSection("stats-per-level"))
            ));
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.pets", true);
    }

    public Optional<PetDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<PetDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(PetDefinition::id))
                .toList();
    }

    public Optional<PetItemDefinition> petItem(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(petItems.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<PetItemDefinition> petItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return Optional.empty();
        }
        return customItems.definition(itemStack)
                .map(CustomItemDefinition::id)
                .map(petItemsByItem::get);
    }

    public boolean isPetItem(ItemStack itemStack) {
        return petItem(itemStack).isPresent();
    }

    public OwnedPet addPet(SkyBlockProfile profile, PetDefinition definition) {
        OwnedPet pet = new OwnedPet(UUID.randomUUID().toString(), definition.id(), 0.0D);
        profile.pets().add(pet);
        if (profile.activePetInstanceId() == null || profile.activePetInstanceId().isBlank()) {
            profile.activePetInstanceId(pet.instanceId());
        }
        return pet;
    }

    public boolean activate(Player player, int petIndex) {
        if (!enabled()) {
            text.send(player, "commands.pet-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (petIndex < 0 || petIndex >= profile.pets().size()) {
            text.send(player, "commands.pet-not-found");
            return false;
        }
        OwnedPet pet = profile.pets().get(petIndex);
        PetDefinition definition = definition(pet.petId()).orElse(null);
        if (definition == null) {
            text.send(player, "commands.pet-not-found");
            return false;
        }
        profile.activePetInstanceId(pet.instanceId());
        text.send(player, "commands.pet-activated", placeholders(pet, definition, true));
        return true;
    }

    public boolean attachItem(Player player, int petIndex, ItemStack itemStack) {
        if (!enabled()) {
            text.send(player, "commands.pet-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (petIndex < 0 || petIndex >= profile.pets().size()) {
            text.send(player, "commands.pet-not-found");
            return false;
        }
        PetItemDefinition petItem = petItem(itemStack).orElse(null);
        if (petItem == null) {
            text.send(player, "commands.pet-item-invalid");
            return false;
        }
        OwnedPet pet = profile.pets().get(petIndex);
        PetDefinition definition = definition(pet.petId()).orElse(null);
        if (definition == null) {
            text.send(player, "commands.pet-not-found");
            return false;
        }
        pet.petItemId(petItem.id());
        profiles.save(player);
        text.send(player, "commands.pet-item-attached", placeholders(pet, definition, isActive(profile, pet)));
        return true;
    }

    public Optional<OwnedPet> activePet(SkyBlockProfile profile) {
        String activeId = profile.activePetInstanceId();
        if (activeId == null || activeId.isBlank()) {
            return Optional.empty();
        }
        return profile.pets().stream()
                .filter(pet -> pet.instanceId().equals(activeId))
                .findFirst();
    }

    public Optional<PetDefinition> activeDefinition(SkyBlockProfile profile) {
        return activePet(profile).flatMap(pet -> definition(pet.petId()));
    }

    public Map<String, Double> activeStats(SkyBlockProfile profile) {
        if (!enabled()) {
            return Map.of();
        }
        OwnedPet pet = activePet(profile).orElse(null);
        if (pet == null) {
            return Map.of();
        }
        PetDefinition definition = definition(pet.petId()).orElse(null);
        if (definition == null) {
            return Map.of();
        }
        return combinedStats(definition, pet);
    }

    public boolean addXp(Player player, double amount) {
        if (!enabled()) {
            text.send(player, "commands.pet-disabled");
            return false;
        }
        if (amount <= 0.0D || Double.isNaN(amount) || Double.isInfinite(amount)) {
            text.send(player, "errors.invalid-number");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        OwnedPet pet = activePet(profile).orElse(null);
        if (pet == null) {
            text.send(player, "commands.pet-no-active");
            return false;
        }
        PetDefinition definition = definition(pet.petId()).orElse(null);
        if (definition == null) {
            text.send(player, "commands.pet-not-found");
            return false;
        }
        int beforeLevel = level(definition, pet);
        pet.xp(Math.min(maxXp(definition), pet.xp() + amount));
        int afterLevel = level(definition, pet);
        text.send(player, "commands.pet-xp", List.of(
                TextService.parsed("pet", definition.displayName()),
                TextService.raw("xp", text.formatNumber(amount)),
                TextService.raw("level", Integer.toString(afterLevel))
        ));
        if (afterLevel > beforeLevel) {
            text.send(player, "commands.pet-level-up", List.of(
                    TextService.parsed("pet", definition.displayName()),
                    TextService.raw("level", Integer.toString(afterLevel))
            ));
        }
        return true;
    }

    public void sendList(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        if (!enabled()) {
            text.send(player, "commands.pet-disabled");
            return;
        }
        if (profile.pets().isEmpty()) {
            text.send(player, "commands.pet-empty");
            return;
        }
        text.send(player, "commands.pet-list-header");
        for (int index = 0; index < profile.pets().size(); index++) {
            OwnedPet pet = profile.pets().get(index);
            PetDefinition definition = definition(pet.petId()).orElse(null);
            if (definition == null) {
                continue;
            }
            text.send(player, "commands.pet-list-line", withSlot(index + 1, placeholders(pet, definition, isActive(profile, pet))));
        }
    }

    public boolean isActive(SkyBlockProfile profile, OwnedPet pet) {
        String activeId = profile.activePetInstanceId();
        return activeId != null && activeId.equals(pet.instanceId());
    }

    public int level(PetDefinition definition, OwnedPet pet) {
        double xpPerLevel = Math.max(1.0D, definition.xpPerLevel());
        int level = (int) Math.floor(pet.xp() / xpPerLevel) + 1;
        return Math.max(1, Math.min(definition.maxLevel(), level));
    }

    public double xpToNextLevel(PetDefinition definition, OwnedPet pet) {
        if (level(definition, pet) >= definition.maxLevel()) {
            return 0.0D;
        }
        double xpPerLevel = Math.max(1.0D, definition.xpPerLevel());
        double intoLevel = pet.xp() % xpPerLevel;
        return Math.max(0.0D, xpPerLevel - intoLevel);
    }

    public List<Component> statLore(PetDefinition definition, OwnedPet pet) {
        Map<String, Double> stats = combinedStats(definition, pet);
        if (stats.isEmpty()) {
            return List.of(text.deserialize(text.rawMessage("pets.no-stats")));
        }
        return stats.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> text.message("pets.stat-line", List.of(
                        TextService.raw("stat", statLabel(entry.getKey())),
                        TextService.raw("value", text.formatNumber(entry.getValue()))
                )))
                .toList();
    }

    public List<TextService.TextPlaceholder> placeholders(OwnedPet pet, PetDefinition definition, boolean active) {
        String xpToNext = level(definition, pet) >= definition.maxLevel()
                ? text.rawMessage("pets.max-level-xp")
                : text.formatNumber(xpToNextLevel(definition, pet));
        return List.of(
                TextService.raw("pet_id", definition.id()),
                TextService.parsed("pet", definition.displayName()),
                TextService.raw("level", Integer.toString(level(definition, pet))),
                TextService.raw("max_level", Integer.toString(definition.maxLevel())),
                TextService.raw("xp", text.formatNumber(pet.xp())),
                TextService.parsed("xp_to_next", xpToNext),
                TextService.raw("rarity", definition.rarity().name()),
                TextService.parsed("rarity_colored", definition.rarity().colorTag() + definition.rarity().name()),
                TextService.parsed("pet_item", petItemName(pet)),
                TextService.parsed("status", text.rawMessage(active ? "pets.active-status" : "pets.inactive-status"))
        );
    }

    public String petItemName(OwnedPet pet) {
        return petItem(pet.petItemId())
                .map(PetItemDefinition::displayName)
                .orElseGet(() -> text.rawMessage("pets.no-item"));
    }

    public String statLabel(String stat) {
        String normalized = StatSnapshot.normalize(stat);
        String configured = configService.messages().getString("items.stat-labels." + normalized);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String readable = normalized.replace('_', ' ');
        if (readable.isBlank()) {
            return stat;
        }
        return Character.toUpperCase(readable.charAt(0)) + readable.substring(1);
    }

    private List<TextService.TextPlaceholder> withSlot(int slot, List<TextService.TextPlaceholder> placeholders) {
        Map<String, TextService.TextPlaceholder> merged = new LinkedHashMap<>();
        merged.put("slot", TextService.raw("slot", Integer.toString(slot)));
        for (TextService.TextPlaceholder placeholder : placeholders) {
            merged.put(placeholder.key(), placeholder);
        }
        return merged.values().stream().toList();
    }

    private void readPetItems(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(id);
            if (itemSection == null) {
                continue;
            }
            String normalizedId = id.toUpperCase(Locale.ROOT);
            String itemId = itemSection.getString("item", normalizedId).toUpperCase(Locale.ROOT);
            PetItemDefinition petItem = new PetItemDefinition(
                    normalizedId,
                    itemSection.getString("display-name", normalizedId),
                    itemId,
                    Map.copyOf(stats(itemSection.getConfigurationSection("stats")))
            );
            petItems.put(normalizedId, petItem);
            if (!itemId.isBlank()) {
                petItemsByItem.put(itemId, petItem);
            }
        }
    }

    private Map<String, Double> combinedStats(PetDefinition definition, OwnedPet pet) {
        Map<String, Double> stats = new HashMap<>(statsAtLevel(definition, level(definition, pet)));
        PetItemDefinition petItem = petItem(pet.petItemId()).orElse(null);
        if (petItem != null) {
            for (Map.Entry<String, Double> entry : petItem.stats().entrySet()) {
                stats.put(entry.getKey(), stats.getOrDefault(entry.getKey(), 0.0D) + entry.getValue());
            }
        }
        stats.entrySet().removeIf(entry -> Math.abs(entry.getValue()) <= 0.000001D);
        return stats;
    }

    private Map<String, Double> statsAtLevel(PetDefinition definition, int level) {
        Map<String, Double> stats = new HashMap<>();
        for (Map.Entry<String, Double> entry : definition.baseStats().entrySet()) {
            stats.put(entry.getKey(), stats.getOrDefault(entry.getKey(), 0.0D) + entry.getValue());
        }
        for (Map.Entry<String, Double> entry : definition.statsPerLevel().entrySet()) {
            stats.put(entry.getKey(), stats.getOrDefault(entry.getKey(), 0.0D) + entry.getValue() * level);
        }
        stats.entrySet().removeIf(entry -> Math.abs(entry.getValue()) <= 0.000001D);
        return stats;
    }

    private double maxXp(PetDefinition definition) {
        return Math.max(0.0D, (definition.maxLevel() - 1) * Math.max(1.0D, definition.xpPerLevel()));
    }

    private Map<String, Double> stats(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Double> stats = new HashMap<>();
        for (String key : section.getKeys(false)) {
            stats.put(StatSnapshot.normalize(key), section.getDouble(key, 0.0D));
        }
        return stats;
    }
}
