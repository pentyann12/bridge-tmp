# Sample telemetry generator for trying out the LGTM stack.
# Each iteration emits one trace (frontend -> checkout -> payment/db) to Tempo
# via OTLP/HTTP, and log lines carrying the same traceID to Loki.
# Tempo's metrics-generator turns the spans into RED metrics and a service
# graph in Prometheus, so all three signals get data.
param(
    [string]$TempoOtlp = 'http://127.0.0.1:4318/v1/traces',
    [string]$LokiPush  = 'http://127.0.0.1:3100/loki/api/v1/push',
    [int]$IntervalMs   = 2000,
    [int]$Count        = 0      # 0 = run until Ctrl+C
)

$rand = New-Object System.Random

function New-Hex([int]$bytes) {
    $b = New-Object byte[] $bytes
    $rand.NextBytes($b)
    ($b | ForEach-Object { $_.ToString('x2') }) -join ''
}

function Get-NowNs {
    [string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000000)
}

function New-Span($traceId, $spanId, $parentId, $name, $kind, [long]$startNs, [long]$durMs, $isError, $attrs) {
    $span = [ordered]@{
        traceId           = $traceId
        spanId            = $spanId
        name              = $name
        kind              = $kind   # 1=INTERNAL 2=SERVER 3=CLIENT
        startTimeUnixNano = [string]$startNs
        endTimeUnixNano   = [string]($startNs + $durMs * 1000000)
        attributes        = @($attrs.GetEnumerator() | ForEach-Object {
            @{ key = $_.Key; value = @{ stringValue = [string]$_.Value } } })
        status            = @{ code = $(if ($isError) { 2 } else { 1 }) }
    }
    if ($parentId) { $span.parentSpanId = $parentId }
    $span
}

function New-Resource($service, $spans) {
    @{
        resource   = @{ attributes = @(@{ key = 'service.name'; value = @{ stringValue = $service } }) }
        scopeSpans = @(@{ scope = @{ name = 'lgtm-sample' }; spans = @($spans) })
    }
}

$paths = '/api/cart', '/api/checkout', '/api/items', '/api/user'
$i = 0
Write-Host "Sending sample data every $IntervalMs ms (Ctrl+C to stop) ..."

while ($Count -eq 0 -or $i -lt $Count) {
    $i++
    $traceId = New-Hex 16
    $path    = $paths[$rand.Next($paths.Count)]
    $isError = ($rand.Next(100) -lt 10)                 # ~10% errors
    $dbMs    = $rand.Next(5, 80)
    $payMs   = $rand.Next(20, 300)
    $svcMs   = $dbMs + $payMs + $rand.Next(5, 30)
    $feMs    = $svcMs + $rand.Next(5, 40)
    $t0      = [long](Get-NowNs) - $feMs * 1000000

    $sFe = New-Hex 8; $sFeCli = New-Hex 8; $sSvc = New-Hex 8
    $sDb = New-Hex 8; $sPayCli = New-Hex 8; $sPay = New-Hex 8

    $body = @{
        resourceSpans = @(
            (New-Resource 'frontend' @(
                (New-Span $traceId $sFe $null "GET $path" 2 $t0 $feMs $isError @{ 'http.request.method' = 'GET'; 'http.route' = $path; 'http.response.status_code' = $(if ($isError) { 500 } else { 200 }) }),
                (New-Span $traceId $sFeCli $sFe 'POST checkout' 3 ($t0 + 2000000) $svcMs $isError @{ 'server.address' = 'checkout' })
            )),
            (New-Resource 'checkout' @(
                (New-Span $traceId $sSvc $sFeCli 'POST /checkout' 2 ($t0 + 3000000) ($svcMs - 2) $isError @{ 'http.route' = '/checkout' }),
                (New-Span $traceId $sDb $sSvc 'SELECT orders' 3 ($t0 + 4000000) $dbMs $false @{ 'db.system' = 'postgresql'; 'db.statement' = 'SELECT * FROM orders WHERE id = ?' }),
                (New-Span $traceId $sPayCli $sSvc 'POST payment' 3 ($t0 + (5 + $dbMs) * 1000000) $payMs $isError @{ 'server.address' = 'payment' })
            )),
            (New-Resource 'payment' @(
                (New-Span $traceId $sPay $sPayCli 'POST /charge' 2 ($t0 + (6 + $dbMs) * 1000000) ($payMs - 2) $isError @{ 'http.route' = '/charge' })
            ))
        )
    }

    $now = Get-NowNs
    $level = $(if ($isError) { 'error' } else { 'info' })
    $logs = @{
        streams = @(
            @{ stream = @{ service_name = 'frontend'; level = $level }
               values = @(, @($now, "level=$level msg=`"request handled`" method=GET path=$path duration_ms=$feMs traceID=$traceId")) },
            @{ stream = @{ service_name = 'checkout'; level = $level }
               values = @(, @($now, "level=$level msg=`"checkout processed`" db_ms=$dbMs payment_ms=$payMs traceID=$traceId")) },
            @{ stream = @{ service_name = 'payment'; level = $level }
               values = @(, @($now, $(if ($isError) { "level=error msg=`"card declined`" traceID=$traceId" } else { "level=info msg=`"charge ok`" traceID=$traceId" }))) }
        )
    }

    try {
        Invoke-RestMethod -Method Post -Uri $TempoOtlp -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 20 -Compress) | Out-Null
        Invoke-RestMethod -Method Post -Uri $LokiPush  -ContentType 'application/json' -Body ($logs | ConvertTo-Json -Depth 20 -Compress) | Out-Null
        Write-Host ("[{0}] {1,-14} {2,5} ms  {3}  traceID={4}" -f $i, $path, $feMs, $level.ToUpper(), $traceId)
    } catch {
        Write-Warning "send failed: $($_.Exception.Message)"
    }
    Start-Sleep -Milliseconds $IntervalMs
}
