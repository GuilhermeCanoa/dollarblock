# Prompt — continuar capturas de tablet (Play Store)

Cole o prompt abaixo numa nova sessão do Claude Code (diretório `C:\dev\dollarblock`).

---

Preciso gerar as capturas de tela de **tablet 7" e tablet 10"** exigidas pela Play Store,
via emulador. As capturas de telefone já estão prontas em
`docs/play-store/screenshots/phone/` — leia `docs/play-store/README.md` antes de começar
(tem o processo completo, requisitos da loja e o passo a passo de preparação de dados
que usei no telefone; replique o mesmo estado no emulador).

Requisitos da loja:
- Tablet 7": até 8 capturas, PNG 16:9 ou 9:16, cada lado 320–3840 px.
- Tablet 10": até 8 capturas, PNG 16:9 ou 9:16, cada lado **1080–7680 px**.

Contexto do ambiente:
- `adb` não está no PATH: usar `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`.
  Emulador: `$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe`.
- Só existe o AVD `Medium_Phone_API_36.1` (phone). Na última sessão o emulador
  `emulator-5554` estava com o system server travado ("Broken pipe" em qualquer `cmd`) —
  reiniciá-lo (`adb -s emulator-5554 reboot`) ou matar e subir de novo.
- Não é preciso criar AVD de tablet: usar o truque `wm size` + `wm density` no emulador
  para simular tablet (o mesmo usado no telefone, documentado no README):
  - **Tablet 10"**: `wm size 1440x2560` + `wm density 240` → smallest-width 960dp
    (layout de tablet), captura 1440x2560 (9:16, atende o mínimo de 1080px).
  - **Tablet 7"**: `wm size 1080x1920` + `wm density 280` → sw ~617dp, captura 1080x1920.
  - Reverter com `wm size reset` / `wm density reset` ao final de cada bateria.
- Instalar o app no emulador: `.\gradlew.bat :app:installDebug --console=plain --no-daemon`
  com `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` (ver CLAUDE.md).
- Habilitar a acessibilidade via adb depois de instalar (comandos no CLAUDE.md, seção
  "Accessibility service"). Overlay: `appops set com.dollarblock SYSTEM_ALERT_WINDOW allow`.
  Notificações: `pm grant com.dollarblock android.permission.POST_NOTIFICATIONS`.
  Usage access: `appops set com.dollarblock GET_USAGE_STATS allow`.

Preparação de dados no emulador (o emulador não tem uso real de apps):
1. Completar o onboarding (com as permissões já concedidas via adb, a tela "A burocracia"
   mostra tudo "Concedida"; se o app pedir de novo, conceder pela UI do sistema).
2. Calibrar o salário em R$ 3.500 (Home → card de salário).
3. Gerar um pouco de uso real: abrir Chrome e YouTube (pré-instalados) por 1–2 min cada
   (`monkey -p com.android.chrome -c android.intent.category.LAUNCHER 1`, interagir um
   pouco via `input swipe`, esperar). O `UsageSyncWorker` pode demorar; abrir e fechar o
   DollarBlock força re-leitura (Flows demoram ~2–4 s após `am start`).
4. Adicionar Chrome e YouTube ao taxímetro com limites baixos (ex.: Chrome 15 min,
   YouTube 30 min) para as barras aparecerem.
5. Para a captura da tela de bloqueio no tablet: limite do Chrome = 1 min, usar o Chrome
   até passar de 1 min, reabrir → `BlockActivity`. (Build debug tem o botão
   "Simular pagamento"; NÃO capturá-lo — ele não existe no release.)

Telas a capturar em CADA formato (10" primeiro, depois 7" — mesma sessão, só mudando
`wm size`/`density`; nomes `tablet10-XX-nome.png` em `docs/play-store/screenshots/tablet-10/`
e `tablet7-XX-nome.png` em `docs/play-store/screenshots/tablet-7/`):
1. Home com prejuízo do dia
2. Apps com limites configurados
3. Extrato (donut)
4. Tela de bloqueio (fatura)
5. Onboarding página 1 ("Tempo é dinheiro...")
6. Perfil

Validar no final: dimensões de cada PNG (`System.Drawing`), proporção exata 16:9/9:16,
lado mínimo de 1080px nas de 10". Atualizar o checklist de status no
`docs/play-store/README.md` e registrar qualquer desvio do processo.

Cuidado: telas do sistema/launcher do emulador em densidade alterada podem quebrar o
layout do launcher — irrelevante, só as telas do app importam. Se alguma tela do app
quebrar o layout na proporção forçada (como aconteceu na tela de bloqueio do telefone),
ajustar a densidade para baixo até renderizar bem e anotar no README.

---
