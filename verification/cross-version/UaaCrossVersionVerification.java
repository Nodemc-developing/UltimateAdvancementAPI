import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.packets.PacketPlayOutAdvancementsWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.util.ReflectionUtil;
import com.fren_gor.ultimateAdvancementAPI.util.AdvancementMessages;
import com.fren_gor.ultimateAdvancementAPI.util.SchedulerSupport;
import com.fren_gor.ultimateAdvancementAPI.util.Versions;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Test plugin only: native reflection is deliberately isolated from the business distribution. */
public final class UaaCrossVersionVerification extends JavaPlugin {
    private static final Set<String> EXPECTED_ADAPTERS = Set.of(
            "v1_21_R1", "v1_21_R2", "v1_21_R3", "v1_21_R4", "v1_21_R5",
            "v1_21_R6", "v1_21_R7", "v26_1_R2", "v26_2_R1", "v26_3_R1");
    private static final String TITLE = "uaa.cross_version.title";
    private static final String DESCRIPTION = "uaa.cross_version.description";
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Map<String, Object>> checks = new ArrayList<>();
    private final Map<String, Object> packets = new LinkedHashMap<>();
    private final List<Advancement> nodes = new ArrayList<>();
    private UltimateAdvancementAPI api;
    private RootAdvancement root;
    private BaseAdvancement left, right;
    private String adapter;
    private String namespace;
    private boolean finished;

    @Override
    public void onEnable() {
        report.put("schemaVersion", 1);
        report.put("startedAt", Instant.now().toString());
        report.put("server", Bukkit.getVersion());
        report.put("minecraft", ReflectionUtil.MINECRAFT_VERSION);
        report.put("java", System.getProperty("java.version"));
        report.put("checks", checks);
        report.put("packets", packets);
        report.put("coverage", Map.of(
                "nativeAdapters", "Current server adapter only; aggregate separate runs for the matrix",
                "nativePackets", "Construction and native codec round trip; no packets sent to players",
                "entityOwnership", "NOT_RUN", "regionOwnership", "NOT_RUN", "clientRendering", "NOT_RUN"));
        try {
            report.put("folia", SchedulerSupport.isFolia());
            SchedulerSupport.global(this, 40, this::verify);
        } catch (Throwable error) {
            failure("global_scheduler", error);
            report.put("initializationAborted", true);
            finish();
        }
    }

