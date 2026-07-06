package io.github.openskyblock.config;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public final class TextService {
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0.##");

    private final ConfigService configService;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration messages;

    public TextService(ConfigService configService) {
        this.configService = configService;
        reload();
    }

    public void reload() {
        this.messages = configService.messages();
    }

    public Component message(String path) {
        return deserialize(rawMessage(path));
    }

    public Component message(String path, Collection<TextPlaceholder> placeholders) {
        return deserialize(rawMessage(path), placeholders);
    }

    public Component deserialize(String raw) {
        return miniMessage.deserialize(expandPrefix(raw));
    }

    public Component deserialize(String raw, Collection<TextPlaceholder> placeholders) {
        TagResolver[] resolvers = placeholders.stream()
                .map(TextPlaceholder::resolver)
                .toArray(TagResolver[]::new);
        return miniMessage.deserialize(expandPrefix(raw), resolvers);
    }

    public String rawMessage(String path) {
        return messages.getString(path, "<red>Missing message: " + path + "</red>");
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(message(path));
    }

    public void send(CommandSender sender, String path, Collection<TextPlaceholder> placeholders) {
        sender.sendMessage(message(path, placeholders));
    }

    public String formatNumber(double value) {
        return NUMBER_FORMAT.format(value);
    }

    public String formatNumber(long value) {
        return NUMBER_FORMAT.format(value);
    }

    private String expandPrefix(String raw) {
        String prefix = messages.getString("prefix", "");
        return raw.replace("<prefix>", prefix);
    }

    public static TextPlaceholder raw(String key, String value) {
        return new TextPlaceholder(key, value, false);
    }

    public static TextPlaceholder parsed(String key, String value) {
        return new TextPlaceholder(key, value, true);
    }

    public static Collection<TextPlaceholder> placeholders(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> raw(entry.getKey(), entry.getValue()))
                .toList();
    }

    public record TextPlaceholder(String key, String value, boolean parsed) {
        private TagResolver resolver() {
            if (parsed) {
                return Placeholder.parsed(key, value);
            }
            return Placeholder.unparsed(key, value);
        }
    }
}
