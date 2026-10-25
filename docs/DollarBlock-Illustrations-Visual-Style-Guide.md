# DollarBlock — Guia visual para ilustrações

> **Para quê:** referência obrigatória antes de gerar qualquer ilustração nova do DollarBlock (telas do app e material da Play Store). Vale tanto para quem escreve o prompt quanto para quem aprova o resultado.
>
> **Regra de prioridade:** `docs/art/onb_hero_mockup.webp` é a **referência absoluta** de estilo e vence este texto. Se a descrição aqui contradisser a referência absoluta, ela manda — e este guia deve ser corrigido.

## 1. Imagens de referência

Ficam em `docs/art/`. Anexe sempre `onb_hero_scene.webp` (o mesmo desenho, sem a tela em volta) ao gerar; a praia só serve para ver a variante diurna.

| Arquivo | Papel | O que ensina | O que **não** copiar |
|---|---|---|---|
| `docs/art/onb_hero_mockup.webp` | **Referência absoluta** — personagem, paleta, acabamento, iluminação | Escudo com `$` e óculos, poltrona, lixeira com ícones, luminária, janela, tênis | É um *mockup da tela inteira* (status bar, título, botão, texto "taxímetro"). Para gerar, use o recorte abaixo |
| `docs/art/onb_hero_scene.webp` | **Recorte limpo** da referência (941×632, sem perda; só a ilustração) | Mesmos pixels do mockup, sem status bar, título, texto e botões. Reaproveitável em outras telas. O fio da luminária sai cortado no topo e o piso sangra nas laterais: ambos se fundem ao fundo escuro | Não é o asset do app (ver nota abaixo) |
| `docs/art/onb_lounge_source.webp` | Variante **diurna** (praia), banner 1672×471 | Céu/mar em degradê verde, guarda-sol com letreiro, sombras na areia | **Personagem fora do modelo:** de costas, cabeça redonda sem escudo/`$`/óculos. Cadeira com listras vermelho/laranja/azul quebra a paleta. Serve para clima e composição, não para o personagem |

**Não confundir com o asset do app:** `app/src/main/res/drawable-nodpi/onb_hero.jpg` é uma versão *editada* do mockup (luminária maior, lixeira menor e deslocada, natureza na janela, mais 130 px de teto esticado), gerada por `scripts/build-onb-hero.ps1`. As animações em `OnboardingHero.kt` dependem dela; o recorte limpo não as afeta.

Cada imagem nova deve contar uma situação nova no mesmo universo — não repetir as cenas de exemplo.

## 2. Fluxo de trabalho

1. Leia este guia e abra `onb_hero_scene.webp`.
2. Preencha o briefing da seção 11 (só isso muda a cada imagem).
3. Gere anexando `onb_hero_scene.webp` ao prompt, pedindo **somente a arte**: sem status bar, botões, paginação ou texto de interface.
4. Revise com o checklist da seção 12. Se o personagem sair fora do modelo, regenere — não tente consertar com prompt longo.
5. Salve a aprovada em `docs/art/` (nome `<tela>_<assunto>.webp`; fonte maior que o necessário, o app reduz).
6. Se a arte vai para o app com animação por cima ou recorte, siga as seções 8 e 9 e documente o script em `scripts/README.md`.

## 3. Direção de arte

Ilustração digital estilizada com **volume 3D pintado** (2.5D): formas sólidas e legíveis, degradês suaves, sombras profundas e luz dramática vinda de poucas fontes. Pense em ilustração de jogo mobile premium com paleta de fintech escura.

- Silhuetas fortes, poucos detalhes finos, leitura imediata em tela pequena.
- Contraste por luz e material; contornos escuros só onde a forma pede (roupa, objetos), nunca um traço preto uniforme em tudo.
- Sombras escuras mas com informação — nada de massa preta.

Não deve parecer: ícone vetorial plano, foto, render CAD, desenho infantil, arte genérica de IA.

**Princípios:** reconhecer o personagem de imediato · uma ideia por imagem · humor seco, nunca infantil · cenário a serviço da ideia · identidade própria (nunca copiar mascote ou arte de outra marca).

## 4. Personagem: o escudo DollarBlock

