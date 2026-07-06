package io.github.openskyblock.equipment;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class EquipmentService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CustomItemService customItems;
    private final Map<String, EquipmentSlotDefinition> slots = new LinkedHashMap<>();

    public EquipmentService(ConfigService configService, TextService text, ProfileManager profiles, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.customItems = customItems;
    }

    public void reload() {
        slots.clear();
        ConfigurationSection section = configService.equipment().getConfigurationSection("slots");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection slotSection = section.getConfigurationSection(id);
            if (slotSection == null) {
                continue;
            }
            Material material = Material.matchMaterial(slotSection.getString("material", "CHAINMAIL_CHESTPLATE"));
            String normalizedId = id.toUpperCase(Locale.ROOT);
            slots.put(normalizedId, new EquipmentSlotDefinition(
                    normalizedId,
                    slotSection.getString("display-name", normalizedId),
                    material == null ? Material.CHAINMAIL_CHESTPLATE : material
            ));
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.equipment", true);
    }

    public Optional<EquipmentSlotDefinition> slot(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(slots.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<EquipmentSlotDefinition> slots() {
        return List.copyOf(slots.values());
    }

    public boolean equipHeld(Player player, String requestedSlot) {
        if (!enabled()) {
            text.send(player, "commands.equipment-disabled");
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition definition = customItems.definition(held).orElse(null);
        if (definition == null || !isEquipment(definition)) {
            text.send(player, "commands.equipment-held-missing");
            return false;
        }
        String slotId = requestedSlot == null || requestedSlot.isBlank() ? definition.equipmentSlot() : requestedSlot;
        EquipmentSlotDefinition slot = slot(slotId).orElse(null);
        if (slot == null) {
            text.send(player, "commands.equipment-unknown-slot", List.of(TextService.raw("slot", slotId == null ? "" : slotId)));
            return false;
        }
        if (definition.equipmentSlot() != null && !definition.equipmentSlot().isBlank() && !definition.equipmentSlot().equalsIgnoreCase(slot.id())) {
            text.send(player, "commands.equipment-wrong-slot", placeholders(slot, definition));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        ItemStack equipped = held.clone();
        equipped.setAmount(1);
        held.setAmount(held.getAmount() - 1);
        if (held.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }
        ItemStack previous = profile.equipment().put(slot.id(), equipped);
        if (previous != null) {
            giveOrDrop(player, previous);
        }
        text.send(player, "commands.equipment-equipped", placeholders(slot, definition));
        return true;
    }

    public boolean unequip(Player player, String rawSlot) {
        if (!enabled()) {
            text.send(player, "commands.equipment-disabled");
            return false;
        }
        EquipmentSlotDefinition slot = slot(rawSlot).orElse(null);
        if (slot == null) {
            text.send(player, "commands.equipment-unknown-slot", List.of(TextService.raw("slot", rawSlot)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        ItemStack itemStack = profile.equipment().remove(slot.id());
        if (itemStack == null) {
            text.send(player, "commands.equipment-empty", List.of(TextService.parsed("slot", slot.displayName())));
            return false;
        }
        giveOrDrop(player, itemStack);
        text.send(player, "commands.equipment-unequipped", List.of(TextService.parsed("slot", slot.displayName())));
        return true;
    }

    public void sendSummary(Player player) {
        if (!enabled()) {
            text.send(player, "commands.equipment-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.equipment-summary-header");
        for (EquipmentSlotDefinition slot : slots()) {
            ItemStack itemStack = profile.equipment().get(slot.id());
            String itemName = text.rawMessage("equipment.empty-slot");
            if (itemStack != null) {
                CustomItemDefinition definition = customItems.definition(itemStack).orElse(null);
                if (definition != null) {
                    itemName = definition.displayName();
                }
            }
            text.send(player, "commands.equipment-summary-line", List.of(
                    TextService.parsed("slot", slot.displayName()),
                    TextService.parsed("item", itemName)
            ));
        }
    }

    public boolean isEquipment(CustomItemDefinition definition) {
        return definition.category().equalsIgnoreCase("EQUIPMENT")
                || definition.equipmentSlot() != null && !definition.equipmentSlot().isBlank();
    }

    public int equippedCount(SkyBlockProfile profile) {
        return (int) profile.equipment().entrySet().stream()
                .filter(entry -> slot(entry.getKey()).isPresent())
                .filter(entry -> customItems.definition(entry.getValue()).map(this::isEquipment).orElse(false))
                .count();
    }

    private List<TextService.TextPlaceholder> placeholders(EquipmentSlotDefinition slot, CustomItemDefinition definition) {
        return List.of(
                TextService.raw("slot_id", slot.id()),
                TextService.parsed("slot", slot.displayName()),
                TextService.parsed("item", definition.displayName())
        );
    }

    private void giveOrDrop(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
