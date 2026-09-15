$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"
$script:pass = 0
$script:fail = 0

function Check($name, $cond) {
    if ($cond) { Write-Output "PASS: $name"; $script:pass++ } else { Write-Output "FAIL: $name"; $script:fail++ }
}

function CallAPI($method, $uri, $body, $h) {
    try {
        if ($body -eq $null) {
            return Invoke-RestMethod -Uri $uri -Method $method -Headers $h -ContentType "application/json"
        } else {
            return Invoke-RestMethod -Uri $uri -Method $method -Headers $h -ContentType "application/json" -Body $body
        }
    } catch { return $null }
}

# ========== 1. Super admin login ==========
$admin = CallAPI "Post" "$base/api/admin/login" '{"username":"admin","password":"admin123"}' @{}
Check "Admin login OK, role=SUPER_ADMIN" ($admin -ne $null -and $admin.code -eq 0 -and $admin.data.role -eq "SUPER_ADMIN")
$h = @{ Authorization = "Bearer $($admin.data.token)" }

# ========== 2. Create OPERATOR admin ==========
$r = CallAPI "Post" "$base/api/admin/system/admin" '{"username":"op_test","password":"op123456","realName":"OP Test","role":"OPERATOR"}' $h
Check "Create OPERATOR admin OK" ($r -ne $null -and $r.code -eq 0)

$r = CallAPI "Post" "$base/api/admin/system/admin" '{"username":"op_test","password":"op123456","role":"OPERATOR"}' $h
Check "Duplicate username rejected (50001)" ($r -ne $null -and $r.code -eq 50001)

$r = CallAPI "Post" "$base/api/admin/system/admin" '{"username":"op_super","password":"op123456","role":"SUPER_ADMIN"}' $h
Check "Illegal role SUPER_ADMIN rejected (40000)" ($r -ne $null -and $r.code -eq 40000)

$r = CallAPI "Post" "$base/api/admin/system/admin" '{"username":"op_short","password":"123","role":"OPERATOR"}' $h
Check "Short password rejected (40000)" ($r -ne $null -and $r.code -eq 40000)

# ========== 3. OPERATOR role permission matrix ==========
$opLogin = CallAPI "Post" "$base/api/admin/login" '{"username":"op_test","password":"op123456"}' @{}
Check "OPERATOR login OK" ($opLogin -ne $null -and $opLogin.code -eq 0 -and $opLogin.data.role -eq "OPERATOR")
$opH = @{ Authorization = "Bearer $($opLogin.data.token)" }

$r = CallAPI "Get" "$base/api/admin/system/admin/list" $null $opH
Check "OPERATOR access system API rejected (40201)" ($r -ne $null -and $r.code -eq 40201)

$r = CallAPI "Get" "$base/api/admin/user/list" $null $opH
Check "OPERATOR access user list rejected (40201)" ($r -ne $null -and $r.code -eq 40201)

$r = CallAPI "Get" "$base/api/admin/review/list?pageNum=1&pageSize=1" $null $opH
Check "OPERATOR access review list rejected (40201)" ($r -ne $null -and $r.code -eq 40201)

$r = CallAPI "Get" "$base/api/admin/product/list?pageNum=1&pageSize=1" $null $opH
Check "OPERATOR access product list OK" ($r -ne $null -and $r.code -eq 0)

$r = CallAPI "Get" "$base/api/admin/order/list?pageNum=1&pageSize=1" $null $opH
Check "OPERATOR access order list OK" ($r -ne $null -and $r.code -eq 0)

# ========== 4. Edit / reset password ==========
$list = CallAPI "Get" "$base/api/admin/system/admin/list?pageNum=1&pageSize=10" $null $h
$opId = $list.data.records | Where-Object { $_.username -eq "op_test" } | Select-Object -First 1 -ExpandProperty id
Check "Admin list contains op_test" ($opId -ne $null)

$r = CallAPI "Put" "$base/api/admin/system/admin/$opId" '{"realName":"OP Renamed","role":"ADMIN"}' $h
Check "Update op_test to ADMIN OK" ($r -ne $null -and $r.code -eq 0)

