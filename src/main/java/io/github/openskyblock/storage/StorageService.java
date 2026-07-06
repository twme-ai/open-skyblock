package io.github.openskyblock.storage;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.menu.StorageHolder;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class StorageService {
    private static final int MAX_PAGE_SIZE = 54;

    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CustomItemService customItems;
    private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();
    private int pages = 3;
    private int rows = 6;
    private String title = "<dark_gray>Storage Page <page></dark_gray>";
    private String sortMode = "NAME";
    private int searchLimit = 10;

    public StorageService(ConfigService configService, TextService text, ProfileManager profiles, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.customItems = customItems;
    }

    public void reload() {
        this.pages = Math.max(1, Math.min(18, configService.storage().getInt("settings.pages", 3)));
        this.rows = Math.max(1, Math.min(6, configService.storage().getInt("settings.rows", 6)));
        this.title = configService.storage().getString("settings.title", "<dark_gray>Storage Page <page></dark_gray>");
        this.sortMode = configService.storage().getString("settings.sort-mode", "NAME").toUpperCase(Locale.ROOT);
        this.searchLimit = Math.max(1, Math.min(100, configService.storage().getInt("settings.search-results", 10)));
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.storage", true);
    }

    public int pages() {
        return pages;
    }

    public void open(Player player, int requestedPage) {
        if (!enabled()) {
            text.send(player, "commands.storage-disabled");
            return;
        }
        int page = Math.max(1, Math.min(requestedPage, pages));
        StorageHolder holder = new StorageHolder(player.getUniqueId(), page);
        Inventory inventory = Bukkit.createInventory(
                holder,
                rows * 9,
                text.deserialize(title, List.of(
                        TextService.raw("page", Integer.toString(page)),
                        TextService.raw("pages", Integer.toString(pages))
                ))
        );
        holder.inventory(inventory);
        ItemStack[] contents = profiles.profile(player).storagePages().get(page);
        if (contents != null) {
            for (int slot = 0; slot < inventory.getSize() && slot < contents.length; slot++) {
                ItemStack itemStack = contents[slot];
                if (itemStack != null && !itemStack.getType().isAir()) {
                    inventory.setItem(slot, itemStack.clone());
                }
            }
        }
        player.openInventory(inventory);
        text.send(player, "commands.storage-opened", List.of(
                TextService.raw("page", Integer.toString(page)),
                TextService.raw("pages", Integer.toString(pages))
        ));
    }

    public void save(Player player, StorageHolder holder, Inventory inventory) {
        if (!holder.profileId().equals(player.getUniqueId())) {
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        ItemStack[] contents = profile.storagePages().computeIfAbsent(holder.page(), ignored -> new ItemStack[MAX_PAGE_SIZE]);
        for (int slot = 0; slot < inventory.getSize() && slot < contents.length; slot++) {
            ItemStack itemStack = inventory.getItem(slot);
            contents[slot] = itemStack == null || itemStack.getType().isAir() ? null : itemStack.clone();
        }
        if (empty(contents)) {
            profile.storagePages().remove(holder.page());
        }
        profiles.save(player);
    }

    public void sort(Player player, String rawTarget) {
        if (!enabled()) {
            text.send(player, "commands.storage-disabled");
            return;
        }
        if (rawTarget == null || rawTarget.isBlank() || rawTarget.equalsIgnoreCase("all")) {
            sortAll(player);
            return;
        }
        try {
            sortPage(player, Integer.parseInt(rawTarget));
        } catch (NumberFormatException ignored) {
            text.send(player, "commands.storage-usage");
        }
    }

    public void search(Player player, String query) {
        if (!enabled()) {
            text.send(player, "commands.storage-disabled");
            return;
        }
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isBlank()) {
            text.send(player, "commands.storage-usage");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        List<StorageMatch> matches = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            ItemStack[] contents = profile.storagePages().get(page);
            if (contents == null) {
                continue;
            }
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack itemStack = contents[slot];
                if (itemStack == null || itemStack.getType().isAir()) {
                    continue;
                }
                if (searchText(itemStack).contains(normalizedQuery)) {
                    matches.add(new StorageMatch(page, slot + 1, itemStack.clone()));
                }
            }
        }
        if (matches.isEmpty()) {
            text.send(player, "commands.storage-search-empty", List.of(TextService.raw("query", query.trim())));
            return;
        }
        text.send(player, "commands.storage-search-header", List.of(
                TextService.raw("query", query.trim()),
                TextService.raw("matches", Integer.toString(matches.size()))
        ));
        int shown = 0;
        for (StorageMatch match : matches) {
            if (shown >= searchLimit) {
                break;
            }
            ItemStack itemStack = match.itemStack();
            text.send(player, "commands.storage-search-line", List.of(
                    TextService.raw("page", Integer.toString(match.page())),
                    TextService.raw("slot", Integer.toString(match.slot())),
                    TextService.raw("amount", Integer.toString(itemStack.getAmount())),
                    TextService.parsed("item", displayName(itemStack))
            ));
            shown++;
        }
        if (matches.size() > shown) {
            text.send(player, "commands.storage-search-more", List.of(
                    TextService.raw("shown", Integer.toString(shown)),
                    TextService.raw("matches", Integer.toString(matches.size()))
            ));
        }
    }

    private void sortPage(Player player, int requestedPage) {
        int page = Math.max(1, Math.min(requestedPage, pages));
        SkyBlockProfile profile = profiles.profile(player);
        ItemStack[] contents = profile.storagePages().get(page);
        if (contents == null || empty(contents)) {
            text.send(player, "commands.storage-sort-empty", List.of(TextService.raw("page", Integer.toString(page))));
            return;
        }
        List<ItemStack> sorted = sortedStacks(items(contents));
        writePage(profile, page, sorted);
        profiles.save(player);
        text.send(player, "commands.storage-sorted", List.of(
                TextService.raw("page", Integer.toString(page)),
                TextService.raw("items", Integer.toString(totalAmount(sorted)))
        ));
    }

    private void sortAll(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        List<ItemStack> allItems = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            allItems.addAll(items(profile.storagePages().get(page)));
        }
        if (allItems.isEmpty()) {
            text.send(player, "commands.storage-sort-empty", List.of(TextService.raw("page", "all")));
            return;
        }
        List<ItemStack> sorted = sortedStacks(allItems);
        for (int page = 1; page <= pages; page++) {
            int from = Math.min(sorted.size(), (page - 1) * MAX_PAGE_SIZE);
            int to = Math.min(sorted.size(), page * MAX_PAGE_SIZE);
            writePage(profile, page, sorted.subList(from, to));
        }
        profiles.save(player);
        text.send(player, "commands.storage-sorted-all", List.of(TextService.raw("items", Integer.toString(totalAmount(sorted)))));
    }

    private List<ItemStack> items(ItemStack[] contents) {
        List<ItemStack> items = new ArrayList<>();
        if (contents == null) {
            return items;
        }
        for (ItemStack itemStack : contents) {
            if (itemStack != null && !itemStack.getType().isAir()) {
                items.add(itemStack.clone());
            }
        }
        return items;
    }

    private List<ItemStack> sortedStacks(List<ItemStack> items) {
        List<ItemStack> compacted = compact(items);
        compacted.sort(sortComparator());
        return compacted;
    }

    private List<ItemStack> compact(List<ItemStack> items) {
        List<ItemStack> compacted = new ArrayList<>();
        for (ItemStack source : items) {
            ItemStack remaining = source.clone();
            while (remaining.getAmount() > 0) {
                ItemStack target = firstStackable(compacted, remaining);
                if (target == null) {
                    int amount = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
                    ItemStack created = remaining.clone();
                    created.setAmount(amount);
                    compacted.add(created);
                    remaining.setAmount(remaining.getAmount() - amount);
                    continue;
                }
                int space = target.getMaxStackSize() - target.getAmount();
                int moved = Math.min(space, remaining.getAmount());
                target.setAmount(target.getAmount() + moved);
                remaining.setAmount(remaining.getAmount() - moved);
            }
        }
        return compacted;
    }

    private ItemStack firstStackable(List<ItemStack> items, ItemStack source) {
        for (ItemStack itemStack : items) {
            if (itemStack.getAmount() < itemStack.getMaxStackSize() && itemStack.isSimilar(source)) {
                return itemStack;
            }
        }
        return null;
    }

    private Comparator<ItemStack> sortComparator() {
        Comparator<ItemStack> byType = Comparator
                .comparing((ItemStack itemStack) -> customItems.definition(itemStack).map(CustomItemDefinition::id).orElse(itemStack.getType().name()))
                .thenComparing(this::plainDisplayName)
                .thenComparingInt(ItemStack::getAmount);
        Comparator<ItemStack> byName = Comparator
                .comparing(this::plainDisplayName)
                .thenComparing(itemStack -> itemStack.getType().name())
                .thenComparingInt(ItemStack::getAmount);
        return sortMode.equals("TYPE") ? byType : byName;
    }

    private void writePage(SkyBlockProfile profile, int page, List<ItemStack> items) {
        ItemStack[] contents = new ItemStack[MAX_PAGE_SIZE];
        for (int index = 0; index < items.size() && index < contents.length; index++) {
            contents[index] = items.get(index).clone();
        }
        if (empty(contents)) {
            profile.storagePages().remove(page);
        } else {
            profile.storagePages().put(page, contents);
        }
    }

    private int totalAmount(List<ItemStack> items) {
        return items.stream().mapToInt(ItemStack::getAmount).sum();
    }

    private String searchText(ItemStack itemStack) {
        StringBuilder builder = new StringBuilder();
        builder.append(itemStack.getType().name()).append(' ');
        customItems.definition(itemStack).ifPresent(definition -> builder
                .append(definition.id()).append(' ')
                .append(definition.displayName()).append(' ')
                .append(definition.category()).append(' '));
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                builder.append(plainText.serialize(meta.displayName())).append(' ');
            }
            if (meta.hasLore() && meta.lore() != null) {
                for (net.kyori.adventure.text.Component line : meta.lore()) {
                    builder.append(plainText.serialize(line)).append(' ');
                }
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private String displayName(ItemStack itemStack) {
        return customItems.definition(itemStack)
                .map(CustomItemDefinition::displayName)
                .orElseGet(() -> readableMaterial(itemStack.getType()));
    }

    private String plainDisplayName(ItemStack itemStack) {
        return customItems.definition(itemStack)
                .map(CustomItemDefinition::displayName)
                .orElseGet(() -> {
                    ItemMeta meta = itemStack.getItemMeta();
                    if (meta != null && meta.hasDisplayName()) {
                        return plainText.serialize(meta.displayName());
                    }
                    return readableMaterial(itemStack.getType());
                })
                .toLowerCase(Locale.ROOT);
    }

    private String readableMaterial(Material material) {
        String value = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (value.isBlank()) {
            return material.name();
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private boolean empty(ItemStack[] contents) {
        for (ItemStack itemStack : contents) {
            if (itemStack != null && !itemStack.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private record StorageMatch(int page, int slot, ItemStack itemStack) {
    }
}
