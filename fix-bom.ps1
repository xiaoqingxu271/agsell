$path = 'D:\Code\cc\agsell\test-system-module.ps1'
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText($path, $c, [System.Text.UTF8Encoding]::new($true))
Write-Output 'BOM ADDED'
