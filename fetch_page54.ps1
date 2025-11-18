$headers = @{
    Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:T8W8rxIo5y566jm79HgSs9Mi'))
}

Invoke-RestMethod -Uri 'http://hbtcomputers.com.au.test/index.php?rest_route=/oxygen/v1/pages/54' -Headers $headers | ConvertTo-Json -Depth 50 | Out-File -Encoding utf8 'c:\GitHub\ui\forma\page54-with-code-elements.json'

Write-Host "Page 54 tree saved to page54-with-code-elements.json"