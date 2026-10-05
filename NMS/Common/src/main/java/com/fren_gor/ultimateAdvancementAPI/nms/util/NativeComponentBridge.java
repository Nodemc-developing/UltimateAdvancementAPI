package com.fren_gor.ultimateAdvancementAPI.nms.util;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.jetbrains.annotations.ApiStatus.Internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.function.Function;

/** Resolves the server's versioned component conversion once when an adapter initializes. */
@Internal
public final class NativeComponentBridge {
    private NativeComponentBridge() {}

    public static <T> Function<BaseComponent, T> converter(Class<?> craftChatMessage,
                                                         Function<String, T> legacyJsonParser) {
        Objects.requireNonNull(craftChatMessage, "CraftChatMessage class is null.");
        Objects.requireNonNull(legacyJsonParser, "Legacy component parser is null.");
        final Method method;
        try {
            method = craftChatMessage.getMethod("bungeeToVanilla", BaseComponent[].class);
        } catch (NoSuchMethodException unavailable) {
            return component -> legacyJsonParser.apply(ComponentSerializer.toString(component));
        }
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("CraftChatMessage.bungeeToVanilla must be static.");
        }
        final MethodHandle converter;
        try {
            converter = MethodHandles.publicLookup().unreflect(method).asFixedArity()
                    .asType(MethodType.methodType(Object.class, BaseComponent[].class));
        } catch (IllegalAccessException inaccessible) {
            throw new IllegalStateException("Cannot access the server's component converter.", inaccessible);
        }
        return component -> {
            try {
                @SuppressWarnings("unchecked")
                T converted = (T) converter.invokeExact(new BaseComponent[]{component});
                return converted;
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("Server component conversion failed.", failure);
            }
        };
    }
}