    private void verify() {
        try {
            check("global_scheduler", () -> {
                require(SchedulerSupport.isTickThread(), "Task did not run on a tick thread");
                if (SchedulerSupport.isFolia()) {
                    require((Boolean) Bukkit.class.getMethod("isGlobalTickThread").invoke(null),
                            "Task did not run on Folia's global tick thread");
                } else {
                    require(Bukkit.isPrimaryThread(), "Task did not run on the primary thread");
                }
            });
            check("adapter_loading", () -> {
                adapter = Versions.getNMSVersion().orElseThrow(() -> new AssertionError("Unsupported server version"));
                report.put("adapter", adapter);
                report.put("apiVersion", Versions.getApiVersion());
                report.put("advertisedAdapters", Versions.getSupportedNMSVersions());
                require(EXPECTED_ADAPTERS.contains(adapter), "Adapter is outside the intended ten-version matrix");
                require(Versions.getSupportedNMSVersions().containsAll(EXPECTED_ADAPTERS),
                        "Installed distribution does not advertise all ten adapters");
                Class.forName("com.fren_gor.ultimateAdvancementAPI.nms." + adapter + ".Util",
                        true, AdvancementWrapper.class.getClassLoader());
                api = UltimateAdvancementAPI.getInstance(this);
            });
            check("advancement_initialization", this::createTree);
            check("automatic_layout", this::verifyLayout);
            check("localized_native_display", () -> {
                for (Advancement node : nodes) {
                    Object display = node.getNMSWrapper().getDisplay().toNMS();
                    require(containsTranslation(member(display, "title", "getTitle"), TITLE), "Lost display title translation");
                    require(containsTranslation(member(display, "description", "getDescription"), DESCRIPTION),
                            "Lost display description translation");
                    require(node.getNMSWrapper().getClass().getName().contains(".nms." + adapter + "."),
                            "Loaded wrapper belongs to a different adapter");
                }
                require(!nodes.isEmpty(), "No native displays were created");
            });
            check("native_icon_copy", () -> {
                var display = root.getNMSWrapper().getDisplay();
                require(display.getIcon().getType() == Material.APPLE, "Native icon conversion changed the material");
                var copy = display.getIcon();
                copy.setType(Material.STONE);
                require(display.getIcon().getType() == Material.APPLE, "Returned icon aliases native storage");
            });
            check("localized_toasts_and_announcements", () -> {
                Class<?> util = Class.forName("com.fren_gor.ultimateAdvancementAPI.nms." + adapter + ".Util",
                        true, AdvancementWrapper.class.getClassLoader());
                Method convert = util.getMethod("fromComponent", BaseComponent.class);
                for (AdvancementFrameType frame : AdvancementFrameType.values()) {
                    AdvancementDisplay display = translated(frame);
                    var toastWrapper = display.getToastNMSWrapper();
                    Object toast = toastWrapper.toNMS();
                    require(containsTranslation(member(toast, "title", "getTitle"), TITLE), "Lost toast title translation");
                    require(containsTranslation(member(toast, "description", "getDescription"), DESCRIPTION),
                            "Lost toast description translation");
                    require(toastWrapper.doesShowToast() && !toastWrapper.doesAnnounceToChat() && !toastWrapper.isHidden(),
                            "Incorrect toast flags");
                    Object announcement = convert.invoke(null, new TextComponent(AdvancementMessages.announcement("Probe", display)));
                    require(containsTranslation(announcement, frame.getChatTranslationKey()), "Lost announcement translation");
                    require(containsTranslation(announcement, TITLE), "Lost announcement argument title translation");
                    require(nativeChatJson(announcement).contains("\"translate\":\"" + DESCRIPTION + "\""),
                            "Lost announcement hover description translation");
                }
            });
            verifyPackets();
        } catch (Throwable error) {
            failure("verification_runner", error);
        } finally {
            finish();
        }
    }

    private AdvancementDisplay translated(AdvancementFrameType frame) {
        return new AdvancementDisplay(Material.APPLE, TITLE, frame, false, false, 0, 0, DESCRIPTION) {
            @Override public boolean usesComponentDisplay() { return true; }
            @Override public BaseComponent[] getChatTitle() { return new BaseComponent[]{new TranslatableComponent(TITLE)}; }
            @Override public BaseComponent[] getChatDescription() { return new BaseComponent[]{new TranslatableComponent(DESCRIPTION)}; }
        };
    }

    private void createTree() throws Exception {
        require(api != null, "API initialization did not succeed");
        namespace = "uaa_cross_version_" + Long.toString(System.currentTimeMillis(), 36);
        AdvancementTab tab = api.createAdvancementTab(namespace);
        root = new RootAdvancement(tab, "root", translated(AdvancementFrameType.TASK), "textures/block/stone.png");
        left = new BaseAdvancement("left", translated(AdvancementFrameType.GOAL), root, 3);
        right = new BaseAdvancement("right", translated(AdvancementFrameType.CHALLENGE), root);
        BaseAdvancement leafA = new BaseAdvancement("leaf_a", translated(AdvancementFrameType.TASK), left);
        BaseAdvancement leafB = new BaseAdvancement("leaf_b", translated(AdvancementFrameType.TASK), left);
        tab.registerAdvancements(root, Set.of(left, right, leafA, leafB), true);
        nodes.addAll(List.of(root, left, right, leafA, leafB));
        require(tab.isInitialised() && tab.getAdvancements().size() == 5, "Tree did not initialize all five nodes");
        for (Advancement node : nodes) require(node.getNMSWrapper().toNMS() != null, "Null native advancement");
        require(root.getNMSWrapper().getDisplay().getBackgroundTexture() != null, "Root background was lost");
    }

