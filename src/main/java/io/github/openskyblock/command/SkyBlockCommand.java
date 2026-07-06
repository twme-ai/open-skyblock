package io.github.openskyblock.command;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.PlacedMinion;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CollectionDefinition;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.MinionDefinition;
import io.github.openskyblock.service.SkillDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SkyBlockCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_SUBCOMMANDS = List.of(
            "help",
            "menu",
            "island",
            "profile",
            "purse",
            "skills",
            "collections",
            "giveitem",
            "minion",
            "reload"
    );

    private final OpenSkyBlockPlugin plugin;

    public SkyBlockCommand(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("openskyblock.command")) {
            plugin.text().send(sender, "errors.no-permission");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "profile" -> profile(sender);
            case "menu" -> menu(sender);
            case "island" -> island(sender, args);
            case "purse" -> purse(sender);
            case "skills" -> skills(sender);
            case "collections" -> collections(sender);
            case "giveitem" -> giveItem(sender, args);
            case "minion" -> minion(sender, args);
            case "reload" -> reload(sender);
            default -> plugin.text().send(sender, "errors.unknown-command");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 1) {
            return startsWith(ROOT_SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("giveitem")) {
            return startsWith(plugin.customItems().definitions().stream().map(CustomItemDefinition::id).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("island")) {
            return startsWith(List.of("create", "home", "info"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("giveitem")) {
            return startsWith(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("minion")) {
            return startsWith(List.of("add", "list", "claim"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("minion") && args[1].equalsIgnoreCase("add")) {
            return startsWith(plugin.minions().definitions().stream().map(MinionDefinition::id).toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("minion") && args[1].equalsIgnoreCase("claim")) {
            return startsWith(List.of("all", "1", "2", "3", "4", "5"), args[2]);
        }
        return List.of();
    }

    private void help(CommandSender sender, String label) {
        TextService text = plugin.text();
        text.send(sender, "commands.help-header");
        helpLine(sender, label + " menu", "commands.help.menu");
        helpLine(sender, label + " island create|home|info", "commands.help.island");
        helpLine(sender, label + " profile", "commands.help.profile");
        helpLine(sender, label + " purse", "commands.help.purse");
        helpLine(sender, label + " skills", "commands.help.skills");
        helpLine(sender, label + " collections", "commands.help.collections");
        if (sender.hasPermission("openskyblock.admin")) {
            helpLine(sender, label + " giveitem <id> [player]", "commands.help.giveitem");
            helpLine(sender, label + " reload", "commands.help.reload");
        }
        helpLine(sender, label + " minion add <id>", "commands.help.minion-add");
        helpLine(sender, label + " minion list", "commands.help.minion-list");
        helpLine(sender, label + " minion claim [slot|all]", "commands.help.minion-claim");
    }

    private void helpLine(CommandSender sender, String command, String descriptionPath) {
        plugin.text().send(sender, "commands.help-line", List.of(
                TextService.raw("command", command),
                TextService.raw("description", plugin.configService().messages().getString(descriptionPath, ""))
        ));
    }

    private void profile(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        SkyBlockProfile profile = plugin.profiles().profile(player);
        plugin.text().send(player, "commands.profile-summary", List.of(
                TextService.raw("player", profile.playerName()),
                TextService.raw("level", Integer.toString(plugin.skills().skyBlockLevel(profile))),
                TextService.raw("purse", plugin.text().formatNumber(profile.purse())),
                TextService.raw("bank", plugin.text().formatNumber(profile.bank()))
        ));
    }

    private void menu(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.menus().openSkyBlockMenu(player);
    }

    private void island(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.islands().teleportHome(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> plugin.islands().createOrTeleport(player);
            case "home" -> plugin.islands().teleportHome(player);
            case "info" -> plugin.islands().sendInfo(player);
            default -> plugin.text().send(player, "errors.unknown-command");
        }
    }

    private void purse(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        SkyBlockProfile profile = plugin.profiles().profile(player);
        plugin.text().send(player, "commands.purse-summary", List.of(
                TextService.raw("purse", plugin.text().formatNumber(profile.purse())),
                TextService.raw("bank", plugin.text().formatNumber(profile.bank()))
        ));
    }

    private void skills(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        SkyBlockProfile profile = plugin.profiles().profile(player);
        plugin.text().send(player, "commands.skills-header");
        for (SkillDefinition definition : plugin.skills().definitions()) {
            double xp = profile.skillXp(definition.type());
            plugin.text().send(player, "commands.skill-line", List.of(
                    TextService.parsed("skill", definition.displayName()),
                    TextService.raw("level", Integer.toString(plugin.skills().level(definition.type(), xp))),
                    TextService.raw("xp", plugin.text().formatNumber(xp))
            ));
        }
    }

    private void collections(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        SkyBlockProfile profile = plugin.profiles().profile(player);
        plugin.text().send(player, "commands.collections-header");
        for (CollectionDefinition definition : plugin.collections().definitions()) {
            long amount = profile.collectionAmount(definition.id());
            plugin.text().send(player, "commands.collection-line", List.of(
                    TextService.parsed("collection", definition.displayName()),
                    TextService.raw("amount", plugin.text().formatNumber(amount)),
                    TextService.raw("tier", Integer.toString(plugin.collections().tier(definition, amount)))
            ));
        }
    }

    private void giveItem(CommandSender sender, String[] args) {
        if (!sender.hasPermission("openskyblock.admin")) {
            plugin.text().send(sender, "errors.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.text().send(sender, "errors.unknown-command");
            return;
        }
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                plugin.text().send(sender, "errors.unknown-player");
                return;
            }
        } else {
            target = requirePlayer(sender);
            if (target == null) {
                return;
            }
        }
        CustomItemDefinition definition = plugin.customItems().definition(args[1]).orElse(null);
        if (definition == null) {
            plugin.text().send(sender, "errors.unknown-item", List.of(TextService.raw("item", args[1])));
            return;
        }
        ItemStack itemStack = plugin.customItems().createItem(definition);
        target.getInventory().addItem(itemStack).values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        plugin.text().send(sender, "commands.item-given", List.of(
                TextService.parsed("item_name", definition.displayName()),
                TextService.raw("player", target.getName())
        ));
    }

    private void minion(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.text().send(player, "errors.unknown-command");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> minionAdd(player, args);
            case "list" -> minionList(player);
            case "claim" -> minionClaim(player, args);
            default -> plugin.text().send(player, "errors.unknown-command");
        }
    }

    private void minionAdd(Player player, String[] args) {
        if (args.length < 3) {
            plugin.text().send(player, "errors.unknown-command");
            return;
        }
        MinionDefinition definition = plugin.minions().definition(args[2]).orElse(null);
        if (definition == null) {
            plugin.text().send(player, "errors.unknown-minion", List.of(TextService.raw("minion", args[2])));
            return;
        }
        plugin.minions().addMinion(player, definition);
    }

    private void minionList(Player player) {
        SkyBlockProfile profile = plugin.profiles().profile(player);
        plugin.text().send(player, "commands.minion-list-header");
        for (int index = 0; index < profile.minions().size(); index++) {
            PlacedMinion placedMinion = profile.minions().get(index);
            MinionDefinition definition = plugin.minions().definition(placedMinion.id()).orElse(null);
            String displayName = definition == null ? placedMinion.id() : definition.displayName();
            plugin.text().send(player, "commands.minion-list-line", List.of(
                    TextService.raw("slot", Integer.toString(index + 1)),
                    TextService.parsed("minion_name", displayName),
                    TextService.raw("generated", plugin.text().formatNumber(placedMinion.generatedAmount()))
            ));
        }
    }

    private void minionClaim(Player player, String[] args) {
        if (args.length < 3 || args[2].equalsIgnoreCase("all")) {
            long claimed = plugin.minions().claimAll(player);
            sendClaimResult(player, claimed);
            return;
        }
        try {
            int slot = Integer.parseInt(args[2]) - 1;
            long claimed = plugin.minions().claim(player, slot);
            sendClaimResult(player, claimed);
        } catch (NumberFormatException ignored) {
            plugin.text().send(player, "errors.invalid-number");
        }
    }

    private void sendClaimResult(Player player, long claimed) {
        if (claimed <= 0L) {
            plugin.text().send(player, "commands.minion-nothing");
            return;
        }
        plugin.text().send(player, "commands.minion-claimed", List.of(TextService.raw("amount", plugin.text().formatNumber(claimed))));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("openskyblock.admin")) {
            plugin.text().send(sender, "errors.no-permission");
            return;
        }
        plugin.reloadOpenSkyBlock();
        plugin.text().send(sender, "startup.reloaded");
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.text().send(sender, "errors.players-only");
        return null;
    }

    private List<String> startsWith(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(value);
            }
        }
        return matches;
    }
}
