param(
    [switch]$Force,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'

$packages = @(
    'com.xiaomi.phone',
    'com.android.incallui',
    'com.android.contacts',
    'com.xiaomi.aiasst.service',
    'com.miui.voiceassist'
)

# Never kill InCallUI/aiasst/Osbot while a call is ringing or active:
# a force-stop at that point loses the current call's AI auto-answer trigger.
$telecom = & adb shell dumpsys telecom 2>&1 | Out-String
$busy = [regex]::IsMatch($telecom, 'state=(RINGING|DIALING|ACTIVE|CONNECTING|HOLDING)')

if ($busy -and -not $Force) {
    Write-Host 'Refusing to restart hosts: a call is ringing/active.' -ForegroundColor Yellow
    Write-Host 'Hang up (or wait) and rerun. Override only if you accept dropping the call: -Force'
    exit 1
}

if ($CheckOnly) {
    Write-Host $(if ($busy) { 'busy: a call is ringing/active' } else { 'idle: safe to restart hosts' })
    exit 0
}

foreach ($pkg in $packages) {
    Write-Host "force-stop $pkg"
    & adb shell am force-stop $pkg
}

Write-Host 'Done. Hooks install on next process start; verify with:'
Write-Host '  adb logcat -d -s OhMiCall'
