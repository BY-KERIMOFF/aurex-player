$logosPath = "C:\Users\by-kerimoff\AndroidStudioProjects\aurex-player\logos"
$indexPath = "C:\Users\by-kerimoff\AndroidStudioProjects\aurex-player\app\src\main\assets\logos_index.txt"
$suffixes = "\b(hd|sd|fhd|uhd|4k|5k|8k|fullhd|yedek|backup|rezerv|reserve|test|back|plus|\+\d|\(\d+\)|1080p|720p|hevc|h265|60fps|50fps)\b"

$results = Get-ChildItem $logosPath -Filter *.png | ForEach-Object {
    $clean = $_.BaseName.ToLower()
    $clean = $clean -replace $suffixes, ""
    $clean = $clean -replace "\s+", ""
    $clean = $clean -replace "ə", "e" -replace "ı", "i" -replace "ö", "o" -replace "ğ", "g" -replace "ü", "u" -replace "ç", "c" -replace "ş", "s"
    $clean = $clean -replace "[^a-z0-9а-я]", ""

    if ($clean.Length -gt 0) {
        $clean + "->" + $_.Name
    }
}

$results | Out-File -FilePath $indexPath -Encoding utf8
