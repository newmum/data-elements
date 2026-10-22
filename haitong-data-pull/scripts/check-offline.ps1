$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
New-Item -ItemType Directory -Force target/offline-check | Out-Null
$classes = @('Rules','RelayException','CsvRecords','PayloadFiles','HttpCalls','BusinessFields') | ForEach-Object { "src/main/java/com/haitong/core/$_.java" }
$classes += 'src/main/java/com/haitong/store/DatabaseDialect.java'
$classes += @('OfflineChecks','SyntaxCheck','DatabaseChecks') | ForEach-Object { "tests/offline/$_.java" }
& javac --release 21 --add-modules jdk.httpserver,jdk.compiler -d target/offline-check @classes
if ($LASTEXITCODE -ne 0) { throw 'JDK-only check compilation failed.' }
& java --add-modules jdk.httpserver,jdk.compiler -cp target/offline-check OfflineChecks
if ($LASTEXITCODE -ne 0) { throw 'OfflineChecks failed.' }
& java -cp target/offline-check DatabaseChecks
if ($LASTEXITCODE -ne 0) { throw 'DatabaseChecks failed.' }
& java --add-modules jdk.compiler -cp target/offline-check SyntaxCheck .
if ($LASTEXITCODE -ne 0) { throw 'SyntaxCheck failed.' }
Write-Host 'These are JDK-only checks, not a Maven build or actual database test.'
