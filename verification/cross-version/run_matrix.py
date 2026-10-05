"""Prepare and explicitly run fresh, loopback-only UAA native verification servers."""
from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import os
import re
import shutil
import socket
import subprocess
import threading
import time
import uuid
import zipfile
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
LOCAL = REPO / ".local" / "cross-version"
ASSETS = LOCAL / "server-assets"
OFFICIAL_MANIFEST = ASSETS / "official-assets-manifest.json"
SUPPLEMENTAL_MANIFEST = ASSETS / "supplemental-assets-manifest.json"
SERVERS: Path | None = None  # Chosen explicitly during preparation, retained in the prepared plan.
DEFAULT_UAA = os.environ.get("UAA_VERIFICATION_JAR")
JAVA21 = os.environ.get("JAVA21_HOME") or os.environ.get("JDK21_HOME") or os.environ.get("JAVA_HOME")
JAVA25 = os.environ.get("JAVA25_HOME") or os.environ.get("JDK25_HOME")
PROBE = LOCAL / "probe" / "UaaCrossVersionVerification.jar"
NO_WINDOW = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
ALLOWED_RUNTIME_SEEDS: tuple[Path, ...] = ()
ADAPTERS = {
    "1.21": "v1_21_R1", "1.21.1": "v1_21_R1", "1.21.2": "v1_21_R2", "1.21.3": "v1_21_R2",
    "1.21.4": "v1_21_R3", "1.21.5": "v1_21_R4", "1.21.6": "v1_21_R5", "1.21.7": "v1_21_R5",
    "1.21.8": "v1_21_R5", "1.21.9": "v1_21_R6", "1.21.10": "v1_21_R6", "1.21.11": "v1_21_R7",
    "26.1": "v26_1_R2", "26.1.1": "v26_1_R2", "26.1.2": "v26_1_R2",
    "26.2": "v26_2_R1", "26.3": "v26_3_R1",
}


def utc() -> str:
    return datetime.now(timezone.utc).isoformat()


def parse_instant(value: str) -> datetime:
    # Java Instant may emit nine fractional digits; Python before 3.11 accepts at most six.
    normalized = re.sub(r"(\.\d{6})\d+(?=Z|[+-]\d\d:\d\d|$)", r"\1", value)
    return datetime.fromisoformat(normalized.replace("Z", "+00:00"))


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def artifact_version(path: Path) -> str:
    with zipfile.ZipFile(path) as jar:
        manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8")
    for line in manifest.splitlines():
        if line.startswith("Implementation-Version:"):
            return line.split(":", 1)[1].strip()
    raise ValueError("Final UAA artifact does not declare Implementation-Version")


def inside(path: Path, root: Path) -> Path:
    resolved = path.resolve()
    if not resolved.is_relative_to(root.resolve()):
        raise ValueError(f"Path is outside the required directory: {resolved}")
    return resolved


def manifest_assets() -> list[dict]:
    assets = read_json(OFFICIAL_MANIFEST)["assets"]
    if SUPPLEMENTAL_MANIFEST.exists():
        assets += read_json(SUPPLEMENTAL_MANIFEST)["assets"]
    keys = [(item["project"], item["minecraftVersion"]) for item in assets]
    if len(keys) != len(set(keys)):
        raise ValueError("Duplicate server/version entries in asset manifests")
    return assets


