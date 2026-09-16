$playlistPath = "C:\Users\by-kerimoff\Desktop\tsttt.txt"
$logosPath = "C:\Users\by-kerimoff\AndroidStudioProjects\aurex-player\logos"
$suffixes = "\b(hd|sd|fhd|uhd|4k|5k|8k|fullhd|yedek|backup|rezerv|reserve|test|back|plus|\+\d|\(\d+\)|1080p|720p|hevc|h265|60fps|50fps)\b"

# 1. Extract all channel names from playlist
Write-Host "Extracting channel names from playlist..."
$channelNames = Get-Content $playlistPath | Select-String -Pattern "^#EXTINF" | ForEach-Object {
    if ($_ -match ",(.*)$") {
        $matches[1].Trim()
    }
} | Select-Object -Unique

Write-Host "Found $($channelNames.Count) unique channel names."

# Helper function to normalize names
function Get-Normalized($name) {
    $n = $name.ToLower()
    $n = $n -replace $suffixes, ""
    $n = $n -replace "\s+", ""
    $n = $n -replace "ə", "e" -replace "ı", "i" -replace "ö", "o" -replace "ğ", "g" -replace "ü", "u" -replace "ç", "c" -replace "ş", "s"
    $n = $n -replace "[^a-z0-9а-я]", ""
    return $n
}

# 2. Map normalized names to original playlist names
$normalizedToOriginal = @{}
foreach ($name in $channelNames) {
    $norm = Get-Normalized $name
    if ($norm.Length -gt 0 -and -not $normalizedToOriginal.ContainsKey($norm)) {
        $normalizedToOriginal[$norm] = $name
    }
}

# 3. Rename logos
Write-Host "Matching and renaming logos..."
Get-ChildItem $logosPath -Filter *.png | ForEach-Object {
    $logoNorm = Get-Normalized $_.BaseName
    # Special case: remove AZ_, AM_, etc. prefixes from logo files if they exist
    $logoNorm = $logoNorm -replace "^(az|am|tr|ru)_", ""

    if ($normalizedToOriginal.ContainsKey($logoNorm)) {
        $newName = $normalizedToOriginal[$logoNorm] + ".png"
        # Sanitize filename (remove characters not allowed in files, though playlist names should be mostly fine)
        $newName = $newName -replace '[\\\/\:\*\?\"\<\>\|]', "_"

        $targetPath = Join-Path $logosPath $newName

        if ($newName -ne $_.Name) {
            if (Test-Path $targetPath) {
                Write-Host "Match found but target exists: $($_.Name) -> $newName (skipping)"
            } else {
                Write-Host "Renaming: $($_.Name) -> $newName"
                Rename-Item $_.FullName -NewName $newName -Force
            }
        }
    }
}
