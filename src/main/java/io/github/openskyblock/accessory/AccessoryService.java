package io.github.openskyblock.accessory;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AccessoryService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CustomItemService customItems;

    public AccessoryService(ConfigService configService, TextService text, ProfileManager profiles, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.customItems = customItems;
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.accessory-bag", true);
    }

    public int capacity() {
        return Math.max(0, configService.main().getInt("accessory-bag.capacity", 18));
    }

    public boolean addHeld(Player player) {
        if (!enabled()) {
            text.send(player, "commands.accessory-bag-disabled");
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition definition = customItems.definition(held).orElse(null);
        if (definition == null || !isAccessory(definition)) {
            text.send(player, "commands.accessory-bag-not-accessory");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.hasAccessory(definition.id())) {
            text.send(player, "commands.accessory-bag-duplicate");
            return false;
        }
        if (profile.accessoryBag().size() >= capacity()) {
            text.send(player, "commands.accessory-bag-full");
            return false;
        }
        held.setAmount(held.getAmount() - 1);
        if (held.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }
        profile.addAccessory(definition.id());
        text.send(player, "commands.accessory-bag-added", List.of(TextService.parsed("accessory", definition.displayName())));
        return true;
    }

    public boolean withdraw(Player player, String itemId) {
        if (!enabled()) {
            text.send(player, "commands.accessory-bag-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        CustomItemDefinition definition = customItems.definition(itemId).orElse(null);
        if (definition == null || !profile.removeAccessory(itemId)) {
            text.send(player, "commands.accessory-bag-missing");
            return false;
        }
        giveOrDrop(player, customItems.createItem(definition));
        text.send(player, "commands.accessory-bag-removed", List.of(TextService.parsed("accessory", definition.displayName())));
        return true;
    }

    public int magicalPower(SkyBlockProfile profile) {
        int magicalPower = 0;
        for (String itemId : profile.accessoryBag()) {
            CustomItemDefinition definition = customItems.definition(itemId).orElse(null);
            if (definition == null || !isAccessory(definition)) {
                continue;
            }
            magicalPower += magicalPower(definition);
        }
        return magicalPower;
    }

    public int magicalPower(CustomItemDefinition definition) {
        String key = "stats.magical-power." + definition.rarity().name().toUpperCase(Locale.ROOT);
        return Math.max(0, configService.main().getInt(key, 0));
    }

    public List<CustomItemDefinition> accessories(SkyBlockProfile profile) {
        return profile.accessoryBag().stream()
                .map(customItems::definition)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(this::isAccessory)
                .toList();
    }

    public boolean isAccessory(CustomItemDefinition definition) {
        return definition.category().equalsIgnoreCase("ACCESSORY");
    }

    public void sendSummary(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.accessory-bag-summary", List.of(
                TextService.raw("count", Integer.toString(profile.accessoryBag().size())),
                TextService.raw("capacity", Integer.toString(capacity())),
                TextService.raw("magical_power", Integer.toString(magicalPower(profile)))
        ));
    }

    private void giveOrDrop(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
