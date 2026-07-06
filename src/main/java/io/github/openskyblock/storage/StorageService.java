package io.github.openskyblock.storage;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.menu.StorageHolder;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class StorageService {
    private static final int MAX_PAGE_SIZE = 54;

    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private int pages = 3;
    private int rows = 6;
    private String title = "<dark_gray>Storage Page <page></dark_gray>";

    public StorageService(ConfigService configService, TextService text, ProfileManager profiles) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
    }

    public void reload() {
        this.pages = Math.max(1, Math.min(18, configService.storage().getInt("settings.pages", 3)));
        this.rows = Math.max(1, Math.min(6, configService.storage().getInt("settings.rows", 6)));
        this.title = configService.storage().getString("settings.title", "<dark_gray>Storage Page <page></dark_gray>");
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

    private boolean empty(ItemStack[] contents) {
        for (ItemStack itemStack : contents) {
            if (itemStack != null && !itemStack.getType().isAir()) {
                return false;
            }
        }
        return true;
    }
}
