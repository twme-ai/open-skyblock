package io.github.openskyblock.recipe;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CollectionDefinition;
import io.github.openskyblock.service.CollectionService;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.service.MinionDefinition;
import io.github.openskyblock.service.MinionService;
import io.github.openskyblock.slayer.SlayerDefinition;
import io.github.openskyblock.slayer.SlayerService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class RecipeService {
    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CollectionService collections;
    private final CustomItemService customItems;
    private final MinionService minions;
    private final SlayerService slayers;
    private final Map<NamespacedKey, SkyBlockRecipe> recipesByKey = new HashMap<>();
    private final Map<String, SkyBlockRecipe> recipesById = new HashMap<>();

    public RecipeService(
            JavaPlugin plugin,
            ConfigService configService,
            TextService text,
            ProfileManager profiles,
            CollectionService collections,
            CustomItemService customItems,
            MinionService minions,
            SlayerService slayers
    ) {
        this.plugin = plugin;
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.collections = collections;
        this.customItems = customItems;
        this.minions = minions;
        this.slayers = slayers;
    }

    public void reload() {
        unregister();
        ConfigurationSection section = configService.recipes().getConfigurationSection("recipes");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection recipeSection = section.getConfigurationSection(id);
            if (recipeSection == null) {
                continue;
            }
            register(id.toUpperCase(Locale.ROOT), recipeSection);
        }
    }

    public void unregister() {
        for (NamespacedKey key : recipesByKey.keySet()) {
            Bukkit.removeRecipe(key);
        }
        recipesByKey.clear();
        recipesById.clear();
    }

    public Optional<SkyBlockRecipe> recipe(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return Optional.empty();
        }
        return Optional.ofNullable(recipesByKey.get(keyed.getKey()));
    }

    public List<SkyBlockRecipe> recipes() {
        return recipesById.values().stream()
                .sorted(Comparator.comparing(SkyBlockRecipe::id))
                .toList();
    }

    public boolean canCraft(Player player, SkyBlockRecipe recipe) {
        if (!recipe.hasRequirement()) {
            return true;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (recipe.hasCollectionRequirement() && collections.tier(profile, recipe.requiredCollection()) < recipe.requiredTier()) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : recipe.requiredSlayers().entrySet()) {
            if (profile.slayerLevels().getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public void sendRecipes(Player player) {
        text.send(player, "commands.recipes-header");
        for (SkyBlockRecipe recipe : recipes()) {
            boolean unlocked = canCraft(player, recipe);
            text.send(player, "commands.recipe-line", List.of(
                    TextService.parsed("recipe", recipe.displayName()),
                    TextService.parsed("status", text.rawMessage(unlocked ? "commands.recipe-unlocked" : "commands.recipe-locked")),
                    TextService.parsed("requirement", requirementText(recipe))
            ));
        }
    }

    public String requirementText(SkyBlockRecipe recipe) {
        if (!recipe.hasRequirement()) {
            return text.rawMessage("commands.recipe-no-requirement");
        }
        List<String> requirements = new ArrayList<>();
        if (recipe.hasCollectionRequirement()) {
            String collection = collections.definition(recipe.requiredCollection())
                    .map(CollectionDefinition::displayName)
                    .orElse("<white>" + recipe.requiredCollection() + "</white>");
            requirements.add(text.rawMessage("commands.recipe-requirement")
                    .replace("<collection>", collection)
                    .replace("<tier>", Integer.toString(recipe.requiredTier())));
        }
        for (Map.Entry<String, Integer> entry : recipe.requiredSlayers().entrySet()) {
            String slayer = slayers.definition(entry.getKey())
                    .map(SlayerDefinition::displayName)
                    .orElse("<white>" + entry.getKey() + "</white>");
            requirements.add(text.rawMessage("commands.recipe-slayer-requirement")
                    .replace("<slayer>", slayer)
                    .replace("<level>", Integer.toString(entry.getValue())));
        }
        return String.join(text.rawMessage("commands.recipe-requirement-separator"), requirements);
    }

    private void register(String id, ConfigurationSection section) {
        ItemStack result = result(section.getConfigurationSection("result"));
        if (result == null) {
            plugin.getLogger().warning("Skipping recipe with invalid result: " + id);
            return;
        }
        int amount = section.getInt("result.amount", 1);
        result.setAmount(Math.max(1, amount));
        NamespacedKey key = new NamespacedKey(plugin, id.toLowerCase(Locale.ROOT));
        ShapedRecipe shapedRecipe = new ShapedRecipe(key, result);
        List<String> shape = section.getStringList("shape");
        if (shape.isEmpty()) {
            plugin.getLogger().warning("Skipping recipe with empty shape: " + id);
            return;
        }
        shapedRecipe.shape(shape.toArray(String[]::new));
        ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
        if (ingredients != null) {
            for (String ingredientKey : ingredients.getKeys(false)) {
                if (ingredientKey.length() != 1) {
                    continue;
                }
                Material material = Material.matchMaterial(ingredients.getString(ingredientKey, ""));
                if (material != null) {
                    shapedRecipe.setIngredient(ingredientKey.charAt(0), material);
                }
            }
        }
        Bukkit.addRecipe(shapedRecipe);
        SkyBlockRecipe skyBlockRecipe = new SkyBlockRecipe(
                id,
                section.getString("display-name", id),
                key,
                result,
                section.getString("requirement.collection", ""),
                section.getInt("requirement.tier", 0),
                readSlayerRequirements(section.getConfigurationSection("requirement.slayers"))
        );
        recipesByKey.put(key, skyBlockRecipe);
        recipesById.put(id, skyBlockRecipe);
    }

    private Map<String, Integer> readSlayerRequirements(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Integer> requirements = new HashMap<>();
        for (String slayerId : section.getKeys(false)) {
            int level = section.getInt(slayerId, 0);
            if (level > 0) {
                requirements.put(slayerId.toUpperCase(Locale.ROOT), level);
            }
        }
        return Map.copyOf(requirements);
    }

    private ItemStack result(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        RecipeResultType type = RecipeResultType.parse(section.getString("type", "VANILLA"));
        String id = section.getString("id", "");
        return switch (type) {
            case CUSTOM_ITEM -> customItems.definition(id).map(CustomItemDefinition.class::cast)
                    .map(customItems::createItem)
                    .orElse(null);
            case MINION -> minions.definition(id).map(MinionDefinition.class::cast)
                    .map(minions::createMinionItem)
                    .orElse(null);
            case VANILLA -> {
                Material material = Material.matchMaterial(id);
                yield material == null ? null : new ItemStack(material);
            }
        };
    }
}