    private void verifyLayout() {
        require(nodes.size() == 5, "Native tree was not initialized");
        Map<Float, List<Float>> columns = new TreeMap<>();
        Map<Advancement, List<BaseAdvancement>> branches = new LinkedHashMap<>();
        for (Advancement node : nodes) {
            float x = node.getDisplay().getX(), y = node.getDisplay().getY();
            require(Float.isFinite(x) && Float.isFinite(y) && x >= 0 && y >= 0, "Invalid tree coordinate");
            columns.computeIfAbsent(x, ignored -> new ArrayList<>()).add(y);
            var nativeDisplay = node.getNMSWrapper().getDisplay();
            near(x, nativeDisplay.getX(), "Native x differs from assigned layout");
            near(y, nativeDisplay.getY(), "Native y differs from assigned layout");
            if (node instanceof BaseAdvancement child) {
                near(child.getParent().getDisplay().getX() + 1, x, "Child column is incorrect");
                branches.computeIfAbsent(child.getParent(), ignored -> new ArrayList<>()).add(child);
            }
        }
        for (List<Float> rows : columns.values()) {
            rows.sort(Comparator.naturalOrder());
            for (int i = 1; i < rows.size(); i++) require(rows.get(i) - rows.get(i - 1) >= 0.999f, "Nodes overlap");
        }
        branches.forEach((parent, children) -> {
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            for (BaseAdvancement child : children) {
                min = Math.min(min, child.getDisplay().getY()); max = Math.max(max, child.getDisplay().getY());
            }
            near((min + max) / 2, parent.getDisplay().getY(), "Parent is not centered over its children");
        });
        near(0, root.getDisplay().getX(), "Root column is incorrect");
    }

    private void verifyPackets() {
        checkPacket("full", () -> {
            Map<AdvancementWrapper, Integer> definitions = new LinkedHashMap<>();
            for (Advancement node : nodes) definitions.put(node.getNMSWrapper(), 0);
            require(definitions.size() == 5, "Missing initialized advancement definitions");
            return packet(PacketPlayOutAdvancementsWrapper.craftSendPacket(definitions));
        }, () -> new Expected(false, allKeys(), Set.of(), progress(0, 0, 0, 0, 0)));
        checkPacket("delta", () -> packet(PacketPlayOutAdvancementsWrapper.craftUpdatePacket(
                Map.of(), Set.of(), Map.of(left.getNMSWrapper(), 2))),
                () -> new Expected(false, Set.of(), Set.of(), Map.of(key(left), 2)));
        checkPacket("mixed", () -> packet(PacketPlayOutAdvancementsWrapper.craftUpdatePacket(
                Map.of(right.getNMSWrapper(), 1), Set.of(left.getKey().getNMSWrapper()), Map.of(root.getNMSWrapper(), 1))),
                () -> new Expected(false, Set.of(key(right)), Set.of(key(left)), Map.of(key(right), 1, key(root), 1)));
        checkPacket("overlap", () -> packet(PacketPlayOutAdvancementsWrapper.craftUpdatePacket(
                Map.of(left.getNMSWrapper(), 1), Set.of(), Map.of(left.getNMSWrapper(), 2))),
                () -> new Expected(false, Set.of(key(left)), Set.of(), Map.of(key(left), 1)));
        checkPacket("remove", () -> packet(PacketPlayOutAdvancementsWrapper.craftRemovePacket(Set.of(left.getKey().getNMSWrapper()))),
                () -> new Expected(false, Set.of(), Set.of(key(left)), Map.of()));
        checkPacket("reset", () -> packet(PacketPlayOutAdvancementsWrapper.craftResetPacket()),
                () -> new Expected(true, Set.of(), Set.of(), Map.of()));
    }

