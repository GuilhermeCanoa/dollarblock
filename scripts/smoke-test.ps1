<#
.SYNOPSIS
    Teste "de verdade" no emulador/celular: instala o app, liga as permissoes,
    força um app a passar do limite e confere se a tela de BLOQUEIO aparece.

.DESCRIPTION
    Diferente do run-unit-tests.ps1 (que testa a logica isolada, sem celular), este
    script valida o app rodando de ponta a ponta num DISPOSITIVO REAL ou EMULADOR.
    Ele automatiza o que um humano faria na mao:

      1. Confere que ha um dispositivo conectado (adb).
      2. Compila e instala a versao debug do app.
      3. Liga as duas permissoes especiais que o bloqueio exige
         (Uso de Apps e Acessibilidade) via adb.
      4. Configura um app-alvo como "monitorado" com limite de 1 minuto, usando
         o modo de debug do proprio app (Intent), sem precisar mexer na tela.
      5. Abre o app-alvo e o mantem em primeiro plano ate passar de 1 minuto.
      6. Confere, lendo a tela via uiautomator, se a BlockActivity do DollarBlock
         subiu (= bloqueio funcionou). Opcionalmente confere a notificacao de aviso.

    No fim imprime PASSOU ou FALHOU para cada verificacao.

    IMPORTANTE / limitacao honesta: o Android nao deixa "injetar" tempo de uso falso.
    Por isso o teste usa tempo REAL — ele espera de fato ~90s com o app-alvo aberto.
    O app-alvo padrao e o Chrome (com.android.chrome), que existe na maioria dos
    emuladores. Troque com -TargetPackage se quiser outro.

.PARAMETER TargetPackage
    Pacote do app que sera monitorado e bloqueado. Padrao: com.android.chrome.

.PARAMETER SkipInstall
    Pula compilar/instalar (use quando o app ja esta instalado e voce so quer repetir o teste).

.EXAMPLE
    .\scripts\smoke-test.ps1

.EXAMPLE
    .\scripts\smoke-test.ps1 -TargetPackage com.google.android.youtube -SkipInstall
#>

param(
    [string]$TargetPackage = "com.android.chrome",
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$app = "com.dollarblock"
$a11y = "$app/$app.service.accessibility.DollarBlockAccessibilityService"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$results = [System.Collections.Generic.List[object]]::new()
function Record($name, $ok, $detail = "") {
    $results.Add([PSCustomObject]@{ Check = $name; Ok = $ok; Detail = $detail })
    $tag = if ($ok) { "PASSOU" } else { "FALHOU" }
    $color = if ($ok) { "Green" } else { "Red" }
    Write-Host ("[{0}] {1} {2}" -f $tag, $name, $detail) -ForegroundColor $color
}

function Adb { param([Parameter(ValueFromRemainingArguments=$true)]$a) & adb @a }

# --- 0. adb + dispositivo -------------------------------------------------
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Host "adb nao encontrado no PATH. Instale o platform-tools do Android SDK." -ForegroundColor Red
    exit 2
}
$devices = (& adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" }
if (-not $devices) {
    Write-Host "Nenhum emulador/celular conectado. Abra um emulador (ou conecte um celular com depuracao USB) e rode de novo." -ForegroundColor Red
    exit 2
}
Write-Host "Dispositivo conectado: $($devices -join ', ')" -ForegroundColor Cyan

# --- 1. Java para o Gradle ------------------------------------------------
if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
    $jbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path $jbr) { $env:JAVA_HOME = $jbr }
}

# --- 2. Compilar + instalar ----------------------------------------------
if (-not $SkipInstall) {
    Write-Host "Compilando e instalando o app (pode levar alguns minutos)..." -ForegroundColor Cyan
    & ".\gradlew.bat" ":app:installDebug" "--console=plain" "--no-daemon"
    if ($LASTEXITCODE -ne 0) { Write-Host "Falha ao instalar o app." -ForegroundColor Red; exit 2 }
} else {
    Write-Host "Pulando instalacao (-SkipInstall)." -ForegroundColor DarkGray
}

