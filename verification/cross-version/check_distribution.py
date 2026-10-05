#!/usr/bin/env python3
"""Read-only checks of the assembled UAA installation JAR; standard library only."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zipfile


PACKAGE = "com/fren_gor/ultimateAdvancementAPI/"
ADAPTERS = {**{f"v1_21_R{i}": 65 for i in range(1, 8)},
            "v26_1_R2": 69, "v26_2_R1": 69, "v26_3_R1": 69}
ADAPTER_TYPES = (
    "MinecraftKeyWrapper", "VanillaAdvancementDisablerWrapper",
    "advancement/AdvancementWrapper", "advancement/PreparedAdvancementWrapper",
    "advancement/AdvancementDisplayWrapper", "advancement/AdvancementFrameTypeWrapper",
    "packets/PacketPlayOutAdvancementsWrapper",
    "packets/PacketPlayOutSelectAdvancementTabWrapper",
)
SERVER_PACKAGES = ("org/bukkit/", "org/spigotmc/", "net/minecraft/", "io/netty/",
                   "io/papermc/", "com/destroystokyo/paper/")
VERSIONED_CRAFTBUKKIT = re.compile(r"org[/.]bukkit[/.]craftbukkit[/.]v\d+_\d+_R\d+(?:[/$.;]|$)")
ADAPTER_PACKAGE = re.compile(re.escape(PACKAGE) + r"nms/(v\d+_\d+_R\d+)/")
# Full retained license from Common/src/licenses, normalized to LF for cross-platform packaging.
APACHE_LICENSE = "META-INF/.libs/Apache License, Version 2.0.txt"
APACHE_LICENSE_SHA256 = "c71d239df91726fc519c6eb72d318ec65820627232b2f796219e87dcf35d0ab4"


def class_info(data):
    """Read Java class versions and constant-pool UTF-8 entries without loading classes."""
    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("Invalid or truncated class header")
    minor, major, count = struct.unpack_from(">HHH", data, 4)
    offset, index, strings = 10, 1, []
    while index < count:
        if offset >= len(data):
            raise ValueError("Truncated constant pool")
        tag = data[offset]
        offset += 1
        if tag == 1:
            if offset + 2 > len(data):
                raise ValueError("Truncated UTF-8 length")
            length = struct.unpack_from(">H", data, offset)[0]
            offset += 2
            if offset + length > len(data):
                raise ValueError("Truncated UTF-8 constant")
            # Modified UTF-8 can contain surrogate pairs. ASCII package names are unaffected.
            strings.append(data[offset:offset + length].decode("utf-8", errors="replace"))
            offset += length
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            offset += 4
        elif tag in (5, 6):
            offset += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            offset += 2
        elif tag == 15:
            offset += 3
        else:
            raise ValueError(f"Unknown constant-pool tag {tag}")
        if offset > len(data):
            raise ValueError("Truncated constant-pool entry")
        index += 1
    return major, minor, strings


def manifest_attributes(data):
    lines = []
    for line in data.decode("utf-8-sig").splitlines():
        if not line:
            break  # Main attributes only; later sections can have different versions.
        if line.startswith(" ") and lines:
            lines[-1] += line[1:]
        else:
            lines.append(line)
    return {key.lower(): value.strip() for line in lines if ":" in line
            for key, value in [line.split(":", 1)]}


def plugin_attributes(data):
    # These release metadata fields are single-line YAML scalars, not general YAML documents.
    attributes = {}
    for line in data.decode("utf-8-sig").splitlines():
        match = re.match(r"^([\w-]+):[ \t]*(.*)$", line)
        if match:
            value = match.group(2).strip()
            if value[:1] in ("'", '"') and value[-1:] == value[:1]:
                value = value[1:-1]
            else:
                value = re.split(r"\s+#", value, maxsplit=1)[0].strip()
            attributes[match.group(1)] = value
    return attributes


def check_jar(jar_path, expected_version):
    report = {"schemaVersion": 1, "checkedAt": datetime.now(timezone.utc).isoformat(),
              "scope": "distribution-structure", "jar": str(jar_path),
              "expectedVersion": expected_version, "checks": []}

    def check(check_id, passed, detail, **evidence):
        report["checks"].append({"id": check_id, "status": "PASS" if passed else "FAIL",
                                 "required": True, "detail": detail, **evidence})

    try:
        digest = hashlib.sha256()
        with jar_path.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
        report.update(sha256=digest.hexdigest(), bytes=jar_path.stat().st_size)
        with zipfile.ZipFile(jar_path) as jar:
            corrupt_entry = jar.testzip()
            check("zip_integrity", corrupt_entry is None,
                  "Every entry passes its ZIP CRC check.", corruptEntry=corrupt_entry)
            counts = Counter(info.filename for info in jar.infolist() if not info.is_dir())
            duplicates = sorted(name for name, count in counts.items() if count > 1)
            check("unique_entries", not duplicates, "Archive entries must be unambiguous.",
                  duplicates=duplicates)
            names = set(counts)

            classes, malformed, preview, craft_references, server_classes = [], [], [], [], []
            majors = Counter()
            adapter_classes = {adapter: [] for adapter in ADAPTERS}
            limits = {"common": [], "shared": [], "native": []}
            unknown_adapters = set()
            for entry in sorted(name for name in names if name.endswith(".class")):
                logical_path = re.sub(r"^META-INF/versions/\d+/", "", entry)
                if logical_path.startswith(SERVER_PACKAGES):
                    server_classes.append(entry)
                try:
                    major, minor, constants = class_info(jar.read(entry))
                except (ValueError, struct.error) as error:
                    malformed.append({"class": entry, "error": str(error)})
                    continue
                classes.append(entry)
                majors[str(major)] += 1
                if minor == 65535:
                    preview.append(entry)
                references = sorted(set(match.group(0) for constant in constants
                                        for match in VERSIONED_CRAFTBUKKIT.finditer(constant)))
                if references:
                    craft_references.append({"class": entry, "references": references})
                adapter_match = ADAPTER_PACKAGE.match(logical_path)
                if adapter_match:
                    adapter = adapter_match.group(1)
                    if adapter not in ADAPTERS:
                        unknown_adapters.add(adapter)
                        limits["native"].append({"class": entry, "major": major,
                                                 "error": "Unexpected adapter family"})
                        continue
                    adapter_classes[adapter].append(entry)
                    expected_major = ADAPTERS[adapter]
                    valid = major <= expected_major if expected_major == 65 else major == 69
                    if not valid:
                        limits["native"].append({"class": entry, "major": major,
                                                 "expected": "<=65" if expected_major == 65 else "69"})
                    continue

                # Everything outside the selected native packages must load on Java 21.
                if major > 65:
                    limits["shared"].append({"class": entry, "major": major, "maximum": 65})
                relative = logical_path[len(PACKAGE):] if logical_path.startswith(PACKAGE) else None
                plugin_class = relative is not None and (
                    relative.startswith(("commands/", "metrics/", "libs/"))
                    or re.match(r"(?:AdvancementPlugin|ConfigManager)(?:\$|\.class$)", relative)
                )
                if relative is not None and not plugin_class and major > 60:
                    limits["common"].append({"class": entry, "major": major, "maximum": 60})

            report["classCount"] = len(classes)
            report["classMajorVersions"] = dict(sorted(majors.items()))
            check("class_headers", bool(classes) and not malformed,
                  "Class headers and constant pools are readable.", malformed=malformed)
            check("no_preview_bytecode", not preview,
                  "The distribution does not require --enable-preview.", classes=preview)
            check("java21_shared_boundary", not limits["shared"],
                  "Every class outside native adapter packages targets Java 21 or older.",
                  violations=limits["shared"])
            check("java16_common_boundary", not limits["common"],
                  "Common API and shared NMS interfaces retain Java 16 or older bytecode.",
                  violations=limits["common"])
            check("native_bytecode_boundaries", not limits["native"],
                  "1.21 native classes target Java 21 or older; 26.x native classes target Java 25.",
                  violations=limits["native"], unexpectedAdapters=sorted(unknown_adapters))

            missing = {}
            for adapter in ADAPTERS:
                prefix = PACKAGE + "nms/" + adapter + "/"
                required = [prefix + kind + "_" + adapter + ".class" for kind in ADAPTER_TYPES]
                absent = [entry for entry in required if entry not in names]
                if absent:
                    missing[adapter] = absent
            check("ten_native_adapters", not missing,
                  "All ten adapters contain their eight core wrapper classes.", missing=missing,
                  classCounts={adapter: len(entries) for adapter, entries in adapter_classes.items()})
            check("no_bundled_server_classes", not server_classes,
                  "Bukkit, Spigot, Minecraft, Netty and Paper server packages are not bundled.",
                  classes=server_classes)
            check("unversioned_craftbukkit", not craft_references,
                  "No class constant pool retains a versioned CraftBukkit package reference.",
                  violations=craft_references)

            metadata = {}
            if "plugin.yml" in names:
                metadata = plugin_attributes(jar.read("plugin.yml"))
            check("plugin_metadata", metadata.get("version") == expected_version
                  and metadata.get("main") == "com.fren_gor.ultimateAdvancementAPI.AdvancementPlugin"
                  and metadata.get("folia-supported", "").lower() == "true"
                  and PACKAGE + "AdvancementPlugin.class" in names,
                  "plugin.yml declares the release version, entry point and Folia support.",
                  metadata=metadata)
            manifest = manifest_attributes(jar.read("META-INF/MANIFEST.MF")) \
                if "META-INF/MANIFEST.MF" in names else {}
            check("mojang_manifest", manifest.get("paperweight-mappings-namespace") == "mojang"
                  and manifest.get("implementation-version") == expected_version,
                  "Manifest declares Mojang mappings and the same release version.",
                  attributes=manifest)
            marker = jar.read("uaa-modern-distribution").decode("utf-8-sig").strip() \
                if "uaa-modern-distribution" in names else ""
            check("distribution_marker", bool(marker),
                  "The modern distribution marker exists and is nonempty.", content=marker)
            required_notices = {"LICENSE": b"GNU GENERAL PUBLIC LICENSE",
                                "LGPL": b"GNU LESSER GENERAL PUBLIC LICENSE",
                                "NOTICE": b"IOVEYOUMC0/UltimateAdvancementAPI"}
            missing_notices = [name for name, text in required_notices.items()
                               if name not in names or text not in jar.read(name)]
            check("licenses_and_notice", not missing_notices,
                  "GPL/LGPL license texts and the retained upstream attribution are present.",
                  missingOrIncomplete=missing_notices)
            apache_digest = hashlib.sha256(jar.read(APACHE_LICENSE).replace(b"\r\n", b"\n")).hexdigest() \
                if APACHE_LICENSE in names else None
            check("retained_apache_license", apache_digest == APACHE_LICENSE_SHA256,
                  "The separate Apache license retains its complete source text after LF normalization.",
                  entry=APACHE_LICENSE, actualSha256=apache_digest, expectedSha256=APACHE_LICENSE_SHA256)
    except (OSError, ValueError, zipfile.BadZipFile, RuntimeError, UnicodeError) as error:
        check("read_distribution", False, f"{type(error).__name__}: {error}")

    report["success"] = bool(report["checks"]) and all(item["status"] == "PASS" for item in report["checks"])
    report["overall"] = "PASS" if report["success"] else "FAIL"
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path, help="Final installation JAR to inspect")
    parser.add_argument("--expected-version", required=True, help="Expected plugin and manifest version")
    parser.add_argument("--output", type=Path, help="Also save the JSON report to this path")
    args = parser.parse_args()
    jar_path = args.jar.resolve()
    if args.output and (args.output.resolve() == jar_path
                        or args.output.exists() and jar_path.exists() and args.output.samefile(jar_path)):
        parser.error("--output must not overwrite the installation JAR")
    report = check_jar(jar_path, args.expected_version)
    encoded = json.dumps(report, ensure_ascii=True, indent=2) + "\n"
    if args.output:
        try:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(encoded, encoding="utf-8")
        except OSError as error:
            print(f"Cannot save distribution report: {error}", file=sys.stderr)
            return 2
    sys.stdout.write(encoded)
    failed = [check["id"] for check in report["checks"] if check["status"] == "FAIL"]
    if failed:
        print("Distribution audit FAIL: " + ", ".join(failed), file=sys.stderr)
        return 1
    print(f"Distribution audit PASS: {report['classCount']} classes, 10 adapters; SHA-256 {report['sha256']}",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
