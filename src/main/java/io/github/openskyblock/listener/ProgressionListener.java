package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.pet.AutoPetTrigger;
import io.github.openskyblock.stats.StatSnapshot;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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
        plugin.farmingContests().recordCrop(event.getPlayer(), event.getBlock().getType(), 1L);
        plugin.pets().triggerAutoPet(event.getPlayer(), AutoPetTrigger.BLOCK_BREAK);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        plugin.pets().triggerAutoPet(killer, AutoPetTrigger.KILL);
        if (plugin.mobs().enabled() && plugin.mobs().definition(event.getEntity()).isPresent()) {
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
        plugin.skills().grantPickupReward(player, itemStack.getType(), itemStack.getAmount());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCustomItemDamage(EntityDamageByEntityEvent event) {
        if (!plugin.configService().main().getBoolean("features.combat-stats", true)) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        StatSnapshot stats = plugin.stats().snapshot(player);
        double damage = Math.max(1.0D, stats.damage());
        double multiplier = 1.0D + Math.max(0.0D, stats.strength()) / 100.0D;
        double customDamage = damage * multiplier;
        if (ThreadLocalRandom.current().nextDouble(100.0D) < Math.max(0.0D, stats.critChance())) {
            customDamage *= 1.0D + Math.max(0.0D, stats.critDamage()) / 100.0D;
        }
        event.setDamage(customDamage);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerDefense(EntityDamageEvent event) {
        if (!plugin.configService().main().getBoolean("features.combat-stats", true)) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        double defense = Math.max(0.0D, plugin.stats().snapshot(player).defense());
        event.setDamage(event.getDamage() * (100.0D / (100.0D + defense)));
    }
}
