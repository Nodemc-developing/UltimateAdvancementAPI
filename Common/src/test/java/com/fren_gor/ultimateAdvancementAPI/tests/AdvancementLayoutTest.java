package com.fren_gor.ultimateAdvancementAPI.tests;

import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.advancement.*;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.*;
import com.fren_gor.ultimateAdvancementAPI.util.AdvancementLayout;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.junit.*;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AdvancementLayoutTest {
    private MockedStatic<Bukkit> bukkit;
    private AdvancementTab tab;
    @Before public void setup() {
        bukkit = Utils.mockServer();
        var plugin = InterfaceImplementer.newFakePlugin("layout");
        tab = Utils.newAdvancementMain(plugin).createAdvancementTab(plugin, "layout");
    }
    @After public void close() { if (bukkit != null) bukkit.close(); }
    private AdvancementDisplay display() {
        return new AdvancementDisplay(Material.STONE, "Title", AdvancementFrameType.TASK, false, false, 17, 19);
    }
    private RootAdvancement root() { return new RootAdvancement(tab, "root", display(), "textures/block/stone.png"); }
    private BaseAdvancement child(String key, Advancement parent) { return new BaseAdvancement(key, display(), parent); }

    @Test public void unevenTreeUsesSpaceAcrossDifferentColumns() {
        var root = root();
        var left = child("a", root);
        var right = child("b", root);
        var children = List.of(child("b0", right), child("b1", right), child("b2", right));
        List<BaseAdvancement> all = new ArrayList<>(children);
        all.add(left); all.add(right);
        AdvancementLayout.applyVanilla(root, all);
        assertEquals(0, left.getDisplay().getY(), 0);
        assertEquals(1, right.getDisplay().getY(), 0);
        assertEquals(0.5, root.getDisplay().getY(), 0);
        for (int i = 0; i < 3; i++) assertEquals(i, children.get(i).getDisplay().getY(), 0);
    }

    @Test public void deepAndWideTreesDoNotUseTheCallStack() {
        var root = root();
        List<BaseAdvancement> chain = new ArrayList<>();
        Advancement parent = root;
        for (int i = 0; i < 10000; i++) { var next = child("n" + i, parent); chain.add(next); parent = next; }
        AdvancementLayout.applyVanilla(root, chain);
        assertEquals(10000, parent.getDisplay().getX(), 0);
        assertEquals(0, parent.getDisplay().getY(), 0);
        List<BaseAdvancement> wide = new ArrayList<>();
        for (int i = 0; i < 10000; i++) wide.add(child("w" + i, root));
        AdvancementLayout.applyVanilla(root, wide);
        assertEquals(4999.5, root.getDisplay().getY(), 0);
        assertEquals(10000, wide.stream().map(a -> a.getDisplay().getY()).distinct().count());
    }

    @Test public void shuffledRandomTreesRemainCenteredAndSeparated() {
        Random random = new Random(92817);
        for (int trial = 0; trial < 100; trial++) {
            var root = root();
            List<Advancement> tree = new ArrayList<>(List.of(root));
            List<BaseAdvancement> all = new ArrayList<>();
            Map<Advancement, List<BaseAdvancement>> children = new IdentityHashMap<>();
            for (int i = 0; i < 80; i++) {
                Advancement parent = tree.get(random.nextInt(tree.size()));
                var child = child("n" + i, parent);
                children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(child);
                all.add(child); tree.add(child);
            }
            AdvancementLayout.applyVanilla(root, all);
            List<String> original = tree.stream().map(a -> a.getDisplay().getX() + ":" + a.getDisplay().getY()).toList();
            Collections.shuffle(all, random);
            AdvancementLayout.applyVanilla(root, all);
            assertEquals(original, tree.stream().map(a -> a.getDisplay().getX() + ":" + a.getDisplay().getY()).toList());
            Map<Float, List<Float>> columns = new HashMap<>();
            for (Advancement a : tree) {
                assertTrue(a.getDisplay().getY() >= 0);
                columns.computeIfAbsent(a.getDisplay().getX(), ignored -> new ArrayList<>()).add(a.getDisplay().getY());
            }
            for (List<Float> rows : columns.values()) {
                Collections.sort(rows);
                for (int i = 1; i < rows.size(); i++) assertTrue(rows.get(i) - rows.get(i - 1) >= 0.999f);
            }
            children.forEach((parent, branch) -> {
                branch.sort(Comparator.comparing(a -> a.getKey().toString()));
                float midpoint = (branch.get(0).getDisplay().getY() + branch.get(branch.size() - 1).getDisplay().getY()) / 2;
                assertEquals(midpoint, parent.getDisplay().getY(), 0.001);
                for (BaseAdvancement child : branch) assertEquals(parent.getDisplay().getX() + 1, child.getDisplay().getX(), 0);
            });
        }
    }

    @Test public void malformedTreesFailBeforeChangingCoordinates() {
        var root = root();
        var child = child("a", root);
        assertThrows(IllegalArgumentException.class, () -> AdvancementLayout.applyVanilla(root, List.of(child, child)));
        assertThrows(IllegalArgumentException.class, () -> AdvancementLayout.applyVanilla(root, List.of(child, child("a", root))));
        assertThrows(IllegalArgumentException.class, () -> AdvancementLayout.applyVanilla(root, List.of(child("b", child))));
        BaseAdvancement cycle = mock(BaseAdvancement.class);
        when(cycle.getKey()).thenReturn(child.getKey());
        when(cycle.getParent()).thenReturn(cycle);
        assertThrows(IllegalArgumentException.class, () -> AdvancementLayout.applyVanilla(root, List.of(cycle)));
        assertEquals(17, root.getDisplay().getX(), 0);
        assertEquals(19, root.getDisplay().getY(), 0);
    }
}