Escudo heráldico antropomórfico, guardião calmo e meticuloso do tempo e do dinheiro do usuário.

**Corpo**
- Cabeça/tronco é o escudo vertical: topo largo e arredondado, laterais curvas, ponta inferior afunilada.
- Placas concêntricas em camadas (moldura externa petróleo, interna esmeralda/lima), bordas chanfradas, acabamento esmaltado.
- Braços e pernas humanoides finos, vestidos de preto/verde-petróleo, com dobras discretas; mãos escuras com dedos legíveis.
- Tênis streetwear escuros com sola clara e detalhes verde-limão.
- Poses confiantes e relaxadas: sentado, de pé, andando, braços cruzados, apontando, interagindo com objetos.

**Rosto — identificadores obrigatórios**
- **Óculos escuros pretos** de armação robusta, lentes com reflexo discreto, ocupando a metade superior do escudo.
- **Cifrão `$` grande** no centro/inferior do escudo, em relevo ou embutido, sempre legível.
- Sem olhos, boca ou nariz desenhados: a expressão vem da inclinação do escudo, das sobrancelhas (quando aparecem acima dos óculos) e da postura.
- Expressão padrão: sereno, confiante, ligeiramente cínico. Alternativas: concentração, ceticismo, desaprovação contida, satisfação tranquila.

**Atuação:** auditor irônico, não herói. A graça está na situação (analisar um recibo minúsculo com seriedade), não em caretas ou euforia.

| Tom | Linguagem corporal |
|---|---|
| Relaxado, no controle | Recostado, mãos atrás da cabeça, pernas cruzadas |
| Analítico | Inclinado para o objeto, mãos em ação |
| Cético | Cabeça inclinada, braços cruzados, olhando o celular |
| Protetor | Postura firme perto do que protege |
| Disciplinado | Gesto preciso: ajustar timer, revisar lista |
| Satisfeito | Tranquilo, sem comemorar |

Peso, articulações e contato com objetos sempre plausíveis.

## 5. Paleta

Valores orientativos; **a referência absoluta prevalece**. As referências são um pouco mais escuras e menos saturadas que os tons "elétricos" — não estoure o verde.

| Nome | HEX | Uso |
|---|---|---|
| Near Black Green | `#02100D` | Sombras de contato, quase-preto |
| Midnight Forest | `#031B15` | Fundo principal (referência real: `#051F16`) |
| Deep Pine | `#062820` | Planos secundários |
| Petrol Green | `#0B4033` | Roupas, móveis, moldura externa do escudo (referência real: `#1B4829`) |
| Emerald Green | `#00A968` | Superfícies iluminadas |
| Electric Green | `#00E88A` | Acentos luminosos pontuais |
| Acid Lime | `#B7F34A` | Luz de contorno, filetes, luz da luminária/sol |
| Mint Highlight | `#75FFC0` | Highlights localizados (referência real: `#7AD4A1`) |
| Cool Gray | `#A9C0B8` | Neutros secundários |

**Distribuição (aprox.):** 55–70% fundo/sombras escuros · 15–25% superfícies escuras de personagem/móveis · 8–15% esmeralda iluminado · 2–5% acentos elétricos/lima.

**Regras**
- Sem fundo branco ou cinza-claro; sem roxo, azul neon, vermelho, laranja ou arco-íris como cor dominante.
- Verdes mais luminosos reservados ao personagem e ao ponto focal.
- Ícones de redes sociais **recoloridos** em verdes na arte estática. A cor oficial da marca só aparece na animação (ex.: o brilho que passa pelos ícones da lixeira), nunca na arte parada.
- Luz quente só em lima/amarelo-esverdeado (luminária, sol); nada de laranja.

### Variante diurna
Para cenas de dia (praia, janela aberta): céu e água em degradês de verde e verde-azulado, areia em verde-lima claro, sol amarelo-esverdeado. A regra de 55–70% escuro **não se aplica**, mas continua valendo: nada de cores fora da família verde (cadeira listrada colorida da referência é erro), o personagem mantém escudo, `$` e óculos, e ele continua sendo o ponto de maior contraste.

## 6. Materiais e luz

