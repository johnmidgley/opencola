Param([switch]$disable)

$linkPath = "$env:AppData\Microsoft\Windows\Start Menu\Programs\Startup\OpenCola.lnk"

if($disable.IsPresent) {
    if (Test-Path -Path $linkPath) {
        Remove-Item $linkPath
    }
    "Auto-start disabled"
} else {
    $WshShell = New-Object -comObject WScript.Shell
    $Shortcut = $WshShell.CreateShortcut($linkPath)
    $Shortcut.TargetPath = "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe"
    $Shortcut.Arguments = "-Command $pwd\start.ps1 -mode java"
    $Shortcut.WorkingDirectory = "$pwd"
    $Shortcut.WindowStyle = 7
    $Shortcut.Save()
    "Auto-start enabled - to disable run with '-disable'"
}