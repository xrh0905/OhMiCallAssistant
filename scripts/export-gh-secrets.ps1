$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$propsPath = Join-Path $root 'signing/keystore.properties'
$ksPath = Join-Path $root 'signing/ohmicall-release.jks'

if (-not (Test-Path $propsPath) -or -not (Test-Path $ksPath)) {
    throw "Missing $propsPath or $ksPath"
}

$props = @{}
Get-Content $propsPath | ForEach-Object {
    if ($_ -match '^\s*([^#=]+)=(.*)$') {
        $props[$matches[1].Trim()] = $matches[2].Trim()
    }
}

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($ksPath))

Write-Output 'ANDROID_KEYSTORE_BASE64='
Write-Output $b64
Write-Output ''
Write-Output ('ANDROID_KEYSTORE_PASSWORD=' + $props['KEYSTORE_PASSWORD'])
Write-Output ('ANDROID_KEY_ALIAS=' + $props['KEY_ALIAS'])
Write-Output ('ANDROID_KEY_PASSWORD=' + $props['KEY_PASSWORD'])
