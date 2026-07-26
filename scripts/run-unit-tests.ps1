<#
.SYNOPSIS
    Roda toda a suite de testes automaticos do DollarBlock (os testes "baratos", que
    NAO precisam de celular nem emulador).

.DESCRIPTION
    Esta e a checagem do dia a dia. Ela valida a logica central do app:
      - medicao do tempo de uso por app (UsageAggregator)
      - aviso de "faltam X min" (LimitWarningPolicy)
      - metricas da Home (dinheiro perdido, apps bloqueados)
      - desbloqueio ate a meia-noite (BlockPreferences)
      - gravacao de uso no banco (DailyUsageDao, via Robolectric)

    Se TUDO passar, aparece "BUILD SUCCESSFUL" no fim. Qualquer falha e mostrada
    em vermelho com o nome do teste que quebrou.

.EXAMPLE
    .\scripts\run-unit-tests.ps1
#>

$ErrorActionPreference = "Stop"

# Raiz do projeto = pasta acima de scripts/
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

# O Gradle precisa de um Java. Usamos o que ja vem com o Android Studio (JBR),
# entao voce nao precisa instalar Java separadamente.
if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
    $jbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path $jbr) {
        $env:JAVA_HOME = $jbr
        Write-Host "JAVA_HOME nao estava definido; usando o JBR do Android Studio." -ForegroundColor DarkGray
    } else {
        Write-Warning "Nao encontrei o Java do Android Studio em '$jbr'. Se der erro, abra o Android Studio uma vez ou ajuste o caminho."
    }
}

Write-Host "Rodando os testes automaticos do DollarBlock..." -ForegroundColor Cyan
Write-Host ""

& ".\gradlew.bat" ":app:testDebugUnitTest" "--console=plain" "--no-daemon"
$exit = $LASTEXITCODE

Write-Host ""
if ($exit -eq 0) {
    Write-Host "OK - todos os testes passaram." -ForegroundColor Green
    $report = Join-Path $repoRoot "app\build\reports\tests\testDebugUnitTest\index.html"
    if (Test-Path $report) {
        Write-Host "Relatorio detalhado (abra no navegador): $report" -ForegroundColor DarkGray
    }
} else {
    Write-Host "FALHOU - algum teste nao passou. Veja o que apareceu em vermelho acima." -ForegroundColor Red
    $report = Join-Path $repoRoot "app\build\reports\tests\testDebugUnitTest\index.html"
    if (Test-Path $report) {
        Write-Host "Relatorio detalhado (abra no navegador): $report" -ForegroundColor DarkGray
    }
}

exit $exit
