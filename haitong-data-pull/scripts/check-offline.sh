#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p target/offline-check ../logs/haitong-data-pull
javac --release 21 --add-modules jdk.httpserver,jdk.compiler -d target/offline-check \
  src/main/java/com/haitong/core/{Rules,RelayException,CsvRecords,PayloadFiles,HttpCalls,BusinessFields}.java \
  src/main/java/com/haitong/store/DatabaseDialect.java \
  tests/offline/{OfflineChecks,SyntaxCheck,DatabaseChecks}.java
{
  java --add-modules jdk.httpserver,jdk.compiler -cp target/offline-check OfflineChecks
  java -cp target/offline-check DatabaseChecks
  java --add-modules jdk.compiler -cp target/offline-check SyntaxCheck .
} | tee ../logs/haitong-data-pull/offline-checks.txt
