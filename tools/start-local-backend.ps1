param(
    [string]$ConfigPath = (Join-Path $env:USERPROFILE '.codex/private/data-elements/local-runtime.private.json'),
    [switch]$Restart,
    [switch]$WithoutNacos,
    [string]$LocalConfigPath = (Join-Path $PSScriptRoot '../db/nacos/chengtian-14.yml'),
    [string]$LocalEnvironmentPath = (Join-Path $env:USERPROFILE '.codex/private/data-elements/no-nacos-14-environment.private.json'),
    [string]$JarPath = '',
    [string]$JavaExe = '',
    [int]$Port = 8088,
    [switch]$ValidateOnly,
    [hashtable]$NacosSettings = @{}
)

$ErrorActionPreference = 'Stop'
$taskRepo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskPrivatePath = [IO.Path]::GetFullPath($ConfigPath)
if ($taskPrivatePath.StartsWith($taskRepo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Runtime credentials must stay outside the Git workspace'
}
$taskConfig = if (Test-Path -LiteralPath $taskPrivatePath -PathType Leaf) {
    Get-Content -LiteralPath $taskPrivatePath -Raw -Encoding UTF8 | ConvertFrom-Json
} elseif ($WithoutNacos) {
    [pscustomobject]@{ environment = [pscustomobject]@{} }
} else { throw 'Private runtime configuration is unavailable' }
foreach ($taskSetting in $NacosSettings.GetEnumerator()) {
    if ($taskSetting.Key -notin @('NACOS_HOST','NACOS_PORT','NACOS_NAMESPACE','NACOS_CONFIG_IMPORTS')) {
        throw 'Unexpected Nacos setting'
    }
    $taskConfig.environment | Add-Member -NotePropertyName $taskSetting.Key -NotePropertyValue ([string]$taskSetting.Value) -Force
}
$taskRuntimeJar = Join-Path $taskRepo '.runtime/data-element-runtime.jar'
$taskLogRoot = Join-Path $taskRepo 'logs/backend/local-runtime'
if ($WithoutNacos) {
    $taskTransportKeyFile = $taskConfig.transportKeyFile
    $taskBusinessConfig = [IO.Path]::GetFullPath($LocalConfigPath)
    $taskLocalModeConfig = Join-Path $taskRepo 'db/nacos/application-no-nacos.yml'
    foreach ($taskConfigFile in @($taskBusinessConfig, $taskLocalModeConfig)) {
        if (!(Test-Path -LiteralPath $taskConfigFile -PathType Leaf)) { throw 'Local YAML configuration is unavailable' }
    }
    $taskEnvironment = @{}
    foreach ($taskProperty in $taskConfig.environment.PSObject.Properties) {
        $taskEnvironment[$taskProperty.Name] = [string]$taskProperty.Value
    }
    if (Test-Path -LiteralPath $LocalEnvironmentPath -PathType Leaf) {
        $taskEnvironmentFile = [IO.Path]::GetFullPath($LocalEnvironmentPath)
        if ($taskEnvironmentFile.StartsWith($taskRepo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Private environment must stay outside the Git workspace'
        }
        $taskLocalEnvironment = Get-Content -LiteralPath $taskEnvironmentFile -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($taskProperty in $taskLocalEnvironment.environment.PSObject.Properties) {
            $taskEnvironment[$taskProperty.Name] = [string]$taskProperty.Value
        }
    }
    # Neither stale bootstrap variables nor SPRING_APPLICATION_JSON may reactivate Nacos.
    foreach ($taskName in @('NACOS_HOST','NACOS_PORT','NACOS_USER','NACOS_PASSWORD','NACOS_NAMESPACE','NACOS_CONFIG_IMPORTS',
                            'SPRING_CONFIG_IMPORT','SPRING_CONFIG_LOCATION','SPRING_CONFIG_ADDITIONAL_LOCATION')) {
        $taskEnvironment.Remove($taskName)
    }
    $taskEnvironment['SPRING_CLOUD_NACOS_CONFIG_ENABLED'] = 'false'
    $taskEnvironment['SPRING_CLOUD_NACOS_CONFIG_IMPORT_CHECK_ENABLED'] = 'false'
    $taskEnvironment['SPRING_CLOUD_NACOS_DISCOVERY_ENABLED'] = 'false'
    $taskEnvironment['SPRING_CLOUD_SERVICE_REGISTRY_AUTO_REGISTRATION_ENABLED'] = 'false'
    $taskEnvironment['SPRING_CLOUD_DISCOVERY_ENABLED'] = 'false'
    $taskExecutable = if ($JavaExe) { $JavaExe } elseif ($taskConfig.executable) { [string]$taskConfig.executable }
        elseif ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe -ErrorAction Stop).Source }
    $taskSourceJar = if ($JarPath) { [IO.Path]::GetFullPath($JarPath) } elseif (Test-Path -LiteralPath $taskRuntimeJar) { $taskRuntimeJar }
        else { Join-Path $taskRepo 'data-elements-parent/target/data-element.jar' }
    if (!(Test-Path -LiteralPath $taskSourceJar -PathType Leaf)) { throw 'Build the backend JAR before no-Nacos startup' }
    $taskRuntimeJar = Join-Path $taskRepo '.runtime/no-nacos/data-element-runtime.jar'
    $taskLogRoot = Join-Path $taskRepo 'logs/backend/no-nacos'
    # Keep Windows selector/pipe sockets below the OS Unix-domain path limit.
    $taskSocketDirectory = Join-Path $taskRepo 'logs/uds'
    $taskConfigLocations = 'file:' + $taskBusinessConfig.Replace('\','/') + ',file:' + $taskLocalModeConfig.Replace('\','/')
    $taskArgumentLine = '-Dfile.encoding=UTF-8 "-Djdk.net.unixdomain.tmpdir=' + $taskSocketDirectory + '" -jar "' + $taskRuntimeJar + '" "--spring.config.location=' + $taskConfigLocations + '"' +
        ' --spring.config.import= --spring.cloud.nacos.config.enabled=false --spring.cloud.nacos.config.import-check.enabled=false' +
        ' --spring.cloud.nacos.discovery.enabled=false --spring.cloud.nacos.discovery.register-enabled=false' +
        ' --spring.cloud.discovery.enabled=false --spring.cloud.service-registry.auto-registration.enabled=false' +
        ' --server.port=' + $Port + ' "--storage.error-root-dir=' + $taskLogRoot + '"'
    $taskConfig = [pscustomobject]@{ executable = $taskExecutable; argumentLine = $taskArgumentLine; port = $Port;
        environment = [pscustomobject]$taskEnvironment; transportKeyFile = $taskTransportKeyFile }
}
if ($ValidateOnly) {
    if (!$WithoutNacos) { throw '-ValidateOnly requires -WithoutNacos' }
    if (!(Test-Path -LiteralPath $taskConfig.executable -PathType Leaf)) { throw 'Configured Java executable is unavailable' }
    @{mode='without-nacos'; port=$taskConfig.port; businessConfiguration=$taskBusinessConfig; overrides=$taskLocalModeConfig;
      jarSource=$taskSourceJar; nacosConfigEnabled=$false; nacosDiscoveryEnabled=$false;
      environmentVariableNames=@($taskConfig.environment.PSObject.Properties.Name | Sort-Object);
      argumentLine=$taskConfig.argumentLine} | ConvertTo-Json -Depth 4
    return
}
if ($WithoutNacos) {
    # The isolated runtime copy is written only after ownership/key checks below.
} elseif (!(Test-Path -LiteralPath $taskRuntimeJar -PathType Leaf)) { throw 'Install the reviewed backend JAR in .runtime first' }
if (!(Test-Path -LiteralPath $taskConfig.executable -PathType Leaf)) { throw 'Configured Java executable is unavailable' }
if (!$taskConfig.argumentLine -or !$taskConfig.environment -or !$taskConfig.port) { throw 'Incomplete private runtime configuration' }
$taskNormalizedJar = $taskRuntimeJar.Replace('\', '/').ToLowerInvariant()
if (!$taskConfig.argumentLine.Replace('\', '/').ToLowerInvariant().Contains($taskNormalizedJar)) {
    throw 'Configured arguments must run this workspace runtime JAR'
}
foreach ($taskProperty in $taskConfig.environment.PSObject.Properties) {
    if ($WithoutNacos -and $taskProperty.Name -cmatch '^[A-Z][A-Z0-9_]*$' -and
        $taskProperty.Name -notin @('PATH','COMSPEC','PSMODULEPATH','USERPROFILE','HOME','SYSTEMROOT','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS')) { continue }
    if ($taskProperty.Name -notmatch '^(NACOS_|DATA_ELEMENT_|DATA_ELEMENTS_|SPRING_)[A-Z0-9_]+$' -and
        $taskProperty.Name -notin @('IDAAS_TRANSPORT_KEY', 'IDAAS_TRANSPORT_ALLOW_LOCAL_HTTP', 'JDBC_DRIVER_DIRECTORY',
                                    'TENANT_CHENGTIAN_DB_USER', 'TENANT_CHENGTIAN_DB_PASSWORD')) {
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
if ($WithoutNacos) {
    foreach ($taskName in @('NACOS_HOST','NACOS_PORT','NACOS_USER','NACOS_PASSWORD','NACOS_NAMESPACE','NACOS_CONFIG_IMPORTS',
                            'SPRING_CONFIG_IMPORT','SPRING_CONFIG_LOCATION','SPRING_CONFIG_ADDITIONAL_LOCATION')) {
        [Environment]::SetEnvironmentVariable($taskName, $null, 'Process')
    }
}
foreach ($taskProperty in $taskConfig.environment.PSObject.Properties) {
    [Environment]::SetEnvironmentVariable($taskProperty.Name, [string]$taskProperty.Value, 'Process')
}
if (![string]::IsNullOrWhiteSpace($env:IDAAS_TRANSPORT_ALLOW_LOCAL_HTTP) -and
    $env:IDAAS_TRANSPORT_ALLOW_LOCAL_HTTP -notmatch '^(true|false)$') {
    throw 'IDAAS_TRANSPORT_ALLOW_LOCAL_HTTP must be true or false'
}
# Use the same persistent key as start-data-elements.ps1. It also protects write
# receipts, so even a basic personnel save needs it. Never generate a replacement.
$taskTransportKey = $env:IDAAS_TRANSPORT_KEY
if ([string]::IsNullOrWhiteSpace($taskTransportKey)) {
    $taskKeyPath = if ($taskConfig.transportKeyFile) { [string]$taskConfig.transportKeyFile }
                   else { Join-Path $env:USERPROFILE '.codex/private/idaas-provision/transport-master.key' }
    $taskKeyPath = [IO.Path]::GetFullPath($taskKeyPath)
    if ($taskKeyPath.StartsWith($taskRepo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'IDAAS transport key must stay outside the Git workspace'
    }
    if (Test-Path -LiteralPath $taskKeyPath -PathType Leaf) {
        $taskTransportKey = Get-Content -LiteralPath $taskKeyPath -Raw -Encoding UTF8
    }
}
if ([string]::IsNullOrWhiteSpace($taskTransportKey)) {
    throw 'IDAAS transport key is missing; configure IDAAS_TRANSPORT_KEY or restore the existing private transportKeyFile'
}
$taskTransportKey = $taskTransportKey.Trim()
try { $taskKeyBytes = [Convert]::FromBase64String($taskTransportKey) }
catch { throw 'IDAAS transport key must be Base64 encoding of exactly 32 bytes' }
if ($taskKeyBytes.Length -ne 32) { throw 'IDAAS transport key must be Base64 encoding of exactly 32 bytes' }
[Environment]::SetEnvironmentVariable('IDAAS_TRANSPORT_KEY', $taskTransportKey, 'Process')
# Validate configuration before interrupting an existing backend.
if ($Restart -and $taskOwners.Count -gt 0 -and $NacosSettings.Count -gt 0) {
    $taskNacosUrl = 'http://' + $taskConfig.environment.NACOS_HOST + ':' + $taskConfig.environment.NACOS_PORT + '/nacos/'
    try {
        $taskRequest = [Net.HttpWebRequest]::Create($taskNacosUrl)
        $taskRequest.Timeout = 5000
        $taskResponse = $taskRequest.GetResponse()
        $taskResponse.Close()
    } catch [Net.WebException] {
        if ($_.Exception.Response) { $_.Exception.Response.Close() }
        else { throw "Nacos HTTP is unreachable at $taskNacosUrl; current backend was left running" }
    }
}
foreach ($taskOwner in $taskOwners) {
    Stop-Process -Id $taskOwner -ErrorAction Stop
    Wait-Process -Id $taskOwner -ErrorAction SilentlyContinue -Timeout 20
}
if ($WithoutNacos) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $taskRuntimeJar) -Force | Out-Null
    New-Item -ItemType Directory -Path $taskSocketDirectory -Force | Out-Null
    Copy-Item -LiteralPath $taskSourceJar -Destination $taskRuntimeJar -Force
    [Environment]::SetEnvironmentVariable('DATA_ELEMENTS_LOG_DIR', $taskLogRoot, 'Process')
}
New-Item -Path $taskLogRoot -ItemType Directory -Force | Out-Null
$taskLaunched = Start-Process -FilePath $taskConfig.executable -ArgumentList $taskConfig.argumentLine `
    -WorkingDirectory $taskRepo -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $taskLogRoot 'stdout.log') `
    -RedirectStandardError (Join-Path $taskLogRoot 'stderr.log') -PassThru
@{pid = $taskLaunched.Id; port = [int]$taskConfig.port; configuration = 'private'; status = 'started-awaiting-health'} | ConvertTo-Json -Compress
