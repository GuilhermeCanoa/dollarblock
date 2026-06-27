# DOLLARBLOCK — MINI STYLE GUIDE & BRAND IDENTITY

> **Missão da Marca:** Defender o recurso mais valioso da era moderna: o tempo do usuário. Através da economia comportamental e da disciplina digital, transformamos distração em foco e blocos de tempo em valor real.

---

## 1. Identidade Visual Central

Como pode ser visto na marca conceitual desenvolvida em **docs/logos/dollarblock_logo_full.webp**, a identidade visual foge do minimalismo corporativo tradicional e abraça uma estética fintech premium, energética e disruptiva. Ela é construída sobre três pilares fundamentais: **Proteção (Escudo), Tempo (Ampulheta) e Valor (Cifrão)**.

---

## 2. Paleta de Cores (Color System)

Para interfaces Android, utilizaremos o conceito de *tokens* de cor, garantindo alto contraste para acessibilidade (WCAG AA) e profundidade visual com gradientes vibrantes.

### Cores Principais (Brand Colors)

* **Emerald Premium (Primary):** `#00E676`
* *Uso:* Botões de ação principal (CTA), estados ativos, destaques de foco e elementos de sucesso. Transmite energia, crescimento e disrupção.


* **Deep Green Velvet (Background/Surface):** `#0A241D`
* *Uso:* Cor de fundo principal do app (Dark Mode nativo). Substitui o preto genérico por um tom rico que remete à sofisticação e segurança financeira.


* **Mint Glow (Accent/Neon):** `#64FFDA`
* *Uso:* Sutil iluminação de bordas, gradientes de progresso e badges de conquistas dentro do app.



### Cores de Suporte e Semânticas

* **Text Primary:** `#FFFFFF` (Alta legibilidade sobre superfícies escuras).
* **Text Secondary (Muted):** `#A0B2AE` (Para textos de apoio, legendas e descrições).
* **Alert/Blocking:** `#FF5252` (Utilizado estritamente para indicar tempo esgotado ou aplicativos bloqueados).

### Diretriz de Gradiente (Marketing & Onboarding)

Para materiais de marketing e telas de onboarding, utilize um gradiente linear a 135°:

* De `Deep Green Velvet (#0A241D)` para um tom médio `Emerald (`#00A86B`)`, finalizando com o brilho do `Mint Glow`.

---

## 3. Tipografia (Typography)

A escolha tipográfica equilibra a robustez de uma marca de segurança com a agilidade de um produto digital moderno.

* **Títulos e Headings (Display & Headline):** **Syne** ou **Plus Jakarta Sans** (Bold/Extra Bold).
* *Tom:* Confiante, marcante e geométrica. Usada para grandes afirmações de marketing e títulos de seções principais.


* **Corpo de Texto e Interface (Body & Label):** **Inter** ou **Roboto** (Regular/Medium).
* *Tom:* Extremamente limpa, otimizada para telas de smartphones de alta e baixa densidade de pixels, garantindo leitura rápida de métricas de tempo.



---

## 4. Componentes de Interface UI (Android Specs)

Seguindo as diretrizes do ecossistema Android moderno, os componentes devem transmitir a ideia de "bloqueio" e "proteção", mas com acabamento premium.

* **Elevação e Profundidade:** Evitar componentes totalmente planos. Utilizar pequenas bordas iluminadas (*inner shadows* ou *strokes* finos com opacidade de 15% do Mint Glow) para simular camadas de vidro fosco (*glassmorphism* controlado).
* **Arredondamento (Corner Radius):**
* Botões e Cards Principais: `16dp` a `24dp` (Curvas suaves que conversam com as linhas do escudo do logo em **docs/logos/dollarblock_logo_full.webp**).


* **Animações e Transições (Micro-interactions):**
* O ato de ativar o bloqueio de um app deve parecer pesado e seguro: uma transição suave onde o escudo se expande na tela com um feedback tátil (vibração curta e firme).



---

## 5. Tom de Voz & Diretrizes de Marketing (Copywriting)

O DollarBlock não é um aplicativo de produtividade chato ou punitivo; ele é um aliado de elite na guerra pela atenção.

### Pilares da Comunicação:

* **Provocativo, mas Profissional:** Desafia o status quo das redes sociais que lucram com a distração do usuário.
* **Orientado a Dados:** Troca o conceito abstrato de "foco" por métricas de valor. Não mostramos apenas "você economizou 2 horas", mostramos "você protegeu R$ 200 do seu tempo hoje".
* **Energético:** Usa termos fortes como *Proteger*, *Blindar*, *Resgatar*, *Valorizar*.

### Exemplos de Aplicação em Campanhas:

> **Headline para Landing Page:** "O mercado financeiro quer seu dinheiro. As redes sociais querem seu tempo. Proteja ambos."
> **Push Notification (Notificação de Sucesso):** "🛡️ **Foco Blindado!** Você completou seu bloco de tempo sem distrações. Mais 45 minutos produtivos salvos."
> **Slogan Principal:** "Protect your attention. Value your time."

---

## 6. Aplicação do Logo (Icon Assets)

* **Launcher Icon (App no celular):** O emblema central (escudo com a ampulheta e o cifrão em espaço negativo) deve ocupar o centro do grid circular ou quadrado do Android, mantendo o fundo escuro texturizado para se destacar na tela inicial do usuário.
* **Versão Horizontal (Brand Mark):** O símbolo à esquerda, seguido pelo texto **DOLLAR** (em peso regular/light) e **BLOCK** (em peso extra bold) na cor branca ou verde esmeralda.