"""Assemble the native Haitong flow package from the two local build outputs."""

import argparse
from pathlib import Path
import shutil
from zipfile import ZIP_DEFLATED, ZipFile

from haitong_quality_example import build_quality_example

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "data-elements-haitong" / "standalone-backend"
OUTPUT = ROOT / "logs" / "haitong-flow-lite"
PACKAGE = "haitong-flow-lite-1.0.0"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, default=OUTPUT / "release")
    args = parser.parse_args()
    backend = OUTPUT / "backend-build"
    frontend = OUTPUT / "frontend-build"
    stage = OUTPUT / "release" / PACKAGE
    for directory in ("backend/lib", "frontend/assets", "db", "drivers", "examples"):
        (stage / directory).mkdir(parents=True, exist_ok=True)
    for name in ("start.ps1", "start.sh", "application.env.example", "部署手册.md", "操作手册.md"):
        shutil.copy2(PROJECT / "install" / name, stage / name)
    shutil.copy2(ROOT / "db" / "haitong-flow-lite" / "init.sql", stage / "db" / "init.sql")
    shutil.copy2(PROJECT / "examples" / "mariadb.sql", stage / "db" / "example-mariadb.sql")
    shutil.copy2(backend / "haitong-flow-lite.jar", stage / "backend")
    for library in (backend / "lib").glob("*.jar"):
        shutil.copy2(library, stage / "backend" / "lib")
    shutil.copy2(backend / "drivers" / "mariadb-java-client-3.4.1.jar", stage / "drivers")
    shutil.copy2(frontend / "flow-lite.html", stage / "frontend" / "index.html")
    # Only package the assets from this build, even when repackaging into an existing staging directory.
    assets = list((frontend / "assets").glob("*"))
    if not assets or any(not asset.is_file() for asset in assets):
        raise SystemExit("Missing flat Vite assets from the standalone build")
    for asset in assets:
        shutil.copy2(asset, stage / "frontend" / "assets")
    shutil.copy2(PROJECT / "examples" / "QualityCheck.java", stage / "examples")
    build_quality_example(sorted((backend / "lib").glob("jackson-*.jar")),
                          stage / "examples" / "quality-check.jar", OUTPUT / "example-build")
    args.destination.mkdir(parents=True, exist_ok=True)
    archive = args.destination / (PACKAGE + ".zip")
    with ZipFile(archive, "w", ZIP_DEFLATED) as result:
        asset_names = {asset.name for asset in assets}
        expected_jars = {file.name for file in (backend / "lib").glob("*.jar")}
        files = [*stage.glob("*.md"), stage / "start.ps1", stage / "start.sh", stage / "application.env.example",
                 stage / "frontend" / "index.html", stage / "backend" / "haitong-flow-lite.jar",
                 stage / "drivers" / "mariadb-java-client-3.4.1.jar",
                 stage / "examples" / "QualityCheck.java", stage / "examples" / "quality-check.jar",
                 stage / "db" / "init.sql", stage / "db" / "example-mariadb.sql"]
        files.extend(stage / "frontend" / "assets" / name for name in sorted(asset_names))
        files.extend(stage / "backend" / "lib" / name for name in sorted(expected_jars))
        for file in sorted(files):
            result.write(file, Path(PACKAGE) / file.relative_to(stage))
    with ZipFile(archive) as result:
        if result.testzip() is not None:
            raise ValueError("Package ZIP failed its checksum verification")
    print(archive.resolve())


if __name__ == "__main__":
    main()
