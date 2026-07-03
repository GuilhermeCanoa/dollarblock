# Regras de Negócio — Controle de Tempo de Uso

> Documento de referência para as regras que governam o monitoramento, bloqueio e desbloqueio de apps no DollarBlock.

---

## 1. Configuração de limite diário

### Como o usuário define o limite

O usuário entra na tela **Apps**, ativa o monitoramento de um app (toggle) e toca no app para abrir o diálogo de limite. No diálogo ele escolhe um valor em minutos (ex.: 30 min, 1h, 2h). O limite é salvo imediatamente.

**Opções disponíveis hoje:**
- O campo aceita qualquer valor em minutos (inteiro positivo).
- Não há um menu fixo de opções pré-definidas — é um input numérico.
- Definir `null` (limpar) remove o limite: o app continua monitorado (aparece na lista e acumula uso), mas **nunca é bloqueado**.

### Sem limite configurado = sem bloqueio

Um app pode ser monitorado (aparece na lista com barra de uso) sem ter limite definido. Nesse caso ele nunca dispara o bloqueio — só acumula dados de uso para exibição.

---

## 2. Uso = 100% da métrica do celular (sem baseline)

O DollarBlock **não tem métrica própria de uso**. O uso comparado ao limite e exibido na UI é sempre 100% do que o Android (`UsageStatsManager`/`UsageEvents`) registrou desde a meia-noite local — não importa quando o app foi monitorado ou quando o limite foi configurado.

**Exemplo prático:** O usuário usou o Instagram por 1h hoje, sem monitoramento ativo. Ele então ativa o monitoramento e configura um limite de 50 min. Como o uso do dia (1h) já é maior que o limite (50 min), o Instagram é bloqueado na próxima vez que entrar em foreground — **imediatamente**, sem precisar de novo uso.

**Antes de E12 (histórico):** o sistema chegou a manter um *baseline* — o uso já feito antes de ativar o monitoramento era descontado do total, e o app só contava uso "a partir da ativação". Esse comportamento foi removido: hoje não há desconto nenhum, o uso do dia é sempre bruto e único (mesmo valor usado para bloqueio, exibido na UI e usado no Statistics). Ver `docs/specs/E12-uso-100-porcento-real.md`.

---

## 3. Medição de uso em tempo real

O sistema usa a API de **UsageEvents** do Android (eventos `ACTIVITY_RESUMED` / `ACTIVITY_PAUSED`), que registra cada sessão em foreground com timestamps precisos.

**Como funciona a contagem:**
- Soma todas as sessões fechadas desde a meia-noite local.
- Inclui a sessão em andamento (app ainda aberto) contada até o momento da verificação.
- Nenhum desconto é aplicado — o resultado é o **uso bruto do dia**, igual ao que o Android mediria.

**Por que isso importa:** A contagem é baseada em tempo de tela real, não em tempo de relógio. Se o usuário recebe uma ligação com o app em background, esse tempo não é contado.

---

## 4. Quando o bloqueio é disparado

O serviço de acessibilidade (`DollarBlockAccessibilityService`) monitora qual app está em foreground. Quando o usuário **abre** um app monitorado:

1. O serviço consulta o uso efetivo do dia.
2. Se `uso >= limite`, verifica se há janela de desbloqueio ativa (paga).
3. Se não há janela ativa → abre imediatamente a `BlockActivity` (tela de bloqueio).
4. Se há janela ativa → entra em modo de polling para bloquear quando a janela expirar.

**Enquanto o app já está aberto (polling adaptativo):**
- O serviço verifica o uso periodicamente com intervalo entre **3 e 30 segundos**, proporcional ao tempo restante para atingir o limite.
- Quando o limite é atingido, o bloqueio é disparado na próxima iteração.
- O bloqueio só ocorre visualmente se o app ainda estiver em foreground no momento da verificação.

**Reasserção do bloqueio:**
- Ao abrir a `BlockActivity`, o sistema aguarda **500 ms** e reabre a tela de bloqueio caso o app bloqueado tente se recolocar em foreground. Isso cobre o caso em que o app alvo resiste ao bloqueio na inicialização.

