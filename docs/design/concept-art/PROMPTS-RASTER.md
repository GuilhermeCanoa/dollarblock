# Concept art — prompts para geração raster (v2: ilustrativo, amigável, claro)

Tela de teste: **onboarding, página 1** — *"Time is money. Yours is leaking."*

> **v2** — a v1 saiu realista e sombria (pedia "3D premium", "moody", "surreal").
> Agora: ilustração **flat de vetor**, formas arredondadas, cores chapadas, luminosa e
> amigável. O humor seco fica no texto do app, não no clima da imagem.

## Como usar

1. **Anexe sempre 1 ou 2 imagens** junto com o prompt:
   - **(obrigatório)** o **print da tela do app** "Time is money. Yours is leaking."
   - **(recomendado)** um dos previews em `previews/` (`E-recibo.png` ou `B-editorial.png`)
     como referência de **estilo de traço e acabamento**.
2. Cole **BASE + uma direção** na mesma mensagem. Uma direção por vez.
3. Itere no mesmo chat: "mesma cena, mas [ajuste]" — não reescreva o prompt.
4. Salve as escolhidas em `docs/design/concept-art/raster/` (`C-porquinho.png`, etc.).

## Bloco BASE (colar sempre)

```
I'm attaching a screenshot of an onboarding screen from my Android app, DollarBlock
(a screen-time limiter where going over your daily limit costs a small real fee). Design
a NEW illustration to replace the empty top area of that screen. Keep the screenshot's
layout in mind: the illustration occupies the top ~55% of a vertical 9:16 canvas
(1080x1920); the bottom ~45% stays EMPTY background (the app draws its own title and
body text there). Deliver the full 9:16 image with the illustration only, no text.

Concept of the screen: "Time is money. Yours is leaking." — a visual metaphor for time
slipping away as coins.

STYLE (important):
- Flat vector illustration, like a modern fintech/education app (Duolingo, Nubank,
  Headspace energy): rounded shapes, simple geometry, clean confident outlines or no
  outlines, solid fills with at most one soft shade tone per shape.
- Friendly, bright, optimistic-neutral mood. NOT dark, NOT gloomy, NOT dramatic.
- NOT realistic, NOT photographic, NOT 3D-rendered, no heavy shadows, no film grain,
  no vignette, no lens effects.
- Simple and legible at a small size. Few elements, lots of breathing room.

PALETTE (match the app screenshot): background deep green #0A241D, surface greens
#103129 and #15453A, main accent emerald #00E676, secondary green #00A86B, highlight mint
#64FFDA, coins/money amber #FFC24B (with a darker amber #B9801C for edges), cream
#E6F2EC for paper and light shapes. Use red #FF5252 only for tiny details. Characters
and objects should be LIGHT and COLORFUL so they pop against the dark green background.

HARD RULES: no text, letters, numbers or currency symbols anywhere; no logos; no
watermarks; no UI elements; no real banknote designs or portraits.
```

## Direções

### C — Porquinho (mascote amigável)  ⭐ prioridade

```
Scene: a cute, chubby piggy bank mascot in soft pink-cream (#F7C6C0 with #E8A29A
shading) with a golden coin slot on its back, little round ears, a curly tail, stubby
legs. Big friendly eyes and a small gentle smile — warm, approachable, a bit
bewildered, like it just noticed something. A tiny crack on its side; a thin stream of
golden coins trickles out and falls to a small pile of coins on the ground. One coin
mid-air with a small motion arc. A simple soft circle of lighter green behind it as a
spotlight. Flat vector, rounded, charming, mascot-quality character design that could
work as a recurring app character in many poses.
```

### A — Gerente simpático (personagem)

```
Scene: a friendly little bank-manager character, round and compact, in a navy-green
suit with a cream shirt, round glasses, a neat mustache, a calm slightly amused
expression (kind, composed, not stern). He stands next to a big round wall clock whose
bottom edge leaks a trickle of golden coins into a small bucket at his feet; he holds
the invoice paper in one hand like a menu. Flat vector character art with thick soft
shapes, expressive but minimal face, a character that could be reused across screens.
```

### D — Moedinhas com carinha (elenco)

```
Scene: a small cheerful group of 3 round golden coins and 1 green banknote-shaped
character walking in a line toward the right edge of a rounded-rectangle phone shape,
waving goodbye with tiny arms. Minimal faces: two dot eyes and a small smile. The last
coin has already stepped off the edge and is tumbling down playfully. Light, funny,
gentle. Flat vector, paper-cut feeling, bold simple shapes.
```

### L — Ampulheta amigável (objeto)

```
Scene: a cute, rounded hourglass standing upright, cream frame with emerald glass. Golden
coins fall instead of sand from the top bulb to the bottom bulb, and a few coins spill
out through a small crack at the bottom and bounce onto the ground. Two small dot eyes
and a tiny worried-but-sweet expression on the glass. Flat vector, clear and cheerful,
centered, simple background circle.
```

### B-pro — Linha editorial (versão profissional do preview)

```
Use the attached preview image as the STYLE reference (cream line-art cartoon on dark
green, editorial/New Yorker-meets-fintech look) but redraw it more polished and
professional: consistent line weight, refined proportions, balanced composition, subtle
flat color fills in the palette.
Scene: a man in a suit and bowler hat holding a bucket under a wall clock that drips
golden coins; a small pile of coins beside him. Dry, gentle humor; calm expression.
Keep it flat vector, bright linework on dark green, no gradients, no realism.
```

## Teste de escala (decisivo)

Quando uma direção passar, peça **no mesmo chat**, anexando o print da tela de bloqueio
(`BlockActivity`): *"mesmo personagem/estilo, agora para a tela de bloqueio: tempo
esgotado, cadeado, conta chegou"*. Estilo que não replica em outra cena não serve de
styleguide.

## Critérios de avaliação

| Direção | Amigável/claro? | Legível pequeno? | Anima bem? | Escala para 10+ telas? | Nota |
|---|---|---|---|---|---|
| C Porquinho | | | | | |
| A Gerente | | | | | |
| D Moedinhas | | | | | |
| L Ampulheta | | | | | |
| B-pro Linha | | | | | |

> **Atenção de marca:** o `MANIFESTO.md` diz "Não tem mascote. Tem uma conta aberta."
> Se o porquinho (ou o gerente) vencer, o manifesto precisa ser atualizado junto — dá para
> manter o tom seco: o mascote é amigável, o texto continua cobrando a fatura.
