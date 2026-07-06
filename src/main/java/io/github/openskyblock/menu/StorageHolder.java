package io.github.openskyblock.menu;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class StorageHolder implements InventoryHolder {
    private final UUID profileId;
    private final int page;
    private Inventory inventory;

    public StorageHolder(UUID profileId, int page) {
        this.profileId = profileId;
        this.page = page;
    }

    public UUID profileId() {
        return profileId;
    }

    public int page() {
        return page;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
