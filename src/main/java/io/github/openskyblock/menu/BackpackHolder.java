package io.github.openskyblock.menu;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class BackpackHolder implements InventoryHolder {
    private final UUID profileId;
    private final int slot;
    private Inventory inventory;

    public BackpackHolder(UUID profileId, int slot) {
        this.profileId = profileId;
        this.slot = slot;
    }

    public UUID profileId() {
        return profileId;
    }

    public int slot() {
        return slot;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