def supplement(args: argparse.Namespace) -> None:
    """Read only approved launcher files; metadata and copies stay in UAA's local assets."""
    records = []
    entries = [("paper", "26.3", 140, Path(args.paper_launcher)),
               ("folia", "26.2", 7, Path(args.folia_launcher))]
    ASSETS.mkdir(parents=True, exist_ok=True)
    for project, version, build, launcher in entries:
        source = launcher.resolve()
        base = source.parent
        if not source.is_file() or source.suffix.lower() != ".jar":
            raise FileNotFoundError(f"Explicit {project} {version} launcher was not found: {source}")
        url = f"https://fill.papermc.io/v3/projects/{project}/versions/{version}/builds/{build}"
        metadata_path = ASSETS / f"{project}-{version}-build-{build}.json"
        result = subprocess.run(["curl.exe", "--http1.1", "--fail", "--silent", "--show-error",
                                 "--connect-timeout", "6", "--max-time", "30", "--user-agent",
                                 "UltimateAdvancementAPI-Compatibility-Validation/1.0 (isolated local testing)",
                                 "--output", str(metadata_path), url], capture_output=True, text=True,
                                encoding="utf-8", errors="replace", creationflags=NO_WINDOW)
        if result.returncode:
            raise RuntimeError(f"Official supplemental metadata failed: {url}: {result.stderr.strip()}")
        download = read_json(metadata_path)["downloads"]["server:default"]
        actual = sha256(source)
        expected = download["checksums"]["sha256"]
        if actual != expected or source.stat().st_size != download["size"]:
            raise ValueError(f"Approved {project} launcher differs from official build {build}; no copy made")
        destination = inside(ASSETS / Path(download["name"]).name, ASSETS)
        shutil.copyfile(source, destination)
        records.append({"project": project, "minecraftVersion": version, "build": build,
                        "minimumJava": 25, "availability": "available", "downloadStatus": "sha256-verified",
                        "expectedSha256": expected, "expectedBytes": download["size"],
                        "actualSha256": actual, "localPath": str(destination), "buildMetadataUrl": url,
                        "downloadUrl": download["url"], "approvedReadOnlyRuntimeSeed": str(base)})
    write_json(SUPPLEMENTAL_MANIFEST, {"fetchedUtc": utc(), "scope": "Approved launcher read-copy only; no server started", "assets": records})
    print(json.dumps({"supplementalManifest": str(SUPPLEMENTAL_MANIFEST), "assets": len(records)}), flush=True)


