package io.github.openskyblock.config;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigService {
    private static final List<String> RESOURCE_FILES = List.of(
            "messages.yml",
            "skills.yml",
            "collections.yml",
            "items.yml",
            "sacks.yml",
            "quiver.yml",
            "potions.yml",
            "cakes.yml",
            "upgrades.yml",
            "auctions.yml",
            "bazaar.yml",
            "trades.yml",
            "storage.yml",
            "backpacks.yml",
            "mobs.yml",
            "mob_spawns.yml",
            "bestiary.yml",
            "museum.yml",
            "slayers.yml",
            "equipment.yml",
            "wardrobe.yml",
            "armor_sets.yml",
            "reforges.yml",
            "enchantments.yml",
            "stars.yml",
            "gemstones.yml",
            "pets.yml",
            "minions.yml",
            "menus.yml",
            "recipes.yml",
            "shops.yml"
    );

    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> configurations = new HashMap<>();

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        for (String resource : RESOURCE_FILES) {
            File file = new File(plugin.getDataFolder(), resource);
            if (!file.exists()) {
                plugin.saveResource(resource, false);
            }
            configurations.put(resource, YamlConfiguration.loadConfiguration(file));
        }
    }

    public FileConfiguration main() {
        return plugin.getConfig();
    }

    public FileConfiguration messages() {
        return file("messages.yml");
    }

    public FileConfiguration skills() {
        return file("skills.yml");
    }

    public FileConfiguration collections() {
        return file("collections.yml");
    }

    public FileConfiguration items() {
        return file("items.yml");
    }

    public FileConfiguration sacks() {
        return file("sacks.yml");
    }

    public FileConfiguration quiver() {
        return file("quiver.yml");
    }

    public FileConfiguration potions() {
        return file("potions.yml");
    }

    public FileConfiguration cakes() {
        return file("cakes.yml");
    }

    public FileConfiguration upgrades() {
        return file("upgrades.yml");
    }

    public FileConfiguration auctions() {
        return file("auctions.yml");
    }

    public FileConfiguration bazaar() {
        return file("bazaar.yml");
    }

    public FileConfiguration trades() {
        return file("trades.yml");
    }

    public FileConfiguration storage() {
        return file("storage.yml");
    }

    public FileConfiguration backpacks() {
        return file("backpacks.yml");
    }

    public FileConfiguration mobs() {
        return file("mobs.yml");
    }

    public FileConfiguration mobSpawns() {
        return file("mob_spawns.yml");
    }

    public FileConfiguration bestiary() {
        return file("bestiary.yml");
    }

    public FileConfiguration museum() {
        return file("museum.yml");
    }

    public FileConfiguration slayers() {
        return file("slayers.yml");
    }

    public FileConfiguration equipment() {
        return file("equipment.yml");
    }

    public FileConfiguration wardrobe() {
        return file("wardrobe.yml");
    }

    public FileConfiguration armorSets() {
        return file("armor_sets.yml");
    }

    public FileConfiguration reforges() {
        return file("reforges.yml");
    }

    public FileConfiguration enchantments() {
        return file("enchantments.yml");
    }

    public FileConfiguration stars() {
        return file("stars.yml");
    }

    public FileConfiguration gemstones() {
        return file("gemstones.yml");
    }

    public FileConfiguration pets() {
        return file("pets.yml");
    }

    public FileConfiguration minions() {
        return file("minions.yml");
    }

    public FileConfiguration menus() {
        return file("menus.yml");
    }

    public FileConfiguration recipes() {
        return file("recipes.yml");
    }

    public FileConfiguration shops() {
        return file("shops.yml");
    }

    private FileConfiguration file(String name) {
        FileConfiguration configuration = configurations.get(name);
        if (configuration == null) {
            throw new IllegalStateException("Configuration has not been loaded: " + name);
        }
        return configuration;
    }
}
