"""Build the Java quality example shipped with Haitong deployments."""
import os
from pathlib import Path
import subprocess
from zipfile import ZIP_DEFLATED, ZipFile


EXAMPLES = Path(__file__).resolve().parents[1] / "data-elements-haitong/standalone-backend/examples"


def build_quality_example(jackson: list[Path], target: Path, classes: Path) -> None:
    classes.mkdir(parents=True, exist_ok=True)
    java_home = os.environ.get("JAVA_HOME")
    javac = str(Path(java_home) / "bin" / ("javac.exe" if os.name == "nt" else "javac")) if java_home else "javac"
    if len(jackson) != 3:
        raise ValueError("Expected jackson-annotations, jackson-core and jackson-databind")
    subprocess.run([javac, "--release", "21", "-encoding", "UTF-8", "-cp",
                    os.pathsep.join(map(str, jackson)), "-d", str(classes),
                    str(EXAMPLES / "QualityCheck.java")], check=True)
    with ZipFile(target, "w", ZIP_DEFLATED) as result:
        result.writestr("META-INF/MANIFEST.MF",
                        "Manifest-Version: 1.0\r\nMain-Class: QualityCheck\r\nMulti-Release: true\r\n\r\n")
        result.write(classes / "QualityCheck.class", "QualityCheck.class")
        names = {"META-INF/MANIFEST.MF", "QualityCheck.class"}
        for library in jackson:
            with ZipFile(library) as dependency:
                for member in dependency.infolist():
                    name = member.filename
                    if member.is_dir() or name == "META-INF/MANIFEST.MF" or name.endswith("module-info.class"):
                        continue
                    if name.upper().endswith((".SF", ".RSA", ".DSA")):
                        continue
                    if name.startswith(("META-INF/LICENSE", "META-INF/NOTICE")):
                        name = f"META-INF/third-party/{library.stem}/{Path(name).name}"
                    if name in names:
                        if result.read(name) != dependency.read(member):
                            raise ValueError(f"Conflicting JAR entry: {name}")
                        continue
                    names.add(name)
                    result.writestr(name, dependency.read(member))
