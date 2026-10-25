param(
    [string]$ConfigPath = (Join-Path $env:USERPROFILE '.codex/private/data-elements/haitong-registry-service.yml'),
    [string]$JavaPath = $env:HAITONG_JAVA_BIN,
    [ValidateRange(0, 65535)][int]$Port = 0,
    [ValidateRange(5, 180)][int]$WaitSeconds = 90
)

$ErrorActionPreference = 'Stop'
$taskRepo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskApplication = Join-Path $taskRepo 'haitong-data-pull'
$taskJar = Join-Path $taskApplication 'target/haitong-data-pull.jar'
$taskConfig = [IO.Path]::GetFullPath($ConfigPath)
if ($taskConfig.StartsWith($taskRepo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Local runtime credentials must stay outside the Git workspace.'
}
if (!(Test-Path -LiteralPath $taskConfig -PathType Leaf)) { throw 'The private Haitong YAML configuration is missing; pass -ConfigPath.' }
if (!(Test-Path -LiteralPath $taskJar -PathType Leaf)) { throw 'Build haitong-data-pull first with scripts/build.cmd.' }
if ([string]::IsNullOrWhiteSpace($JavaPath)) {
    if ($env:JAVA_HOME) { $JavaPath = Join-Path $env:JAVA_HOME 'bin/java.exe' }
    else { $JavaPath = (Get-Command java.exe -ErrorAction Stop).Source }
}
if (!(Test-Path -LiteralPath $JavaPath -PathType Leaf)) { throw 'Java executable not found; pass -JavaPath or set HAITONG_JAVA_BIN.' }
$taskJavaVersion = (& $JavaPath --version | Out-String)
if ($LASTEXITCODE -ne 0 -or $taskJavaVersion -notmatch '(?:java|openjdk) 21(?:[.\s]|$)') { throw 'Haitong requires Java 21.' }

# Read only a literal server.port for the health probe. Dynamic YAML expressions
# remain Spring's responsibility; callers can supply -Port for that case.
if ($Port -eq 0) {
    $taskYaml = Get-Content -LiteralPath $taskConfig -Raw -Encoding UTF8
    $taskServer = [regex]::Match($taskYaml, '(?m)^server:\s*\r?\n((?:(?:[ \t]+[^\r\n]*|[ \t]*)\r?\n)*)')
    $taskPortMatch = [regex]::Match($taskServer.Groups[1].Value, '(?m)^  port:\s*["'']?(\d+)["'']?\s*(?:#[^\r\n]*)?$')
    if (!$taskPortMatch.Success) { throw 'Cannot resolve server.port for health checking; pass -Port explicitly.' }
    $Port = [int]$taskPortMatch.Groups[1].Value
    if ($Port -lt 1 -or $Port -gt 65535) { throw 'Invalid configured server.port.' }
}
$taskHealthUrl = 'http://127.0.0.1:' + $Port + '/actuator/health'
$taskOwners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique)
$taskNormalizedJar = $taskJar.Replace('\', '/').ToLowerInvariant()
$taskNormalizedConfig = $taskConfig.Replace('\', '/').ToLowerInvariant()
$taskConfigurationUri = ([Uri]$taskConfig).AbsoluteUri
if ($taskOwners.Count -gt 0) {
    foreach ($taskOwner in $taskOwners) {
        $taskProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$taskOwner"
        $taskCommandLine = if ($taskProcess.CommandLine) { $taskProcess.CommandLine.Replace('\', '/').ToLowerInvariant() } else { '' }
        $taskRelativeArchive = $taskCommandLine -match '(?:^|\s)-jar\s+"?target/haitong-data-pull\.jar(?:"|\s|$)'
        if ((!$taskCommandLine.Contains($taskNormalizedJar) -and !$taskRelativeArchive) -or
            (!$taskCommandLine.Contains($taskNormalizedConfig) -and !$taskCommandLine.Contains($taskConfigurationUri.ToLowerInvariant()))) {
            throw 'The configured port belongs to another process. No process was stopped.'
        }
    }
    $taskHealth = Invoke-RestMethod -Uri $taskHealthUrl -TimeoutSec 5
    if ($taskHealth.status -ne 'UP') { throw 'Existing Haitong process is not healthy. No process was stopped.' }
    @{pid = $taskOwners[0]; port = $Port; status = 'already-running'; health = 'UP'} | ConvertTo-Json -Compress
    exit 0
}

$taskLogRoot = Join-Path $taskRepo 'logs/haitong-data-pull/local-runtime'
New-Item -Path $taskLogRoot -ItemType Directory -Force | Out-Null
# Windows Java 21 uses a local socket while initializing HTTP selectors. Use
# an explicit short temporary path, as the shared backend already does.
$taskUdsRoot = if ($env:HAITONG_UDS_DIR) { $env:HAITONG_UDS_DIR } else { Join-Path ($env:SystemDrive + '\Temp') 'haitong-uds' }
New-Item -Path $taskUdsRoot -ItemType Directory -Force | Out-Null
$taskArguments = @(
    '-Duser.timezone=UTC', ('"-Djdk.net.unixdomain.tmpdir=' + $taskUdsRoot + '"'), '-jar', ('"' + $taskJar + '"'),
    ('--spring.config.location="classpath:/application.yml,' + $taskConfigurationUri + '"'),
    ('--logging.file.name="' + (Join-Path $taskLogRoot 'service.log') + '"'),
    '--magic-api.show-sql=false'
) -join ' '
$taskStarted = Start-Process -FilePath $JavaPath -ArgumentList $taskArguments `
    -WorkingDirectory $taskApplication -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $taskLogRoot 'stdout.log') `
    -RedirectStandardError (Join-Path $taskLogRoot 'stderr.log') -PassThru
$taskDeadline = (Get-Date).AddSeconds($WaitSeconds)
do {
    $taskStarted.Refresh()
    if ($taskStarted.HasExited) { throw "Haitong exited during startup. Inspect $taskLogRoot; credentials were not printed." }
    try {
        $taskHealth = Invoke-RestMethod -Uri $taskHealthUrl -TimeoutSec 2
        if ($taskHealth.status -eq 'UP') {
            @{pid = $taskStarted.Id; port = $Port; status = 'started'; health = 'UP'; logs = $taskLogRoot} | ConvertTo-Json -Compress
            exit 0
        }
    } catch { }
    Start-Sleep -Seconds 1
} while ((Get-Date) -lt $taskDeadline)
throw "Startup has not passed health checking within $WaitSeconds seconds. The process was retained; inspect $taskLogRoot."
