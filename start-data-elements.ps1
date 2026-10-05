param(
    # The packaged application is compiled for Java 21.
    [string]$JavaExe = "",
    [string]$JarPath = "",
    [switch]$Build,
    [int]$Port = 8088,
    [int]$StartupTimeoutSeconds = 300,
    [string]$NacosHost = "192.168.175.86",
    [int]$NacosPort = 8848,
    [string]$Profile = "dev",
    [string]$KafkaBootstrapServers = "192.168.175.86:9092",
    # The platform address reachable from the NiFi execution network.  API
    # registrations may legitimately use localhost during local testing, but
    # that loopback points at the NiFi node when it executes InvokeHTTP.
    [string]$NifiApiPullReachableBaseUrl = "",
    [switch]$NoAuthBypass,
    # Logs are always retained under .\logs.  This switch is only useful for
    # callers that explicitly want a quiet startup window.
    [switch]$NoLiveLog
)

$ErrorActionPreference = "Stop"

# The local development environment is defined by the control-environment
# Nacos profile. Set these values explicitly so stale process or user-level
# environment variables cannot silently reconnect the backend to the demo host.
$env:NACOS_HOST = $NacosHost
$env:NACOS_PORT = $NacosPort.ToString()
$env:DATA_ELEMENT_PROFILE = $Profile
$env:KAFKA_BOOTSTRAP_SERVERS = $KafkaBootstrapServers

# Provisioning encryption key stays outside the repository and persists across restarts.
$idaasKeyPath = Join-Path $env:USERPROFILE ".codex\private\idaas-provision\transport-master.key"
if ([string]::IsNullOrWhiteSpace($env:IDAAS_TRANSPORT_KEY) -and (Test-Path -LiteralPath $idaasKeyPath)) {
    $env:IDAAS_TRANSPORT_KEY = (Get-Content -LiteralPath $idaasKeyPath -Raw).Trim()
}

function Write-Status {
    param([string]$Message)
    Write-Host "[start-data-elements] $Message"
}

function Get-PortOwnerPid {
    param([int]$PortNumber)

    try {
        # Get-NetTCPConnection uses the same Windows management provider that
        # can stall on this workstation.  netstat is a direct OS query and is
        # reliable for this small, one-port startup check.
        $netstatLine = & "$env:SystemRoot\System32\netstat.exe" -ano -p tcp |
            Select-String (":$PortNumber\s+.*LISTENING\s+\d+\s*$") |
            Select-Object -First 1
        if ($netstatLine) {
            $parts = ($netstatLine -split '\s+') | Where-Object { $_ -ne '' }
            if ($parts.Count -ge 5) {
                return [int]$parts[-1]
            }
        }
    } catch {
        # ignore
    }

    return $null
}

function Show-LogTail {
    param(
        [string]$Path,
        [int]$Lines = 80
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        Write-Host "Log file not found: $Path"
        return
    }

    Write-Host "--- Tail of $Path ---"
    Get-Content -LiteralPath $Path -Tail $Lines
}

function Test-MagicLoginReadiness {
    param([int]$PortNumber)
    # An empty login validates the route before credentials or business data.
    # A listening socket / Spring "Started" message does not mean Magic's
    # database resource tree has finished loading.
    foreach ($magicLoginPath in @('/portal/login', '/idaas/auth/login')) {
        try {
            $magicLoginResult = Invoke-RestMethod -Uri "http://localhost:$PortNumber$magicLoginPath" -Method Post -ContentType 'application/json' -Body '{}' -TimeoutSec 3
            if ($magicLoginResult.code -ne 400) { return $false }
        } catch { return $false }
    }
    return $true
}

Write-Status "Validating Java, backend project, and port $Port."

$projectDir = Join-Path $PSScriptRoot "data-elements-parent"
$pomPath = Join-Path $projectDir "pom.xml"
if (-not (Test-Path -LiteralPath $pomPath -PathType Leaf)) {
    throw "Backend project not found beside the startup script: $pomPath"
}

