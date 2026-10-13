param(
    [string]$ConfigPath = (Join-Path $env:USERPROFILE '.codex/private/data-elements/local-runtime.private.json'),
    [switch]$Restart
)

$ErrorActionPreference = 'Stop'
$taskRepo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskPrivatePath = [IO.Path]::GetFullPath($ConfigPath)
if ($taskPrivatePath.StartsWith($taskRepo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Runtime credentials must stay outside the Git workspace'
}
$taskConfig = Get-Content -LiteralPath $taskPrivatePath -Raw -Encoding UTF8 | ConvertFrom-Json
$taskRuntimeJar = Join-Path $taskRepo '.runtime/data-element-runtime.jar'
if (!(Test-Path -LiteralPath $taskRuntimeJar -PathType Leaf)) { throw 'Install the reviewed backend JAR in .runtime first' }
if (!(Test-Path -LiteralPath $taskConfig.executable -PathType Leaf)) { throw 'Configured Java executable is unavailable' }
if (!$taskConfig.argumentLine -or !$taskConfig.environment -or !$taskConfig.port) { throw 'Incomplete private runtime configuration' }
$taskNormalizedJar = $taskRuntimeJar.Replace('\', '/').ToLowerInvariant()
if (!$taskConfig.argumentLine.Replace('\', '/').ToLowerInvariant().Contains($taskNormalizedJar)) {
    throw 'Configured arguments must run this workspace runtime JAR'
}
foreach ($taskProperty in $taskConfig.environment.PSObject.Properties) {
    if ($taskProperty.Name -notmatch '^(NACOS_|DATA_ELEMENT_|DATA_ELEMENTS_|SPRING_)[A-Z0-9_]+$') {
        throw 'Unexpected runtime environment variable name'
    }
}
$taskOwners = @(Get-NetTCPConnection -LocalPort ([int]$taskConfig.port) -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique)
if ($taskOwners.Count -gt 0 -and !$Restart) { throw 'A backend is already listening; use -Restart only for an authorized restart' }
foreach ($taskOwner in $taskOwners) {
    $taskProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$taskOwner"
    if (!$taskProcess -or !$taskProcess.CommandLine -or
        !$taskProcess.CommandLine.Replace('\', '/').ToLowerInvariant().Contains($taskNormalizedJar) -or
        $taskProcess.ExecutablePath -ne $taskConfig.executable) {
        throw 'The port belongs to another process; refusing to stop it'
    }
}
foreach ($taskOwner in $taskOwners) {
    Stop-Process -Id $taskOwner -ErrorAction Stop
    Wait-Process -Id $taskOwner -ErrorAction SilentlyContinue -Timeout 20
}
foreach ($taskProperty in $taskConfig.environment.PSObject.Properties) {
    [Environment]::SetEnvironmentVariable($taskProperty.Name, [string]$taskProperty.Value, 'Process')
}
$taskLogRoot = Join-Path $taskRepo 'logs/backend/local-runtime'
New-Item -Path $taskLogRoot -ItemType Directory -Force | Out-Null
$taskLaunched = Start-Process -FilePath $taskConfig.executable -ArgumentList $taskConfig.argumentLine `
    -WorkingDirectory $taskRepo -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $taskLogRoot 'stdout.log') `
    -RedirectStandardError (Join-Path $taskLogRoot 'stderr.log') -PassThru
@{pid = $taskLaunched.Id; port = [int]$taskConfig.port; configuration = 'private'; status = 'started-awaiting-health'} | ConvertTo-Json -Compress