---

## 5. Tela de bloqueio

Quando o bloqueio é disparado, o usuário vê a `BlockActivity` (estética de fatura/recibo, carimbo "BLOQUEADO") com:
- Nome do app bloqueado.
- Botão de pagamento via **Google Pay** — preço do "passe do dia" (ver §6).
- Botão "Ir para Home" (fecha a tela de bloqueio e vai para a tela inicial do celular).
- Em builds de debug: botão "Simular pagamento" (libera sem cobrar).

O usuário **não pode voltar** para o app bloqueado pressionando o botão de voltar — o back button redireciona para a Home do celular.

---

## 6. Desbloqueio pago — "passe do dia"

### Fluxo de pagamento

1. Usuário toca em "Pagar com Google Pay" na tela de bloqueio.
2. O Google Pay Sheet abre e o usuário confirma o pagamento.
3. O token é enviado ao backend AWS (Lambda + Stripe) para cobrança real.
4. Se a resposta for `status == "succeeded"`, o desbloqueio é concedido.
5. O app é aberto automaticamente e o evento de desbloqueio é registrado no histórico.

### Valores e configurações atuais

| Item | Valor atual |
|---|---|
| Preço do passe do dia | **R$ 1,00** (valor exibido; preço real é resolvido via `GET /pricing` com fallback local — ver `docs/specs/E11-preco-dinamico.md`) |
| Duração da liberação | **Até a meia-noite local** (wall-clock, não tempo de uso) |
| Escopo da liberação | Apenas o app pago (não libera outros apps monitorados) |
| Método de pagamento | Google Pay |
| Ambiente | Teste (Stripe test mode) |
| Cartões aceitos | Visa, Mastercard, Amex |

### O que significa "passe do dia"

O passe do dia **não é medido em tempo de uso**. Ao pagar, o app fica liberado até a **meia-noite local do dia do pagamento** (`BlockPreferences.grantUnlockForToday` grava `unlockUntilMs` = início do dia seguinte). O serviço de acessibilidade só compara `now < unlockUntilMs` — não há contagem de minutos de uso dentro da janela.

**Exemplo:** O usuário paga às 14h00 pelo Instagram. Pode usar o Instagram livremente pelo resto do dia — o limite configurado não volta a valer até o novo dia começar à meia-noite.

**Máximo 1 pagamento por app por dia:** como o passe já libera até a meia-noite, pagar de novo pelo mesmo app no mesmo dia não é necessário nem oferecido (o app simplesmente não bloqueia mais).

### O que acontece quando o passe expira

Na virada da meia-noite local:
- O grant expira automaticamente (comparação wall-clock).
- O uso do novo dia começa do zero (uso bruto, sem baseline — ver §2).
- Se o limite configurado continuar valendo, o app volta a bloquear assim que o uso do novo dia atingir o limite.

---

## 7. Edge cases de virada de dia (meia-noite)

### Contagem de uso

O uso é contado **desde a meia-noite local do dispositivo**. Ao virar o dia:
- O uso do dia anterior para de contar.
- O uso do novo dia começa do zero — sem nenhum desconto ou baseline (ver §2).

### Bloqueio e virada de dia

Se o usuário atingiu o limite e foi bloqueado às 23h55, às 00h01 o uso do novo dia é zero. Se ele abrir o app bloqueado após a meia-noite, o serviço de acessibilidade recalcula o uso com base no novo dia e **não bloqueia** (uso = 0, que é menor que qualquer limite). O "débito" do dia anterior não é transportado.

### Passe do dia e virada de dia

O passe do dia é concedido com um timestamp absoluto de expiração (`unlockUntilMs` = meia-noite local do dia do pagamento). Ele **não atravessa a virada do dia** por construção: se o usuário pagou às 23h57, o passe expira minutos depois, à meia-noite, e o limite normal volta a valer no novo dia (sujeito a novo bloqueio se o uso do novo dia atingir o limite).

---

## 8. Cenários combinados: limite + pagamento