if ([string]::IsNullOrWhiteSpace($JavaExe)) {
    $javaFromHome = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { $null }
    if ($javaFromHome -and (Test-Path -LiteralPath $javaFromHome -PathType Leaf)) {
        $JavaExe = $javaFromHome
    } else {
        $javaOnPath = Get-Command java.exe -ErrorAction SilentlyContinue
        if ($javaOnPath) { $JavaExe = $javaOnPath.Source }
    }
}
if (-not $JavaExe -or -not (Test-Path -LiteralPath $JavaExe -PathType Leaf)) {
    throw "Java not found. Install JDK 21, set JAVA_HOME, or pass -JavaExe with its full path."
}

$usingDefaultJar = [string]::IsNullOrWhiteSpace($JarPath)
if ($usingDefaultJar) { $JarPath = Join-Path $projectDir "target\data-element.jar" }
if (-not [System.IO.Path]::IsPathRooted($JarPath)) { $JarPath = Join-Path $PSScriptRoot $JarPath }
$JarPath = [System.IO.Path]::GetFullPath($JarPath)
if ($Build -and -not $usingDefaultJar) {
    throw "-Build packages the default backend JAR. Omit -JarPath when using -Build."
}
$logDir = Join-Path $PSScriptRoot "logs"
$unixDomainTempDir = Join-Path $env:SystemDrive "Temp\data-elements-uds"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$stdoutLog = Join-Path $logDir "data-elements-local.$timestamp.out.log"
$stderrLog = Join-Path $logDir "data-elements-local.$timestamp.err.log"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
# Elasticsearch's async client opens a JDK Selector. On this workstation the
# default per-user TEMP directory is unsuitable for Windows AF_UNIX wakeup
# sockets, so use a short, local directory that the JDK can bind successfully.
New-Item -ItemType Directory -Force -Path $unixDomainTempDir | Out-Null

# Do not enumerate Win32_Process here.  On some Windows hosts that WMI query
# blocks indefinitely, leaving the user with no console output, no log file,
# and no backend process.  A concrete port check is both sufficient and safer:
# a process using 8088 must be stopped deliberately rather than guessing which
# unrelated Java process is safe to terminate.
$portOwnerPid = Get-PortOwnerPid -PortNumber $Port
if ($portOwnerPid) {
    $ownerProcess = Get-Process -Id $portOwnerPid -ErrorAction SilentlyContinue
    $ownerName = if ($ownerProcess) { $ownerProcess.ProcessName } else { 'unknown' }
    throw "Port $Port is already in use by PID=$portOwnerPid ($ownerName). Stop that process deliberately or choose a different port."
}

if ($NacosHost -eq "192.168.175.86") {
    # Prefer process/user environment values. On a fresh terminal, request the
    # local Nacos credentials without echoing or storing the password in the repo.
    foreach ($name in @("NACOS_USER", "NACOS_PASSWORD")) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, "Process"))) {
            $savedValue = [Environment]::GetEnvironmentVariable($name, "User")
            if (-not [string]::IsNullOrWhiteSpace($savedValue)) {
                [Environment]::SetEnvironmentVariable($name, $savedValue, "Process")
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:NACOS_USER) -or [string]::IsNullOrWhiteSpace($env:NACOS_PASSWORD)) {
        if ([Console]::IsInputRedirected) {
            throw "Nacos credentials are unavailable in this noninteractive session. Set NACOS_USER and NACOS_PASSWORD in the process environment."
        }
        if ([string]::IsNullOrWhiteSpace($env:NACOS_USER)) {
            $enteredUser = Read-Host "Nacos username [nacos]"
            $env:NACOS_USER = if ([string]::IsNullOrWhiteSpace($enteredUser)) { "nacos" } else { $enteredUser.Trim() }
        }
        if ([string]::IsNullOrWhiteSpace($env:NACOS_PASSWORD)) {
            $securePassword = Read-Host "Nacos password" -AsSecureString
            if (-not $securePassword -or $securePassword.Length -eq 0) { throw "Nacos password cannot be empty." }
            $env:NACOS_PASSWORD = ([pscredential]::new($env:NACOS_USER, $securePassword)).GetNetworkCredential().Password
        }
    }
}

