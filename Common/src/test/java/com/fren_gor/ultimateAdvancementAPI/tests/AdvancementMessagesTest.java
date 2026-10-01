package com.fren_gor.ultimateAdvancementAPI.tests;

import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType;
import com.fren_gor.ultimateAdvancementAPI.util.AdvancementMessages;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdvancementMessagesTest {
    private static AdvancementDisplay translated(AdvancementFrameType frame) {
        return new AdvancementDisplay(Material.BRICKS, "farmersdelight.advancement.place_cooking_pot", frame, true, true, 0, 0, "Description") {
            @Override public boolean usesComponentDisplay() { return true; }
            @Override public BaseComponent[] getChatTitle() {
                TranslatableComponent title = new TranslatableComponent("farmersdelight.advancement.place_cooking_pot");
                title.setColor(frame.getColor());
                return new BaseComponent[]{title};
            }
            @Override public BaseComponent[] getChatDescription() {
                return new BaseComponent[]{new TranslatableComponent("farmersdelight.advancement.place_cooking_pot.desc")};
            }
        };
    }

    @Test public void eachFrameUsesTheVanillaClientTranslation() {
        for (AdvancementFrameType frame : AdvancementFrameType.values()) {
            TranslatableComponent message = (TranslatableComponent) AdvancementMessages.announcement("ydxc2009", translated(frame))[0];
            assertEquals("chat.type.advancement." + frame.name().toLowerCase(java.util.Locale.ROOT), message.getTranslate());
            assertEquals("ydxc2009", message.getWith().get(0).toPlainText());
            assertEquals(ChatColor.WHITE, message.getColor());
            assertEquals(2, message.getWith().size());
        }
    }

    @Test public void announcementPreservesTheTitleAndHoverTranslations() {
        String json = ComponentSerializer.toString(AdvancementMessages.announcement("ydxc2009", translated(AdvancementFrameType.GOAL)));
        assertTrue(json.contains("\"translate\":\"farmersdelight.advancement.place_cooking_pot\""));
        assertTrue(json.contains("\"translate\":\"farmersdelight.advancement.place_cooking_pot.desc\""));
        assertTrue(json.contains("show_text"));
        assertTrue(json.contains("green"));
        assertFalse(json.contains("has reached the goal"));
    }

    @Test public void toastTitleDoesNotBecomeALiteralTranslationKey() {
        String json = ComponentSerializer.toString(AdvancementMessages.toastTitle(translated(AdvancementFrameType.GOAL)));
        assertTrue(json.contains("\"translate\":\"farmersdelight.advancement.place_cooking_pot\""));
        assertFalse(json.contains("\"text\":\"farmersdelight.advancement.place_cooking_pot\""));
    }
}
