param(
  [string]$BaseUrl = "http://127.0.0.1:18083",
  [string]$RegistryKey = "",
  [Parameter(Mandatory=$true)][string]$RequestPath,
  [string]$IdempotencyKey = ""
)

function Read-ApplicationYmlValue([string]$Name) {
  $configPath = Join-Path $PSScriptRoot "..\config\application.yml"
  if (-not (Test-Path $configPath)) { throw "找不到配置文件：$configPath" }
  $line = Get-Content -LiteralPath $configPath | Where-Object { $_ -match "^\s*$Name\s*:\s*" } | Select-Object -Last 1
  if ([string]::IsNullOrWhiteSpace($line)) { throw "application.yml 中未找到 $Name" }
  $value = ($line -replace "^\s*$Name\s*:\s*", "").Trim()
  $value = $value.Trim('"').Trim("'")
  if ([string]::IsNullOrWhiteSpace($value) -or $value -like "REPLACE_WITH_*") { throw "application.yml 中的 $Name 尚未填写" }
  return $value
}

if ([string]::IsNullOrWhiteSpace($RegistryKey)) { $RegistryKey = Read-ApplicationYmlValue "sync-key" }
$body = Get-Content -Raw -Encoding UTF8 $RequestPath
if ([string]::IsNullOrWhiteSpace($IdempotencyKey)) { $IdempotencyKey = 'registry-' + [guid]::NewGuid().ToString('N') }
$headers = @{
  "Content-Type" = "application/json"
  "X-Standalone-Registry-Key" = $RegistryKey
  "Idempotency-Key" = $IdempotencyKey
}
Invoke-RestMethod -Uri ($BaseUrl.TrimEnd('/') + "/api/v1/data-push") -Method POST -Headers $headers -Body $body
