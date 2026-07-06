package io.github.openskyblock.quiver;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
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
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuiverService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CustomItemService customItems;
    private final SkillService skills;
    private final NamespacedKey proxyArrowKey;
    private final Map<String, QuiverItemDefinition> definitions = new LinkedHashMap<>();
    private int slots = 27;
    private int stackSize = 64;
    private boolean autoPickup = true;

    public QuiverService(JavaPlugin plugin, ConfigService configService, TextService text, ProfileManager profiles, CustomItemService customItems, SkillService skills) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.customItems = customItems;
        this.skills = skills;
        this.proxyArrowKey = new NamespacedKey(plugin, "quiver_proxy_arrow");
    }

    public void reload() {
        definitions.clear();
        this.slots = Math.max(1, Math.min(54, configService.quiver().getInt("settings.slots", 27)));
        this.stackSize = Math.max(1, configService.quiver().getInt("settings.stack-size", 64));
        this.autoPickup = configService.quiver().getBoolean("settings.auto-pickup", true);
        ConfigurationSection section = configService.quiver().getConfigurationSection("items");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(id);
            if (itemSection == null) {
                continue;
            }
            Material material = Material.matchMaterial(itemSection.getString("material", id));
            if (material == null) {
                continue;
            }
            definitions.put(id.toUpperCase(Locale.ROOT), new QuiverItemDefinition(
                    id.toUpperCase(Locale.ROOT),
                    material,
                    itemSection.getString("display-name", id),
                    itemSection.getInt("priority", 0)
            ));
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.quiver", true);
    }

    public List<QuiverItemDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(QuiverItemDefinition::id))
                .toList();
    }

    public Optional<QuiverItemDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<QuiverItemDefinition> definition(Material material) {
        return definitions.values().stream()
                .filter(item -> item.material() == material)
                .findFirst();
    }

    public long capacity() {
        return (long) slots * stackSize;
    }

    public long totalStored(SkyBlockProfile profile) {
        return profile.quiver().values().stream().mapToLong(Long::longValue).sum();
    }

    public long stored(SkyBlockProfile profile, QuiverItemDefinition item) {
        return profile.quiver().getOrDefault(item.id(), 0L);
    }

    public Optional<QuiverItemDefinition> selectedDefinition(SkyBlockProfile profile) {
        String selected = profile.selectedQuiverItem();
        if (selected != null && !selected.isBlank()) {
            QuiverItemDefinition definition = definition(selected).orElse(null);
            if (definition != null && stored(profile, definition) > 0L) {
                return Optional.of(definition);
            }
        }
        return definitions.values().stream()
                .filter(item -> stored(profile, item) > 0L)
                .max(Comparator.comparingInt(QuiverItemDefinition::priority).thenComparing(QuiverItemDefinition::id));
    }

    public boolean select(Player player, String itemId) {
        if (!enabled()) {
            text.send(player, "commands.quiver-disabled");
            return false;
        }
        QuiverItemDefinition item = definition(itemId).orElse(null);
        if (item == null) {
            text.send(player, "commands.quiver-unknown-item", List.of(TextService.raw("item", itemId == null ? "" : itemId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        profile.selectedQuiverItem(item.id());
        text.send(player, "commands.quiver-selected", List.of(TextService.parsed("item", item.displayName())));
        return true;
    }

    public int depositInventory(Player player) {
        if (!enabled()) {
            text.send(player, "commands.quiver-disabled");
            return 0;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int deposited = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack itemStack = contents[slot];
            if (!plainStorable(itemStack)) {
                continue;
            }
            QuiverItemDefinition item = definition(itemStack.getType()).orElse(null);
            if (item == null) {
                continue;
            }
            int accepted = (int) Math.min(itemStack.getAmount(), remainingCapacity(profile));
            if (accepted <= 0) {
                break;
            }
            addStored(profile, item, accepted);
            itemStack.setAmount(itemStack.getAmount() - accepted);
            if (itemStack.getAmount() <= 0) {
                contents[slot] = null;
            }
            deposited += accepted;
        }
        player.getInventory().setStorageContents(contents);
        if (deposited <= 0) {
            text.send(player, "commands.quiver-no-arrows");
            return 0;
        }
        text.send(player, "commands.quiver-deposited", List.of(TextService.raw("amount", text.formatNumber(deposited))));
        return deposited;
    }

    public int withdraw(Player player, String itemId, int requestedAmount) {
        if (!enabled()) {
            text.send(player, "commands.quiver-disabled");
            return 0;
        }
        QuiverItemDefinition item = definition(itemId).orElse(null);
        if (item == null) {
            text.send(player, "commands.quiver-unknown-item", List.of(TextService.raw("item", itemId == null ? "" : itemId)));
            return 0;
        }
        SkyBlockProfile profile = profiles.profile(player);
        long stored = stored(profile, item);
        if (stored <= 0L) {
            text.send(player, "commands.quiver-empty", List.of(TextService.parsed("item", item.displayName())));
            return 0;
        }
        int amount = (int) Math.min(stored, requestedAmount <= 0 ? stored : requestedAmount);
        removeStored(profile, item, amount);
        giveOrDrop(player, item.material(), amount);
        text.send(player, "commands.quiver-withdrawn", List.of(
                TextService.raw("amount", text.formatNumber(amount)),
                TextService.parsed("item", item.displayName())
        ));
        return amount;
    }

    public int storePickup(Player player, ItemStack itemStack) {
        if (!enabled() || !autoPickup || !plainStorable(itemStack)) {
            return 0;
        }
        QuiverItemDefinition item = definition(itemStack.getType()).orElse(null);
        if (item == null) {
            return 0;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int accepted = (int) Math.min(itemStack.getAmount(), remainingCapacity(profile));
        if (accepted <= 0) {
            return 0;
        }
        addStored(profile, item, accepted);
        skills.grantPickupReward(player, item.material(), accepted);
        text.send(player, "commands.quiver-auto-stored", List.of(TextService.raw("amount", text.formatNumber(accepted))));
        return accepted;
    }

    public boolean prepareShot(Player player) {
        if (!enabled()) {
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (selectedDefinition(profile).isEmpty()) {
            return false;
        }
        if (hasProxyArrow(player) || hasRegularArrow(player)) {
            return true;
        }
        int slot = player.getInventory().firstEmpty();
        if (slot < 0) {
            return false;
        }
        player.getInventory().setItem(slot, proxyArrow());
        return true;
    }

    public boolean consumeShot(Player player, ItemStack consumable) {
        if (!enabled() || !isProxyArrow(consumable)) {
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        QuiverItemDefinition item = selectedDefinition(profile).orElse(null);
        if (item == null) {
            text.send(player, "commands.quiver-empty-shot");
            return false;
        }
        removeStored(profile, item, 1L);
        return true;
    }

    public void removeProxyArrows(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (int slot = 0; slot < contents.length; slot++) {
            if (isProxyArrow(contents[slot])) {
                contents[slot] = null;
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setStorageContents(contents);
        }
    }

    public boolean isProxyArrow(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().has(proxyArrowKey, PersistentDataType.BYTE);
    }

    public void sendSummary(Player player) {
        if (!enabled()) {
            text.send(player, "commands.quiver-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        String selected = selectedDefinition(profile)
                .map(QuiverItemDefinition::displayName)
                .orElse(text.rawMessage("quiver.selected-none"));
        text.send(player, "commands.quiver-summary", List.of(
                TextService.raw("stored", text.formatNumber(totalStored(profile))),
                TextService.raw("capacity", text.formatNumber(capacity())),
                TextService.parsed("selected", selected)
        ));
        for (QuiverItemDefinition item : definitions()) {
            text.send(player, "commands.quiver-line", List.of(
                    TextService.raw("item_id", item.id()),
                    TextService.parsed("item", item.displayName()),
                    TextService.raw("stored", text.formatNumber(stored(profile, item)))
            ));
        }
    }

    private long remainingCapacity(SkyBlockProfile profile) {
        return Math.max(0L, capacity() - totalStored(profile));
    }

    private void addStored(SkyBlockProfile profile, QuiverItemDefinition item, long amount) {
        profile.quiver().put(item.id(), Math.max(0L, profile.quiver().getOrDefault(item.id(), 0L) + amount));
        if (profile.selectedQuiverItem() == null || profile.selectedQuiverItem().isBlank()) {
            profile.selectedQuiverItem(item.id());
        }
    }

    private void removeStored(SkyBlockProfile profile, QuiverItemDefinition item, long amount) {
        long next = Math.max(0L, profile.quiver().getOrDefault(item.id(), 0L) - amount);
        if (next <= 0L) {
            profile.quiver().remove(item.id());
        } else {
            profile.quiver().put(item.id(), next);
        }
    }

    private boolean plainStorable(ItemStack itemStack) {
        return itemStack != null
                && !itemStack.getType().isAir()
                && itemStack.getAmount() > 0
                && !isProxyArrow(itemStack)
                && customItems.definition(itemStack).isEmpty();
    }

    private boolean hasProxyArrow(Player player) {
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            if (isProxyArrow(itemStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRegularArrow(Player player) {
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            if (plainStorable(itemStack) && definition(itemStack.getType()).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private ItemStack proxyArrow() {
        ItemStack itemStack = new ItemStack(Material.ARROW);
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(text.deserialize(text.rawMessage("quiver.proxy-arrow-name")));
        meta.getPersistentDataContainer().set(proxyArrowKey, PersistentDataType.BYTE, (byte) 1);
        itemStack.setItemMeta(meta);
        return itemStack;
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
