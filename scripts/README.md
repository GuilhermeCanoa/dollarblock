# Scripts de teste do DollarBlock

Dois comandos para você ter confiança de que o app funciona — **sem precisar de IA**
e sem ser desenvolvedor. Rode-os no PowerShell, a partir da pasta raiz do projeto
(`C:\dev\dollarblock`).

---

## 1. `run-unit-tests.ps1` — a checagem do dia a dia (rápida, sem celular)

**O que faz:** roda todos os testes automáticos da *lógica* do app. Não precisa de
celular nem emulador. Leva ~2 minutos.

**O que ele garante que funciona:**

| Funcionalidade | O que é testado |
|---|---|
| Medição de tempo de uso | Somar corretamente as sessões de cada app, inclusive app aberto na virada da meia-noite e eventos "bagunçados" do Android (`UsageAggregator`) |
| Aviso "faltam X min" | Avisar **uma única vez** ao entrar nos 5 min finais; nunca avisar depois de estourar (`LimitWarningPolicy`) |
| Tela inicial (Home) | Cálculo do dinheiro perdido no dia e de quantos apps estão bloqueados (`HomeMetrics`) |
| Desbloqueio do dia | O passe pago vale até a meia-noite e expira sozinho (`BlockPreferences`) |
| Banco de dados | Gravar o uso sem duplicar linhas por app/dia (`DailyUsageDao`) |

**Como rodar:**

```powershell
.\scripts\run-unit-tests.ps1
```

**Como ler o resultado:**
- Verde **"OK - todos os testes passaram"** no fim = tudo certo.
- Vermelho **"FALHOU"** = algo quebrou; o nome do teste aparece logo acima.
- Ele também mostra o caminho de um relatório `.html` que você pode abrir no navegador
  para ver teste por teste.

Rode este script sempre que mexer no app, antes de gerar uma versão nova.

---

## 2. `smoke-test.ps1` — o teste "de verdade" (no emulador, mais lento)

**O que faz:** instala o app num emulador (ou celular conectado), liga as permissões
sozinho, força um app a passar do limite e confere se a **tela de bloqueio realmente
aparece**. É o teste que imita um usuário de verdade.

**Antes de rodar, você precisa de:**
- Um **emulador aberto** (pelo Android Studio → Device Manager → ▶), **ou**
- Um **celular Android** conectado por USB com a "Depuração USB" ligada.

**Como rodar (a primeira vez, instalando o app):**

```powershell
.\scripts\smoke-test.ps1
```

**Como rodar de novo sem reinstalar (mais rápido):**

```powershell
.\scripts\smoke-test.ps1 -SkipInstall
```

**Testar bloqueando outro app (ex.: YouTube):**

```powershell
.\scripts\smoke-test.ps1 -TargetPackage com.google.android.youtube
```

**Como ler o resultado:** ao final ele imprime uma tabela com `PASSOU`/`FALHOU` para
cada etapa (permissões ligadas, cenário configurado, tela de bloqueio apareceu).

**Importante — por que ele demora ~2 minutos "parado":** o Android **não deixa**
inventar tempo de uso falso. Então o teste usa tempo real: ele mantém o app-alvo aberto
por ~90 segundos (dando alguns toques na tela) até passar do limite de 1 minuto, e só
então confere o bloqueio. É esperado ele ficar aparentemente parado nesse intervalo.

---

## O que estes scripts **não** cobrem (validação manual)

Algumas coisas só dá para conferir com o olho, porque dependem de serviços externos ou
do próprio sistema Android:

- **Pagamento pelo Google Play** (comprar o passe do dia): depende do Google Play e de
  uma conta de teste; valide manualmente no fluxo de compra.
- **A notificação de aviso aparecendo na barra**: o `smoke-test` prepara o cenário, mas
  confirmar visualmente o texto/ícone da notificação é melhor a olho.
- **A aparência das telas** (cores, textos, tradução PT/EN).

Esses pontos estão documentados em `docs/specs/TESTING-strategy.md`.
