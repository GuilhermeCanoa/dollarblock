# DollarBlock — smoke test end-to-end no emulador.
#
# Automatiza o roteiro de validação manual: instala o build debug, concede as permissões,
# põe um app na régua com limite de 1 minuto, usa o app até estourar o limite e confere
# que a tela de bloqueio aparece com a saída de cortesia (E17) — porque no emulador não há
# Play Store, o Billing nunca fica pronto e o app cai exatamente no cenário de falha.
#
# Uso:  powershell -File scripts/smoke-test-emulator.ps1
#
# Requer um emulador já rodando (`emulator -avd <nome>`). Sai com código != 0 se algo falhar.

# NAO usar "Stop": o adb escreve mensagens normais ("1 file pulled") na stderr e o
# PowerShell 5.1 as transforma em erro, abortando o script no meio do teste.
$ErrorActionPreference = "Continue"

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = "com.dollarblock"
$target = "com.google.android.youtube"
$out = Join-Path $env:TEMP "dollarblock-smoke"
$adbLog = Join-Path $out "adb.log"
New-Item -ItemType Directory -Force -Path $out | Out-Null

$failures = @()
function Check($name, $condition, $detail) {
    if ($condition) { Write-Host "  [OK]   $name" -ForegroundColor Green }
    else { Write-Host "  [FALHA] $name — $detail" -ForegroundColor Red; $script:failures += $name }
}
function Step($t) { Write-Host ""; Write-Host "== $t ==" -ForegroundColor Cyan }

function UiText {
    & $adb shell uiautomator dump /sdcard/ui.xml | Out-Null
    & $adb pull /sdcard/ui.xml "$out\ui.xml" *>$adbLog
    ([xml](Get-Content "$out\ui.xml")).SelectNodes("//node") | Where-Object { $_.text } | ForEach-Object { $_.text }
}
function TopActivity {
    (& $adb shell dumpsys activity activities | Select-String "topResumedActivity" | Select-Object -First 1).ToString()
}
# Toca no centro do nó cujo texto casa com o padrão. Devolve $false se não achou.
function TapText($pattern) {
    & $adb shell uiautomator dump /sdcard/ui.xml | Out-Null
    & $adb pull /sdcard/ui.xml "$out\ui.xml" *>$adbLog
    $n = ([xml](Get-Content "$out\ui.xml")).SelectNodes("//node") | Where-Object { $_.text -match $pattern } | Select-Object -First 1
    if (-not $n) { return $false }
    # Em Compose o no com texto costuma nao ser o clicavel: sobe ate o ancestral clicavel.
    $c = $n
    while ($c -and $c.clickable -ne "true") { $c = $c.ParentNode }
    if (-not $c -or -not $c.bounds) { $c = $n }
    $b = $c.bounds -replace '[\[\]]', ' ' -split '[, ]+' | Where-Object { $_ }
    & $adb shell input tap ([int](([int]$b[0] + [int]$b[2]) / 2)) ([int](([int]$b[1] + [int]$b[3]) / 2))
    return $true
}

Step "Dispositivo"
$devices = (& $adb devices | Select-String "device$")
if (-not $devices) { Write-Host "Nenhum emulador/dispositivo conectado. Suba um com: emulator -avd NOME_DO_AVD" -ForegroundColor Red; exit 1 }
& $adb wait-for-device
Write-Host "  device: $(($devices | Select-Object -First 1).ToString().Trim())"

Step "Instalação"
Push-Location (Split-Path $PSScriptRoot -Parent)
if (-not $env:JAVA_HOME -and (Test-Path "C:\Program Files\Android\Android Studio\jbr")) {
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
}
& .\gradlew.bat :app:installDebug --console=plain --no-daemon | Select-Object -Last 3
if ($LASTEXITCODE -ne 0) { Write-Host "installDebug falhou" -ForegroundColor Red; exit 1 }
Pop-Location

