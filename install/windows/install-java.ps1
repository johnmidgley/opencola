if(!([Security.Principal.WindowsPrincipal] `
  [Security.Principal.WindowsIdentity]::GetCurrent() `
).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
	Write-Host "This command must be run as Administrator" -ForegroundColor Red
	exit 1
}

"Downloading Java..."
Start-BitsTransfer -Source "https://download.oracle.com/java/19/latest/jdk-19_windows-x64_bin.exe" -Destination "jdk-19_windows-x64_bin.exe"
"Installing Java"
Start-Process ./jdk-19_windows-x64_bin.exe -NoNewWindow -Wait
"Removing installer"
rm jdk-19_windows-x64_bin.exe

exit 0
