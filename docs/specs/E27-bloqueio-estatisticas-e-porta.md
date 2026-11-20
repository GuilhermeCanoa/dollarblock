# Spec: Tela de bloqueio com estatística + saída que passa pelo app

**Status:** done
**Épico:** E27
**Data de criação:** 2026-11-20
**Última atualização:** 2026-11-20

---

## Contexto

O usuário quase nunca abre o app principal: o contato com o DollarBlock acontece na tela de
bloqueio. Duas mudanças aproveitam esse momento:

1. **Estatística na tela de bloqueio**: uma linha de dado real ("7ª tentativa de abrir o
   Instagram hoje", "27 min acima do contrato") para a tela trazer informação, não só o preço.
2. **Saída pelo app**: recusar a fatura ("Ficar com meu dinheiro") leva para dentro do app
   com um parabéns, e uma portinha ("Sair") continua levando ao launcher.

## Requisitos

- R1: A tela mostra **uma** estatística, a mesma nas 3 cenas, numa linha logo acima dos
  botões. Ela é escolhida pela função pura `pickBlockStat`:
  - prioridade: sequência ≥2 dias sem passe pago > tentativas ≥3 hoje > excedente grande
    (≥15 min e ≥50% do limite) > primeira tentativa do dia;
  - se não houver nenhuma prioritária, sorteia entre uso/limite, contagem regressiva até a
    meia-noite e economia do mês;
  - nunca repete o tipo mostrado na abertura anterior (exceto quando é a única opção). A
    re-afirmação do serviço (mesmo app, <2 s) mantém o tipo.
- R2: Os dados são lidos **depois** do primeiro frame (ADR-001). A linha tem altura reservada
  e aparece com fade, sem empurrar os botões.
- R3 (P3): tocar na estatística abre o app: economia do mês → Home; as outras → Extrato
  com o dia de hoje aberto.
- R4 (P1): "Ficar com meu dinheiro" abre o app (Home) com o parabéns quando o último
  parabéns foi há ≥ `CelebrationPolicy.INTERVAL_MS` (2 h, P4); senão vai ao launcher.
- R5 (P1): ícone de porta "Sair" no canto superior direito, sempre leva ao launcher. Voltar
  também continua indo ao launcher.
- R6 (P2): o parabéns varia o título e a frase (sorteio entre várias, sem repetir a anterior),
  mostra o valor guardado no mês com contagem animada e o nº de faturas recusadas. Oferece
  "Ver extrato". Tom da casa: sem emoji, sem motivação, precisão contábil.
- R7: Strings em EN, PT e HI.

## Tarefas

- [x] T1: `feature/blocking/stats/BlockStat.kt`: tipos, `pickBlockStat`, rotação em memória, sequência
- [x] T2: `BlockStatsLoader`: lê tentativas, uso real, limite, sequência do app e carteira do mês
- [x] T3: `BlockStatLine` na tela de bloqueio + clique → app
- [x] T4: portinha "Sair" + "Ficar com meu dinheiro" → app/launcher conforme a janela de 2 h
- [x] T5: `AppLaunchRequest` (extras do intent) + MainActivity abre aba / dia / parabéns
- [x] T6: `feature/celebration`: preferências, política, ViewModel, diálogo com variações
- [x] T7: testes JVM (`BlockStatTest`, `CelebrationPolicyTest`)
- [x] T8: CHANGELOG

## Critérios de aceite

- CA1: `./gradlew :app:testDebugUnitTest` verde.
- CA2: Em 3 bloqueios seguidos do mesmo app, a estatística não se repete em sequência.
- CA3: Recusar → abre a Home com o parabéns; recusar de novo em <2 h → launcher.
- CA4: "Sair" → launcher sempre.

## Notas / Decisões

- A estatística ficou **fora do recibo**: na cena A o recibo completo só aparece numa sheet,
  e nas cenas B e C ele está girado/animado, o que atrapalharia o toque (P3).
- O instante do último parabéns é gravado quando o parabéns **aparece** (MainActivity), não
  quando o botão é tocado: se o app não chegar a abrir, o próximo ainda tem direito.
- "Economia do mês" usa a `MonthWallet` da Home; o bloqueio de hoje já conta como recusado
  (par app/dia sem pagamento), então o valor mostrado já inclui esta recusa.
