package com.fren_gor.ultimateAdvancementAPI.commands;

import com.fren_gor.ultimateAdvancementAPI.util.Versions;
import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;
import org.bukkit.Bukkit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class CommandAPIManagerTest {
    static Stream<Throwable> dependencyLoadingFailures() {
        return Stream.of(
            new IllegalStateException("Command dependency is unavailable"),
            new UnsupportedClassVersionError("Command dependency needs a newer Java runtime"),
            new NoClassDefFoundError("Command dependency needs a newer server API")
        );
    }

    @ParameterizedTest
    @MethodSource("dependencyLoadingFailures")
    void unavailableOrIncompatibleDependencyAllowsNativeFallback(Throwable failure) {
        LibraryManager manager = mock(LibraryManager.class);
        doThrow(failure).when(manager).loadLibrary(any(Library.class));
        try (MockedStatic<Versions> versions = mockStatic(Versions.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            versions.when(Versions::getNMSVersion).thenReturn(Optional.of("v1_21_R1"));
            bukkit.when(Bukkit::getLogger).thenReturn(mock(Logger.class));

            assertNull(CommandAPIManager.loadManager(manager));
            verify(manager).loadLibrary(any(Library.class));
        }
    }
}
