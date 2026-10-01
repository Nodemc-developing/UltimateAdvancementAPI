package com.fren_gor.ultimateAdvancementAPI.tests;

import com.fren_gor.ultimateAdvancementAPI.util.ClientStateDelta;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ClientStateDeltaTest {
    @Test public void oneChangedNodeDoesNotResendTheTree() {
        Map<String, Integer> old = new HashMap<>();
        for (int i = 0; i < 300; i++) old.put("node" + i, 0);
        Map<String, Integer> current = new HashMap<>(old);
        current.put("node157", 1);
        var delta = ClientStateDelta.between(old, current);
        assertTrue(delta.added().isEmpty());
        assertTrue(delta.removed().isEmpty());
        assertEquals(Map.of("node157", 1), delta.progress());
        assertTrue(ClientStateDelta.between(current, current).isEmpty());
    }
    @Test public void visibilityTransitionsAndForceResendAreRepresented() {
        var delta = ClientStateDelta.between(Map.of("root", 1, "hidden", 0), Map.of("root", 1, "unlocked", 0));
        assertEquals(Set.of("hidden"), delta.removed());
        assertEquals(Map.of("unlocked", 0), delta.added());
        assertTrue(delta.progress().isEmpty());
        assertEquals(2, ClientStateDelta.between(Map.of(), Map.of("root", 1, "unlocked", 0)).added().size());
    }
}
