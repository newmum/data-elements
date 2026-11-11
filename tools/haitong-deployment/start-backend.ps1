$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
$runtime = Join-Path $PSScriptRoot 'runtime-tmp'
[IO.Directory]::CreateDirectory($runtime) | Out-Null
& $java '-Dfile.encoding=UTF-8' "-Djdk.net.unixdomain.tmpdir=$runtime" -jar 'backend/data-element.jar' '--spring.config.location=file:./config/application.yml'
exit $LASTEXITCODE
