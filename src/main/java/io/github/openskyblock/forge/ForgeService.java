package io.github.openskyblock.forge;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.profile.ActiveForgeJob;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ForgeService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final CustomItemService customItems;
    private final Map<String, ForgeRecipeDefinition> recipes = new HashMap<>();
    private int defaultSlots = 2;
    private int maxSlots = 5;

    public ForgeService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.customItems = customItems;
    }

    public void reload() {
        defaultSlots = Math.max(1, configService.forge().getInt("settings.default-slots", 2));
        maxSlots = Math.max(defaultSlots, configService.forge().getInt("settings.max-slots", 5));
        loadRecipes();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.forge", true);
    }

    public List<String> recipeIds() {
        return recipes().stream().map(ForgeRecipeDefinition::id).toList();
    }

    public Optional<ForgeRecipeDefinition> recipe(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(recipes.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<ForgeRecipeDefinition> recipes() {
        return recipes.values().stream()
                .sorted(Comparator.comparingInt(ForgeRecipeDefinition::requiredHotmLevel).thenComparing(ForgeRecipeDefinition::id))
                .toList();
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.forge-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int slots = slotCount(profile);
        long now = System.currentTimeMillis();
        text.send(player, "commands.forge-status-header", List.of(
                TextService.raw("hotm_level", Integer.toString(hotmLevel(profile))),
                TextService.raw("active", Integer.toString(profile.forgeJobs().size())),
                TextService.raw("slots", Integer.toString(slots))
        ));
        for (int slot = 1; slot <= slots; slot++) {
            ActiveForgeJob job = profile.forgeJobs().get(slot);
            if (job == null) {
                text.send(player, "commands.forge-status-empty", List.of(TextService.raw("slot", Integer.toString(slot))));
                continue;
            }
            ForgeRecipeDefinition recipe = recipe(job.recipeId()).orElse(null);
            if (recipe == null) {
                text.send(player, "commands.forge-status-missing-recipe", List.of(
                        TextService.raw("slot", Integer.toString(slot)),
                        TextService.raw("recipe", job.recipeId())
                ));
                continue;
            }
            text.send(player, "commands.forge-status-line", jobPlaceholders(slot, job, recipe, now));
        }
    }

    public void sendList(Player player) {
        if (!enabled()) {
            text.send(player, "commands.forge-disabled");
            return;
        }
        if (recipes.isEmpty()) {
            text.send(player, "commands.forge-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.forge-list-header", List.of(TextService.raw("hotm_level", Integer.toString(hotmLevel(profile)))));
        for (ForgeRecipeDefinition recipe : recipes()) {
            text.send(player, "commands.forge-list-line", recipePlaceholders(recipe));
        }
    }

    public boolean start(Player player, String recipeId) {
        if (!enabled()) {
            text.send(player, "commands.forge-disabled");
            return false;
        }
        ForgeRecipeDefinition recipe = recipe(recipeId).orElse(null);
        if (recipe == null) {
            text.send(player, "commands.forge-unknown-recipe", List.of(TextService.raw("recipe", recipeId == null ? "" : recipeId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int hotmLevel = hotmLevel(profile);
        if (hotmLevel < recipe.requiredHotmLevel()) {
            text.send(player, "commands.forge-requires-hotm", recipePlaceholders(recipe));
            return false;
        }
        int slot = firstAvailableSlot(profile);
        if (slot <= 0) {
            text.send(player, "commands.forge-no-slots", List.of(TextService.raw("slots", Integer.toString(slotCount(profile)))));
            return false;
        }
        ForgeItemDefinition missing = recipe.ingredients().stream()
                .filter(ingredient -> countItem(player, ingredient) < ingredient.amount())
                .findFirst()
                .orElse(null);
        if (missing != null) {
            text.send(player, "commands.forge-missing-ingredient", itemPlaceholders(missing));
            return false;
        }
        if (profile.purse() < recipe.cost()) {
            text.send(player, "commands.forge-no-money", recipePlaceholders(recipe));
            return false;
        }
        if (!economy.spendPurse(player, recipe.cost())) {
            text.send(player, "commands.forge-no-money", recipePlaceholders(recipe));
            return false;
        }
        for (ForgeItemDefinition ingredient : recipe.ingredients()) {
            consumeItem(player, ingredient);
        }
        ActiveForgeJob job = new ActiveForgeJob(slot, recipe.id(), System.currentTimeMillis(), recipe.durationMillis());
        profile.forgeJobs().put(slot, job);
        profiles.saveAll();
        text.send(player, "commands.forge-started", jobPlaceholders(slot, job, recipe, System.currentTimeMillis()));
        return true;
    }

    public boolean collect(Player player, int slot) {
        if (!enabled()) {
            text.send(player, "commands.forge-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int slots = slotCount(profile);
        if (slot < 1 || slot > slots) {
            text.send(player, "commands.forge-invalid-slot", List.of(TextService.raw("slots", Integer.toString(slots))));
            return false;
        }
        ActiveForgeJob job = profile.forgeJobs().get(slot);
        if (job == null) {
            text.send(player, "commands.forge-empty-slot", List.of(TextService.raw("slot", Integer.toString(slot))));
            return false;
        }
        ForgeRecipeDefinition recipe = recipe(job.recipeId()).orElse(null);
        if (recipe == null) {
            profile.forgeJobs().remove(slot);
            profiles.saveAll();
            text.send(player, "commands.forge-missing-recipe", List.of(TextService.raw("recipe", job.recipeId())));
            return false;
        }
        long now = System.currentTimeMillis();
        if (!job.complete(now)) {
            text.send(player, "commands.forge-not-ready", jobPlaceholders(slot, job, recipe, now));
            return false;
        }
        ItemStack output = createItem(recipe.output()).orElse(null);
        if (output == null) {
            text.send(player, "commands.forge-missing-output", itemPlaceholders(recipe.output()));
            return false;
        }
        giveItem(player, output);
        profile.forgeJobs().remove(slot);
        profiles.saveAll();
        text.send(player, "commands.forge-collected", jobPlaceholders(slot, job, recipe, now));
        return true;
    }

    public int collectReady(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        long now = System.currentTimeMillis();
        List<Integer> slots = profile.forgeJobs().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().complete(now))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        int collected = 0;
        for (int slot : slots) {
            if (collect(player, slot)) {
                collected++;
            }
        }
        if (collected == 0) {
            text.send(player, "commands.forge-no-ready-jobs");
        }
        return collected;
    }

    private void loadRecipes() {
        recipes.clear();
        ConfigurationSection section = configService.forge().getConfigurationSection("recipes");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection recipe = section.getConfigurationSection(id);
            if (recipe == null) {
                continue;
            }
            ForgeItemDefinition output = item(recipe.getConfigurationSection("output"));
            if (output == null) {
                continue;
            }
            List<ForgeItemDefinition> ingredients = new ArrayList<>();
            for (Map<?, ?> raw : recipe.getMapList("ingredients")) {
                ForgeItemDefinition ingredient = item(raw);
                if (ingredient != null) {
                    ingredients.add(ingredient);
                }
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            recipes.put(normalized, new ForgeRecipeDefinition(
                    normalized,
                    recipe.getString("display-name", normalized),
                    recipe.getString("description", ""),
                    Math.max(1, recipe.getInt("required-hotm-level", 1)),
                    Math.max(0L, recipe.getLong("duration-seconds", 60L)) * 1000L,
                    Math.max(0.0D, recipe.getDouble("cost", 0.0D)),
                    List.copyOf(ingredients),
                    output
            ));
        }
    }

    private ForgeItemDefinition item(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String type = section.getString("type", "VANILLA").toUpperCase(Locale.ROOT);
        int amount = Math.max(1, section.getInt("amount", 1));
        if (type.equals("CUSTOM_ITEM")) {
            String itemId = section.getString("item", "").toUpperCase(Locale.ROOT);
            return itemId.isBlank() ? null : new ForgeItemDefinition(type, itemId, Material.STONE, amount);
        }
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null || material.isAir()) {
            return null;
        }
        return new ForgeItemDefinition("VANILLA", "", material, amount);
    }

    private ForgeItemDefinition item(Map<?, ?> section) {
        String type = string(section.get("type"), "VANILLA").toUpperCase(Locale.ROOT);
        int amount = Math.max(1, integer(section.get("amount"), 1));
        if (type.equals("CUSTOM_ITEM")) {
            String itemId = string(section.get("item"), "").toUpperCase(Locale.ROOT);
            return itemId.isBlank() ? null : new ForgeItemDefinition(type, itemId, Material.STONE, amount);
        }
        Material material = Material.matchMaterial(string(section.get("material"), "STONE"));
        if (material == null || material.isAir()) {
            return null;
        }
        return new ForgeItemDefinition("VANILLA", "", material, amount);
    }

    private int hotmLevel(SkyBlockProfile profile) {
        int level = 1;
        ConfigurationSection section = configService.commissions().getConfigurationSection("hotm-levels");
        if (section == null) {
            return level;
        }
        for (String levelKey : section.getKeys(false)) {
            try {
                int candidate = Integer.parseInt(levelKey);
                if (profile.hotmXp() >= section.getDouble(levelKey + ".required-xp", 0.0D)) {
                    level = Math.max(level, candidate);
                }
            } catch (NumberFormatException ignored) {
                // Invalid HotM level keys are skipped.
            }
        }
        return level;
    }

    private int slotCount(SkyBlockProfile profile) {
        int slots = defaultSlots;
        int hotmLevel = hotmLevel(profile);
        ConfigurationSection section = configService.forge().getConfigurationSection("hotm-slot-milestones");
        if (section != null) {
            for (String levelKey : section.getKeys(false)) {
                try {
                    int requiredLevel = Integer.parseInt(levelKey);
                    if (hotmLevel >= requiredLevel) {
                        slots = Math.max(slots, section.getInt(levelKey, slots));
                    }
                } catch (NumberFormatException ignored) {
                    // Invalid milestone keys are skipped.
                }
            }
        }
        profile.forgeJobs().entrySet().removeIf(entry -> entry.getKey() < 1 || entry.getKey() > maxSlots);
        return Math.max(1, Math.min(maxSlots, slots));
    }

    private int firstAvailableSlot(SkyBlockProfile profile) {
        int slots = slotCount(profile);
        for (int slot = 1; slot <= slots; slot++) {
            if (!profile.forgeJobs().containsKey(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private int countItem(Player player, ForgeItemDefinition item) {
        int amount = 0;
        for (int slot = 0; slot < player.getInventory().getStorageContents().length; slot++) {
            ItemStack itemStack = player.getInventory().getItem(slot);
            if (matches(itemStack, item)) {
                amount += itemStack.getAmount();
            }
        }
        return amount;
    }

    private void consumeItem(Player player, ForgeItemDefinition item) {
        int remaining = item.amount();
        for (int slot = 0; slot < player.getInventory().getStorageContents().length && remaining > 0; slot++) {
            ItemStack itemStack = player.getInventory().getItem(slot);
            if (!matches(itemStack, item)) {
                continue;
            }
            int removed = Math.min(remaining, itemStack.getAmount());
            itemStack.setAmount(itemStack.getAmount() - removed);
            remaining -= removed;
            if (itemStack.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private boolean matches(ItemStack itemStack, ForgeItemDefinition item) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        if (item.customItem()) {
            return customItems.definition(itemStack)
                    .map(definition -> definition.id().equalsIgnoreCase(item.itemId()))
                    .orElse(false);
        }
        return itemStack.getType() == item.material() && customItems.definition(itemStack).isEmpty();
    }

    private Optional<ItemStack> createItem(ForgeItemDefinition item) {
        ItemStack itemStack;
        if (item.customItem()) {
            CustomItemDefinition definition = customItems.definition(item.itemId()).orElse(null);
            if (definition == null) {
                return Optional.empty();
            }
            itemStack = customItems.createItem(definition);
        } else {
            itemStack = new ItemStack(item.material());
        }
        itemStack.setAmount(item.amount());
        return Optional.of(itemStack);
    }

    private void giveItem(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private List<TextService.TextPlaceholder> recipePlaceholders(ForgeRecipeDefinition recipe) {
        return List.of(
                TextService.raw("id", recipe.id()),
                TextService.parsed("recipe", recipe.displayName()),
                TextService.raw("description", recipe.description()),
                TextService.raw("required_hotm", Integer.toString(recipe.requiredHotmLevel())),
                TextService.raw("duration", formatDuration(recipe.durationMillis())),
                TextService.raw("cost", text.formatNumber(recipe.cost())),
                TextService.parsed("ingredients", itemList(recipe.ingredients())),
                TextService.parsed("output", itemDisplay(recipe.output()))
        );
    }

    private List<TextService.TextPlaceholder> jobPlaceholders(int slot, ActiveForgeJob job, ForgeRecipeDefinition recipe, long now) {
        return List.of(
                TextService.raw("slot", Integer.toString(slot)),
                TextService.raw("id", recipe.id()),
                TextService.parsed("recipe", recipe.displayName()),
                TextService.raw("remaining", formatDuration(job.remainingMillis(now))),
                TextService.parsed("status", job.complete(now) ? text.rawMessage("forge.status-ready") : text.rawMessage("forge.status-working")),
                TextService.parsed("output", itemDisplay(recipe.output()))
        );
    }

    private List<TextService.TextPlaceholder> itemPlaceholders(ForgeItemDefinition item) {
        return List.of(
                TextService.raw("item_id", item.key()),
                TextService.raw("amount", Integer.toString(item.amount())),
                TextService.parsed("item", itemDisplay(item))
        );
    }

    private String itemList(List<ForgeItemDefinition> items) {
        return String.join("<gray>, </gray>", items.stream().map(this::itemDisplay).toList());
    }

    private String itemDisplay(ForgeItemDefinition item) {
        String name = item.customItem()
                ? customItems.definition(item.itemId()).map(CustomItemDefinition::displayName).orElse(item.itemId())
                : readableMaterial(item.material());
        return "<yellow>" + item.amount() + "x</yellow> " + name;
    }

    private String readableMaterial(Material material) {
        String normalized = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainingSeconds = seconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + remainingSeconds + "s";
        }
        return remainingSeconds + "s";
    }

    private String string(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(string(value, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
