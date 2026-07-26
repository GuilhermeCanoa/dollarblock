# Capturas de tela — Play Store

Assets de listagem da loja. Requisitos (jul/2026):

| Tipo | Qtde | Formato | Dimensões |
|---|---|---|---|
| Telefone | 2–8 (mín. 4 com ≥1080px por lado p/ promoção) | PNG/JPEG ≤8MB, 16:9 ou 9:16 | 320–3840 px por lado |
| Tablet 7" | até 8 | PNG/JPEG ≤8MB, 16:9 ou 9:16 | 320–3840 px por lado |
| Tablet 10" | até 8 | PNG/JPEG ≤8MB, 16:9 ou 9:16 | 1080–7680 px por lado |

## Status

- ✅ `screenshots/phone/` — 8 capturas, 1080x1920 (9:16), tiradas em um Galaxy S25+ real
- ✅ `screenshots/tablet-7/` — 6 capturas, 1080x1920 (9:16, cada lado dentro de 320–3840), emulador
- ✅ `screenshots/tablet-10/` — 6 capturas, 1440x2560 (9:16, lado menor ≥1080), emulador

## Capturas do telefone (ordem sugerida de upload)

| Arquivo | Tela | Por quê |
|---|---|---|
| `phone-02-home-prejuizo.png` | Home com "Prejuízo de hoje R$ 5,19 = 86% de um café" | Proposta de valor imediata |
| `phone-07-bloqueio-fatura.png` | Tela de bloqueio (fatura + passe do dia R$ 4,99) | Feature assinatura do app |
| `phone-03-apps-limites.png` | Apps com 3 limites configurados e barras de uso | Como configurar |
| `phone-04-extrato.png` | Extrato semanal com gráfico donut | Estatísticas |
| `phone-08-apps-limite-estourado.png` | Apps com Instagram "+25m acima do limite" | Estado de excedente |
| `phone-01-onboarding-conceito.png` | Onboarding "Tempo é dinheiro. O seu está vazando." | Brand voice |
| `phone-05-perfil.png` | Perfil com stats do dia + permissões | Transparência |
| `phone-06-onboarding-permissoes.png` | Onboarding "A burocracia" com 4 permissões concedidas | Transparência (opcional) |

## Como as capturas foram tiradas (para refazer/atualizar)

O S25+ tem tela 1080x2340 (9:19,5) — **não** atende a proporção 9:16 exigida.
Solução: forçar resolução lógica 9:16 via `wm size` durante a sessão de captura,
sem distorção (o app apenas re-layouta), e reverter no final.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"   # adb não está no PATH
$d = '<serial do device>'   # & $adb devices

# 1. Preparar display 9:16
& $adb -s $d shell wm size 1080x1920
& $adb -s $d shell wm density 400     # densidade 450 nativa deixa só ~682dp de altura e
                                      # QUEBRA o layout da tela de bloqueio (título sobrepõe
                                      # a citação). Com 400 (=768dp) tudo renderiza bem.

# 2. Capturar (NUNCA redirecionar com > no PowerShell — corrompe o PNG)
& $adb -s $d shell screencap -p /sdcard/s.png
& $adb -s $d pull /sdcard/s.png docs/play-store/screenshots/phone/phone-XX-nome.png

