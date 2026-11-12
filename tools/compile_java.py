#!/usr/bin/env python3
"""Incremental Java -> classes.dex compiler for Leostryx/src."""
import hashlib
import shutil
import struct
import subprocess
import tempfile
import zlib
from pathlib import Path

ANDROID_JAR = Path("/opt/android/sdk/platforms/android-36/android.jar")
D8_BIN = Path("/opt/android/sdk/build-tools/36.0.0/d8")


def _strip_classes_from_dex(dex_bytes: bytes, descriptors_to_strip: set) -> bytes:
    dex = bytearray(dex_bytes)
    map_off = struct.unpack_from("<I", dex, 52)[0]
    class_defs_size, class_defs_off = struct.unpack_from("<II", dex, 96)
    _, type_ids_off = struct.unpack_from("<II", dex, 64)
    _, string_ids_off = struct.unpack_from("<II", dex, 56)

    def get_string(idx: int) -> str:
        str_data_off = struct.unpack_from("<I", dex, string_ids_off + idx * 4)[0]
        pos = str_data_off
        while dex[pos] & 0x80:
            pos += 1
        pos += 1
        end = dex.find(b"\x00", pos)
        return dex[pos:end].decode("utf-8", errors="ignore")

    def get_type_desc(type_idx: int) -> str:
        str_idx = struct.unpack_from("<I", dex, type_ids_off + type_idx * 4)[0]
        return get_string(str_idx)

    kept = bytearray()
    removed = 0
    for i in range(class_defs_size):
        off = class_defs_off + i * 32
        class_idx = struct.unpack_from("<I", dex, off)[0]
        if get_type_desc(class_idx) in descriptors_to_strip:
            removed += 1
        else:
            kept.extend(dex[off : off + 32])

    if removed == 0:
        return dex_bytes

    total_bytes = class_defs_size * 32
    dex[class_defs_off : class_defs_off + total_bytes] = kept + (b"\x00" * (removed * 32))
    new_count = class_defs_size - removed
    struct.pack_into("<I", dex, 96, new_count)

    map_size = struct.unpack_from("<I", dex, map_off)[0]
    for i in range(map_size):
        entry_off = map_off + 4 + i * 12
        item_type = struct.unpack_from("<H", dex, entry_off)[0]
        if item_type == 0x0006:
            struct.pack_into("<I", dex, entry_off + 4, new_count)

    dex[12:32] = hashlib.sha1(dex[32:]).digest()
    struct.pack_into("<I", dex, 8, zlib.adler32(dex[12:]) & 0xFFFFFFFF)
    return bytes(dex)


def build(root: Path) -> None:
    src_dir = root / "src"
    if not src_dir.is_dir():
        return
    java_files = sorted(src_dir.rglob("*.java"))
    if not java_files or not ANDROID_JAR.exists() or not D8_BIN.exists():
        return

    javac_bin = shutil.which("javac") or "/usr/bin/javac"
    dex_path = root / "app/classes.dex"
    if not dex_path.exists():
        return

    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        classes_out = tmp_path / "classes"
        classes_out.mkdir(parents=True, exist_ok=True)

        subprocess.check_call([
            javac_bin,
            "-source", "8",
            "-target", "8",
            "-Xlint:-options",
            "-cp", str(ANDROID_JAR),
            "-d", str(classes_out),
            *[str(p) for p in java_files],
        ])

        compiled_classes = sorted(classes_out.rglob("*.class"))
        descriptors = set()
        for cls_file in compiled_classes:
            rel = cls_file.relative_to(classes_out).with_suffix("").as_posix()
            descriptors.add(f"L{rel};")

        stripped_dex = tmp_path / "stripped.dex"
        stripped_dex.write_bytes(_strip_classes_from_dex(dex_path.read_bytes(), descriptors))

        d8_out = tmp_path / "d8_out"
        d8_out.mkdir(parents=True, exist_ok=True)
        subprocess.check_call([
            str(D8_BIN),
            "--min-api", "24",
            "--lib", str(ANDROID_JAR),
            "--output", str(d8_out),
            str(stripped_dex),
            *[str(c) for c in compiled_classes],
        ])

        merged_dex = d8_out / "classes.dex"
        if merged_dex.exists():
            shutil.copy2(merged_dex, dex_path)
            print("compiled java -> classes.dex:", [p.name for p in java_files])