    private void checkPacket(String name, Value<Object> factory, Value<Expected> expectation) {
        check("native_packet_" + name, () -> {
            Object original = factory.get();
            Expected expected = expectation.get();
            verifyPacket(original, expected);
            int bytes = roundTrip(original, decoded -> {
                try { verifyPacket(decoded, expected); }
                catch (Exception error) { throw new IllegalStateException("Decoded packet mismatch", error); }
            });
            packets.put(name, Map.of("encodedBytes", bytes, "nativeClass", original.getClass().getName(),
                    "added", expected.added.size(), "removed", expected.removed.size(), "progress", expected.progress.size()));
        });
    }

    private void verifyPacket(Object packet, Expected expected) throws Exception {
        require((Boolean) member(packet, "shouldReset") == expected.reset, "Incorrect reset flag");
        Collection<?> added = (Collection<?>) member(packet, "added", "getAdded");
        Set<String> addedKeys = new java.util.HashSet<>();
        for (Object definition : added) {
            Object holder = adapter.equals("v26_3_R1") ? member(definition, "advancement") : definition;
            String id = member(holder, "id").toString();
            require(addedKeys.add(id), "Duplicate native definition");
            Object value = member(holder, "value");
            Object display = ((Optional<?>) member(value, "display")).orElseThrow();
            require(containsTranslation(member(display, "title", "getTitle"), TITLE), "Packet lost title translation");
            require(containsTranslation(member(display, "description", "getDescription"), DESCRIPTION), "Packet lost description translation");
            Advancement source = nodes.stream().filter(node -> key(node).equals(id)).findFirst().orElseThrow();
            Optional<?> parent = (Optional<?>) member(value, "parent");
            if (source instanceof BaseAdvancement child) {
                require(parent.isPresent() && parent.get().toString().equals(key(child.getParent())), "Packet lost native parent linkage");
            } else {
                require(parent.isEmpty(), "Native root unexpectedly has a parent");
            }
            require(((Number) member(member(value, "requirements"), "size")).intValue() == source.getMaxProgression(),
                    "Packet changed the number of required progression criteria");
            require(member(display, "type", "getType").equals(source.getDisplay().getFrame().getNMSWrapper().toNMS()),
                    "Packet changed the advancement frame");
            if (adapter.equals("v26_3_R1")) {
                require(definition.getClass().getSimpleName().equals("PositionedAdvancement"), "26.3 definition is not positioned");
                near(source.getDisplay().getX(), ((Number) member(definition, "x")).floatValue(), "Positioned x changed");
                near(source.getDisplay().getY(), ((Number) member(definition, "y")).floatValue(), "Positioned y changed");
            } else {
                near(source.getDisplay().getX(), ((Number) member(display, "getX")).floatValue(), "Encoded x changed");
                near(source.getDisplay().getY(), ((Number) member(display, "getY")).floatValue(), "Encoded y changed");
            }
            Optional<?> background = (Optional<?>) member(display, "background", "getBackground");
            if (source == root) {
                Object texture = background.orElseThrow(() -> new AssertionError("Packet lost root background"));
                boolean legacyTexture = Set.of("v1_21_R1", "v1_21_R2", "v1_21_R3").contains(adapter);
                String backgroundId = legacyTexture ? texture.toString() : member(texture, "id").toString();
                require(backgroundId.equals(legacyTexture ? "minecraft:textures/block/stone.png" : "minecraft:block/stone"),
                        "Packet changed the root background identifier: " + backgroundId);
            } else {
                require(background.isEmpty(), "Child unexpectedly has a background texture");
            }
        }
        require(addedKeys.equals(expected.added), "Native definitions differ from requested additions");
        Set<String> removedKeys = new java.util.HashSet<>();
        for (Object id : (Collection<?>) member(packet, "removed", "getRemoved")) removedKeys.add(id.toString());
        require(removedKeys.equals(expected.removed), "Native removals differ from requested keys");
        Map<String, Integer> progress = new TreeMap<>();
        for (var entry : ((Map<?, ?>) member(packet, "progress", "getProgress")).entrySet()) {
            int completed = 0;
            for (Object ignored : (Iterable<?>) member(entry.getValue(), "getCompletedCriteria")) completed++;
            progress.put(entry.getKey().toString(), completed);
        }
        require(progress.equals(expected.progress), "Native progress differs from requested values: " + progress);
    }