# 3. Reverter SEMPRE ao final
& $adb -s $d shell wm size reset
& $adb -s $d shell wm density reset
```

### Preparação de dados (estado usado nas capturas)

1. App resetado, onboarding completo, 4 permissões concedidas.
2. Salário calibrado: **R$ 3.500/mês** (Home → card "Adicione o seu salário").
3. Apps no taxímetro (aba Apps, uso real do aparelho no dia):
   - Instagram — limite 60 min (uso ~40m → barra ~2/3)
   - WhatsApp — limite 120 min (uso ~10m)
   - YouTube — limite 45 min (uso ~14m)
4. **Tela de bloqueio:** baixar temporariamente o limite do Instagram para 15 min
   (abaixo do uso do dia) e abrir o Instagram → `BlockActivity` aparece na hora.
   Depois restaurar o limite para 60 min (o app mostra um diálogo "Limite aumentado…
   Tem certeza?" — confirmar em "Anotado").
   - Efeito colateral: fica registrado 1 bloqueio no histórico e "R$ 5,00 economizado"
     na Home — proposital, deixa a Home mais rica na captura.
5. Navegação por adb: `input tap`, coordenadas via
   `uiautomator dump /sdcard/ui.xml` + pull + parse dos `bounds`.

### Dicas

- Bateria/relógio aparecem na status bar; se quiser status bar limpa, usar
  `adb shell settings put global sysui_demo_allowed 1` + demo mode (não foi usado).
- Após `am start`, os Flows demoram ~2–4 s para emitir — aguardar antes do screencap.
- Diálogos do app: campo de texto primeiro `input tap` no campo, `input text`,
  `KEYCODE_BACK` para fechar o teclado, depois tap no botão salvar.

## Tablets (concluído — jul/2026)

Capturas geradas no emulador `Medium_Phone_API_36.1` (Windows), simulando tablet via
`wm size`/`wm density` — **10"**: `wm size 1440x2560` + `wm density 240` (sw 960dp);
**7"**: `wm size 1080x1920` + `wm density 280` (sw ~617dp). Sempre `wm size reset` +
`wm density reset` ao final.

Telas em cada formato (mesma ordem dos arquivos): Home com prejuízo, Apps com limites
(Chrome 10m/30m, YouTube 9m/30m), Extrato semanal (donut), bloqueio (fatura R$ 4,99),
onboarding página 1, Perfil (4 permissões concedidas).

### Desvios/notas do processo no emulador

1. **Build release, não debug** — o debug mostra a seção "Debug — Resetar todos os dados"
   no Perfil (no tablet cabe tudo na tela, sem rolagem que a esconda como no telefone) e
   o botão "Simular pagamento" no bloqueio. Usado `:app:assembleRelease` assinado
   (keystore no `local.properties`).
2. **Idioma** — o emulador estava em en-US e o app segue o idioma do sistema
   (`values-pt` só resolve com sistema pt). `cmd locale set-app-locales` não funciona:
   o app limpa o per-app locale no cold start (`DollarBlockApp` reaplica a preferência
   interna, padrão "sistema"). Solução: trocar o idioma do **sistema** para
   Português (Brasil) em Configurações (adicionar idioma e remover English).
3. **Tela de bloqueio no emulador** — sem Play Store logado/faixa de teste, o Play
   Billing não conecta e a tela mostra "A loja do Google Play não respondeu" sem o botão
   de pagar. Para reproduzir o que o usuário real vê (igual à captura do telefone), o
   estado foi forçado **temporariamente** em `BlockActivity` (readyToPay=true, preço
   "R$ 4,99"), build só para captura, **revertido em seguida** (nada commitado).
4. **Uso do Chrome zerado na UI** — bug real encontrado no `UsageStatsProvider`
   (funções via `UsageEvents`): o rastreio de RESUMED/PAUSED usa um slot único por
   pacote, e activities-trampolim do Chrome (`ChromeLauncherActivity`/`FirstRunActivity`)
   emitem `STOPPED` da activity anterior *depois* do `RESUMED` da atual, consumindo o
   slot — a sessão real é descartada e o uso fica subcontado (0m com 15m reais).
   Workaround para as capturas: sessão única "limpa" (abrir via launcher já sem
   first-run, terminar com HOME). **Fix pendente**: rastrear resume por classe, não por
   pacote (as variantes agregadas `queryAndAggregateUsageStats`, usadas pelo serviço de
   bloqueio, não são afetadas).
5. **Acessibilidade via `settings put`** reverte para `null` após reinstalar o APK ou
   `am force-stop` — re-executar os comandos do CLAUDE.md e conferir com
   `dumpsys accessibility` (`Enabled services`) antes de qualquer captura de bloqueio;
   sem isso o Perfil mostra "Pendente" e o bloqueio não dispara.
6. **Chrome first-run** — dispensar a FRE ("Use without an account" + "Got it" do aviso
   de ads) antes de gerar uso, senão todo o tempo cai na `FirstRunActivity` (e o item 4
   engole a sessão).