| Elemento | Acabamento |
|---|---|
| Moldura externa do escudo | Metal pintado petróleo, borda chanfrada |
| Placas internas | Esmalte esmeralda semibrilhante |
| Filetes | Verde-limão com brilho controlado |
| Cifrão | Relevo/inserto claro sobre fundo escuro, legível |
| Óculos | Preto brilhante, reflexo discreto |
| Roupa | Tecido escuro, dobras naturais |
| Tênis | Borracha/painéis escuros, sola clara |
| Móveis e objetos | Fosco ou semibrilhante, formas robustas e chanfradas |

**Luz (cena noturna/interior)**
1. **Key** suave, acima e de lado, que modela o escudo.
2. **Rim** esmeralda separando a silhueta do fundo.
3. **Acentos lima** em algumas bordas e superfícies superiores.
4. **Fill** fraco petróleo para manter informação nas sombras.
5. **Práticas** (luminária, janela, tela) justificam os reflexos.
6. **Oclusão e sombra de contato** onde o personagem toca móveis e objetos; sombra projetada no chão.

Cada fonte de luz atua na geometria — sem halo neon uniforme. Bloom, névoa e profundidade de campo: opcionais e sutis.

## 7. Cenário, objetos e texto

**Cenário:** escuro (ou diurno, ver 5), sofisticado, com volumes claros e detalhes seletivos. Cada objeto tem função narrativa; não preencher vazio com props.

Vocabulário visual compatível, alinhado ao tom do `MANIFESTO.md` ("gerente de banco do seu tempo"): poltrona/sofá, mesa baixa, luminária industrial, janela com skyline, livros e agenda, celular e notificação, cofre, moedas, **recibo, extrato, contrato, carimbo**, cadeado, barreira, ampulheta/cronômetro, lixeira com apps, guarda-sol e cooler (variante diurna).

**Ícones de apps:** pequenos objetos 3D com espessura, recoloridos para a paleta, podem ficar parcialmente escondidos; nunca mais chamativos que o personagem.

**Texto dentro da arte:** por padrão nenhum. Exceção aceita, como nas referências: **texto curto em português nos objetos** (caneca, lombada de livro, letreiro de guarda-sol), secundário, legível e curto (até ~3 palavras por objeto). Texto longo, pseudo-letras ou palavras ilegíveis → regenerar. Não escrever texto de interface (título, botão) na arte.

## 8. Formatos e composição por destino

| Destino | Formato | Observações |
|---|---|---|
| Onboarding / telas do app (padrão) | 9:16, 1080×1920 | Arte nos ~55% superiores; os ~45% de baixo ficam vazios (título, texto e botões são do app). Margem de segurança nas laterais |
| Banner de cena (ex.: Medição, praia) | Sob demanda; referência 1672×471 | Seguir o briefing; personagem num dos cantos, céu/mar livres |
| Play Store | Conforme `docs/play-store/README.md` | Seguir o tamanho exigido pela loja; mesmo estilo |

Hierarquia: personagem → objeto narrativo → cenário; primeiro plano, plano médio e fundo bem separados; silhueta legível reduzida.

**Arte que será animada ou recortada no app** (ver `scripts/README.md`):
- Elementos que o app anima por cima (ventilador, luz, vapor, brilho) devem ser objetos isolados e com posição estável; se mudar algo, as coordenadas em `OnboardingHero.kt` precisam ser conferidas.
- Para recorte por cor (`build-onb-lounge.ps1`), o primeiro plano precisa contrastar com um fundo de degradê simples (céu/mar/areia), sem elementos do fundo encostando na silhueta.

## 9. Animação

A arte é **estática por padrão** e o app anima por cima, em código (Compose `Canvas`, ou Lottie quando o movimento é mais rico). Exemplo vivo: `OnboardingHero.kt`. O que vale sempre:

**Tom do movimento**
- Lento e deadpan, como o resto da marca: ciclos longos (referência: ventilador 1,4 s por volta, vapor 2,8 s, brilho 5,2 s com descanso entre varreduras). Nada de pulos, quiques ou explosões.
- Cada movimento reforça a ideia da cena (luz quente na luminária, vapor do café, brilho nos ícones que estão indo para o lixo). Se não reforça, não anima.
- Poucos elementos em movimento ao mesmo tempo; o personagem é o último a se mover e nunca exagera.
- Loops sem emenda visível. Easing linear em rotações contínuas, suave em entradas e saídas.

