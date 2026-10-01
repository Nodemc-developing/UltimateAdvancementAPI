package com.fren_gor.ultimateAdvancementAPI.util;

import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;

/** Builds client-localized advancement messages without resolving translations on the server. */
public final class AdvancementMessages {
    private AdvancementMessages() {}

    public static BaseComponent[] announcement(String playerName, AdvancementDisplay display) {
        ChatColor color = display.getFrame().getColor();
        BaseComponent[] title = new ComponentBuilder("[")
                .color(color)
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, display.getChatDescription()))
                .append(display.getChatTitle(), FormatRetention.EVENTS)
                .append("]", FormatRetention.EVENTS)
                .color(color)
                .create();
        TranslatableComponent message = new TranslatableComponent(display.getFrame().getChatTranslationKey(),
                new TextComponent(playerName), new TextComponent(title));
        message.setColor(ChatColor.WHITE);
        return new BaseComponent[]{message};
    }

    public static BaseComponent toastTitle(AdvancementDisplay display) {
        return new TextComponent(display.getChatTitle());
    }
}
