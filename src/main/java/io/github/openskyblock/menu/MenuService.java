package io.github.openskyblock.menu;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.MinionPlacement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class MenuService {
    private final OpenSkyBlockPlugin plugin;
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;

    public MenuService(OpenSkyBlockPlugin plugin, ConfigService configService, TextService text, ProfileManager profiles) {
        this.plugin = plugin;
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
    }

    public void reload() {
        // Menu data is read live from menus.yml so reload only exists for service symmetry.
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.skyblock-menu", true);
    }

    public void openSkyBlockMenu(Player player) {
        if (!enabled()) {
            return;
        }
        ConfigurationSection section = configService.menus().getConfigurationSection("skyblock");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 4)));
        int size = rows * 9;
        Map<Integer, MenuAction> actions = new HashMap<>();
        SkyBlockMenuHolder holder = new SkyBlockMenuHolder(actions);
        Inventory inventory = Bukkit.createInventory(holder, size, text.deserialize(section.getString("title", "<dark_gray>SkyBlock Menu</dark_gray>")));
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) {
                    continue;
                }
                int slot = item.getInt("slot", -1);
                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                inventory.setItem(slot, item(player, item));
                actions.put(slot, MenuAction.parse(item.getString("action", "NONE")));
            }
        }
        player.openInventory(inventory);
    }

    public void openMinionMenu(Player player, MinionPlacement placement) {
        ConfigurationSection section = configService.menus().getConfigurationSection("minion");
        if (section == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, section.getInt("rows", 3)));
        int size = rows * 9;
        Map<Integer, MinionMenuAction> actions = new HashMap<>();
        MinionMenuHolder holder = new MinionMenuHolder(placement.profile().uniqueId(), placement.slot(), actions);
        List<TextService.TextPlaceholder> placeholders = plugin.minions()
                .minionPlaceholders(placement.definition(), placement.placedMinion().generatedAmount());
        Inventory inventory = Bukkit.createInventory(
                holder,
                size,
                text.deserialize(section.getString("title", "<dark_gray><minion_name></dark_gray>"), placeholders)
        );
        holder.inventory(inventory);
        fill(inventory, section.getConfigurationSection("filler"));
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) {
                    continue;
                }
                int slot = item.getInt("slot", -1);
                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                inventory.setItem(slot, item(item, placeholders));
                actions.put(slot, MinionMenuAction.parse(item.getString("action", "NONE")));
            }
        }
        player.openInventory(inventory);
    }

    public void runAction(Player player, MenuAction action) {
        switch (action) {
            case PROFILE -> player.performCommand("skyblock profile");
            case ISLAND_HOME -> player.performCommand("skyblock island home");
            case SKILLS -> player.performCommand("skyblock skills");
            case COLLECTIONS -> player.performCommand("skyblock collections");
            case MINIONS -> player.performCommand("skyblock minion list");
            case NONE -> {
            }
        }
    }

    public void runMinionAction(Player player, MinionMenuHolder holder, MinionMenuAction action) {
        MinionPlacement placement = plugin.minions().placement(holder.ownerId(), holder.slot()).orElse(null);
        if (placement == null) {
            text.send(player, "commands.minion-not-found");
            player.closeInventory();
            return;
        }
        switch (action) {
            case CLAIM -> {
                long claimed = plugin.minions().claim(player, placement);
                sendClaimResult(player, claimed);
                player.closeInventory();
            }
            case PICKUP -> {
                plugin.minions().pickup(player, placement);
                player.closeInventory();
            }
            case NONE -> {
            }
        }
    }

    private void fill(Inventory inventory, ConfigurationSection filler) {
        if (filler == null || !filler.getBoolean("enabled", false)) {
            return;
        }
        ItemStack itemStack = item(null, filler);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, itemStack);
        }
    }

    private ItemStack item(Player player, ConfigurationSection section) {
        return item(section, placeholders(player));
    }

    private ItemStack item(ConfigurationSection section, List<TextService.TextPlaceholder> placeholders) {
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        ItemStack itemStack = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(text.deserialize(section.getString("display-name", "<white>Item</white>"), placeholders));
        meta.lore(section.getStringList("lore").stream()
                .map(line -> text.deserialize(line, placeholders))
                .toList());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private void sendClaimResult(Player player, long claimed) {
        if (claimed <= 0L) {
            text.send(player, "commands.minion-nothing");
            return;
        }
        text.send(player, "commands.minion-claimed", List.of(TextService.raw("amount", text.formatNumber(claimed))));
    }

    private List<TextService.TextPlaceholder> placeholders(Player player) {
        if (player == null) {
            return List.of();
        }
        SkyBlockProfile profile = profiles.profile(player);
        return List.of(
                TextService.raw("player", profile.playerName()),
                TextService.raw("level", Integer.toString(plugin.skills().skyBlockLevel(profile))),
                TextService.raw("purse", text.formatNumber(profile.purse())),
                TextService.raw("bank", text.formatNumber(profile.bank()))
        );
    }
}
