---
name: dollarblock-art
description: Fluxo completo de ilustração do DollarBlock — do briefing e prompt de geração (com a referência anexada) até revisão, pós-processamento da imagem e animação em Compose/Lottie. Use quando o usuário pedir uma nova ilustração, cena, arte de tela, "imagem para o onboarding/Medição/Tranca", ajustar uma arte existente, ou animar uma ilustração (ventilador, vapor, brilho, luz). Fonte de verdade das regras visuais é docs/DollarBlock-Illustrations-Visual-Style-Guide.md.
---

# dollarblock-art — ilustração e animação no DollarBlock

Esta skill **orquestra o fluxo**; ela não repete regras de estilo. Antes de qualquer coisa,
leia **inteiro** `docs/DollarBlock-Illustrations-Visual-Style-Guide.md` (personagem, paleta,
luz, formatos, animação, restrições, checklist). Se algo aqui contradisser o guia, o guia
vale e esta skill deve ser corrigida.

Referência visual absoluta: `docs/art/onb_hero_mockup.webp`. Para anexar à geração, use o
recorte limpo `docs/art/onb_hero_scene.webp`.

**Limite importante:** Claude não gera imagens. Quem gera é o usuário, em ferramenta externa
(com a referência anexada). Claude escreve o briefing e o prompt, revisa o resultado,
processa o arquivo e implementa a animação.

## Fluxo

### 1. Briefing (confirmar com o usuário só o que faltar)
Preencha o bloco da seção 11 do guia: conceito (uma ideia), ação do personagem, objeto
narrativo, ambiente (noturno/diurno), tom, composição, saída e **animação**. Antes de
criar texto de objeto ou legenda, consulte o tom em `MANIFESTO.md` ("Como falamos").
Se a tela de destino já existir, leia o composable dela para saber o espaço livre real
(onde ficam título, texto, botões) e o aspecto da arte.

### 2. Prompt de geração
Monte um prompt em inglês, curto, com: identidade fixa (escudo heráldico, `$` grande,
óculos escuros, sem olhos/boca, tênis, paleta verde-escura — por nome, ver seção 5 do guia),
o briefing da cena, o formato de saída e a lista de "não" mais relevante (guia, seção 10).
Lembre o usuário de **anexar `onb_hero_scene.webp`** e pedir "somente a arte, sem UI".
Se houver animação em código, peça os elementos móveis isolados (guia, seção 9).
Entregue o prompt em um bloco de código para copiar.

### 3. Revisão
Quando o usuário trouxer a imagem, abra-a com Read e passe o **checklist da seção 12** item
por item, apontando o que falhou. Erros de personagem (silhueta, `$`, óculos, olhos) →
regenerar, não remendar. Compare lado a lado com `onb_hero_scene.webp`.

### 4. Pós-processamento
- Salve a aprovada em `docs/art/<tela>_<assunto>.webp` (fonte maior que o necessário).
- Se o app usa uma versão processada (recorte, edição, separação de camadas), faça isso por
  **script versionado** em `scripts/build-<nome>.ps1` (padrão de `build-onb-hero.ps1` e
  `build-onb-lounge.ps1`: roda à mão, fora do build, documentado em `scripts/README.md`).
  WebP no Windows: WIC (`PresentationCore`) lê; Python com Pillow também serve para recortes.
- O asset final vai para `app/src/main/res/drawable-nodpi/`. Use WebP/JPEG; PNG só se
  precisar de transparência.
- Nunca sobrescreva `onb_hero.jpg`/`onb_lounge_fg.png` sem rever as constantes da animação.

### 5. Animação (se o briefing pedir)
Siga a seção 9 do guia. Escolha a ferramenta:
- **Compose `Canvas` / `rememberInfiniteTransition`** — movimento simples sobre a arte
  (ventilador, vapor, brilho, cone de luz). Modelo: `feature/onboarding/OnboardingHero.kt`.
  Consulte a skill `compose-animations`.
- **Lottie** — movimento mais rico ou com várias camadas (ex.: carimbo). Skill `text-to-lottie`.
- **SVG animado** — só para ícones/ilustrações vetoriais; skill `svg-animations`.

Regras fixas: usar `rememberAnimationsEnabled()` com **pose parada** definida; ciclos lentos
e deadpan; paleta do guia; constantes em "px da arte" com comentário dizendo de qual versão
da imagem vêm; fade nas bordas se a arte se funde ao fundo.

### 6. Ficha e registro
Ao entregar, registre:
- no `scripts/README.md`: script que gera a arte, o que ele faz, e o que depende dele;
- no `docs/CHANGELOG.md`: a mudança funcional (padrão do projeto);
- no comentário de topo do composable: dimensões da arte e o que é animado em código.

### 7. Verificação
Compile (`./gradlew :app:assembleDebug --console=plain --no-daemon`, ver `CLAUDE.md` para
JAVA_HOME), instale no emulador e tire screenshot da tela com a animação (aguarde 2–4 s
após `am start`; acorde o emulador antes). Confira a pose parada com a escala de animações
em 0 (`adb shell settings put global animator_duration_scale 0`; volte para 1 depois).
Se a mudança fizer parte de uma versão para a Play Store, rode os dois scripts obrigatórios
do `CLAUDE.md` antes de entregar.

## Ficha de animação (template)

```
Arte: <arquivo>  (<largura>×<altura>, gerada por <script>)
Elemento       | Técnica        | Coordenadas (px da arte)   | Ciclo   | Pose parada
---------------|----------------|----------------------------|---------|------------
<ex.: vapor>   | Canvas, curvas | base y=340, x=553/567/581  | 2800 ms | 30%
Dependências: <o que quebra se a arte mudar>
```