$r = CallAPI "Put" "$base/api/admin/system/admin/$opId/password" '{"newPassword":"op_newpass"}' $h
Check "Reset password OK" ($r -ne $null -and $r.code -eq 0)

$admin2 = CallAPI "Post" "$base/api/admin/login" '{"username":"op_test","password":"op_newpass"}' @{}
Check "Login with new password OK, role=ADMIN" ($admin2 -ne $null -and $admin2.code -eq 0 -and $admin2.data.role -eq "ADMIN")
$h2 = @{ Authorization = "Bearer $($admin2.data.token)" }

# ========== 5. ADMIN permission matrix ==========
$r = CallAPI "Get" "$base/api/admin/system/admin/list" $null $h2
Check "ADMIN access system API rejected (40201)" ($r -ne $null -and $r.code -eq 40201)

$r = CallAPI "Get" "$base/api/admin/user/list" $null $h2
Check "ADMIN access user list OK" ($r -ne $null -and $r.code -eq 0)

# ========== 6. Protection rules ==========
$r = CallAPI "Put" "$base/api/admin/system/admin/$($admin.data.adminId)/status?status=0" $null $h
Check "Disable self rejected (50001)" ($r -ne $null -and $r.code -eq 50001)

$r = CallAPI "Delete" "$base/api/admin/system/admin/$($admin.data.adminId)" $null $h
Check "Delete self rejected (50001)" ($r -ne $null -and $r.code -eq 50001)

# ========== 7. Disable -> token invalidated ==========
$r = CallAPI "Put" "$base/api/admin/system/admin/$opId/status?status=0" $null $h
Check "Disable op_test OK" ($r -ne $null -and $r.code -eq 0)

$r = CallAPI "Get" "$base/api/admin/product/list?pageNum=1&pageSize=1" $null $h2
Check "Disabled admin token invalidated (40100)" ($r -ne $null -and $r.code -eq 40100)

# ========== 8. Delete ==========
$r = CallAPI "Delete" "$base/api/admin/system/admin/$opId" $null $h
Check "Delete op_test OK" ($r -ne $null -and $r.code -eq 0)

$list = CallAPI "Get" "$base/api/admin/system/admin/list?pageNum=1&pageSize=10" $null $h
$opGone = $true
foreach ($item in $list.data.records) { if ($item.username -eq "op_test") { $opGone = $false } }
Check "op_test removed from list" ($opGone)

# ========== 9. System config ==========
$cfg = CallAPI "Get" "$base/api/admin/system/config/list" $null $h
Check "Config list has 4 items" ($cfg -ne $null -and $cfg.code -eq 0 -and $cfg.data.Count -eq 4)

$r = CallAPI "Put" "$base/api/admin/system/config" '{"items":[{"configKey":"default_freight","configValue":"8"},{"configKey":"free_shipping_threshold","configValue":"100"}]}' $h
Check "Update config OK" ($r -ne $null -and $r.code -eq 0)

$r = CallAPI "Put" "$base/api/admin/system/config" '{"items":[{"configKey":"evil_key","configValue":"x"}]}' $h
Check "Illegal config key rejected (40000)" ($r -ne $null -and $r.code -eq 40000)

$r = CallAPI "Put" "$base/api/admin/system/config" '{"items":[{"configKey":"default_freight","configValue":"0"},{"configKey":"free_shipping_threshold","configValue":"0"}]}' $h
Check "Restore config OK" ($r -ne $null -and $r.code -eq 0)

# ========== 10. Operation log ==========
Start-Sleep -Seconds 2
$log = CallAPI "Get" "$base/api/admin/system/log/list?pageNum=1&pageSize=10" $null $h
Check "Log list non-empty" ($log -ne $null -and $log.code -eq 0 -and $log.data.total -gt 0)

$logFilter = CallAPI "Get" "$base/api/admin/system/log/list?pageNum=1&pageSize=10&module=admin" $null $h
Check "Log module filter param accepted" ($logFilter -ne $null -and $logFilter.code -eq 0)

Write-Output "=============================="
Write-Output "PASS: $($script:pass)  FAIL: $($script:fail)"
