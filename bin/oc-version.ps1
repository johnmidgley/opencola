function ParseVersionFromGradleKtsFile {
    param (
      [Parameter(Mandatory)]
      [string]$Filepath
    )
  
    # Get the contents of the file.
    $Contents = Get-Content $Filepath
  
    # Find the line that defines the version.
    $VersionLine = $Contents | Where-Object {$_ -match 'version\s*=\s*'}
  
    # Extract the version number from the line.
    $Version = $VersionLine.Split('=')[1]
  
    # Return the version number.
    return $Version
  }
  
  # Get the filepath to the Gradle Kotlin file.
  $Filepath = '../opencola-server/build.gradle.kts'
  
  # Parse the version from the file.    
  $Version = ParseVersionFromGradleKtsFile $Filepath
  $Version = $Version -replace '"', ''
  $Version = $Version -replace ' ', ''
  
  # Print the version number.
  Write-Host $Version