def compile_probe(args: argparse.Namespace) -> None:
    uaa = Path(args.uaa_jar).resolve()
    if not uaa.is_file():
        raise FileNotFoundError(f"Final UAA artifact is not ready: {uaa}")
    compiler = Path(args.java21) / "bin" / "javac.exe"
    archiver = Path(args.java21) / "bin" / "jar.exe"
    api = Path(args.spigot_api)
    bungee = Path(args.bungee_chat)
    for path in (compiler, archiver, api, bungee):
        if not path.is_file():
            raise FileNotFoundError(path)
    output = LOCAL / "probe" / ("compile-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:6])
    classes = output / "classes"
    classes.mkdir(parents=True)
    source = Path(__file__).parent
    classpath = os.pathsep.join(map(str, (uaa, api, bungee)))
    command = [str(compiler), "--release", "21", "-encoding", "UTF-8", "-classpath", classpath,
               "-d", str(classes), str(source / "UaaCrossVersionVerification.java")]
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace", creationflags=NO_WINDOW)
    (output / "javac.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(f"Probe compilation failed; inspect {output / 'javac.log'}")
    shutil.copyfile(source / "plugin.yml", classes / "plugin.yml")
    main_class = classes / "UaaCrossVersionVerification.class"
    if int.from_bytes(main_class.read_bytes()[6:8], "big") != 65:
        raise ValueError("Probe output is not Java 21 bytecode")
    PROBE.parent.mkdir(parents=True, exist_ok=True)
    result = subprocess.run([str(archiver), "--create", "--file", str(PROBE), "--manifest", str(source / "MANIFEST.MF"),
                             "-C", str(classes), "."], capture_output=True, text=True,
                            encoding="utf-8", errors="replace", creationflags=NO_WINDOW)
    (output / "jar.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(f"Probe packaging failed; inspect {output / 'jar.log'}")
    write_json(LOCAL / "probe" / "compile-result.json", {"compiledUtc": utc(), "uaaJar": str(uaa),
               "uaaSha256": sha256(uaa), "probeJar": str(PROBE), "probeSha256": sha256(PROBE), "logDirectory": str(output)})
    print(json.dumps({"probeJar": str(PROBE), "compiledAgainst": str(uaa)}), flush=True)


def safe_copy_runtime(seed: Path, destination: Path) -> None:
    if seed.resolve() not in [root.resolve() for root in ALLOWED_RUNTIME_SEEDS]:
        raise ValueError(f"Runtime seed is not an approved read-only directory: {seed}")
    copy_runtime_trees(seed, destination)


def copy_runtime_trees(seed: Path, destination: Path) -> None:
    # Never enumerate the old server root, plugins, configuration, or worlds.
    for name in ("cache", "libraries"):
        source = inside(seed / name, seed)
        if not source.is_dir():
            continue
        for item in source.rglob("*"):
            checked = inside(item, source)
            target = inside(destination / name / item.relative_to(source), destination)
            if checked.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            elif checked.is_file():
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(checked, target)


def reuse_isolated_runtime(previous: dict, destination: Path, expected_launcher: str) -> None:
    source = inside(Path(previous["serverDirectory"]), SERVERS)
    destination = inside(destination, SERVERS)
    if source == destination:
        raise ValueError("Runtime reuse requires a different, freshly prepared destination")
    marker = read_json(source / ".uaa-isolated-verification.json")
    result = read_json(inside(Path(previous["resultPath"]), source))
    if marker["serverId"] != previous["id"] or result["id"] != previous["id"]:
        raise ValueError("Previous isolated-runtime metadata does not match the requested server")
    if result.get("shutdown") not in ("STDIN_STOP", "ALREADY_EXITED") or result.get("exitCode") != 0:
        raise ValueError("Previous isolated server did not finish with a normal exit; runtime reuse rejected")
    launcher = source / "server.jar"
    if sha256(launcher) != expected_launcher:
        raise ValueError("Previous isolated launcher does not match this official build")
    shutil.copyfile(launcher, destination / "server.jar")
    copy_runtime_trees(source, destination)


def seed_libraries(directory: Path) -> int:
    manifest_path = LOCAL / "shared-libs" / "shared-libs-manifest.json"
    if not manifest_path.is_file():
        return 0
    directory = inside(directory, SERVERS)
    if (directory / "run-state.json").exists():
        return 0  # Never modify a running or previously used server's dependency files.
    manifest = read_json(manifest_path)
    cache = inside(Path(manifest["cacheRoot"]), LOCAL / "shared-libs")
    for library in manifest["libraries"]:
        source = inside(Path(library["localPath"]), cache)
        if library["status"] != "sha256-verified" or sha256(source) != library["expectedSha256"]:
            raise ValueError(f"Unverified shared library: {source}")
        target = inside(directory / "plugins" / "UltimateAdvancementAPI" / ".libs" / library["libbyRelativePath"], directory)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    write_json(directory / ".uaa-libraries-seeded.json", {"seededUtc": utc(), "sourceManifest": str(manifest_path),
               "libraries": [{"path": lib["libbyRelativePath"], "sha256": lib["expectedSha256"]} for lib in manifest["libraries"]]})
    return len(manifest["libraries"])


def seed_prepared_plan(path: Path) -> None:
    plan = read_json(inside(path, SERVERS))
    seeded, skipped = [], []
    for row in plan["rows"]:
        if row["status"] != "PREPARED":
            continue
        count = seed_libraries(Path(row["serverDirectory"]))
        (seeded if count else skipped).append(row["id"])
    print(json.dumps({"seededLibrariesFor": seeded, "unchangedStartedOrUnseeded": skipped}, ensure_ascii=False), flush=True)


def make_plan(args: argparse.Namespace, prepare: bool) -> dict:
    assets = manifest_assets()
    uaa = Path(args.uaa_jar).resolve()
    probe = Path(args.probe_jar).resolve()
    java = {21: Path(args.java21).resolve(), 25: Path(args.java25).resolve()}
    reuse_rows = {}
    if args.reuse_plan:
        previous = read_json(inside(Path(args.reuse_plan), SERVERS))
        if Path(previous["serversRoot"]).resolve() != SERVERS.resolve():
            raise ValueError("Runtime reuse must come from the same explicitly configured isolated root")
        reuse_rows = {row["id"]: row for row in previous["rows"] if row["status"] == "PREPARED"}
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:6]
    batch = inside(SERVERS / stamp, SERVERS)
    if prepare:
        for path in (uaa, probe, java[21] / "bin" / "java.exe", java[25] / "bin" / "java.exe"):
            if not path.is_file():
                raise FileNotFoundError(path)
        compile_result = read_json(LOCAL / "probe" / "compile-result.json")
        if compile_result["uaaSha256"] != sha256(uaa) or compile_result["probeSha256"] != sha256(probe):
            raise ValueError("Probe compilation provenance does not match the final UAA/probe JARs")
        batch.mkdir(parents=True, exist_ok=False)
    plan = {"schemaVersion": 1, "preparedUtc": utc(), "status": "PREPARED" if prepare else "PLAN_ONLY",
            "serversRoot": str(SERVERS), "batchDirectory": str(batch), "uaaJar": str(uaa), "probeJar": str(probe),
            "maximumConcurrency": 3, "defaultConcurrency": 2, "startupTimeoutSeconds": args.timeout,
            "shutdownTimeoutSeconds": args.shutdown_timeout, "rows": []}
    if prepare:
        plan.update(uaaSha256=sha256(uaa), probeSha256=sha256(probe), uaaVersion=artifact_version(uaa))
    index = 0
    for asset in assets:
        project, version = asset["project"], asset["minecraftVersion"]
        row = {"id": f"{project}:{version}", "serverFamily": project, "minecraft": version,
               "expectedAdapter": ADAPTERS.get(version), "entityOwnership": "NOT_RUN",
               "regionOwnership": "NOT_RUN", "clientRendering": "NOT_RUN"}
        if asset.get("availability") != "available":
            row.update(status="NOT_RUN", reason=asset.get("error", "Official build unavailable"))
            plan["rows"].append(row)
            continue
        if asset.get("downloadStatus") not in ("sha256-verified", "verified-existing"):
            row.update(status="NOT_RUN", reason=f"Launcher is not verified: {asset.get('downloadStatus')}")
            plan["rows"].append(row)
            continue
        launcher = inside(Path(asset["localPath"]), ASSETS)
        if not launcher.is_file() or sha256(launcher) != asset["expectedSha256"]:
            raise ValueError(f"Verified launcher asset is missing or changed: {launcher}")
        major = 25 if int(asset["minimumJava"]) >= 25 else 21
        port = args.base_port + index
        if not 1024 <= port <= 65535:
            raise ValueError("Port range is invalid")
        directory = inside(batch / f"{project}-{version}", batch)
        row.update(status="PREPARED" if prepare else "PLANNED", serverBuild=asset["build"], javaMajor=major,
                   javaExecutable=str(java[major] / "bin" / "java.exe"), port=port, bindAddress="127.0.0.1",
                   serverDirectory=str(directory), launcherSha256=asset["expectedSha256"],
                   sourceLauncher=str(launcher), reportPath=str(directory / "plugins" / "UaaCrossVersionVerification" / "report.json"),
                   logPath=str(directory / "process.log"), resultPath=str(directory / "run-result.json"))
        if prepare:
            directory.mkdir(exist_ok=False)
            (directory / "plugins").mkdir()
            shutil.copyfile(launcher, directory / "server.jar")
            shutil.copyfile(uaa, directory / "plugins" / "UltimateAdvancementAPI.jar")
            shutil.copyfile(probe, directory / "plugins" / "UaaCrossVersionVerification.jar")
            seed_libraries(directory)
            seed = asset.get("approvedReadOnlyRuntimeSeed")
            previous_row = reuse_rows.get(row["id"])
            if previous_row is not None:
                if previous_row["serverBuild"] != row["serverBuild"]:
                    raise ValueError("Runtime reuse requires the same official server build")
                reuse_isolated_runtime(previous_row, directory, row["launcherSha256"])
                row["isolatedRuntimeReuse"] = previous_row["serverDirectory"]
            elif seed and Path(seed).resolve() in [root.resolve() for root in ALLOWED_RUNTIME_SEEDS]:
                safe_copy_runtime(Path(seed), directory)
                row["runtimeSeed"] = seed
            (directory / "eula.txt").write_text("eula=true\n", encoding="ascii")
            properties = {
                "server-ip": "127.0.0.1", "server-port": str(port), "online-mode": "true", "white-list": "true",
                "enable-query": "false", "enable-rcon": "false", "enable-status": "false",
                "max-players": "1", "view-distance": "2", "simulation-distance": "2",
                "level-name": "uaa_probe_world", "level-type": "minecraft:flat", "level-seed": "1",
                "generator-settings": json.dumps({"biome": "minecraft:plains", "features": False,
                    "layers": [{"block": "minecraft:bedrock", "height": 1}, {"block": "minecraft:dirt", "height": 2},
                               {"block": "minecraft:grass_block", "height": 1}], "structure_overrides": []}, separators=(",", ":")),
                "generate-structures": "false", "allow-nether": "false", "spawn-protection": "0",
                "spawn-chunk-radius": "0", "spawn-animals": "false", "spawn-monsters": "false", "spawn-npcs": "false",
                "difficulty": "peaceful", "gamemode": "creative", "max-tick-time": "-1",
                "motd": "Isolated UAA native compatibility verification", "sync-chunk-writes": "true",
            }
            (directory / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in properties.items()), encoding="ascii")
            (directory / "bukkit.yml").write_text("settings:\n  allow-end: false\nspawn-limits:\n  monsters: 0\n  animals: 0\n  water-animals: 0\n  water-ambient: 0\n  water-underground-creature: 0\n  axolotls: 0\n  ambient: 0\n", encoding="ascii")
            write_json(directory / ".uaa-isolated-verification.json", {"createdUtc": utc(), "serverId": row["id"],
                       "uaaSha256": plan["uaaSha256"], "probeSha256": plan["probeSha256"], "port": port})
            print(json.dumps({"id": row["id"], "stage": "PREPARED", "reusedIsolatedRuntime": previous_row is not None}, ensure_ascii=False), flush=True)
        plan["rows"].append(row)
        index += 1
    output = batch / "prepared-plan.json" if prepare else LOCAL / "servers-plan.json"
    write_json(output, plan)
    print(json.dumps({"planPath": str(output), "status": plan["status"], "availableServers": index,
                      "notRun": [row["id"] for row in plan["rows"] if row["status"] == "NOT_RUN"]}), flush=True)
    return plan


def stop_process(process: subprocess.Popen, shutdown_timeout: int) -> tuple[str, int | None]:
    if process.poll() is not None:
        return "ALREADY_EXITED", process.returncode
    try:
        process.stdin.write("stop\n")
        process.stdin.flush()
        process.wait(timeout=shutdown_timeout)
        return "STDIN_STOP", process.returncode
    except (BrokenPipeError, OSError, subprocess.TimeoutExpired):
        if process.poll() is None:
            process.kill()  # Only this newly spawned JVM, never a system-wide process command.
        process.wait(timeout=15)
        return "FORCED_TERMINATION_AFTER_TIMEOUT", process.returncode


def run_one(row: dict, plan: dict, timeout: int, shutdown_timeout: int, cancelled: threading.Event) -> dict:
    result = dict(row, startedUtc=utc(), overall="FAIL", status="FAILED")
    process = None
    started = time.monotonic()
    try:
        if cancelled.is_set():
            raise RuntimeError("Run cancelled before launch")
        directory = inside(Path(row["serverDirectory"]), SERVERS)
        inside(Path(row["reportPath"]), directory)
        inside(Path(row["logPath"]), directory)
        inside(Path(row["resultPath"]), directory)
        if row["bindAddress"] != "127.0.0.1":
            raise ValueError("Prepared server is not bound to loopback")
        marker = read_json(directory / ".uaa-isolated-verification.json")
        if marker["serverId"] != row["id"]:
            raise ValueError("Isolated-directory marker does not match the prepared row")
        if (directory / "run-state.json").exists() or Path(row["reportPath"]).exists():
            raise ValueError("Prepared directory was already used; prepare a fresh batch instead")
        properties = (directory / "server.properties").read_text(encoding="ascii").splitlines()
        if "server-ip=127.0.0.1" not in properties or f"server-port={row['port']}" not in properties:
            raise ValueError("Loopback address/port properties differ from the prepared plan")
        files = [(directory / "server.jar", row["launcherSha256"]),
                 (directory / "plugins" / "UltimateAdvancementAPI.jar", plan["uaaSha256"]),
                 (directory / "plugins" / "UaaCrossVersionVerification.jar", plan["probeSha256"])]
        for path, expected in files:
            if sha256(path) != expected:
                raise ValueError(f"Prepared artifact changed: {path}")
        if set(path.name for path in (directory / "plugins").glob("*.jar")) != {
                "UltimateAdvancementAPI.jar", "UaaCrossVersionVerification.jar"}:
            raise ValueError("Prepared server has an unexpected plugin JAR")
        seed_libraries(directory)
        with socket.socket() as reservation:
            if hasattr(socket, "SO_EXCLUSIVEADDRUSE"):
                reservation.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
            reservation.bind(("127.0.0.1", row["port"]))
        write_json(directory / "run-state.json", {"status": "STARTED", "startedUtc": result["startedUtc"]})
        command = [row["javaExecutable"], "-Xms128M", "-Xmx512M", "-XX:ActiveProcessorCount=2",
                   "-Dfile.encoding=UTF-8", "-Djava.net.preferIPv4Stack=true", "-Dterminal.jline=false",
                   "-Dterminal.ansi=false", "-jar", "server.jar", "--nogui"]
        result["command"] = command
        with Path(row["logPath"]).open("wb") as log:
            process = subprocess.Popen(command, cwd=directory, stdin=subprocess.PIPE, stdout=log,
                                       stderr=subprocess.STDOUT, text=True, encoding="utf-8", creationflags=NO_WINDOW)
            result["pid"] = process.pid
            print(json.dumps({"id": row["id"], "stage": "STARTED", "pid": process.pid,
                              "port": row["port"], "logPath": row["logPath"]}, ensure_ascii=False), flush=True)
            last_progress = time.monotonic()
            while time.monotonic() - started < timeout:
                if cancelled.is_set():
                    raise RuntimeError("Run cancelled while waiting for the native report")
                report_path = Path(row["reportPath"])
                if report_path.is_file():
                    try:
                        native = read_json(report_path)
                    except (json.JSONDecodeError, OSError):
                        time.sleep(0.25)
                        continue
                    result["nativeReport"] = native
                    if parse_instant(native["startedAt"]) < parse_instant(result["startedUtc"]):
                        raise ValueError("Native report predates this server launch")
                    if native.get("adapter") != row["expectedAdapter"]:
                        raise ValueError(f"Reported adapter mismatch: {native.get('adapter')}")
                    if native.get("minecraft") != row["minecraft"] or bool(native.get("folia")) != (row["serverFamily"] == "folia"):
                        raise ValueError("Reported server version/family differs from the prepared matrix row")
                    expected_version = plan.get("uaaVersion") or artifact_version(directory / "plugins" / "UltimateAdvancementAPI.jar")
                    if native.get("apiVersion") != expected_version:
                        raise ValueError(f"Reported UAA version differs from the hashed artifact: {native.get('apiVersion')}")
                    required = [check for check in native.get("checks", []) if check.get("required")]
                    required_pass = bool(required) and all(check.get("status") == "PASS" for check in required)
                    result.update(overall="PASS" if native.get("success") is True and native.get("overall") == "PASS" else "FAIL",
                                  status="COMPLETE", adapter=native.get("adapter"))
                    if not required_pass:
                        result["overall"] = "FAIL"
                    break
                if process.poll() is not None:
                    raise RuntimeError(f"Server exited before a native report (exit {process.returncode}); inspect process.log")
                if time.monotonic() - last_progress >= 20:
                    with Path(row["logPath"]).open("rb") as progress_log:
                        progress_log.seek(max(0, Path(row["logPath"]).stat().st_size - 4096))
                        tail = progress_log.read().decode("utf-8", errors="replace").splitlines()
                    print(json.dumps({"id": row["id"], "stage": "WAITING_FOR_NATIVE_REPORT",
                                      "elapsedSeconds": round(time.monotonic() - started, 1),
                                      "latestLogLine": tail[-1] if tail else "No bootstrap output yet"}, ensure_ascii=False), flush=True)
                    last_progress = time.monotonic()
                time.sleep(0.5)
            else:
                raise TimeoutError(f"No native report within {timeout}s; official bootstrap or plugin startup did not complete")
            shutdown, code = stop_process(process, shutdown_timeout)
            result.update(shutdown=shutdown, exitCode=code)
            if shutdown == "FORCED_TERMINATION_AFTER_TIMEOUT" or code != 0:
                result.update(overall="FAIL", status="FAILED", error=f"Server shutdown did not complete normally: {shutdown}, exit {code}")
    except Exception as error:
        result.update(overall="FAIL", status="FAILED", error=str(error))
    finally:
        if process is not None and process.poll() is None:
            shutdown, code = stop_process(process, shutdown_timeout)
            result.update(shutdown=shutdown, exitCode=code)
        result.update(finishedUtc=utc(), elapsedSeconds=round(time.monotonic() - started, 2))
        directory = inside(Path(row["serverDirectory"]), SERVERS)
        write_json(inside(Path(row["resultPath"]), directory), result)
        write_json(directory / "run-state.json", {"status": result["status"], "finishedUtc": result["finishedUtc"]})
        print(json.dumps({key: result.get(key) for key in ("id", "overall", "elapsedSeconds", "shutdown", "error", "reportPath", "logPath")}), flush=True)
    return result


def run_matrix(args: argparse.Namespace) -> None:
    path = inside(Path(args.plan), SERVERS)
    plan = read_json(path)
    if plan.get("status") != "PREPARED" or Path(plan["serversRoot"]).resolve() != SERVERS.resolve():
        raise ValueError("Only a prepared plan under the intended isolated-server root can be run")
    selected = set(args.only.split(",")) if args.only else None
    candidates = [row for row in plan["rows"] if row["status"] == "PREPARED" and (selected is None or row["id"] in selected)]
    existing = []
    rows = []
    for row in candidates:
        if Path(row["resultPath"]).is_file():
            if selected:
                raise ValueError(f"Explicitly selected row was already used: {row['id']}; prepare a fresh batch to repeat it")
            existing.append(read_json(Path(row["resultPath"])))
        elif (Path(row["serverDirectory"]) / "run-state.json").exists():
            raise ValueError(f"Selected row already has a launch state without a completed result: {row['id']}")
        else:
            rows.append(row)
    if selected and selected - {row["id"] for row in rows}:
        raise ValueError(f"Requested rows are not prepared: {sorted(selected - {row['id'] for row in rows})}")
    if not rows and not existing:
        raise ValueError("No prepared server rows were selected")
    timeout = args.timeout or plan["startupTimeoutSeconds"]
    shutdown_timeout = args.shutdown_timeout or plan["shutdownTimeoutSeconds"]
    cancelled = threading.Event()
    results = existing
    aggregate = inside(Path(plan["batchDirectory"]) / "matrix-results.json", SERVERS)
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = [pool.submit(run_one, row, plan, timeout, shutdown_timeout, cancelled) for row in rows]
        try:
            for future in concurrent.futures.as_completed(futures):
                results.append(future.result())
                write_json(aggregate, {"updatedUtc": utc(), "uaaSha256": plan["uaaSha256"],
                           "probeSha256": plan["probeSha256"], "results": results,
                           "notRun": [row for row in plan["rows"] if row["status"] == "NOT_RUN"]})
        except KeyboardInterrupt:
            cancelled.set()
            for future in futures:
                future.cancel()
            raise
    print(json.dumps({"matrixResults": str(aggregate), "passed": sum(row["overall"] == "PASS" for row in results),
                      "failed": sum(row["overall"] == "FAIL" for row in results)}), flush=True)


def repair_timestamp_result(path: Path, server_id: str) -> None:
    plan = read_json(inside(path, SERVERS))
    row = next(row for row in plan["rows"] if row["id"] == server_id)
    directory = inside(Path(row["serverDirectory"]), SERVERS)
    result_path = inside(Path(row["resultPath"]), directory)
    result = read_json(result_path)
    native = read_json(inside(Path(row["reportPath"]), directory))
    original_error = result.get("error", "")
    if "Invalid isoformat string" not in original_error or result.get("shutdown") != "STDIN_STOP" or result.get("exitCode") != 0:
        raise ValueError("Existing failure is not solely the timestamp parser with a normal exit")
    if native != result.get("nativeReport") or native.get("success") is not True or native.get("overall") != "PASS":
        raise ValueError("The preserved native report did not pass or differs from the original controller snapshot")
    if any(check.get("required") and check.get("status") != "PASS" for check in native["checks"]):
        raise ValueError("A required native check did not pass")
    if native.get("adapter") != row["expectedAdapter"] or native.get("minecraft") != row["minecraft"]:
        raise ValueError("Preserved native server/adapter metadata does not match")
    if bool(native.get("folia")) != (row["serverFamily"] == "folia"):
        raise ValueError("Preserved native server family does not match")
    if not parse_instant(result["startedUtc"]) <= parse_instant(native["startedAt"]) <= parse_instant(native["finishedAt"]) <= parse_instant(result["finishedUtc"]):
        raise ValueError("Preserved native report timestamps are outside the original launch")
    for artifact, expected in ((directory / "server.jar", row["launcherSha256"]),
                               (directory / "plugins" / "UltimateAdvancementAPI.jar", plan["uaaSha256"]),
                               (directory / "plugins" / "UaaCrossVersionVerification.jar", plan["probeSha256"])):
        if sha256(artifact) != expected:
            raise ValueError(f"Preserved server artifact differs from the prepared hash: {artifact}")
    result.update(controllerOriginalError=original_error, controllerCorrection="Nanosecond Instant parsing fixed; original native report and log preserved; no server rerun",
                  correctedUtc=utc(), overall="PASS", status="COMPLETE", adapter=native["adapter"])
    result.pop("error", None)
    write_json(result_path, result)
    write_json(directory / "run-state.json", {"status": "COMPLETE", "finishedUtc": result["finishedUtc"], "correctedUtc": result["correctedUtc"]})
    aggregate_path = inside(Path(plan["batchDirectory"]) / "matrix-results.json", SERVERS)
    aggregate = read_json(aggregate_path)
    aggregate["results"] = [result if item["id"] == server_id else item for item in aggregate["results"]]
    aggregate["updatedUtc"] = utc()
    write_json(aggregate_path, aggregate)
    print(json.dumps({"id": server_id, "overall": "PASS", "controllerOriginalError": original_error,
                      "preservedReport": row["reportPath"], "preservedLog": row["logPath"]}, ensure_ascii=False), flush=True)


def main() -> None:
    global SERVERS, ALLOWED_RUNTIME_SEEDS
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="phase", required=True)
    supplemental = subcommands.add_parser("supplement", help="Verify/copy explicitly supplied 26.3/26.2 launchers and official metadata; no startup")
    supplemental.add_argument("--paper-launcher", required=True)
    supplemental.add_argument("--folia-launcher", required=True)
    compiler = subcommands.add_parser("compile-probe", help="Compile and package only the separate verification plugin")
    compiler.add_argument("--uaa-jar", default=DEFAULT_UAA, required=not DEFAULT_UAA)
    compiler.add_argument("--java21", default=JAVA21, required=not JAVA21)
    compiler.add_argument("--spigot-api", default=os.environ.get("UAA_VERIFICATION_SPIGOT_API"), required=not os.environ.get("UAA_VERIFICATION_SPIGOT_API"))
    compiler.add_argument("--bungee-chat", default=os.environ.get("UAA_VERIFICATION_BUNGEE_CHAT"), required=not os.environ.get("UAA_VERIFICATION_BUNGEE_CHAT"))
    seeder = subcommands.add_parser("seed-libs", help="Preseed verified libraries for prepared rows that have never been started")
    seeder.add_argument("--plan", required=True)
    seeder.add_argument("--servers-root", default=os.environ.get("UAA_VERIFICATION_SERVERS_ROOT"))
    repairer = subcommands.add_parser("repair-timestamp", help="Recheck an existing PASS native report rejected only by nanosecond timestamp parsing")
    repairer.add_argument("--plan", required=True)
    repairer.add_argument("--server-id", required=True)
    repairer.add_argument("--servers-root", default=os.environ.get("UAA_VERIFICATION_SERVERS_ROOT"))
    for phase in ("plan", "prepare"):
        command = subcommands.add_parser(phase, help="Create a plan" if phase == "plan" else "Copy artifacts and write fresh server directories; no startup")
        command.add_argument("--uaa-jar", default=DEFAULT_UAA, required=not DEFAULT_UAA)
        command.add_argument("--probe-jar", default=str(PROBE))
        command.add_argument("--java21", default=JAVA21, required=not JAVA21)
        command.add_argument("--java25", default=JAVA25, required=not JAVA25)
        command.add_argument("--servers-root", default=os.environ.get("UAA_VERIFICATION_SERVERS_ROOT"), required=not os.environ.get("UAA_VERIFICATION_SERVERS_ROOT"))
        command.add_argument("--allow-runtime-seed", action="append", default=[], help="Explicitly allow read-copy of only cache/libraries under this directory")
        command.add_argument("--reuse-plan", help="Copy only launcher/cache/libraries from matching, normally exited rows in an earlier isolated batch")
        command.add_argument("--base-port", type=int, default=25670)
        command.add_argument("--timeout", type=int, default=300)
        command.add_argument("--shutdown-timeout", type=int, default=60)
    runner = subcommands.add_parser("run", help="Explicitly launch only fresh prepared isolated server rows")
    runner.add_argument("--plan", required=True)
    runner.add_argument("--servers-root", default=os.environ.get("UAA_VERIFICATION_SERVERS_ROOT"), help="Defaults to the root recorded during explicit plan preparation")
    runner.add_argument("--only", help="Comma-separated exact row IDs, for example paper:1.21.1,folia:26.2")
    runner.add_argument("--concurrency", type=int, choices=(1, 2, 3), default=2)
    runner.add_argument("--timeout", type=int)
    runner.add_argument("--shutdown-timeout", type=int)
    args = parser.parse_args()
    if hasattr(args, "servers_root"):
        root = args.servers_root or read_json(Path(args.plan))["serversRoot"]
        SERVERS = Path(root).resolve()
    ALLOWED_RUNTIME_SEEDS = tuple(Path(root).resolve() for root in getattr(args, "allow_runtime_seed", []))
    if args.phase == "supplement":
        supplement(args)
    elif args.phase == "compile-probe":
        compile_probe(args)
    elif args.phase == "seed-libs":
        seed_prepared_plan(Path(args.plan))
    elif args.phase == "repair-timestamp":
        repair_timestamp_result(Path(args.plan), args.server_id)
    elif args.phase in ("plan", "prepare"):
        make_plan(args, args.phase == "prepare")
    else:
        run_matrix(args)


if __name__ == "__main__":
    main()
