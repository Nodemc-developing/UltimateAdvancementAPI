package com.fren_gor.ultimateAdvancementAPI.util;

import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;

import java.util.*;

/** Deterministic tree layout: depth controls columns, parents are centered above their children. */
public final class AdvancementLayout {
    private AdvancementLayout() {}

    public static void applyVanilla(RootAdvancement root, Collection<? extends BaseAdvancement> advancements) {
        Objects.requireNonNull(root, "Root advancement is null");
        Map<Advancement, List<Advancement>> children = new IdentityHashMap<>();
        children.put(root, new ArrayList<>());
        for (BaseAdvancement advancement : advancements) {
            if (children.put(advancement, new ArrayList<>()) != null) throw new IllegalArgumentException("Duplicate advancement");
        }
        for (BaseAdvancement advancement : advancements) {
            List<Advancement> siblings = children.get(advancement.getParent());
            if (siblings == null) throw new IllegalArgumentException("Advancement parent is outside the registered tree");
            siblings.add(advancement);
        }
        children.values().forEach(list -> list.sort(Comparator.comparing(adv -> adv.getKey().toString())));
        List<Node> order = new ArrayList<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(new Node(root, 0));
        int row = 0;
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            order.add(node);
            List<Advancement> branches = children.get(node.advancement());
            if (branches.isEmpty()) node.advancement().getDisplay().setCoordinates(node.depth(), row++);
            for (int i = branches.size() - 1; i >= 0; --i) stack.push(new Node(branches.get(i), node.depth() + 1));
        }
        if (order.size() != children.size()) throw new IllegalArgumentException("Advancement tree is disconnected or cyclic");
        for (int i = order.size() - 1; i >= 0; --i) {
            Node node = order.get(i);
            List<Advancement> branches = children.get(node.advancement());
            if (!branches.isEmpty()) {
                float y = (branches.get(0).getDisplay().getY() + branches.get(branches.size() - 1).getDisplay().getY()) / 2;
                node.advancement().getDisplay().setCoordinates(node.depth(), y);
            }
        }
    }
    private record Node(Advancement advancement, int depth) {}
}
