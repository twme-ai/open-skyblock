package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.service.ActionReward;
import io.github.openskyblock.service.CustomItemDefinition;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

public final class ProgressionListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public ProgressionListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getBlockData() instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }
        plugin.skills()
                .blockReward(event.getBlock().getType())
                .ifPresent(reward -> plugin.skills().grantActionReward(event.getPlayer(), reward));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        plugin.skills()
                .entityReward(event.getEntityType())
                .ifPresent(reward -> plugin.skills().grantActionReward(killer, reward));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPickup(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }
        ItemStack itemStack = event.getItem().getItemStack();
        plugin.skills().pickupReward(itemStack.getType()).ifPresent(reward -> {
            long amount = Math.max(1L, itemStack.getAmount());
            ActionReward scaled = new ActionReward(
                    reward.skillType(),
                    reward.skillXp() * amount,
                    reward.collectionId(),
                    reward.collectionAmount() * amount
            );
            plugin.skills().grantActionReward(player, scaled);
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCustomItemDamage(EntityDamageByEntityEvent event) {
        if (!plugin.configService().main().getBoolean("features.combat-stats", true)) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        CustomItemDefinition definition = plugin.customItems()
                .definition(player.getInventory().getItemInMainHand())
                .orElse(null);
        if (definition == null) {
            return;
        }
        double itemDamage = definition.stat("damage");
        double strength = definition.stat("strength");
        double multiplier = 1.0D + Math.max(0.0D, strength) / 100.0D;
        event.setDamage(event.getDamage() + itemDamage * multiplier);
    }
}
