package io.github.openskyblock.menu;

import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class MinionMenuHolder implements InventoryHolder {
    private final UUID ownerId;
    private final int slot;
    private final Map<Integer, MinionMenuAction> actions;
    private Inventory inventory;

    public MinionMenuHolder(UUID ownerId, int slot, Map<Integer, MinionMenuAction> actions) {
        this.ownerId = ownerId;
        this.slot = slot;
        this.actions = actions;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public int slot() {
        return slot;
    }

    public MinionMenuAction action(int rawSlot) {
        return actions.getOrDefault(rawSlot, MinionMenuAction.NONE);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
