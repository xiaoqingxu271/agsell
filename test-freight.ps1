$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"
$phone = "13800005555"

function CallJSON($method, $uri, $body, $h) {
    try {
        if ($body -eq $null) { return Invoke-RestMethod -Uri $uri -Method $method -Headers $h -ContentType "application/json" }
        else { return Invoke-RestMethod -Uri $uri -Method $method -Headers $h -ContentType "application/json" -Body $body }
    } catch { Write-Output "  [err] $uri => $($_.Exception.Message)"; return $null }
}

# 0. Admin config -> default_freight=8, free_shipping_threshold=100
$admin = CallJSON "Post" "$base/api/admin/login" '{"username":"admin","password":"admin123"}' @{}
$h = @{ Authorization = "Bearer $($admin.data.token)" }
$cfg = CallJSON "Put" "$base/api/admin/system/config" '{"items":[{"configKey":"default_freight","configValue":"8"},{"configKey":"free_shipping_threshold","configValue":"100"}]}' $h
Write-Output "SET CONFIG: code=$($cfg.code)"

# 1. Send sms code via curl (robust)
$smsFile = "D:\Code\cc\agsell\_sms.json"
curl.exe -s --max-time 20 -X POST "$base/api/sms/send?phone=$phone" -o $smsFile
$smsJson = [System.IO.File]::ReadAllText($smsFile, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$code = $smsJson.data.code
Write-Output "SMS CODE: $code"

# 2. Register user
$reg = CallJSON "Post" "$base/api/user/register" "{`"phone`":`"$phone`",`"password`":`"test123456`",`"code`":`"$code`",`"nickname`":`"FreightTester`"}" @{}
Write-Output "REGISTER: code=$($reg.code) userId=$($reg.data.userId)"
$uh = @{ Authorization = "Bearer $($reg.data.token)" }

# 2.5 Add address for this user (ASCII only, PS5.1 GBK-safe)
$addr = CallJSON "Post" "$base/api/user/address" '{"receiver":"Test","phone":"13800005555","province":"Jiangsu","city":"Nanjing","district":"Xixia","detail":"Test Road 1","isDefault":1}' $uh
Write-Output "ADD ADDR: code=$($addr.code)"
$addrList = CallJSON "Get" "$base/api/user/address/list" $null $uh
$addrId = $addrList.data | Select-Object -First 1 -ExpandProperty id
Write-Output "ADDR ID: $addrId"

# 3. Cart broccoli 12.90 x1 (below threshold -> freight 8)
$c1 = CallJSON "Post" "$base/api/cart/add" '{"productId":2096479314092355583,"quantity":1}' $uh
Write-Output "CART1: code=$($c1.code)"
$cartList = CallJSON "Get" "$base/api/cart/list" $null $uh
$cart1Id = $cartList.data | Where-Object { $_.productId -eq 2096479314092355583 } | Select-Object -First 1 -ExpandProperty id
Write-Output "CART1 ID: $cart1Id"

# 4. Create order 1 (below threshold)
$o1 = CallJSON "Post" "$base/api/order/create" "{`"addressId`":$addrId,`"cartItemIds`":[$cart1Id]}" $uh
if ($o1 -ne $null) { Write-Output "ORDER1: code=$($o1.code) total=$($o1.data.totalAmount) pay=$($o1.data.payAmount)" }
else { Write-Output "ORDER1: FAILED" }

# 5. Cart rice 68.00 x2 (>= threshold -> freight 0)
$c2 = CallJSON "Post" "$base/api/cart/add" '{"productId":2096479314092355584,"quantity":2}' $uh
Write-Output "CART2: code=$($c2.code)"
$cartList2 = CallJSON "Get" "$base/api/cart/list" $null $uh
$cart2Id = $cartList2.data | Where-Object { $_.productId -eq 2096479314092355584 } | Select-Object -First 1 -ExpandProperty id
Write-Output "CART2 ID: $cart2Id"

$o2 = CallJSON "Post" "$base/api/order/create" "{`"addressId`":$addrId,`"cartItemIds`":[$cart2Id]}" $uh
if ($o2 -ne $null) { Write-Output "ORDER2: code=$($o2.code) total=$($o2.data.totalAmount) pay=$($o2.data.payAmount)" }
else { Write-Output "ORDER2: FAILED" }

# 6. Restore config 0/0
$cfg2 = CallJSON "Put" "$base/api/admin/system/config" '{"items":[{"configKey":"default_freight","configValue":"0"},{"configKey":"free_shipping_threshold","configValue":"0"}]}' $h
Write-Output "RESTORE CONFIG: code=$($cfg2.code)"

# 7. Assert (freight = payAmount - totalAmount)
$ok1 = $o1 -ne $null -and $o1.code -eq 0 -and ([decimal]$o1.data.payAmount - [decimal]$o1.data.totalAmount) -eq 8.0
$ok2 = $o2 -ne $null -and $o2.code -eq 0 -and ([decimal]$o2.data.payAmount - [decimal]$o2.data.totalAmount) -eq 0.0
Write-Output "ASSERT below-threshold freight=8 (pay-total): $(if($ok1){'PASS'}else{'FAIL'})"
Write-Output "ASSERT above-threshold freight=0 (pay-total): $(if($ok2){'PASS'}else{'FAIL'})"
