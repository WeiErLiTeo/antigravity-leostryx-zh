#!/usr/bin/env python3
"""Compiles Leostryx/src Java sources directly into app/classes.dex and app/classes3.dex."""
import shutil
import subprocess
import tempfile
from pathlib import Path

ANDROID_JAR = Path("/opt/android/sdk/platforms/android-36/android.jar")
D8_BIN = Path("/opt/android/sdk/build-tools/36.0.0/d8")

CLASSES3_REL_PATHS = {
    "com/agy/ru/AgyEffort.class",
    "com/agy/ru/AgyMediaChooser.class",
}


def build(root: Path) -> None:
    src_dir = root / "src"
    if not src_dir.is_dir():
        return
    java_files = sorted(src_dir.rglob("*.java"))
    if not java_files or not ANDROID_JAR.exists() or not D8_BIN.exists():
        return

    javac_bin = shutil.which("javac") or "/usr/bin/javac"
    dex1_path = root / "app/classes.dex"
    dex3_path = root / "app/classes3.dex"

    stubs_dir = root / "src_stubs"
    stub_files = sorted(stubs_dir.rglob("*.java")) if stubs_dir.is_dir() else []

    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        cp_entries = [str(ANDROID_JAR)]
        stubs_out = tmp_path / "stubs"

        if stub_files:
            stubs_out.mkdir(parents=True, exist_ok=True)
            subprocess.check_call([
                javac_bin,
                "-encoding", "UTF-8",
                "-source", "8",
                "-target", "8",
                "-Xlint:-options",
                "-cp", str(ANDROID_JAR),
                "-d", str(stubs_out),
                *[str(p) for p in stub_files],
            ])
            cp_entries.append(str(stubs_out))

        classes_out = tmp_path / "classes"
        classes_out.mkdir(parents=True, exist_ok=True)

        subprocess.check_call([
            javac_bin,
            "-encoding", "UTF-8",
            "-source", "8",
            "-target", "8",
            "-Xlint:-options",
            "-cp", ":".join(cp_entries),
            "-d", str(classes_out),
            *[str(p) for p in java_files],
        ])

        all_classes = sorted(classes_out.rglob("*.class"))
        c1_files = []
        c3_files = []
        for cls_file in all_classes:
            rel = cls_file.relative_to(classes_out).as_posix()
            if rel in CLASSES3_REL_PATHS:
                c3_files.append(cls_file)
            else:
                c1_files.append(cls_file)

        if c1_files:
            d1_out = tmp_path / "d8_c1"
            d1_out.mkdir(parents=True, exist_ok=True)
            d1_cmd = [
                str(D8_BIN),
                "--min-api", "24",
                "--lib", str(ANDROID_JAR),
                "--output", str(d1_out),
            ]
            if stub_files:
                d1_cmd.extend(["--classpath", str(stubs_out)])
            d1_cmd.extend(str(c) for c in c1_files)
            subprocess.check_call(d1_cmd)
            built_dex1 = d1_out / "classes.dex"
            if built_dex1.exists():
                shutil.copy2(built_dex1, dex1_path)
                print(f"compiled java -> app/classes.dex ({len(c1_files)} classes)")

        if c3_files:
            d3_out = tmp_path / "d8_c3"
            d3_out.mkdir(parents=True, exist_ok=True)
            d3_cmd = [
                str(D8_BIN),
                "--min-api", "24",
                "--lib", str(ANDROID_JAR),
                "--classpath", str(classes_out),
                "--output", str(d3_out),
                *[str(c) for c in c3_files],
            ]
            subprocess.check_call(d3_cmd)
            built_dex3 = d3_out / "classes.dex"
            if built_dex3.exists():
                shutil.copy2(built_dex3, dex3_path)
                print(f"compiled java -> app/classes3.dex ({len(c3_files)} classes)")
