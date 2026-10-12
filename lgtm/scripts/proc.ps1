# Process helper. Targets are identified by the full executable path,
# so same-named processes running from other folders are never touched.
#   -Action status : exit 0 if running, exit 1 if stopped
#   -Action stop   : try a graceful shutdown, force kill after timeout
param(
    [Parameter(Mandatory)][ValidateSet('status', 'stop')][string]$Action,
    [Parameter(Mandatory)][string]$Exe,
    [int]$TimeoutSec = 15
)

$Exe = [System.IO.Path]::GetFullPath($Exe)
$name = [System.IO.Path]::GetFileName($Exe)

function Get-Targets {
    Get-CimInstance Win32_Process -Filter "Name = '$name'" |
        Where-Object { $_.ExecutablePath -and ($_.ExecutablePath -ieq $Exe) }
}

$targets = @(Get-Targets)

if ($Action -eq 'status') {
    if ($targets.Count -gt 0) {
        Write-Host ("  [running] {0} (PID {1})" -f $name, (($targets | ForEach-Object ProcessId) -join ', '))
        exit 0
    }
    Write-Host ("  [stopped] {0}" -f $name)
    exit 1
}

if ($targets.Count -eq 0) {
    Write-Host "  $name is not running."
    exit 0
}

foreach ($t in $targets) {
    Write-Host ("  stopping {0} (PID {1}) ..." -f $name, $t.ProcessId)
    # WM_CLOSE to the console window arrives as SIGTERM in Go programs,
    # which triggers a graceful shutdown. Without a window we fall back to kill.
    $p = Get-Process -Id $t.ProcessId -ErrorAction SilentlyContinue
    if ($p -and $p.MainWindowHandle -ne [IntPtr]::Zero) { [void]$p.CloseMainWindow() }
}

$deadline = (Get-Date).AddSeconds($TimeoutSec)
while ((Get-Date) -lt $deadline -and @(Get-Targets).Count -gt 0) {
    Start-Sleep -Milliseconds 500
}

foreach ($t in @(Get-Targets)) {
    Write-Host ("  force killing {0} (PID {1})" -f $name, $t.ProcessId)
    Stop-Process -Id $t.ProcessId -Force -ErrorAction SilentlyContinue
}
Write-Host "  $name stopped."
exit 0
