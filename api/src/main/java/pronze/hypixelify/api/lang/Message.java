package pronze.hypixelify.api.lang;

import org.screamingsandals.bedwars.lib.ext.kyori.adventure.text.Component;
import org.screamingsandals.bedwars.lib.ext.kyori.adventure.text.minimessage.MiniMessage;
import org.screamingsandals.bedwars.lib.sender.CommandSenderWrapper;
import org.screamingsandals.bedwars.lib.utils.AdventureHelper;
import pronze.hypixelify.api.SBAHypixelifyAPI;

import java.util.List;
import java.util.stream.Collectors;

public class Message {
    private List<String> original;
    private boolean prefix;

    public static Message of(List<String> text) {
        return new Message(text);
    }

    public Message(List<String> text) {
        original = text;
    }

    public Message replace(String key, String value) {
        original = original
                .stream()
                .map(str-> str.replaceAll(key, value))
                .collect(Collectors.toList());
        return this;
    }

    public Message withPrefix() {
        prefix = true;
        return this;
    }

    public Component toComponent() {
        final var component = Component.text();
        original.forEach(str -> {
            if (prefix) {
                str = SBAHypixelifyAPI
                        .getInstance()
                        .getConfigurator()
                        .getString("prefix", "[SBAHypixelify]") + ": " + str;
            }
            component.append(MiniMessage.get().parse(str));
            if (original.indexOf(str) + 1 != original.size()) {
                component.append(Component.text("\n"));
            }
        });
        return component.build();
    }

    @Override
    public String toString() {
        var string = original.get(0);
        if (prefix) {
            string = SBAHypixelifyAPI
                    .getInstance()
                    .getConfigurator()
                    .getString("prefix", "[SBAHypixelify]") + ": ";
        }
        return AdventureHelper.toLegacy(MiniMessage.get().parse(string));
    }

    public List<String> toStringList() {
        return toComponentList()
                .stream()
                .map(AdventureHelper::toLegacy)
                .collect(Collectors.toList());
    }

    public List<Component> toComponentList() {
        return original
                .stream()
                .map(str -> {
                    if (prefix) {
                        str = SBAHypixelifyAPI
                                .getInstance()
                                .getConfigurator()
                                .getString("prefix", "[SBAHypixelify]") + ": " + str;
                    }
                    return str;
                })
                .map(MiniMessage.get()::parse)
                .collect(Collectors.toList());
    }

    public void send(CommandSenderWrapper... wrapper) {
        var message = toComponentList();
        for (var sender : wrapper) {
            message.forEach(sender::sendMessage);
        }
    }
}
