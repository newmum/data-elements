"""Credential disclosure and configuration fidelity checks for the public export."""
import importlib.util
from pathlib import Path
import unittest
import tempfile
import json

import yaml

spec = importlib.util.spec_from_file_location("nacos_export", Path(__file__).resolve().parents[1] / "export-nacos-config.py")
exporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exporter)


class NacosExportTest(unittest.TestCase):
    def test_credentials_and_secret_defaults_are_removed(self):
        config = {"spring": {"datasource": {"username": "example_operator", "password": "example-password"}},
                  "oss": {"accessKeyId": "example-access-id", "accessKeySecret": "example-access-secret"},
                  "registry": {"standalone-sync-key": "${REGISTRY_KEY:example-sync-secret}"},
                  "url": "jdbc:mysql://localhost/db?password=example-url-secret",
                  "copied": "password=example-password", "copiedDefault": "example-sync-secret"}
        result = exporter.export_yaml(config, "data-element.yml", "validation", "DEFAULT_GROUP")
        for secret in ["example_operator", "example-password", "example-access-id", "example-access-secret",
                       "example-sync-secret", "example-url-secret"]:
            self.assertNotIn(secret, result)
        self.assertEqual("${REGISTRY_KEY}", yaml.safe_load(result)["registry"]["standalone-sync-key"])
        self.assertEqual("password=<REDACTED>", yaml.safe_load(result)["copied"])

    def test_nonsecret_structure_and_values_are_preserved(self):
        config = {"server": {"port": 8088}, "tenant": {"enabled": True, "bindings": {"42": "public-security"}},
                  "registry": {"relay-base-url": "http://127.0.0.1:18083", "timeout": 8},
                  "id": {"key-prefix": "business:"}, "routes": ["/api/v1/data-push"]}
        config["sa-token"] = {"token-name": "access-token", "token-style": "uuid", "timeout": 3600}
        self.assertEqual(config, exporter.redact_configuration(config))

    def test_url_userinfo_and_short_numeric_credentials(self):
        config = {"password": 123, "endpoint": "https://operator:example-url-secret@localhost:443/api"}
        result = exporter.redact_configuration(config)
        self.assertTrue(result["password"].startswith("${NACOS_"))
        self.assertNotIn("example-url-secret", result["endpoint"])

    def test_existing_secret_references_survive_export(self):
        config = {"username": "${TENANT_CHENGTIAN_DB_USER}",
                  "password": "${TENANT_CHENGTIAN_DB_PASSWORD}"}
        self.assertEqual(config, exporter.redact_configuration(config))

    def test_private_environment_uses_effective_overrides_without_nacos_credentials(self):
        config = {"spring": {"datasource": {"username": "source-user", "password": "source-secret"}},
                  "tenant": {"password": "${TENANT_PASSWORD}"}}
        runtime = {"NACOS_USER": "config-user", "NACOS_PASSWORD": "config-secret", "TENANT_PASSWORD": "tenant-secret",
                   "DATA_ELEMENT_SYSTEM_CONFIG_GLOBAL_TENANT_ID": "validation-global",
                   "SPRING_APPLICATION_JSON": json.dumps({"spring.datasource.password": "effective-secret",
                                                          "spring.cloud.nacos.password": "config-login-secret",
                                                          "spring": {"config": {"import": "nacos:stale.yml"}}})}
        result = exporter.local_environment(config, runtime)["environment"]
        self.assertEqual("source-user", result["NACOS_SPRING_DATASOURCE_USERNAME"])
        self.assertEqual("effective-secret", result["NACOS_SPRING_DATASOURCE_PASSWORD"])
        self.assertEqual("tenant-secret", result["TENANT_PASSWORD"])
        self.assertEqual("validation-global", result["DATA_ELEMENT_SYSTEM_CONFIG_GLOBAL_TENANT_ID"])
        self.assertNotIn("NACOS_PASSWORD", result)
        self.assertNotIn("NACOS_USER", result)
        self.assertNotIn("config-login-secret", result["SPRING_APPLICATION_JSON"])
        self.assertNotIn("nacos:stale.yml", result["SPRING_APPLICATION_JSON"])
        self.assertNotIn("source-secret", exporter.export_yaml(config, "data-element.yml", "validation", "DEFAULT_GROUP"))

    def test_private_environment_resolves_defaults_and_rejects_missing_values(self):
        config = {"password": "${BUSINESS_SECRET:validation-default}"}
        self.assertEqual("validation-default", exporter.local_environment(config, {})["environment"]["BUSINESS_SECRET"])
        with self.assertRaisesRegex(ValueError, "MISSING_BUSINESS_SECRET"):
            exporter.local_environment({"password": "${MISSING_BUSINESS_SECRET}"}, {})

    def test_private_environment_cannot_be_written_under_git_workspace(self):
        with self.assertRaisesRegex(ValueError, "outside the Git workspace"):
            exporter.write_private_environment(Path(__file__).resolve().parents[2] / "logs/validation-private.json", {})
        with tempfile.TemporaryDirectory() as folder:
            destination = Path(folder) / "environment.private.json"
            configuration = {"environment": {"VALIDATION_SECRET": "synthetic-private-value"}}
            exporter.write_private_environment(destination, configuration)
            self.assertEqual(configuration, json.loads(destination.read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()
