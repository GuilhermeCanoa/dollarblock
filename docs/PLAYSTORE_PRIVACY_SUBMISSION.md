# DollarBlock — Política de Privacidade & Submissão à Google Play

Documento único com **(1)** a política de privacidade pronta para publicar e **(2)** o
conteúdo exato para cada formulário do Google Play Console, montado para maximizar as
chances de aprovação. O ponto forte do DollarBlock na revisão é real: **os dados de uso
nunca saem do aparelho** e a permissão de Acessibilidade tem um propósito único e honesto
(bloquear apps quando o limite é atingido). Este documento explica como comunicar isso.

> `applicationId`: `com.dollarblock` · `versionName` 0.1.0 · público-alvo inicial: Brasil (BRL).
> Ajuste datas, e-mail de contato e URL da política antes de publicar (marcados com `⟨…⟩`).

---

## 0. Antes de tudo — checklist de aprovação

A revisão do Google para apps com **Acessibilidade** + **Acesso de uso** (Usage Access) é
manual e criteriosa. Reprovações quase sempre vêm de: (a) política de privacidade ausente
ou genérica, (b) uso de Acessibilidade não declarado/justificado, (c) formulário de
Segurança dos dados (Data Safety) inconsistente com o que o app faz. Este doc cobre os três.

- [ ] Política de privacidade publicada numa URL pública e estável (ver §5 de hospedagem).
- [ ] Vídeo/descrição de como a Acessibilidade é usada (Play exige — ver §3).
- [ ] Formulário Data Safety preenchido conforme §2.
- [ ] Declaração de "Permissions & APIs that Access Sensitive Information" conforme §3.
- [ ] Ficha da loja (descrição, screenshots) conforme §4.
- [ ] `AccessibilityService` com `<meta-data>` de descrição clara do propósito (ver §3.4).

---

## 1. Política de Privacidade (texto para publicar)

> Publique **este texto** numa página pública (ver §5). Cole a mesma URL no campo
> "Política de privacidade" da ficha da loja **e** no formulário Data Safety.

---

### Política de Privacidade do DollarBlock

**Última atualização:** ⟨DD/MM/AAAA⟩

O DollarBlock ("aplicativo", "nós") foi projetado com um princípio simples: **o seu uso do
celular é seu.** Esta política explica, sem letra miúda, quais dados o aplicativo acessa, o
que fica no seu aparelho, o que é enviado para fora e por quê.

#### 1. Resumo honesto

- **Seus dados de uso de aplicativos ficam 100% no seu aparelho.** Quanto tempo você passa
  em cada app, os limites que você define e o seu histórico de bloqueios **nunca são
  enviados para nossos servidores nem para terceiros.**
- **Não temos conta, login, cadastro nem coleta de e-mail, nome ou telefone.**
- Não enviamos nenhum dado seu para servidores nossos. O pagamento opcional do "passe do
  dia" é processado pelo próprio **Google Play** (compra no app), sem passar por nós.

#### 2. Dados que o aplicativo acessa **e mantém apenas no seu aparelho**

Para funcionar, o DollarBlock lê e armazena localmente (banco de dados interno do app, sem
backup em nuvem nosso):

- **Estatísticas de uso de aplicativos** (via permissão "Acesso de uso"/Usage Access):
  tempo de tela por app e por dia, usado para calcular limites, o "prejuízo" em reais e as
  estatísticas mostradas nas telas do app.
- **Lista de apps monitorados e limites** que você configura.
- **Eventos de bloqueio e desbloqueio** (histórico/extrato).
- **Preferências** (tema, idioma, salário de referência para o "taxímetro").

Esses dados **não** são transmitidos, vendidos, compartilhados ou usados para publicidade.
Eles são apagados quando você limpa os dados do app ou o desinstala.

#### 3. Uso da permissão de Acessibilidade