if ($Build -or ($usingDefaultJar -and -not (Test-Path -LiteralPath $JarPath -PathType Leaf))) {
    $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $maven) { throw "Maven not found. Install Maven 3.8+, add mvn.cmd to PATH, or build data-elements-parent manually." }
    Write-Status "Packaging backend: $pomPath"
    & $maven.Source -f $pomPath -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "Backend package failed with exit code $LASTEXITCODE" }
}
if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
    throw "Jar not found: $JarPath"
}

# Maven replaces target\data-element.jar in place while packaging.  A running
# Spring Boot executable can still read nested entries from that file while
# serving requests, so a concurrent package can surface as EOF/ZIP errors on
# otherwise unrelated APIs.  Copy only after the previous runtime is stopped.
$runtimeDir = Join-Path $PSScriptRoot ".runtime"
$runtimeJar = Join-Path $runtimeDir "data-element-runtime.jar"
New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
Copy-Item -LiteralPath $JarPath -Destination $runtimeJar -Force
$JarPath = $runtimeJar

$localOverrides = @{}

# The relay signing key is deliberately supplied by the Windows user
# environment, never hard-coded in this script or in a project config file.
# When it is present, the platform can issue relay credentials to the local
# Haitong process over the loopback-only registration channel.
$pushRegistryKey = [Environment]::GetEnvironmentVariable("DATA_PUSH_REGISTRY_KEY", "User")
if ([string]::IsNullOrWhiteSpace($pushRegistryKey)) {
    $pushRegistryKey = $env:DATA_PUSH_REGISTRY_KEY
}
if (-not [string]::IsNullOrWhiteSpace($pushRegistryKey)) {
    $pushRegistryUrl = [Environment]::GetEnvironmentVariable("DATA_PUSH_REGISTRY_URL", "User")
    if ([string]::IsNullOrWhiteSpace($pushRegistryUrl)) {
        # haitong-data-pull's published local service port.  Keep this aligned
        # with haitong-data-pull/config/application.yml and its OpenAPI server.
        $pushRegistryUrl = "http://127.0.0.1:18083"
    }
    $localOverrides["data-element"] = @{
        "push-registry" = @{
            "relay-base-url" = $pushRegistryUrl
            "shared-key" = $pushRegistryKey
            "relay-timeout-seconds" = 8
        }
    }
    Write-Status "Enabled local Haitong push-credential registration over loopback."
}

if (-not $NoAuthBypass) {
    # Keep the existing local Magic permission setting, but leave the shared
    # login whitelist under Nacos control instead of replacing it locally.
    $localOverrides["data-trading-parent"] = @{
        config = @{ checkToken = $false }
    }
}

# Do not call Get-NetRoute/Get-NetIPAddress during startup.  They use the
# Windows management provider, which can block indefinitely on this host.
# Nacos supplies the normal runtime value.  A caller that needs to override it
# for a remote NiFi node can still pass -NifiApiPullReachableBaseUrl explicitly.
if ([string]::IsNullOrWhiteSpace($NifiApiPullReachableBaseUrl)) {
    Write-Status "Using the existing NiFi callback configuration; no network adapter override was requested."
}
# 任务监控页面依赖 NiFi provenance 回写“最新运行时间 / 结束时间”。
# 本地环境即使延迟监控被关闭，也要保留执行记录同步，避免监控表长期为空。
$nifiOverrides = @{ "provenance-sync-enabled" = $true }
if (-not [string]::IsNullOrWhiteSpace($NifiApiPullReachableBaseUrl)) {
    $nifiOverrides["api-pull"] = @{ "loopback-base-url" = $NifiApiPullReachableBaseUrl.TrimEnd('/') }
    Write-Status "API pull loopback URLs will be resolved for NiFi through $($NifiApiPullReachableBaseUrl.TrimEnd('/'))."
}
$localOverrides["nifi"] = $nifiOverrides
if ($Profile -eq "dev") {
    $localOverrides["idaas"] = @{ transport = @{ "allow-local-http" = $true } }
}

$env:SPRING_APPLICATION_JSON = $localOverrides | ConvertTo-Json -Depth 8 -Compress
Write-Status "Using Nacos runtime configuration: $NacosHost`:$NacosPort (profile=$Profile)"

Write-Status "Starting backend in foreground."
Write-Status "Live logs will appear in this terminal and are retained in: $stdoutLog"