Step "Permissões e estado limpo"
# pm clear apaga o estado do app E revoga o servico de acessibilidade: limpar primeiro,
# conceder depois, senao a Home abre com o aviso de permissoes faltando.
& $adb shell pm clear $pkg | Out-Null
Start-Sleep -Seconds 2
# O pm clear mata o app com o servico ligado e o Android o marca como "Crashed service",
# sem religar sozinho. Desligar e religar forca um rebind limpo — sem isso o bloqueio
# simplesmente nunca dispara e o teste fica instavel.
& $adb shell settings put secure enabled_accessibility_services "null" | Out-Null
& $adb shell settings put secure accessibility_enabled 0 | Out-Null
Start-Sleep -Seconds 2
& $adb shell settings put secure enabled_accessibility_services "$pkg/$pkg.service.accessibility.DollarBlockAccessibilityService" | Out-Null
& $adb shell settings put secure accessibility_enabled 1 | Out-Null
Start-Sleep -Seconds 3
& $adb shell appops set $pkg android:get_usage_stats allow | Out-Null
& $adb shell appops set $pkg SYSTEM_ALERT_WINDOW allow | Out-Null
& $adb shell pm grant $pkg android.permission.POST_NOTIFICATIONS 2>$null | Out-Null
& $adb shell am force-stop $target | Out-Null
& $adb shell input keyevent KEYCODE_WAKEUP | Out-Null
& $adb shell wm dismiss-keyguard | Out-Null
Write-Host "  permissões concedidas via adb"

$a11y = & $adb shell dumpsys accessibility
Check "Servico de acessibilidade ativo" (($a11y -match "Bound services.*DollarBlock") -and -not ($a11y -match "Crashed services.*dollarblock")) "servico nao vinculou — o bloqueio nao vai disparar"

Step "Onboarding"
& $adb logcat -c
& $adb shell am start -n "$pkg/.MainActivity" | Out-Null
Start-Sleep -Seconds 8
if (TapText "Skip to permissions|Pular para|permiss") { Start-Sleep -Seconds 3 }
for ($i = 0; $i -lt 4; $i++) {
    if (TapText "Continue|Continuar|Challenge accepted|Desafio aceito") { Start-Sleep -Seconds 3 } else { break }
}
# Se o servico de acessibilidade ainda nao registrou, a Home mostra o aviso de
# permissoes — reconcede e segue.
if ((UiText) -match "paperwork|permiss") {
    & $adb shell settings put secure enabled_accessibility_services "$pkg/$pkg.service.accessibility.DollarBlockAccessibilityService" | Out-Null
    & $adb shell settings put secure accessibility_enabled 1 | Out-Null
    Start-Sleep -Seconds 3
    [void](TapText "Keep flying blind")
    Start-Sleep -Seconds 3
}
$t = UiText
Check "Onboarding concluído (Home visível)" ($t -match "Home") "telas: $($t -join ' / ')"

Step "Configurar app monitorado com limite de 1 min"
if (-not (TapText "^Apps$")) { Write-Host "  aba Apps não encontrada" -ForegroundColor Red; exit 1 }
Start-Sleep -Seconds 4
# Adiciona o app-alvo à régua (botão + na linha dele).
& $adb shell uiautomator dump /sdcard/ui.xml | Out-Null; & $adb pull /sdcard/ui.xml "$out\ui.xml" *>$adbLog
$row = ([xml](Get-Content "$out\ui.xml")).SelectNodes("//node") | Where-Object { $_.text -match "YouTube" } | Select-Object -First 1
if ($row) {
    $b = $row.bounds -replace '[\[\]]', ' ' -split '[, ]+' | Where-Object { $_ }
    $y = [int](([int]$b[1] + [int]$b[3]) / 2)
    & $adb shell input tap 961 $y   # botão "+" à direita da linha
    Start-Sleep -Seconds 3
}
Check "App na régua" ((UiText) -match "on the meter|na r") "não apareceu a seção de apps monitorados"

# Abre o diálogo de limite e salva 1 minuto. O teclado empurra o diálogo para cima,
# então as coordenadas do Save são lidas do dump DEPOIS de digitar.
if (TapText "YouTube") { Start-Sleep -Seconds 3 }
& $adb shell uiautomator dump /sdcard/ui.xml | Out-Null; & $adb pull /sdcard/ui.xml "$out\ui.xml" *>$adbLog
$edit = ([xml](Get-Content "$out\ui.xml")).SelectNodes("//node") | Where-Object { $_.'class' -eq 'android.widget.EditText' } | Select-Object -First 1
if ($edit) {
    $b = $edit.bounds -replace '[\[\]]', ' ' -split '[, ]+' | Where-Object { $_ }
    & $adb shell input tap ([int](([int]$b[0] + [int]$b[2]) / 2)) ([int](([int]$b[1] + [int]$b[3]) / 2))
    Start-Sleep -Seconds 2
    & $adb shell input text "1"
    Start-Sleep -Seconds 2
    [void](TapText "^Save$|^Salvar$")
    Start-Sleep -Seconds 3
}
Check "Limite diário salvo" ((UiText) -match "of 1m|de 1m") "o app continua sem limite"