O DollarBlock usa a API de Acessibilidade do Android com **uma única finalidade**: detectar
quando um aplicativo que você escolheu monitorar está aberto em primeiro plano para, se o
limite diário definido por você tiver sido atingido, exibir a tela de bloqueio por cima
dele. **O serviço de Acessibilidade não lê o conteúdo das suas telas, não coleta texto que
você digita, não captura senhas e não transmite nada para fora do aparelho.** Ele apenas
identifica qual aplicativo está em primeiro plano.

#### 4. Pagamentos (opcional)

Se você optar por pagar R$ 5,00 por um "passe do dia" para liberar um app bloqueado, a
compra é processada inteiramente pelo **Google Play** (faturamento do Google Play / Google
Play Billing), como qualquer compra dentro de aplicativo na Play Store. Nesse fluxo:

- O DollarBlock **não vê nem armazena** os dados do seu cartão ou da sua forma de
  pagamento. Eles são tratados diretamente pelo Google Play.
- **Não temos servidor envolvido na compra**: o aplicativo apenas recebe do Google Play a
  confirmação de que a compra foi concluída e registra o desbloqueio **localmente, no seu
  aparelho** (o extrato que você vê no app).
- O tratamento de dados pelo Google Play segue a política de privacidade do Google:
  https://policies.google.com/privacy

#### 5. Permissões e por que são solicitadas

- **Acesso de uso (Usage Access):** medir o tempo de uso dos apps monitorados. Essencial.
- **Acessibilidade:** detectar o app em primeiro plano para aplicar o bloqueio. Essencial
  para o bloqueio funcionar; ver seção 3.
- **Sobreposição a outros apps (Exibir sobre outros apps):** mostrar a tela de bloqueio por
  cima do app que atingiu o limite.
- **Notificações:** avisar sobre limites e o status do monitoramento. Opcional.

Você pode revogar qualquer permissão a qualquer momento nas configurações do Android. Sem
elas, as funções correspondentes deixam de operar, mas o app não coleta nada em troca.

#### 6. Crianças

O DollarBlock não é direcionado a menores de 13 anos e não coleta intencionalmente dados de
crianças.

#### 7. Seus direitos

Como não mantemos dados pessoais seus em nossos servidores, não há dados pessoais para
solicitar, corrigir ou excluir do nosso lado. Todos os dados de uso ficam no seu aparelho e
estão sob seu controle: basta limpar os dados do app ou desinstalá-lo.

#### 8. Alterações nesta política

Podemos atualizar esta política. Mudanças significativas serão refletidas na data de "última
atualização" acima.

#### 9. Contato

Dúvidas sobre privacidade: ⟨seu-email-de-contato⟩

---

## 2. Formulário "Segurança dos dados" (Data Safety) — respostas

No Play Console: **Política do app › Segurança dos dados.** Responda exatamente assim (as
respostas refletem que o app não envia dados pessoais para servidores).

### 2.1 Coleta e compartilhamento
- **O app coleta ou compartilha algum dos tipos de dados obrigatórios?**
  → **Não.** Todos os dados de uso, limites e histórico são processados e armazenados
  apenas no aparelho. O pagamento do "passe do dia" é uma compra no app processada pelo
  **Google Play Billing** — o formulário Data Safety **não exige declarar** dados tratados
  pelo próprio sistema de faturamento do Google Play (o app não coleta nem compartilha
  dados de pagamento; quem processa é a própria plataforma).
- Caso o console force alguma resposta adicional por haver compras no app, mantenha a
  coerência: nenhum dado sai do dispositivo pelo DollarBlock.

### 2.2 Tipos de dados — o que declarar

**NÃO declare** como coletado/enviado (porque fica só no aparelho):
- Uso de apps / histórico de atividade no dispositivo → é processado **apenas no aparelho**.
  No formulário, o Google distingue "coletado" (enviado para fora) de processado localmente:
  como **não sai do dispositivo**, **não** marque como coletado.
