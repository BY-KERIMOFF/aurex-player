$logosPath = "C:\Users\by-kerimoff\AndroidStudioProjects\aurex-player\logos"
Get-ChildItem $logosPath -Filter *.png | ForEach-Object {
    $cleanName = $_.BaseName.ToLower() -replace '\s+', '' -replace '[^a-z0-9а-я]', ''
    $newName = $cleanName + ".png"
    $targetPath = Join-Path $logosPath $newName

    if ($newName -ne $_.Name -and $cleanName.Length -gt 0) {
        if (Test-Path $targetPath) {
            Remove-Item $_.FullName -Force
        } else {
            Rename-Item $_.FullName -NewName $newName -Force
        }
    }
}
