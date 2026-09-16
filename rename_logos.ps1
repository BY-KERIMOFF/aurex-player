$logosPath = "C:\Users\by-kerimoff\AndroidStudioProjects\aurex-player\logos"
$suffixes = "\b(hd|sd|fhd|uhd|4k|5k|8k|fullhd|yedek|backup|rezerv|reserve|test|back|plus|\+\d|\(\d+\)|1080p|720p|hevc|h265|60fps|50fps)\b"

Get-ChildItem $logosPath -Filter *.png | ForEach-Object {
    $baseName = $_.BaseName.ToLower()
    # Remove suffixes
    $baseName = $baseName -replace $suffixes, ""
    # Remove spaces
    $baseName = $baseName -replace "\s+", ""
    # Replace Azeri specific characters
    $baseName = $baseName -replace "ə", "e" -replace "ı", "i" -replace "ö", "o" -replace "ğ", "g" -replace "ü", "u" -replace "ç", "c" -replace "ş", "s"
    # Keep only alphanumeric and cyrillic
    $baseName = $baseName -replace "[^a-z0-9а-я]", ""

    $newName = $baseName + ".png"
    $targetPath = Join-Path $logosPath $newName

    if ($newName -ne $_.Name -and $baseName.Length -gt 0) {
        if (Test-Path $targetPath) {
            Write-Host "Duplicate found: $($_.Name) -> $newName. Deleting original."
            Remove-Item $_.FullName -Force
        } else {
            Write-Host "Renaming: $($_.Name) -> $newName"
            Rename-Item $_.FullName -NewName $newName -Force
        }
    }
}
