package io.github.openskyblock.ability;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.service.AbilityDefinition;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.stats.StatService;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class ItemAbilityService {
    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final TextService text;
    private final CustomItemService customItems;
    private final StatService stats;
    private final Map<UUID, Double> mana = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private double baseMana = 100.0D;
    private double manaPerIntelligence = 1.0D;
    private double manaRegenFlat = 10.0D;
    private double manaRegenPercent = 0.02D;
    private double maxTeleportDistance = 12.0D;
    private boolean actionBarEnabled = true;

    public ItemAbilityService(JavaPlugin plugin, ConfigService configService, TextService text, CustomItemService customItems, StatService stats) {
        this.plugin = plugin;
        this.configService = configService;
        this.text = text;
        this.customItems = customItems;
        this.stats = stats;
    }

    public void reload() {
        this.baseMana = Math.max(0.0D, configService.main().getDouble("item-abilities.base-mana", 100.0D));
        this.manaPerIntelligence = Math.max(0.0D, configService.main().getDouble("item-abilities.mana-per-intelligence", 1.0D));
        this.manaRegenFlat = Math.max(0.0D, configService.main().getDouble("item-abilities.mana-regen-flat", 10.0D));
        this.manaRegenPercent = Math.max(0.0D, configService.main().getDouble("item-abilities.mana-regen-percent", 0.02D));
        this.maxTeleportDistance = Math.max(0.0D, configService.main().getDouble("item-abilities.max-teleport-distance", 12.0D));
        this.actionBarEnabled = configService.main().getBoolean("item-abilities.action-bar", true);
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.item-abilities", true);
    }

    public boolean activate(Player player, ItemStack itemStack) {
        if (!enabled()) {
            return false;
        }
        CustomItemDefinition item = customItems.definition(itemStack).orElse(null);
        if (item == null || item.ability() == null) {
            return false;
        }
        AbilityDefinition ability = item.ability();
        if (!isRightClickAbility(ability) || action(ability).isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long cooldownEnd = cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .getOrDefault(item.id(), 0L);
        if (cooldownEnd > now) {
            text.send(player, "items.ability-cooldown", List.of(
                    TextService.raw("ability", ability.name()),
                    TextService.raw("remaining", text.formatNumber((cooldownEnd - now) / 1000.0D))
            ));
            return true;
        }
        double maxMana = maxMana(player);
        double currentMana = currentMana(player, maxMana);
        if (ability.manaCost() > currentMana) {
            text.send(player, "items.ability-no-mana", List.of(
                    TextService.raw("ability", ability.name()),
                    TextService.raw("mana_cost", text.formatNumber(ability.manaCost())),
                    TextService.raw("mana", text.formatNumber(currentMana)),
                    TextService.raw("max_mana", text.formatNumber(maxMana))
            ));
            showMana(player);
            return true;
        }
        AbilityResult result = execute(player, ability);
        if (!result.success()) {
            if (!result.messagePath().isBlank()) {
                text.send(player, result.messagePath(), List.of(TextService.raw("ability", ability.name())));
            }
            return true;
        }
        mana.put(player.getUniqueId(), Math.max(0.0D, currentMana - ability.manaCost()));
        if (ability.cooldownSeconds() > 0) {
            cooldowns.get(player.getUniqueId()).put(item.id(), now + ability.cooldownSeconds() * 1000L);
            player.setCooldown(item.material(), ability.cooldownSeconds() * 20);
        }
        text.send(player, "items.ability-used", List.of(
                TextService.raw("ability", ability.name()),
                TextService.raw("mana", text.formatNumber(mana.get(player.getUniqueId()))),
                TextService.raw("max_mana", text.formatNumber(maxMana))
        ));
        showMana(player);
        return true;
    }

    public void tickMana() {
        if (!enabled()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            double maxMana = maxMana(player);
            double current = currentMana(player, maxMana);
            double regen = manaRegenFlat + maxMana * manaRegenPercent;
            mana.put(player.getUniqueId(), Math.min(maxMana, current + regen));
            if (actionBarEnabled) {
                showMana(player);
            }
        }
    }

    private AbilityResult execute(Player player, AbilityDefinition ability) {
        return switch (action(ability)) {
            case "SPEED_BOOST" -> speedBoost(player, ability);
            case "TELEPORT" -> teleport(player, ability);
            case "HEAL" -> heal(player, ability);
            case "WITHER_IMPACT" -> witherImpact(player, ability);
            default -> new AbilityResult(false, "items.ability-unknown");
        };
    }

    private AbilityResult speedBoost(Player player, AbilityDefinition ability) {
        applySpeed(player, ability.parameter("speed_amplifier", 1.0D), ability.parameter("duration_seconds", 20.0D));
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.6F, 1.4F);
        return AbilityResult.SUCCESS;
    }

    private AbilityResult teleport(Player player, AbilityDefinition ability) {
        double distance = Math.min(maxTeleportDistance, Math.max(0.0D, ability.parameter("distance", 8.0D)));
        Location target = teleportDestination(player, distance);
        if (target == null) {
            return new AbilityResult(false, "items.ability-no-teleport");
        }
        player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (ability.parameter("speed_duration_seconds", 0.0D) > 0.0D) {
            applySpeed(player, ability.parameter("speed_amplifier", 1.0D), ability.parameter("speed_duration_seconds", 3.0D));
        }
        player.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7F, 1.2F);
        player.getWorld().spawnParticle(Particle.PORTAL, target, 32, 0.4D, 0.8D, 0.4D, 0.05D);
        return AbilityResult.SUCCESS;
    }

    private AbilityResult heal(Player player, AbilityDefinition ability) {
        heal(player, ability.parameter("heal", 40.0D));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.8F);
        return AbilityResult.SUCCESS;
    }

    private AbilityResult witherImpact(Player player, AbilityDefinition ability) {
        double teleportDistance = Math.min(maxTeleportDistance, Math.max(0.0D, ability.parameter("teleport_distance", 0.0D)));
        if (teleportDistance > 0.0D) {
            Location target = teleportDestination(player, teleportDistance);
            if (target == null) {
                return new AbilityResult(false, "items.ability-no-teleport");
            }
            player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
        double radius = Math.max(0.0D, ability.parameter("radius", 6.0D));
        double damage = Math.max(0.0D, ability.parameter("damage", 500.0D));
        if (radius > 0.0D && damage > 0.0D) {
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity livingEntity && !(livingEntity instanceof Player)) {
                    livingEntity.damage(damage, player);
                }
            }
        }
        heal(player, ability.parameter("heal", 0.0D));
        World world = player.getWorld();
        world.spawnParticle(Particle.EXPLOSION, player.getLocation(), 2, 0.8D, 0.4D, 0.8D, 0.02D);
        world.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.9F, 1.0F);
        return AbilityResult.SUCCESS;
    }

    private void applySpeed(Player player, double amplifier, double durationSeconds) {
        int ticks = Math.max(1, (int) Math.round(durationSeconds * 20.0D));
        int potionAmplifier = Math.max(0, (int) Math.round(amplifier));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, potionAmplifier, true, false, true));
    }

    private void heal(Player player, double amount) {
        if (amount <= 0.0D) {
            return;
        }
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) == null
                ? player.getMaxHealth()
                : player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(maxHealth, player.getHealth() + amount));
    }

    private Location teleportDestination(Player player, double distance) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, direction, distance, FluidCollisionMode.NEVER, true);
        double safeDistance = hit == null ? distance : Math.max(0.0D, hit.getHitPosition().distance(eye.toVector()) - 0.6D);
        Location lastSafe = null;
        for (double offset = 1.0D; offset <= safeDistance; offset += 0.5D) {
            Location candidate = player.getLocation().add(direction.clone().multiply(offset));
            candidate.setYaw(player.getLocation().getYaw());
            candidate.setPitch(player.getLocation().getPitch());
            if (isSafe(candidate)) {
                lastSafe = candidate;
            }
        }
        return lastSafe;
    }

    private boolean isSafe(Location location) {
        Block feet = location.getBlock();
        Block head = location.clone().add(0.0D, 1.0D, 0.0D).getBlock();
        return feet.isPassable() && head.isPassable();
    }

    private boolean isRightClickAbility(AbilityDefinition ability) {
        return ability.type() != null && ability.type().toUpperCase(Locale.ROOT).contains("RIGHT CLICK");
    }

    private String action(AbilityDefinition ability) {
        String action = ability.action();
        if (action == null || action.isBlank()) {
            return "";
        }
        return action.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private double maxMana(Player player) {
        return baseMana + Math.max(0.0D, stats.snapshot(player).intelligence()) * manaPerIntelligence;
    }

    private double currentMana(Player player, double maxMana) {
        return Math.min(maxMana, mana.computeIfAbsent(player.getUniqueId(), ignored -> maxMana));
    }

    private void showMana(Player player) {
        if (!actionBarEnabled) {
            return;
        }
        double maxMana = maxMana(player);
        double current = currentMana(player, maxMana);
        player.sendActionBar(text.message("items.mana-actionbar", List.of(
                TextService.raw("mana", text.formatNumber(current)),
                TextService.raw("max_mana", text.formatNumber(maxMana))
        )));
    }

    private record AbilityResult(boolean success, String messagePath) {
        private static final AbilityResult SUCCESS = new AbilityResult(true, "");
    }
}
