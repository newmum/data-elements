param(
    [string]$MavenCommand = 'mvn',
    [string]$PythonCommand = 'python'
)

$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskProject = Join-Path $taskRoot 'data-elements-parent/mapping-extension'
$taskLogRoot = Join-Path $taskRoot 'logs/field-mapping-extension/build'
New-Item -ItemType Directory -Path $taskLogRoot -Force | Out-Null
[xml]$taskPom = Get-Content -LiteralPath (Join-Path $taskProject 'pom.xml') -Raw
$taskVersion = [string]$taskPom.project.version
if ($taskVersion -notmatch '^\d+\.\d+\.\d+$') { throw '扩展版本不是可核验的正式版本号' }

Push-Location -LiteralPath $taskProject
try {
    # clean is required when the version changes: the NAR plugin reuses its dependency staging directory.
    & $MavenCommand clean package *> (Join-Path $taskLogRoot 'maven.log')
    if ($LASTEXITCODE -ne 0) { throw '扩展构建失败，请检查 logs/field-mapping-extension/build/maven.log' }
} finally { Pop-Location }

$taskNar = Join-Path $taskRoot ('logs/field-mapping-extension/haitong-mapping-nar/haitong-mapping-nar-' + $taskVersion + '.nar')
if (!(Test-Path -LiteralPath $taskNar -PathType Leaf)) { throw '构建未生成预期 NAR，禁止安装' }
$taskCheck = & $PythonCommand (Join-Path $taskRoot 'tools/check-mapping-nar.py') $taskNar
if ($LASTEXITCODE -ne 0) { throw 'NAR 重复类或服务注册检查失败，禁止安装' }
$taskCheckResult = $taskCheck | ConvertFrom-Json
if (!$taskCheckResult.registrationsVerified -or $taskCheckResult.duplicateClasses -ne 0) { throw 'NAR 验证结果不完整，禁止安装' }
[pscustomobject]@{
    version = $taskVersion
    nar = $taskNar
    sha256 = (Get-FileHash -LiteralPath $taskNar -Algorithm SHA256).Hash.ToLowerInvariant()
    duplicateClasses = 0
    registrationsVerified = $true
} | ConvertTo-Json