- Informações de pagamento → tratadas pelo **Google Play Billing** (plataforma), não pelo
  app; não há servidor próprio envolvido na compra.

> Nota: se o caminho Stripe/Google Pay (hoje desabilitado no código — ver
> `docs/specs/E16-compliance-play-store-pagamentos.md`) for reativado algum dia, esta seção
> precisa voltar a declarar "Informações financeiras → Informações de pagamento"
> compartilhadas com a Stripe, e o app precisará estar inscrito num programa de billing
> alternativo do Google.

### 2.3 Frases-modelo para os campos de texto livre
> "Os dados de uso de aplicativos, limites e histórico são processados e armazenados
> exclusivamente no dispositivo do usuário e não são transmitidos para nossos servidores.
> O pagamento opcional do 'passe do dia' é uma compra no aplicativo processada pelo
> faturamento do Google Play; o aplicativo não coleta nem compartilha dados de pagamento."

---

## 3. Declaração de Acessibilidade e permissões sensíveis

No Play Console: **Política › Permissões e APIs que acessam informações sensíveis ›
AccessibilityService.** Este é o item que mais reprova apps de bloqueio. Preencha com
precisão.

### 3.1 Finalidade principal declarada do app
> "Aplicativo de bem-estar digital que ajuda o usuário a limitar o próprio tempo de uso de
> aplicativos, bloqueando apps escolhidos por ele após um limite diário definido por ele."

### 3.2 Como o app usa a API de Acessibilidade (texto para o formulário)
> "O DollarBlock usa o AccessibilityService exclusivamente para detectar qual aplicativo
> está em primeiro plano. Quando um aplicativo monitorado pelo usuário atinge o limite
> diário configurado por ele, o app exibe uma tela de bloqueio sobre esse aplicativo. O
> serviço não lê conteúdo de tela, não coleta texto digitado, não captura credenciais e não
> transmite nenhum dado. É a forma padrão e confiável de identificar o app em primeiro plano
> em tempo real para aplicar o bloqueio solicitado pelo próprio usuário."

### 3.3 Por que não há alternativa a Acessibilidade
> "APIs alternativas (UsageStatsManager) informam o tempo de uso, mas não permitem detectar
> a troca de app em primeiro plano em tempo real com a latência necessária para bloquear no
> momento certo. A Acessibilidade é o único mecanismo suportado para esse acionamento
> imediato do bloqueio."