    private int roundTrip(Object packet, Consumer<Object> assertion) throws Exception {
        ClassLoader loader = packet.getClass().getClassLoader();
        Class<?> byteBuf = Class.forName("io.netty.buffer.ByteBuf", true, loader);
        Object raw = Class.forName("io.netty.buffer.Unpooled", true, loader).getMethod("buffer").invoke(null);
        try {
            Object server = member(Bukkit.getServer(), "getServer");
            Object registries = member(server, "registryAccess");
            Class<?> registryType = Class.forName("net.minecraft.core.RegistryAccess", true, loader);
            Object buffer = Class.forName("net.minecraft.network.RegistryFriendlyByteBuf", true, loader)
                    .getConstructor(byteBuf, registryType).newInstance(raw, registries);
            Object codec = packet.getClass().getField("STREAM_CODEC").get(null);
            Class<?> streamCodec = Class.forName("net.minecraft.network.codec.StreamCodec", true, loader);
            streamCodec.getMethod("encode", Object.class, Object.class).invoke(codec, buffer, packet);
            int bytes = (Integer) byteBuf.getMethod("writerIndex").invoke(raw);
            require(bytes > 0, "Native codec wrote no bytes");
            byteBuf.getMethod("readerIndex", int.class).invoke(raw, 0);
            Object decoded = streamCodec.getMethod("decode", Object.class).invoke(codec, buffer);
            require(decoded.getClass() == packet.getClass(), "Native codec decoded a different packet type");
            require((Integer) byteBuf.getMethod("readableBytes").invoke(raw) == 0, "Native codec left trailing bytes");
            assertion.accept(decoded);
            return bytes;
        } finally {
            byteBuf.getMethod("release").invoke(raw);
        }
    }

    private Object packet(Object wrapper) throws Exception {
        require(wrapper.getClass().getName().contains(".nms." + adapter + "."),
                "Packet factory returned a fallback rather than the selected native wrapper");
        Field packet = wrapper.getClass().getDeclaredField("packet");
        packet.setAccessible(true);
        return packet.get(wrapper);
    }