# --- 3. Ligar permissoes especiais ---------------------------------------
# Acessibilidade (obrigatoria para o bloqueio funcionar)
Adb shell settings put secure enabled_accessibility_services $a11y | Out-Null
Adb shell settings put secure accessibility_enabled 1 | Out-Null
$a11yValue = (Adb shell settings get secure enabled_accessibility_services).Trim()
Record "Servico de acessibilidade ligado" ($a11yValue -like "*DollarBlockAccessibilityService*") "($a11yValue)"

# Uso de Apps (Usage Access) — via appops, funciona em emulador
Adb shell appops set $app GET_USAGE_STATS allow | Out-Null
$usageMode = (Adb shell appops get $app GET_USAGE_STATS) -join " "
Record "Permissao de Uso de Apps concedida" ($usageMode -like "*allow*") "($usageMode)"

# Notificacoes (Android 13+) — pode nao existir em API < 33; ignora erro
try { Adb shell pm grant $app android.permission.POST_NOTIFICATIONS 2>$null | Out-Null } catch {}

# --- 4. Configurar app-alvo como monitorado com limite de 1 min ----------
# Usa o broadcast de debug do app (ver DebugSetupReceiver). Se o receiver nao
# existir nesta build, o teste avisa e segue com verificacao manual.
Write-Host "Configurando '$TargetPackage' como monitorado, limite = 1 min..." -ForegroundColor Cyan
$setup = Adb shell am broadcast -a "$app.DEBUG_SET_LIMIT" --es pkg $TargetPackage --ei limit 1 -p $app
$setupOk = ($setup -join " ") -match "result=-1|Broadcast completed"
Record "Cenario de teste configurado (via broadcast de debug)" $setupOk "(se FALHOU, configure o app-alvo com limite 1min na tela Apps e rode com -SkipInstall)"

# --- 5. Acordar a tela e abrir o app-alvo --------------------------------
Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
Adb shell wm dismiss-keyguard | Out-Null
Adb shell monkey -p $TargetPackage -c android.intent.category.LAUNCHER 1 2>$null | Out-Null
Start-Sleep -Seconds 3

Write-Host "Mantendo '$TargetPackage' em primeiro plano por ~90s para ultrapassar o limite de 1 min..." -ForegroundColor Cyan
Write-Host "  (o Android nao permite injetar tempo falso; o teste espera tempo real)" -ForegroundColor DarkGray
# Interacoes leves para o app contar como 'em uso' e nao entrar em Doze.
for ($i = 0; $i -lt 9; $i++) {
    Adb shell input swipe 500 1200 500 600 300 2>$null | Out-Null
    Start-Sleep -Seconds 10
}

# --- 6. Verificar se a BlockActivity subiu -------------------------------
Start-Sleep -Seconds 3
$dumpPath = Join-Path $env:TEMP "db_ui_dump.xml"
Adb shell uiautomator dump /sdcard/db_ui.xml 2>$null | Out-Null
Adb pull /sdcard/db_ui.xml $dumpPath 2>$null | Out-Null

$foreground = (Adb shell dumpsys activity activities) -join "`n"
$blockUp = $foreground -match "com\.dollarblock/.*BlockActivity" -or $foreground -match "BlockActivity"

$dumpText = if (Test-Path $dumpPath) { Get-Content $dumpPath -Raw } else { "" }
$blockUi = $dumpText -match "com.dollarblock"

Record "Tela de bloqueio (BlockActivity) apareceu" ($blockUp -or $blockUi) `
    "(foreground=$([bool]$blockUp), ui-dump-dollarblock=$([bool]$blockUi))"

# --- Resumo ---------------------------------------------------------------
Write-Host ""
Write-Host "===== RESUMO =====" -ForegroundColor Cyan
$results | Format-Table -AutoSize
$failed = @($results | Where-Object { -not $_.Ok }).Count
if ($failed -eq 0) {
    Write-Host "Tudo passou: bloqueio validado de ponta a ponta." -ForegroundColor Green
    exit 0
} else {
    Write-Host "$failed verificacao(oes) falharam. Veja os detalhes acima." -ForegroundColor Red
    Write-Host "Dica: rode de novo com -SkipInstall depois de ajustar as permissoes/cenario na mao." -ForegroundColor DarkGray
    exit 1
}
