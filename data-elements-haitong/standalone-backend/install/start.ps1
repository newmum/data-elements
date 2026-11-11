$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$config = Join-Path $root 'application.env'
$keys = @('HAITONG_BIND', 'HAITONG_PORT', 'HAITONG_ADMIN_PASSWORD', 'HAITONG_STORE_KEY',
          'NIFI_URL', 'NIFI_PARENT_GROUP', 'NIFI_USERNAME', 'NIFI_PASSWORD',
          'NIFI_TRUSTSTORE', 'NIFI_TRUSTSTORE_PASSWORD')
if (Test-Path -LiteralPath $config) {
    foreach ($line in [IO.File]::ReadLines($config)) {
        if ($line -match '^([A-Z_]+)=(.*)$' -and $keys -ccontains $Matches[1]) {
            $key = $Matches[1]
            if ($null -eq [Environment]::GetEnvironmentVariable($key)) {
                [Environment]::SetEnvironmentVariable($key, $Matches[2], 'Process')
            }
        }
    }
}
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
$socketDirectory = Join-Path $root 'runtime-tmp'
[IO.Directory]::CreateDirectory($socketDirectory) | Out-Null
Push-Location -LiteralPath $root
try {
    & $java '-Xms32m' '-Xmx256m' "-Djdk.net.unixdomain.tmpdir=$socketDirectory" `
        -cp 'backend/haitong-flow-lite.jar;backend/lib/*' com.linewell.haitong.standalone.LiteApplication
    exit $LASTEXITCODE
} finally { Pop-Location }
