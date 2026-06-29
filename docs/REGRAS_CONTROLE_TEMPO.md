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

## 2. Baseline de uso

Quando o usuário **ativa o monitoramento** de um app, o sistema captura o tempo que o app já tinha sido usado naquele dia até aquele momento e salva como *baseline*. A partir daí, o DollarBlock desconta esse baseline e conta apenas o uso **a partir da ativação**.

**Exemplo prático:** O usuário usou o Instagram por 45 min antes de ativar o monitoramento. O limite é de 30 min. O DollarBlock zera o contador — o usuário terá 30 min reais de uso monitorado a partir daquele instante.

**Edge case — ativação repetida:** Se o usuário desativar e reativar o monitoramento de um app que já tinha baseline, o baseline antigo é mantido (não captura novamente). O baseline só é capturado na **primeira ativação** do app no sistema.

---

## 3. Medição de uso em tempo real

O sistema usa a API de **UsageEvents** do Android (eventos `ACTIVITY_RESUMED` / `ACTIVITY_PAUSED`), que registra cada sessão em foreground com timestamps precisos.

**Como funciona a contagem:**
- Soma todas as sessões fechadas desde a meia-noite local.
- Inclui a sessão em andamento (app ainda aberto) contada até o momento da verificação.
- Subtrai o baseline capturado na ativação.
- O resultado é o **uso efetivo monitorado** do dia.

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

Quando o bloqueio é disparado, o usuário vê a `BlockActivity` com:
- Nome do app bloqueado.
- Botão de pagamento via **Google Pay** (R$ 4,99).
- Botão "Ir para Home" (fecha a tela de bloqueio e vai para a tela inicial do celular).
- Em builds de debug: botão "Simular pagamento" (libera sem cobrar).

O usuário **não pode voltar** para o app bloqueado pressionando o botão de voltar — o back button redireciona para a Home do celular.

---

## 6. Desbloqueio pago

### Fluxo de pagamento

1. Usuário toca em "Pagar com Google Pay" na tela de bloqueio.
2. O Google Pay Sheet abre e o usuário confirma o pagamento.
3. O token é enviado ao backend AWS (Lambda + Stripe) para cobrança real.
4. Se a resposta for `status == "succeeded"`, o desbloqueio é concedido.
5. O app é aberto automaticamente e o evento de desbloqueio é registrado no histórico.

### Valores e configurações atuais

| Item | Valor atual |
|---|---|
| Preço por desbloqueio | **R$ 4,99** |
| Duração da janela de desbloqueio | **5 minutos de uso real** |
| Método de pagamento | Google Pay |
| Ambiente | Teste (Stripe test mode) |
| Cartões aceitos | Visa, Mastercard, Amex |

### O que significa "5 minutos de uso real"

A janela de desbloqueio é medida em **tempo de foreground efetivo** — não em tempo de relógio. O sistema registra o instante do pagamento e, a partir daí, usa `UsageStats` para acumular quantos minutos o usuário realmente ficou com o app aberto.

**Exemplo:** O usuário paga às 14h00. Usa o app por 3 minutos, fecha, responde uma mensagem, reabre o app. O contador acumula apenas o tempo em que o app estava na tela. A janela só expira quando o somatório de uso real atingir 5 minutos.

### O que acontece quando a janela expira

Quando os 5 minutos de uso real se esgotam:
- Na próxima vez que o app entrar em foreground (ou na próxima iteração do polling se já estiver aberto), o bloqueio é disparado novamente.
- O usuário precisa pagar novamente para ter mais 5 minutos.
- Não há acumulação de janelas: pagar duas vezes não dá 10 minutos — cada pagamento cria uma nova janela de 5 minutos, substituindo a anterior.

---

## 7. Edge cases de virada de dia (meia-noite)

### Contagem de uso

O uso é contado **desde a meia-noite local do dispositivo**. Ao virar o dia:
- O uso do dia anterior para de contar.
- O uso do novo dia começa do zero (sem baseline acumulado do dia anterior).
- O baseline capturado na ativação do monitoramento é permanente e se aplica todos os dias.

**Atenção:** O baseline é subtraído todos os dias, mesmo que o usuário já tenha "zerado" o uso no dia anterior. Isso é um edge case atual — se o usuário usou 0 minutos ontem e tem 45 min de baseline, hoje o contador também começará zerado (0 − 45 = 0, pois há um `coerceAtLeast(0L)`).

### Bloqueio e virada de dia

Se o usuário atingiu o limite e foi bloqueado às 23h55, às 00h01 o uso do novo dia é zero. Se ele abrir o app bloqueado após a meia-noite, o serviço de acessibilidade recalcula o uso com base no novo dia e **não bloqueia** (uso = 0, que é menor que qualquer limite). O "débito" do dia anterior não é transportado.

### Janela de desbloqueio e virada de dia

O grant de desbloqueio é armazenado com timestamp absoluto (`grantedAtMs`). Não há lógica de reset por dia — se o usuário pagou às 23h57 e usou o app por 2 minutos, ainda tem 3 minutos de janela disponíveis após a meia-noite. A janela continua até os 5 minutos de uso real serem esgotados, independente da virada do dia.

---

## 8. Cenários combinados: limite + pagamento

### Cenário: limite de 10 min, usuário paga por 5 min extras

1. Usuário define limite de 10 min para o Instagram.
2. Usa o Instagram por 10 minutos → bloqueio disparado.
3. Paga R$ 4,99 → recebe 5 minutos de uso real.
4. Usa o Instagram por 5 minutos dentro da janela → bloqueio disparado novamente.
5. Para continuar, precisa pagar mais R$ 4,99.

**O limite de 10 min não é estendido para 15 min.** O pagamento concede uma *janela temporária* separada do limite configurado. Quando a janela expira, o limite original volta a vigorar.

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
| Baseline de uso | Room (banco local) | Nunca automático |
| Uso diário acumulado | Room (banco local) | Novo dia começa do zero via nova linha |
| Janela de desbloqueio ativa | DataStore (preferências) | Automático quando expira; manual via botão debug |
| Histórico de bloqueios e desbloqueios | Room (banco local) | Não há limpeza automática |

---

## 11. Limitações e comportamentos conhecidos

- **Google Pay indisponível:** Se o dispositivo não tiver Google Pay configurado, o botão de pagamento não aparece. A tela de bloqueio mostra uma mensagem informando que o pagamento não está disponível. O usuário só pode ir para a Home — não há alternativa de desbloqueio.
- **Sem permissão de Usage Access:** Sem essa permissão, o uso é sempre zero — o limite nunca é atingido e o app nunca é bloqueado. O onboarding guia o usuário para conceder essa permissão.
- **Sem serviço de acessibilidade ativo:** Sem essa permissão, o serviço não detecta foreground — o bloqueio nunca é disparado, mesmo que o uso tenha ultrapassado o limite.
- **Apps de sistema:** O serviço filtra overlays, teclado virtual e painéis de notificação — apenas apps com ícone de launcher (apps "reais") são monitorados e bloqueados.
- **Pagamento em modo teste:** Atualmente o Stripe está em `test mode` — cobranças reais não acontecem. Cartões de teste devem ser usados. Produção ainda não foi ativada.