### Cenário: limite de 10 min, usuário paga o passe do dia

1. Usuário define limite de 10 min para o Instagram.
2. Usa o Instagram por 10 minutos (uso bruto do dia) → bloqueio disparado.
3. Paga o passe do dia (R$ 1,00) → Instagram liberado até a meia-noite local.
4. Usuário pode usar o Instagram livremente pelo resto do dia — **sem novo bloqueio**.
5. À meia-noite, o passe expira e o uso do novo dia começa do zero; o limite de 10 min volta a valer normalmente.

**O limite de 10 min não é "estendido" em minutos.** O pagamento não soma tempo ao limite — ele suspende o bloqueio daquele app até o fim do dia local.

### Cenário: usuário está com app aberto quando limite é atingido

1. Usuário está com o Instagram aberto.
2. O polling detecta que o uso atingiu o limite.
3. A tela de bloqueio é aberta **sobre** o Instagram (o Instagram vai para background).
4. O usuário não consegue voltar ao Instagram pelo botão de voltar.

### Cenário: usuário abre o app antes do limite mas fecha a tela de bloqueio

1. App entra em foreground → ainda não atingiu o limite → polling inicia.
2. Limite atingido enquanto app está em foreground → bloqueio disparado.
3. Usuário toca "Ir para Home" → vai para a tela inicial.
4. Se o usuário tentar abrir o app novamente, o bloqueio é disparado imediatamente na abertura.

### Cenário: monitoramento desativado enquanto app está bloqueado

1. App X está bloqueado.
2. O usuário abre o DollarBlock e desativa o monitoramento do app X.
3. Na próxima vez que abrir o app X, o serviço verifica `isMonitored == false` e não bloqueia.
4. A janela de desbloqueio eventualmente existente é ignorada (monitoramento desativado).

### Cenário: limite removido enquanto app está bloqueado

1. App X está bloqueado (atingiu o limite).
2. O usuário abre o DollarBlock e remove o limite (seta para null).
3. Na próxima vez que abrir o app X, o serviço verifica `dailyLimitMinutes == null` e não bloqueia.

---

## 9. Deduplicação de eventos de bloqueio

Para evitar spam no histórico, o sistema não registra múltiplos eventos de bloqueio para o mesmo app em menos de **5 segundos**. Se o serviço disparar a reasserção do bloqueio (500 ms depois) e o bloqueio for o mesmo app, apenas um evento é gravado no histórico.

---

## 10. Persistência dos dados

| Dado | Onde fica | Reset |
|---|---|---|
| Limite diário configurado | Room (banco local) | Manual pelo usuário |
| Uso diário acumulado (bruto, sem baseline) | Room (banco local) | Novo dia começa do zero via nova linha |
| Passe do dia ativo (`unlockUntilMs`) | DataStore (preferências) | Automático à meia-noite local; manual via botão debug |
| Histórico de bloqueios e desbloqueios | Room (banco local) | Não há limpeza automática |

---

## 11. Limitações e comportamentos conhecidos

- **Google Pay indisponível:** Se o dispositivo não tiver Google Pay configurado, o botão de pagamento não aparece. A tela de bloqueio mostra uma mensagem informando que o pagamento não está disponível. O usuário só pode ir para a Home — não há alternativa de desbloqueio.
- **Sem permissão de Usage Access:** Sem essa permissão, o uso é sempre zero — o limite nunca é atingido e o app nunca é bloqueado. O onboarding guia o usuário para conceder essa permissão.
- **Sem serviço de acessibilidade ativo:** Sem essa permissão, o serviço não detecta foreground — o bloqueio nunca é disparado, mesmo que o uso tenha ultrapassado o limite.
- **Apps de sistema:** O serviço filtra overlays, teclado virtual e painéis de notificação — apenas apps com ícone de launcher (apps "reais") são monitorados e bloqueados.
- **Pagamento em modo teste:** Atualmente o Stripe está em `test mode` — cobranças reais não acontecem. Cartões de teste devem ser usados. Produção ainda não foi ativada.
