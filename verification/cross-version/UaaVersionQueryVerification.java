import com.fren_gor.ultimateAdvancementAPI.util.Versions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standalone installation-JAR check; run with Java 21 and UAA only. */
public final class UaaVersionQueryVerification {

    public static void main(String[] arguments) {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected the release version as the only argument");
        }
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("v1_21_R1", List.of("1.21", "1.21.1"));
        groups.put("v1_21_R2", List.of("1.21.2", "1.21.3"));
        groups.put("v1_21_R3", List.of("1.21.4"));
        groups.put("v1_21_R4", List.of("1.21.5"));
        groups.put("v1_21_R5", List.of("1.21.6", "1.21.7", "1.21.8"));
        groups.put("v1_21_R6", List.of("1.21.9", "1.21.10"));
        groups.put("v1_21_R7", List.of("1.21.11"));
        groups.put("v26_1_R2", List.of("26.1", "26.1.1", "26.1.2"));
        groups.put("v26_2_R1", List.of("26.2"));
        groups.put("v26_3_R1", List.of("26.3"));

        require(Versions.class.getResource("/uaa-modern-distribution") != null,
                "Modern-distribution marker is missing");
        require(arguments[0].equals(Versions.getApiVersion()), "Incorrect release identity");
        require(List.copyOf(groups.keySet()).equals(Versions.getSupportedNMSVersions()),
                "Advertised adapter families do not match the installation distribution");
        List<String> expected = groups.values().stream().flatMap(List::stream).toList();
        require(expected.equals(Versions.getSupportedVersions()), "Incomplete Minecraft coverage");
        groups.forEach((adapter, minecraft) -> {
            require(minecraft.equals(Versions.getNMSVersionsList(adapter)),
                    "Incorrect Minecraft versions for " + adapter);
            require(Versions.getNMSVersionsRange(adapter) != null, "Missing range for " + adapter);
        });
        require(Versions.getNMSVersionsList("v99_99_R1") == null, "Unknown adapter must not be accepted");
        try {
            Versions.getSupportedVersions().add("99.99");
            throw new AssertionError("Supported version queries must be immutable");
        } catch (UnsupportedOperationException expectedFailure) {
            // Required immutable public query contract.
        }
        System.out.println("PASS: " + expected.size() + " Minecraft versions, " + groups.size()
                + " adapter groups; Java " + Runtime.version().feature() + "; API " + arguments[0]);
    }

    private static void require(boolean condition, String detail) {
        if (!condition) {
            throw new AssertionError(detail);
        }
    }
}