Step "Estourar o limite (usa o app-alvo por ~80s)"
& $adb logcat -c
# O rastreamento so comeca numa TRANSICAO de foreground para o app monitorado. Se o
# app-alvo ja estiver aberto (ex.: rodada anterior), abri-lo de novo nao gera evento
# novo e o bloqueio nunca dispara. Fechar e ir para a home garante a transicao.
& $adb shell am force-stop $target | Out-Null
& $adb shell input keyevent KEYCODE_HOME | Out-Null
Start-Sleep -Seconds 3
& $adb shell monkey -p $target -c android.intent.category.LAUNCHER 1 *>$adbLog
Start-Sleep -Seconds 6
if ((TopActivity) -match "permissioncontroller") { [void](TapText "Don.t allow|N.o permitir"); Start-Sleep -Seconds 3 }
# O bloqueio depende do polling de uso do servico: em vez de um sleep fixo (que deixa o
# teste instavel), espera ate a BlockActivity aparecer, com teto de 3 min. Precisa manter
# o app-alvo em foreground, entao so observamos — sem tocar na tela.
Write-Host "  aguardando o limite estourar (ate 180s)..."
$blocked = $false
for ($i = 0; $i -lt 36; $i++) {
    Start-Sleep -Seconds 5
    if ((TopActivity) -match "BlockActivity") { $blocked = $true; break }
}
$top = TopActivity
Check "Tela de bloqueio abriu" $blocked "topResumedActivity=$top"
if (-not $blocked) {
    Write-Host "  (sem bloqueio, as checagens seguintes nao se aplicam)" -ForegroundColor Yellow
}

Step "Saída de cortesia (Billing indisponível no emulador)"
Start-Sleep -Seconds 12   # espera o timeout de 8s do Billing
& $adb shell screencap -p /sdcard/s.png | Out-Null; & $adb pull /sdcard/s.png "$out\block.png" *>$adbLog
$t = UiText
Check "Aviso de falha na cobrança" ($t -match "till broke|maquininha") "textos: $($t -join ' / ')"
Check "Botão de abrir de graça" ($t -match "for free|de gra") "não há saída de cortesia — o usuário ficaria trancado"
Check "Botão de sair sem pagar" ($t -match "Keep my money|Ficar com meu dinheiro") "faltou a saída sem pagar"
$log = & $adb logcat -d -s DollarBlockPay:*
Check "Timeout do Billing registrado" ($log -match "Billing not ready") "esperado o log de timeout"

Step "Conceder a cortesia"
[void](TapText "for free|de gra")
Start-Sleep -Seconds 8
$top = TopActivity
Check "App-alvo abriu" ($top -match $target) "topResumedActivity=$top"
Start-Sleep -Seconds 40
$top = TopActivity
Check "Desbloqueio persiste (não re-bloqueou)" ($top -match $target) "voltou a bloquear: $top"

Step "Extrato: a cortesia é auditável"
# Relanca a MainActivity do zero: reabrir a task traz a ultima aba usada (Apps), e um
# toque perdido pode ter deixado lixo na busca cobrindo a barra inferior. Com force-stop
# o app sempre volta na Home, que e onde fica o recibo.
& $adb shell am force-stop $pkg | Out-Null
Start-Sleep -Seconds 2
& $adb shell am start -n "$pkg/.MainActivity" | Out-Null
Start-Sleep -Seconds 10
# O force-stop desvincula o servico de acessibilidade, entao a Home pode abrir com o
# aviso de permissoes por cima. Dispensa o aviso para chegar nas transacoes.
if ((UiText) -match "paperwork|running blind|permiss") {
    [void](TapText "Keep flying blind")
    Start-Sleep -Seconds 4
}

# A lista de transacoes fica abaixo da dobra na Home: rola ate o fim antes de ler.
& $adb shell input swipe 540 1800 540 500 400 | Out-Null
Start-Sleep -Seconds 2
& $adb shell input swipe 540 1800 540 500 400 | Out-Null
Start-Sleep -Seconds 3
$t = UiText
Check "Recibo de cortesia na Home" ($t -match "Courtesy|Cortesia") "transações: $($t -join ' / ')"
Check "Valor zero no recibo" ($t -match "0[.,]00") "o valor deveria ser 0,00"

Step "Sem crashes"
$crash = & $adb logcat -d -b crash
Check "Nenhum crash registrado" (-not $crash) "há crash no buffer"

Write-Host ""
Write-Host "═══════════════════════════════════════════"
if ($failures.Count -eq 0) {
    Write-Host "✅ Smoke test passou. Screenshots em $out" -ForegroundColor Green
    exit 0
} else {
    Write-Host "❌ $($failures.Count) verificação(ões) falharam:" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "   - $_" -ForegroundColor Red }
    Write-Host "Screenshots em $out"
    exit 1
}
