import importlib.util
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile


spec = importlib.util.spec_from_file_location("nifi_package", Path(__file__).parents[1] / "package-nifi-deployment.py")
package = importlib.util.module_from_spec(spec)
spec.loader.exec_module(package)


class ClickHouseClasspathTest(unittest.TestCase):
    def fixture(self, directory):
        names = ("clickhouse-jdbc-0.6.5.jar", "clickhouse-jdbc-0.6.5-all.jar")
        driver = "com/clickhouse/jdbc/ClickHouseDriver.class"
        for index, name in enumerate(names):
            with ZipFile(directory / name, "w") as jar:
                jar.writestr(driver, b"same-driver")
                if index == 0:
                    jar.writestr("com/clickhouse/client/ClickHouseClient.class", b"client")
        return dict.fromkeys(names), "CLICKHOUSE=" + ",".join("@DRIVER_DIR@/" + name for name in names)

    def test_accepts_the_complete_pair_and_preserves_other_vendor_mappings(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            files, mapping = self.fixture(root)
            package.validate_clickhouse_classpath(root, files, mapping + "\nORACLE=@DRIVER_DIR@/ojdbc8.jar\n")

    def test_rejects_a_missing_main_even_when_the_all_jar_is_present(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            files, mapping = self.fixture(root)
            del files["clickhouse-jdbc-0.6.5.jar"]
            with self.assertRaisesRegex(ValueError, "main JAR"):
                package.validate_clickhouse_classpath(root, files, mapping)

    def test_rejects_a_stale_all_only_mapping_even_when_both_files_are_packaged(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            files, mapping = self.fixture(root)
            with self.assertRaisesRegex(ValueError, "mapping"):
                package.validate_clickhouse_classpath(root, files, "CLICKHOUSE=" + mapping.split(",")[1])

    def test_rejects_main_client_missing_and_mismatched_driver_bytecode(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            files, mapping = self.fixture(root)
            with ZipFile(root / "clickhouse-jdbc-0.6.5.jar", "w") as jar:
                jar.writestr("com/clickhouse/jdbc/ClickHouseDriver.class", b"same-driver")
            with self.assertRaisesRegex(ValueError, "ClickHouseClient"):
                package.validate_clickhouse_classpath(root, files, mapping)
            self.fixture(root)
            with ZipFile(root / "clickhouse-jdbc-0.6.5-all.jar", "w") as jar:
                jar.writestr("com/clickhouse/jdbc/ClickHouseDriver.class", b"different-version")
            with self.assertRaisesRegex(ValueError, "same JDBC driver"):
                package.validate_clickhouse_classpath(root, files, mapping)

    def test_keeps_explicit_other_versions_outside_the_managed_pair_contract(self):
        package.validate_clickhouse_classpath(Path("."), {"clickhouse/custom-all.jar": {}},
                                              "CLICKHOUSE=@DRIVER_DIR@/clickhouse/custom-all.jar")


if __name__ == "__main__":
    unittest.main()
