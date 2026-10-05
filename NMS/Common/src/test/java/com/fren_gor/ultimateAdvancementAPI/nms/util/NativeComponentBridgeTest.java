package com.fren_gor.ultimateAdvancementAPI.nms.util;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NativeComponentBridgeTest {
    @Test
    void usesServerConversionInsteadOfLegacySerialization() {
        BaseComponent title = new TranslatableComponent("advancement.example.title");
        VersionedBridge.expected = title;
        var converter = NativeComponentBridge.<String>converter(VersionedBridge.class, json -> {
            fail("Legacy parser must not override an available versioned bridge");
            return null;
        });
        assertEquals("versioned", converter.apply(title));
    }

    @Test
    void retainsTranslationAndHoverForAnOlderServerWithoutTheBridge() {
        BaseComponent title = new TranslatableComponent("advancement.example.title");
        title.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new BaseComponent[]{new TranslatableComponent("advancement.example.description")}));
        var converter = NativeComponentBridge.<String>converter(OlderBridge.class, json -> json);
        String json = converter.apply(title);
        assertTrue(json.contains("advancement.example.title"));
        assertTrue(json.contains("advancement.example.description"));
        assertTrue(json.contains("hoverEvent"));
    }

    @Test
    void doesNotHideAConversionFailureBehindTheLegacyParser() {
        var converter = NativeComponentBridge.<String>converter(FailingBridge.class, json -> {
            fail("A broken available converter must not silently drop component fields");
            return null;
        });
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> converter.apply(new TextComponent("test")));
        assertEquals("invalid component", error.getMessage());
    }

    public static class VersionedBridge {
        private static BaseComponent expected;
        public static String bungeeToVanilla(BaseComponent... components) {
            assertEquals(1, components.length);
            assertSame(expected, components[0]);
            return "versioned";
        }
    }

    public static class OlderBridge {}

    public static class FailingBridge {
        public static String bungeeToVanilla(BaseComponent... components) {
            throw new IllegalArgumentException("invalid component");
        }
    }
}