    private static Object member(Object target, String... names) throws Exception {
        for (String name : names) {
            try { return target.getClass().getMethod(name).invoke(target); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(target.getClass().getName() + " " + List.of(names));
    }

    private static boolean containsTranslation(Object component, String key) throws Exception {
        ArrayDeque<Object> pending = new ArrayDeque<>();
        pending.add(component);
        while (!pending.isEmpty()) {
            Object current = pending.removeFirst();
            Object contents = member(current, "getContents");
            if (contents.getClass().getSimpleName().equals("TranslatableContents")) {
                if (key.equals(member(contents, "getKey"))) return true;
                Object args = member(contents, "getArgs");
                for (int i = 0; i < Array.getLength(args); i++) {
                    Object arg = Array.get(args, i);
                    if (arg != null && hasMethod(arg, "getContents")) pending.add(arg);
                }
            }
            pending.addAll((Collection<?>) member(current, "getSiblings"));
        }
        return false;
    }

    private static boolean hasMethod(Object target, String name) {
        try { target.getClass().getMethod(name); return true; }
        catch (NoSuchMethodException ignored) { return false; }
    }

    private static String nativeChatJson(Object component) throws Exception {
        ClassLoader loader = component.getClass().getClassLoader();
        Class<?> nativeComponent = Class.forName("net.minecraft.network.chat.Component", true, loader);
        Class<?> chat = Class.forName(Bukkit.getServer().getClass().getPackageName() + ".util.CraftChatMessage", true, loader);
        return (String) chat.getMethod("toJSON", nativeComponent).invoke(null, component);
    }

    private Set<String> allKeys() { return nodes.stream().map(UaaCrossVersionVerification::key).collect(java.util.stream.Collectors.toSet()); }
    private Map<String, Integer> progress(int... values) {
        Map<String, Integer> progress = new LinkedHashMap<>();
        for (int i = 0; i < nodes.size(); i++) progress.put(key(nodes.get(i)), values[i]);
        return progress;
    }
    private static String key(Advancement node) { return node.getKey().getNMSWrapper().toNMS().toString(); }
    private static void near(float expected, float actual, String message) { require(Math.abs(expected - actual) < 0.001f, message); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private record Expected(boolean reset, Set<String> added, Set<String> removed, Map<String, Integer> progress) { }
    @FunctionalInterface private interface Operation { void run() throws Exception; }
    @FunctionalInterface private interface Value<T> { T get() throws Exception; }

    private void check(String id, Operation action) {
        try { action.run(); result(id, "PASS", true, "Verified on the running server"); }
        catch (Throwable error) { failure(id, error); }
    }
    private void failure(String id, Throwable error) {
        while (error instanceof InvocationTargetException invocation && invocation.getCause() != null) error = invocation.getCause();
        result(id, "FAIL", true, error.toString());
        getLogger().log(Level.SEVERE, "[UAA-NATIVE] Failure detail: " + id, error);
    }
    private void result(String id, String status, boolean required, String detail) {
        checks.add(Map.of("id", id, "status", status, "required", required, "detail", detail));
        getLogger().info("[UAA-NATIVE] " + status + " " + id + ": " + detail);
    }

    private void finish() {
        if (finished) return;
        finished = true;
        if (api != null && namespace != null) check("probe_cleanup", () -> api.unregisterAdvancementTab(namespace));
        result("entity_scheduler_ownership", "SKIP", false, "No real player task was scheduled; verify separately with a real player");
        result("folia_region_ownership", "SKIP", false, "No world or region tasks were scheduled; verify separately on Folia");
        result("client_rendering_and_delivery", "SKIP", false, "No player network connection or client was used");
        boolean success = checks.stream().noneMatch(row -> Boolean.TRUE.equals(row.get("required")) && !"PASS".equals(row.get("status")));
        report.put("success", success);
        report.put("overall", success ? "PASS" : "FAIL");
        report.put("scope", "native_initialization_codec_and_global_scheduler");
        report.put("finishedAt", Instant.now().toString());
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve("report.json"), json(report) + System.lineSeparator(), StandardCharsets.UTF_8);
            getLogger().info("[UAA-NATIVE] OVERALL " + report.get("overall") + " (native scope only): " + getDataFolder().toPath().resolve("report.json"));
        } catch (Exception error) {
            getLogger().log(Level.SEVERE, "[UAA-NATIVE] FAIL report_write", error);
        }
        // The controlling process owns server shutdown. This plugin never stops or restarts the server.
    }

    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof String string) {
            StringBuilder escaped = new StringBuilder("\"");
            for (char c : string.toCharArray()) {
                switch (c) {
                    case '"' -> escaped.append("\\\"");
                    case '\\' -> escaped.append("\\\\");
                    case '\n' -> escaped.append("\\n");
                    case '\r' -> escaped.append("\\r");
                    case '\t' -> escaped.append("\\t");
                    default -> { if (c < 32) escaped.append(String.format("\\u%04x", (int) c)); else escaped.append(c); }
                }
            }
            return escaped.append('"').toString();
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().map(entry -> json(entry.getKey().toString()) + ":" + json(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> entries = new ArrayList<>();
            for (Object entry : iterable) entries.add(json(entry));
            return String.join(",", entries).transform(body -> "[" + body + "]");
        }
        throw new IllegalArgumentException("Unsupported report value: " + value.getClass());
    }
}