**Obrigatório**
- Respeitar a escala de animações do sistema (`rememberAnimationsEnabled()`, em `OnboardingVisuals.kt`; escala 0 desliga): cada animação tem uma **pose parada** que funciona sozinha (ventilador num ângulo fixo, vapor a 30%, sem brilho).
- A arte estática precisa se sustentar sem a animação.
- Animações desenhadas em código usam as cores da paleta (seção 5). Luzes e vapor em tons claros do verde/lima, nunca branco puro nem cor nova.

**Como pedir a arte para ser animada**
- Elementos que vão se mover (pás, vapor, ponteiros, partes de recibo) saem **limpos e isolados**: sem texto, sombra ou reflexo colados neles. Se for desenhar em código, a arte base nem precisa conter o elemento.
- Fundo simples ao redor do que se move (sem detalhe atrás para esconder emenda).
- Os cantos e bordas da arte se dissolvem no fundo da tela (fade vertical, como em `HeroFadeMask`); não deixar elementos importantes nas faixas que somem.

**Acoplamento arte ↔ código**
- Toda constante de posição/tamanho em `px da arte` vive no `.kt` e depende de uma versão exata da imagem. Se a arte mudar (posição, escala, recorte), reconferir as constantes na mesma entrega.
- Registrar a ficha da animação (camadas animadas, coordenadas, script que gera a arte) em `scripts/README.md`.

## 10. Restrições

Não:
- Alterar a silhueta do escudo, remover/redesenhar o `$`, trocar óculos por olhos, inventar outro mascote.
- Mudar a paleta ou usar cyberpunk multicolorido; exagerar neon, bloom, partículas, fumaça, lens flare.
- Usar vetor plano como estilo principal, personagens humanos fotorrealistas ou aparência de brinquedo barato.
- Gerar mãos deformadas, membros duplicados, objetos flutuantes, interações impossíveis.
- Incluir componentes de UI quando o pedido é só arte.
- Iluminar tudo com a mesma intensidade ou preencher cada vazio com detalhe.
- Copiar personagens ou ilustrações de outras marcas.
- Usar vocabulário de taxímetro/táxi na narrativa visual (a marca abandonou a metáfora; veja o `MANIFESTO.md`).

## 11. Briefing da cena (único bloco que muda)

**CONCEITO:** [uma ideia por imagem]
**AÇÃO DO PERSONAGEM:** [pose, expressão, olhar, interação]
**OBJETO NARRATIVO:** [objeto central e sua função]
**AMBIENTE:** [local + poucos elementos; noturno ou diurno]
**TOM:** [confiança tranquila / ironia seca / disciplina / desaprovação contida / proteção / satisfação discreta]
**COMPOSIÇÃO:** [câmera, posição, profundidade, espaço negativo]
**SAÍDA:** [proporção, resolução, fundo/recorte, destino, texto permitido nos objetos]
**ANIMAÇÃO:** [nenhuma, ou quais elementos se movem, como (Canvas/Lottie), ciclo e pose parada]

## 12. Checklist de aprovação

- [ ] Escudo com silhueta e placas preservadas
- [ ] `$` e óculos legíveis; sem olhos/boca desenhados
- [ ] Proporções, membros e tênis coerentes com a referência
- [ ] Paleta dentro da família verde (sem listras ou cores fora dela)
- [ ] Luz coerente (key + rim, ou equivalente diurno); sombras com informação
- [ ] Contato físico com o cenário (sombra, oclusão)
- [ ] Hierarquia clara em tamanho mobile
- [ ] Objetos com função narrativa; texto só curto, em português, legível
- [ ] Sem UI, sem defeitos anatômicos
- [ ] Enquadramento certo para o destino (espaço livre onde o app precisa)
- [ ] Se animada: elementos móveis isolados, pose parada definida, constantes do `.kt` conferidas
- [ ] Comparada lado a lado com `onb_hero_mockup.webp`

**A identidade permanece constante; o que muda é a história contada pela imagem.**