### 3.4 `<meta-data>` do serviço (conferir no código antes de subir)
O arquivo de configuração do serviço de acessibilidade deve ter uma **descrição clara** que
o Android mostra ao usuário. Garanta que `accessibility_service_config` aponte para uma
string de descrição honesta (algo como: "Detecta o app em primeiro plano para aplicar o
bloqueio quando o limite é atingido. Não lê o conteúdo das suas telas."). Verifique o
`android:description` do `<accessibility-service>` e a string referenciada.

### 3.5 Vídeo de demonstração
O Google costuma pedir um vídeo curto (link do YouTube não listado) mostrando: o usuário
definindo um limite → usando o app até o limite → a tela de bloqueio aparecendo. Grave 20–40s
e deixe o link pronto para colar no formulário.

---

## 4. Ficha da loja (Store Listing)

### 4.1 Nome
`DollarBlock` (30 caracteres máx.)

### 4.2 Descrição curta (80 caracteres)
> "Bota um taxímetro nos apps que roubam seu tempo. Passou do limite, você paga."

### 4.3 Descrição completa (sugestão, no tom da marca — revise antes de publicar)
> **Seu tempo tem preço. O DollarBlock mostra qual.**
>
> Instagram, TikTok e YouTube têm times inteiros pagos para te manter rolando a tela. O
> DollarBlock coloca um taxímetro neles.
>
> Você escolhe os apps que comem o seu dia e define um limite diário. Passou do limite, o app
> trava. Precisa mesmo dele? Existe um "passe do dia" — mas ele custa dinheiro de verdade. É
> esse o ponto: o incômodo de pagar é o que te faz lembrar.
>
> **Como funciona:**
> • Escolha os apps para monitorar e defina limites diários.
> • O taxímetro mostra quanto tempo (e quanto dinheiro) você está gastando.
> • Atingiu o limite, o app é bloqueado.
> • O extrato mostra sua evolução — a graça é ver o uso cair semana após semana.
>
> **Você no controle, sempre.** O DollarBlock não sequestra o seu celular. Você pode
> desativar, pausar ou desinstalar quando quiser, sem pagar nada. Seus dados de uso ficam no
> seu aparelho — não enviamos nada para lugar nenhum.
>
> Sem cadastro. Sem login. Sem anúncios.

### 4.4 Categoria e tags
- Categoria: **Estilo de vida** ou **Produtividade** (evite "Ferramentas"; "Bem-estar
  digital" é bem visto para este tipo de app).
- Tags: bem-estar digital, foco, tempo de tela, produtividade.

### 4.5 Screenshots (mín. 2, recomendável 4–6)
Use as telas reais já validadas: Home (taxímetro), Apps (definir limite), Tela de bloqueio
(recibo), Extrato (evolução/queda de uso). A tela de bloqueio é o diferencial — inclua.

### 4.6 Classificação de conteúdo
Preencha o questionário: sem violência/conteúdo adulto. Menções a "pagamento" → declare
compras/pagamentos digitais. Não é jogo de azar (é um pagamento por serviço, não aposta).

---

## 5. Hospedagem da política de privacidade (custo $0)

A política precisa de uma URL pública estável. Opções sem custo fixo:

- **GitHub Pages** do próprio repositório (grátis): criar `docs/privacy.html` ou usar a
  branch `gh-pages`; a URL fica `https://⟨usuario⟩.github.io/⟨repo⟩/privacy`.
- **Um arquivo estático no S3 + CloudFront** que já existem no ecossistema AWS do projeto
  (cobra só por request; ~$0 sem tráfego). Alinhado às regras de custo AWS do projeto.

Recomendação: **GitHub Pages** — zero custo, zero infra nova, e satisfaz plenamente a
exigência do Google.

---

## 6. Pendências técnicas antes de publicar (não são deste doc, mas bloqueiam o lançamento)

> O pagamento migrou de Google Pay + Stripe para **Google Play Billing** (E16 — ver
> `docs/specs/E16-compliance-play-store-pagamentos.md`). As chaves Stripe de produção e a
> stack `dollarblock-payment-production` **deixaram de ser pré-requisito** de lançamento; o
> código Stripe permanece no app apenas desabilitado (`PaymentConfig.PROVIDER`).

1. **Produto in-app no Play Console**: criar o produto gerenciado **consumível** com ID
   exatamente `day_pass` (o mesmo de `PaymentConfig.PLAY_PRODUCT_DAY_PASS`), preço
   **R$ 5,00** — igual a `GooglePayConfig.DEFAULT_PRICE`, que alimenta o fallback de
   exibição e o cálculo de "total economizado"; se mudar o preço lá, mude a constante.
   Requer **perfil de pagamentos (merchant)** ativo na conta de developer.
2. **`versionCode`/`versionName`** de release e build assinado (keystore de upload).
3. **Testar a compra na track de teste interno**: produtos in-app só funcionam com o app
   distribuído pelo Play; adicionar **license testers** (Play Console › Configurações ›
   Teste de licença) para comprar sem cobrança real. O botão "passe do dia" mostra
   "indisponível" se o produto não existir/estiver inativo ou o app não vier do Play.
4. **Testar o bloqueio** em fabricantes com gestão agressiva de bateria (Xiaomi, etc.), onde
   o serviço pode ser encerrado.
5. **Classificação de conteúdo / ficha**: declarar que o app tem **compras no aplicativo**
   (in-app purchases) — a Play Store exibe o selo automaticamente para produtos Play
   Billing.
