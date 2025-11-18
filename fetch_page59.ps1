$headers = @{
    Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:T8W8rxIo5y566jm79HgSs9Mi'))
}

Invoke-RestMethod -Uri 'http://hbtcomputers.com.au.test/index.php?rest_route=/oxygen/v1/page/59' -Headers $headers | ConvertTo-Json -Depth 50 | Out-File -Encoding utf8 'c:\GitHub\ui\forma\page59-corrected.json'

Write-Host "Page 59 (corrected) saved to page59-corrected.json"