# Nacos supplies database, Redis, Elasticsearch, NiFi and tenant-routing
# settings. Do not duplicate those settings here: command-line properties have
# higher precedence and would otherwise override the selected environment.
$runtimeArgs = @(
    "--server.port=$Port"
)

try {
    # Do not pipe the JVM through Tee-Object.  Under the local Windows host
    # that pipeline can close the child streams during bootstrap and leave
    # $LASTEXITCODE as -1 without creating the log file.
    # Start-Process joins ArgumentList into one Windows command line. Quote file
    # paths so the launcher also works when this repository lives under a space.
    $javaArgs = @("-Djdk.net.unixdomain.tmpdir=`"$unixDomainTempDir`"", "-jar", "`"$JarPath`"") + $runtimeArgs
    # Keep the JVM hidden and its file handles independent from this PowerShell process, but
    # forward the redirected files back to the same console in real time. A
    # direct `java | Tee-Object` pipeline can close child streams during Spring
    # bootstrap on this Windows host; tailing the files avoids that failure
    # while still making startup errors visible immediately.
    New-Item -ItemType File -Force -Path $stdoutLog, $stderrLog | Out-Null
    $process = Start-Process -FilePath $JavaExe -ArgumentList $javaArgs -WorkingDirectory $projectDir -PassThru -WindowStyle Hidden -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
    $logTailJobs = @()
    if (-not $NoLiveLog) {
        $logTailJobs += Start-Job -ScriptBlock {
            param([string]$Path)
            Get-Content -LiteralPath $Path -Tail 0 -Wait
        } -ArgumentList $stdoutLog
        $logTailJobs += Start-Job -ScriptBlock {
            param([string]$Path)
            Get-Content -LiteralPath $Path -Tail 0 -Wait | ForEach-Object { "[stderr] $_" }
        } -ArgumentList $stderrLog
    }
    $magicReady = $false
    $magicReadinessFailed = $false
    $magicReadinessDeadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    $magicNextProbe = Get-Date
    Write-Status 'Waiting for tenant and identity login routes to finish loading.'
    while (-not $process.HasExited) {
        foreach ($job in $logTailJobs) {
            Receive-Job -Job $job -ErrorAction SilentlyContinue
        }
        if (-not $magicReady -and (Get-Date) -ge $magicNextProbe) {
            $magicReady = Test-MagicLoginReadiness -PortNumber $Port
            $magicNextProbe = (Get-Date).AddSeconds(5)
            if ($magicReady) {
                Write-Status 'Backend ready: tenant login and identity login routes are loaded.'
            } elseif ((Get-Date) -ge $magicReadinessDeadline) {
                $magicReadinessFailed = $true
                Write-Status 'Startup failed: Magic login routes did not load. Check control database connectivity and the Magic resource errors in the retained startup log.'
                Stop-Process -Id $process.Id -ErrorAction SilentlyContinue
            }
        }
        Start-Sleep -Milliseconds 250
        $process.Refresh()
    }
    foreach ($job in $logTailJobs) {
        Receive-Job -Job $job -ErrorAction SilentlyContinue
        Stop-Job -Job $job -ErrorAction SilentlyContinue
        Remove-Job -Job $job -Force -ErrorAction SilentlyContinue
    }
    $exitCode = $process.ExitCode
    if ($magicReadinessFailed) { $exitCode = 1 }
} catch {
    # Do not silently turn a launcher failure into a successful return code.
    # Without this detail a missing JAR, denied log-file handle, or failed JVM
    # launch leaves the caller with an unreachable backend and an empty console.
    $launchError = $_
    $exitCode = if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { $LASTEXITCODE } else { 1 }
    Write-Status "Backend launcher failed: $($launchError.Exception.Message)"
    Show-LogTail -Path $stdoutLog -Lines 200
    Show-LogTail -Path $stderrLog -Lines 200
}

if ($null -eq $exitCode) { $exitCode = 0 }

if ($exitCode -ne 0) {
    Write-Status "Backend exited with code $exitCode. Showing last 200 lines from log: $stdoutLog"
    Show-LogTail -Path $stdoutLog -Lines 200
    exit $exitCode
}
