package io.github.openskyblock.backpack;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.menu.BackpackHolder;
import io.github.openskyblock.profile.OwnedBackpack;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class BackpackService {
    private static final int MAX_CONTENTS = 54;

    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final NamespacedKey backpackIdKey;
    private final Map<String, BackpackDefinition> definitions = new HashMap<>();
    private int slots = 18;
    private String title = "<dark_gray>Backpack <slot>: <backpack></dark_gray>";

    public BackpackService(JavaPlugin plugin, ConfigService configService, TextService text, ProfileManager profiles) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.backpackIdKey = new NamespacedKey(plugin, "backpack_id");
    }

    public void reload() {
        definitions.clear();
        this.slots = Math.max(1, Math.min(36, configService.backpacks().getInt("settings.slots", 18)));
        this.title = configService.backpacks().getString("settings.title", "<dark_gray>Backpack <slot>: <backpack></dark_gray>");
        ConfigurationSection section = configService.backpacks().getConfigurationSection("definitions");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection backpack = section.getConfigurationSection(id);
            if (backpack == null) {
                continue;
            }
            Material material = Material.matchMaterial(backpack.getString("material", "CHEST"));
            String normalized = id.toUpperCase(Locale.ROOT);
            definitions.put(normalized, new BackpackDefinition(
                    normalized,
                    material == null || !material.isItem() || material.isAir() ? Material.CHEST : material,
                    backpack.getString("display-name", id),
                    Math.max(1, Math.min(6, backpack.getInt("rows", 3)))
            ));
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.backpacks", true);
    }

    public int slots() {
        return slots;
    }

    public List<BackpackDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparingInt(BackpackDefinition::rows).thenComparing(BackpackDefinition::id))
                .toList();
    }

    public Optional<BackpackDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<BackpackDefinition> definition(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return Optional.empty();
        }
        String id = itemStack.getItemMeta().getPersistentDataContainer().get(backpackIdKey, PersistentDataType.STRING);
        return definition(id);
    }

    public ItemStack createItem(BackpackDefinition definition) {
        ItemStack itemStack = new ItemStack(definition.material());
        ItemMeta meta = itemStack.getItemMeta();
        meta.getPersistentDataContainer().set(backpackIdKey, PersistentDataType.STRING, definition.id());
        meta.displayName(text.deserialize(definition.displayName()));
        meta.lore(configService.messages().getStringList("backpacks.item-lore").stream()
                .map(line -> text.deserialize(line, List.of(
                        TextService.parsed("backpack", definition.displayName()),
                        TextService.raw("rows", Integer.toString(definition.rows())),
                        TextService.raw("slots", Integer.toString(definition.rows() * 9))
                )))
                .toList());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public void give(Player target, BackpackDefinition definition) {
        giveOrDrop(target, createItem(definition));
    }

    public boolean installHeld(Player player) {
        if (!enabled()) {
            text.send(player, "commands.backpack-disabled");
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        BackpackDefinition definition = definition(held).orElse(null);
        if (definition == null) {
            text.send(player, "commands.backpack-held-missing");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int slot = firstEmptySlot(profile);
        if (slot < 0) {
            text.send(player, "commands.backpack-full", List.of(TextService.raw("slots", Integer.toString(slots))));
            return false;
        }
        ItemStack[] contents = new ItemStack[MAX_CONTENTS];
        profile.backpacks().put(slot, new OwnedBackpack(slot, definition.id(), contents));
        held.setAmount(held.getAmount() - 1);
        if (held.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
        profiles.save(player);
        text.send(player, "commands.backpack-installed", List.of(
                TextService.raw("slot", Integer.toString(slot)),
                TextService.parsed("backpack", definition.displayName())
        ));
        return true;
    }

    public void open(Player player, int requestedSlot) {
        if (!enabled()) {
            text.send(player, "commands.backpack-disabled");
            return;
        }
        if (requestedSlot < 1 || requestedSlot > slots) {
            text.send(player, "commands.backpack-invalid-slot", List.of(TextService.raw("slots", Integer.toString(slots))));
            return;
        }
        OwnedBackpack backpack = profiles.profile(player).backpacks().get(requestedSlot);
        if (backpack == null) {
            text.send(player, "commands.backpack-empty-slot", List.of(TextService.raw("slot", Integer.toString(requestedSlot))));
            return;
        }
        BackpackDefinition definition = definition(backpack.id()).orElse(null);
        if (definition == null) {
            text.send(player, "commands.backpack-unknown", List.of(TextService.raw("backpack", backpack.id())));
            return;
        }
        BackpackHolder holder = new BackpackHolder(player.getUniqueId(), requestedSlot);
        Inventory inventory = Bukkit.createInventory(
                holder,
                definition.rows() * 9,
                text.deserialize(title, List.of(
                        TextService.raw("slot", Integer.toString(requestedSlot)),
                        TextService.parsed("backpack", definition.displayName())
                ))
        );
        holder.inventory(inventory);
        ItemStack[] contents = backpack.contents();
        for (int slot = 0; slot < inventory.getSize() && slot < contents.length; slot++) {
            ItemStack itemStack = contents[slot];
            if (itemStack != null && !itemStack.getType().isAir()) {
                inventory.setItem(slot, itemStack.clone());
            }
        }
        player.openInventory(inventory);
    }

    public void save(Player player, BackpackHolder holder, Inventory inventory) {
        if (!holder.profileId().equals(player.getUniqueId())) {
            return;
        }
        OwnedBackpack backpack = profiles.profile(player).backpacks().get(holder.slot());
        if (backpack == null) {
            return;
        }
        ItemStack[] contents = backpack.contents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack itemStack = slot < inventory.getSize() ? inventory.getItem(slot) : null;
            contents[slot] = itemStack == null || itemStack.getType().isAir() ? null : itemStack.clone();
        }
        profiles.save(player);
    }

    public void remove(Player player, int slot) {
        if (!enabled()) {
            text.send(player, "commands.backpack-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        OwnedBackpack backpack = profile.backpacks().get(slot);
        if (backpack == null) {
            text.send(player, "commands.backpack-empty-slot", List.of(TextService.raw("slot", Integer.toString(slot))));
            return;
        }
        if (!empty(backpack.contents())) {
            text.send(player, "commands.backpack-not-empty", List.of(TextService.raw("slot", Integer.toString(slot))));
            return;
        }
        BackpackDefinition definition = definition(backpack.id()).orElse(null);
        profile.backpacks().remove(slot);
        if (definition != null) {
            giveOrDrop(player, createItem(definition));
        }
        profiles.save(player);
        text.send(player, "commands.backpack-removed", List.of(
                TextService.raw("slot", Integer.toString(slot)),
                TextService.parsed("backpack", definition == null ? backpack.id() : definition.displayName())
        ));
    }

    public void sendList(Player player) {
        if (!enabled()) {
            text.send(player, "commands.backpack-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.backpack-list-header");
        for (int slot = 1; slot <= slots; slot++) {
            OwnedBackpack backpack = profile.backpacks().get(slot);
            if (backpack == null) {
                text.send(player, "commands.backpack-list-line", List.of(
                        TextService.raw("slot", Integer.toString(slot)),
                        TextService.parsed("status", text.rawMessage("backpacks.empty-slot"))
                ));
                continue;
            }
            BackpackDefinition definition = definition(backpack.id()).orElse(null);
            long used = used(backpack.contents());
            int capacity = definition == null ? 0 : definition.rows() * 9;
            text.send(player, "commands.backpack-list-line", List.of(
                    TextService.raw("slot", Integer.toString(slot)),
                    TextService.parsed("status", text.rawMessage("backpacks.installed-slot")
                            .replace("<used>", Long.toString(used))
                            .replace("<capacity>", Integer.toString(capacity))
                            .replace("<backpack>", definition == null ? backpack.id() : definition.displayName()))
            ));
        }
    }

    private int firstEmptySlot(SkyBlockProfile profile) {
        for (int slot = 1; slot <= slots; slot++) {
            if (!profile.backpacks().containsKey(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean empty(ItemStack[] contents) {
        for (ItemStack itemStack : contents) {
            if (itemStack != null && !itemStack.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private long used(ItemStack[] contents) {
        long used = 0L;
        for (ItemStack itemStack : contents) {
            if (itemStack != null && !itemStack.getType().isAir()) {
                used++;
            }
        }
        return used;
    }

    private void giveOrDrop(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
