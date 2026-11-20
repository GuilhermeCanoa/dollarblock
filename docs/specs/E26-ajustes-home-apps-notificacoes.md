# Spec: Ajustes de uso — barra da Home, app reinstalado, menos notificações, swipe entre abas

**Status:** done
**Épico:** adhoc
**Data de criação:** 2026-11-19
**Última atualização:** 2026-11-19

---

## Contexto

Três problemas que o dono do produto encontrou usando o app no próprio celular:

1. **A barra da Home não se explica.** Ela é a soma dos limites diários, com um trecho por app
   colorido pela cor do ícone, mas não tem legenda: dá para ver cores, não para saber o que
   cada uma significa.
2. **Um app desinstalado e reinstalado some da aba Apps.** O Instagram, depois de reinstalado,
   continuava na Home (que lê o banco), mas não aparecia em Apps para configurar.
3. **Notificações demais**: até 3 cobranças do gerente mais 1 aviso de limite por app, por dia.
4. **Trocar de aba só pelo botão.** Faltava arrastar para o lado e trocar de seção.

## Regras

- R1 (barra): a barra continua igual (proposta B). Tocar nela abre logo abaixo uma legenda:
  uma linha por app (cor, ícone, nome, "uso de hoje de limite"), o trecho "Ainda disponível"
  e uma frase de leitura. Tocar de novo fecha a legenda. Uma setinha ao lado de
  "limite total" indica que a barra abre. O toque na conta (rótulo + valor + comparação)
  continua abrindo o popup "Prejuízo de hoje", agora separado do toque na barra.
- R2 (apps): a lista de apps instalados é descartada sempre que um app é instalado,
  removido, atualizado ou alterado (`ACTION_PACKAGE_*`), e a aba Apps recarrega sozinha.
- R3 (cobranças): teto de **1 por dia**, com o fechamento das 21h **contando** no teto.
  Gatilho de sessão longa sobe de 30 para **45 min** (A1 refeito para 45 min por dia).
  **E22** (desbloqueou e foi direto) e **E23/E24** (recibo do passe) foram **cortadas**.
- R4 (aviso de limite): no máximo **2 por dia somando todos os apps**. Os dois primeiros
  apps a entrar nos últimos 5 min são avisados; os outros trancam sem aviso.

- R5 (swipe): arrastar para a esquerda/direita troca entre Home, Apps, Statement e Profile,
  com a tela acompanhando o dedo. A bottom bar acompanha o arrasto e continua funcionando por
  toque. Voltar numa aba que não é a Home leva à Home. Sub-telas (histórico) não respondem ao
  swipe de abas.

## Tarefas

- [x] T1: `BudgetSegment` (nome/uso/limite) em `HomeDashboard.budget` + `freeMinutes`, com testes.
- [x] T2: `BudgetBar` com toque próprio + `BudgetLegend`; o toque do herói fica só na conta.
- [x] T3: `InstalledAppsProvider` com receiver de pacotes + `changes`; `AppsViewModel` coleta.
- [x] T4: `NudgePolicy` (teto 1, 45 min), corte de E22/E23/E24 (enum, engine, strings,
      receiver de desbloqueio no serviço, chamada no `BlockActivity`).
- [x] T5: `LimitWarningPolicy.DAILY_CAP = 2` + contador diário em `NotificationPreferences`.
- [x] T6: `HorizontalPager` no destino `TABS_ROUTE`, `PagerState` compartilhado com a bottom
      bar, `goToTab`, `BackHandler`; extensions `homeScreen/appsScreen/statisticsScreen/
      profileScreen` removidas (rotas mantidas como chave de página). Testado no aparelho.

## Fora do escopo / pendências

- App monitorado que foi desinstalado continua no banco e aparece na Home sem estar em
  Apps. Ainda não há marcação de "não instalado".
