#!/usr/bin/env python3
"""Verify the isolated Kafka client, service registration and NiFi parent before publication."""
import argparse
from collections import defaultdict
import hashlib
import io
import json
from pathlib import Path
import zipfile


def check(path, nifi_version):
    path = Path(path)
    classes = defaultdict(list)
    registrations = []
    service = "com.linewell.dataelement.nifi.HaitongLegacyKafkaConnectionService"
    with zipfile.ZipFile(path) as nar:
        if nar.testzip() is not None:
            raise ValueError("Invalid NAR archive")
        manifest = nar.read("META-INF/MANIFEST.MF").decode()
        for expected in ("Nar-Id: haitong-kafka-legacy-nar", "Nar-Group: com.linewell.dataelement",
                         "Nar-Dependency-Id: nifi-kafka-service-api-nar",
                         "Nar-Dependency-Version: " + nifi_version):
            if expected not in manifest.splitlines():
                raise ValueError("Unexpected NAR coordinate or parent: " + expected)
        jars = [name for name in nar.namelist() if name.endswith(".jar")]
        clients = [name for name in jars if "kafka-clients-" in name]
        if len(clients) != 1 or not clients[0].endswith("/kafka-clients-3.7.2.jar"):
            raise ValueError("Expected exactly one Kafka 3.7.2 client")
        for name in jars:
            with zipfile.ZipFile(io.BytesIO(nar.read(name))) as jar:
                for entry in jar.namelist():
                    if entry.endswith(".class") and not entry.endswith("module-info.class"):
                        classes[entry].append(name)
                    if entry == "META-INF/services/org.apache.nifi.controller.ControllerService":
                        registrations.extend(line.strip() for line in jar.read(entry).decode().splitlines()
                                             if line.strip() and not line.startswith("#"))
        duplicates = {name: owners for name, owners in classes.items() if len(owners) > 1}
        if duplicates:
            raise ValueError("Duplicate classes: " + json.dumps(dict(list(duplicates.items())[:5])))
        embedded_interfaces = [name for name in classes if name.startswith("org/apache/nifi/")
                               and not name.startswith(("org/apache/nifi/kafka/service/", "org/apache/nifi/kafka/shared/"))]
        embedded_interfaces.extend(name for name in classes if name.startswith("org/apache/nifi/kafka/service/api/"))
        if embedded_interfaces:
            raise ValueError("NiFi interfaces must come from the parent: " + ", ".join(embedded_interfaces[:5]))
        if registrations != [service] or service.replace(".", "/") + ".class" not in classes:
            raise ValueError("Expected one distinct legacy service registration")
    return {"nifiVersion": nifi_version, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "sizeBytes": path.stat().st_size, "jarCount": len(jars), "classCount": len(classes),
            "duplicateClasses": 0, "apiInterfacesEmbedded": 0, "singleLegacyClient": True,
            "serviceRegistration": registrations}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("nar")
    parser.add_argument("--nifi-version", required=True, choices=("2.9.0", "2.11.0"))
    args = parser.parse_args()
    print(json.dumps(check(args.nar, args.nifi_version)))
