package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public final class DragonListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public DragonListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDragonDamage(EntityDamageEvent event) {
        if (!plugin.dragons().isLiveDragon(event.getEntity())) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent entityDamage) {
            Player player = playerDamager(entityDamage);
            if (player != null) {
                plugin.dragons().damageLiveDragon(event.getEntity(), player, Math.max(0.0D, event.getFinalDamage()));
            }
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!plugin.dragons().isLiveDragon(event.getEntity())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        plugin.dragons().handleExternalDeath(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDragonExplode(EntityExplodeEvent event) {
        if (plugin.dragons().isLiveDragon(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDragonChangeBlock(EntityChangeBlockEvent event) {
        if (plugin.dragons().isLiveDragon(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private Player playerDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
