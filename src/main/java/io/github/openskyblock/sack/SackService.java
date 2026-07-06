package io.github.openskyblock.sack;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.service.SkillService;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class SackService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CustomItemService customItems;
    private final SkillService skills;
    private final Map<String, SackDefinition> definitions = new LinkedHashMap<>();

    public SackService(ConfigService configService, TextService text, ProfileManager profiles, CustomItemService customItems, SkillService skills) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.customItems = customItems;
        this.skills = skills;
    }

    public void reload() {
        definitions.clear();
        ConfigurationSection section = configService.sacks().getConfigurationSection("sacks");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection sackSection = section.getConfigurationSection(id);
            if (sackSection == null) {
                continue;
            }
            Material material = Material.matchMaterial(sackSection.getString("material", "CHEST"));
            definitions.put(id.toUpperCase(Locale.ROOT), new SackDefinition(
                    id.toUpperCase(Locale.ROOT),
                    sackSection.getString("display-name", id),
                    material == null ? Material.CHEST : material,
                    sackSection.getString("item-id", ""),
                    sackSection.getBoolean("require-item", true),
                    sackSection.getBoolean("auto-pickup", true),
                    Math.max(1L, sackSection.getLong("capacity-per-item", 640L)),
                    loadItems(sackSection.getConfigurationSection("items"))
            ));
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.sacks", true);
    }

    public List<SackDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(SackDefinition::id))
                .toList();
    }

    public Optional<SackDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<SackItemDefinition> item(SackDefinition sack, String itemId) {
        if (itemId == null) {
            return Optional.empty();
        }
        return sack.items().stream()
                .filter(item -> item.id().equalsIgnoreCase(itemId))
                .findFirst();
    }

    public Optional<SackItemDefinition> item(SackDefinition sack, Material material) {
        return sack.items().stream()
                .filter(item -> item.material() == material)
                .findFirst();
    }

    public long stored(SkyBlockProfile profile, SackDefinition sack, SackItemDefinition item) {
        return profile.sacks()
                .getOrDefault(sack.id(), Map.of())
                .getOrDefault(item.id(), 0L);
    }

    public long totalStored(SkyBlockProfile profile, SackDefinition sack) {
        return sack.items().stream()
                .mapToLong(item -> stored(profile, sack, item))
                .sum();
    }

    public long totalCapacity(SackDefinition sack) {
        return sack.items().stream()
                .mapToLong(sack::capacity)
                .sum();
    }

    public String accessText(Player player, SackDefinition sack) {
        return text.rawMessage(hasAccess(player, sack) ? "sacks.access-available" : "sacks.access-missing");
    }

    public boolean hasAccess(Player player, SackDefinition sack) {
        if (!sack.requireItem()) {
            return true;
        }
        if (sack.itemId() == null || sack.itemId().isBlank()) {
            return true;
        }
        String required = sack.itemId().toUpperCase(Locale.ROOT);
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            if (customItems.definition(itemStack).map(CustomItemDefinition::id).filter(required::equalsIgnoreCase).isPresent()) {
                return true;
            }
        }
        return false;
    }

    public int depositInventory(Player player, String sackId) {
        SackDefinition sack = requireSack(player, sackId).orElse(null);
        if (sack == null || !requireAccess(player, sack)) {
            return 0;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int stored = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack itemStack = contents[slot];
            if (!plainStorable(itemStack)) {
                continue;
            }
            SackItemDefinition item = item(sack, itemStack.getType()).orElse(null);
            if (item == null) {
                continue;
            }
            int accepted = (int) Math.min(itemStack.getAmount(), remainingCapacity(profile, sack, item));
            if (accepted <= 0) {
                continue;
            }
            addStored(profile, sack, item, accepted);
            itemStack.setAmount(itemStack.getAmount() - accepted);
            if (itemStack.getAmount() <= 0) {
                contents[slot] = null;
            }
            stored += accepted;
        }
        player.getInventory().setStorageContents(contents);
        if (stored <= 0) {
            text.send(player, "commands.sack-no-items", List.of(TextService.parsed("sack", sack.displayName())));
            return 0;
        }
        text.send(player, "commands.sack-deposited", List.of(
                TextService.raw("amount", text.formatNumber(stored)),
                TextService.parsed("sack", sack.displayName())
        ));
        return stored;
    }

    public int withdraw(Player player, String sackId, String itemId, int requestedAmount) {
        SackDefinition sack = requireSack(player, sackId).orElse(null);
        if (sack == null || !requireAccess(player, sack)) {
            return 0;
        }
        SackItemDefinition item = item(sack, itemId).orElse(null);
        if (item == null) {
            text.send(player, "commands.sack-unknown-item", List.of(TextService.raw("item", itemId)));
            return 0;
        }
        SkyBlockProfile profile = profiles.profile(player);
        long stored = stored(profile, sack, item);
        if (stored <= 0L) {
            text.send(player, "commands.sack-empty", List.of(
                    TextService.parsed("sack", sack.displayName()),
                    TextService.parsed("item", item.displayName())
            ));
            return 0;
        }
        int amount = (int) Math.min(stored, requestedAmount <= 0 ? stored : requestedAmount);
        removeStored(profile, sack, item, amount);
        giveOrDrop(player, item.material(), amount);
        text.send(player, "commands.sack-withdrawn", List.of(
                TextService.raw("amount", text.formatNumber(amount)),
                TextService.parsed("item", item.displayName()),
                TextService.parsed("sack", sack.displayName())
        ));
        return amount;
    }

    public int storePickup(Player player, ItemStack itemStack) {
        if (!enabled() || !plainStorable(itemStack)) {
            return 0;
        }
        int remaining = itemStack.getAmount();
        int stored = 0;
        SkyBlockProfile profile = profiles.profile(player);
        for (SackDefinition sack : definitions.values()) {
            if (!sack.autoPickup() || !hasAccess(player, sack)) {
                continue;
            }
            SackItemDefinition item = item(sack, itemStack.getType()).orElse(null);
            if (item == null) {
                continue;
            }
            int accepted = (int) Math.min(remaining, remainingCapacity(profile, sack, item));
            if (accepted <= 0) {
                continue;
            }
            addStored(profile, sack, item, accepted);
            stored += accepted;
            remaining -= accepted;
            skills.grantPickupReward(player, item.material(), accepted);
            if (remaining <= 0) {
                break;
            }
        }
        if (stored > 0) {
            text.send(player, "commands.sack-auto-stored", List.of(TextService.raw("amount", text.formatNumber(stored))));
        }
        return stored;
    }

    public void sendSummary(Player player, String sackId) {
        if (!enabled()) {
            text.send(player, "commands.sack-disabled");
            return;
        }
        if (sackId == null || sackId.isBlank()) {
            text.send(player, "commands.sack-summary-header");
            SkyBlockProfile profile = profiles.profile(player);
            for (SackDefinition sack : definitions()) {
                text.send(player, "commands.sack-summary-line", List.of(
                        TextService.raw("sack_id", sack.id()),
                        TextService.parsed("sack", sack.displayName()),
                        TextService.raw("stored", text.formatNumber(totalStored(profile, sack))),
                        TextService.raw("capacity", text.formatNumber(totalCapacity(sack))),
                        TextService.parsed("access", accessText(player, sack))
                ));
            }
            return;
        }
        SackDefinition sack = requireSack(player, sackId).orElse(null);
        if (sack == null) {
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.sack-detail-header", List.of(TextService.parsed("sack", sack.displayName())));
        for (SackItemDefinition item : sack.items()) {
            text.send(player, "commands.sack-detail-line", List.of(
                    TextService.raw("item_id", item.id()),
                    TextService.parsed("item", item.displayName()),
                    TextService.raw("stored", text.formatNumber(stored(profile, sack, item))),
                    TextService.raw("capacity", text.formatNumber(sack.capacity(item)))
            ));
        }
    }

    private Optional<SackDefinition> requireSack(Player player, String sackId) {
        if (!enabled()) {
            text.send(player, "commands.sack-disabled");
            return Optional.empty();
        }
        SackDefinition sack = definition(sackId).orElse(null);
        if (sack == null) {
            text.send(player, "commands.sack-unknown", List.of(TextService.raw("sack", sackId == null ? "" : sackId)));
            return Optional.empty();
        }
        return Optional.of(sack);
    }

    private boolean requireAccess(Player player, SackDefinition sack) {
        if (hasAccess(player, sack)) {
            return true;
        }
        text.send(player, "commands.sack-missing", List.of(TextService.parsed("sack", sack.displayName())));
        return false;
    }

    private long remainingCapacity(SkyBlockProfile profile, SackDefinition sack, SackItemDefinition item) {
        return Math.max(0L, sack.capacity(item) - stored(profile, sack, item));
    }

    private void addStored(SkyBlockProfile profile, SackDefinition sack, SackItemDefinition item, long amount) {
        Map<String, Long> storage = profile.sacks().computeIfAbsent(sack.id(), ignored -> new HashMap<>());
        storage.put(item.id(), Math.max(0L, storage.getOrDefault(item.id(), 0L) + amount));
    }

    private void removeStored(SkyBlockProfile profile, SackDefinition sack, SackItemDefinition item, long amount) {
        Map<String, Long> storage = profile.sacks().computeIfAbsent(sack.id(), ignored -> new HashMap<>());
        long next = Math.max(0L, storage.getOrDefault(item.id(), 0L) - amount);
        if (next <= 0L) {
            storage.remove(item.id());
        } else {
            storage.put(item.id(), next);
        }
        if (storage.isEmpty()) {
            profile.sacks().remove(sack.id());
        }
    }

    private List<SackItemDefinition> loadItems(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        return section.getKeys(false).stream()
                .map(id -> loadItem(id.toUpperCase(Locale.ROOT), section.getConfigurationSection(id)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(SackItemDefinition::id))
                .toList();
    }

    private Optional<SackItemDefinition> loadItem(String id, ConfigurationSection section) {
        if (section == null) {
            return Optional.empty();
        }
        Material material = Material.matchMaterial(section.getString("material", id));
        if (material == null) {
            return Optional.empty();
        }
        return Optional.of(new SackItemDefinition(
                id,
                material,
                section.getString("display-name", id)
        ));
    }

    private boolean plainStorable(ItemStack itemStack) {
        return itemStack != null
                && !itemStack.getType().isAir()
                && itemStack.getAmount() > 0
                && customItems.definition(itemStack).isEmpty();
    }

    private void giveOrDrop(Player player, Material material, int amount) {
        int remaining = amount;
        int stackSize = Math.max(1, material.getMaxStackSize());
        while (remaining > 0) {
            int next = Math.min(remaining, stackSize);
            ItemStack itemStack = new ItemStack(material, next);
            player.getInventory().addItem(itemStack).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            remaining -= next;
        }
    }
}
