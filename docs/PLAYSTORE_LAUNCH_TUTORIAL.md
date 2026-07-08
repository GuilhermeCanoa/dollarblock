# DollarBlock — Tutorial de Publicação na Google Play

Passo a passo completo, do zero até o app aprovado e visível na Play Store, considerando o
estado atual do projeto: pagamento via **Google Play Billing** (E16), `applicationId`
`com.dollarblock`, `versionName` `0.1.0`, sem `signingConfig` de release configurado ainda.

Este documento é o **guia operacional** (contas, links, formulários, ordem das ações). Os
**textos prontos** para colar em cada formulário (política de privacidade, Data Safety,
declaração de Acessibilidade, ficha da loja) já existem em
`docs/PLAYSTORE_PRIVACY_SUBMISSION.md` — aqui apenas referenciamos onde usá-los.

> Convenção: `⟨...⟩` marca algo que só você preenche (e-mail, CPF/CNPJ, valores reais).

---

## Visão geral — ordem das etapas

```
1. Conta de developer Google Play (paga uma vez)
2. Perfil de pagamentos (merchant) — para vender o "passe do dia"
3. Gerar keystore de release e assinar o app
4. Criar o app no Play Console
5. Publicar a política de privacidade (URL pública)
6. Preencher Data Safety, Acessibilidade, ficha da loja (usar PLAYSTORE_PRIVACY_SUBMISSION.md)
7. Criar o produto in-app "day_pass" (Google Play Billing)
8. Subir o primeiro build para o Internal Testing
9. Adicionar license testers e validar a compra real
10. Preencher classificação de conteúdo, público-alvo, formulário de app para famílias (se aplicável)
11. Promover para produção (rollout gradual)
```

---

## 1. Conta de developer Google Play

1. Acesse **https://play.google.com/console/signup**.
2. Entre com a conta Google que vai administrar o app (recomendo uma conta dedicada ao
   projeto/empresa, não sua pessoal, se possível — é a conta que aparecerá nos recibos e
   controlará o app para sempre).
3. Escolha o tipo de conta:
   - **Individual (pessoa física)** — mais simples, requer CPF e documento de identidade.
   - **Organização (empresa)** — requer CNPJ, D-U-N-S Number (a Google gera/verifica um
     automaticamente pelo nome da empresa) e comprovação de representação legal.
   - Se o DollarBlock for pessoal/indie, comece como Individual — dá para migrar depois,
     mas com fricção. Se já existe empresa formalizada, considere abrir como Organização
     desde já (facilita emissão de nota fiscal/repasses).
4. Pague a **taxa única de registro: USD 25** (cartão de crédito internacional). Não é
   assinatura, é pagamento único e vitalício por conta.
5. Preencha identidade: nome legal, endereço, telefone, e-mail de contato **público** (vai
   aparecer na ficha da loja).
6. **Verificação de identidade**: a Google pode pedir foto de documento + selfie (D-U-N-S
   para empresa). Pode levar de algumas horas a alguns dias — inicie isso o quanto antes,
   é o item que mais atrasa o cronograma.

> Enquanto a conta está em verificação, você já pode seguir com os passos 3–4 (keystore,
> criar o app em rascunho) — a maior parte do Console fica acessível antes da aprovação
> final da conta.

---

## 2. Perfil de pagamentos (merchant) — obrigatório para vender o "passe do dia"

O DollarBlock cobra R$ 5,00 via **Google Play Billing** (produto consumível `day_pass`).
Vender qualquer produto in-app exige um **perfil de pagamentos** vinculado à conta de
developer.

1. No Play Console: **Configurações › Perfil de pagamentos** (ou você será redirecionado
   automaticamente ao criar o primeiro produto in-app).
2. Link direto: **https://payments.google.com/business/console**.
3. Preencha dados fiscais do recebedor (CPF/CNPJ, banco, endereço). Para conta Individual,
   normalmente é CPF + conta bancária brasileira.
4. A Google define a comissão padrão da Play Billing: **15% até US$ 1M/ano em receita,
   30% acima disso** (Google Play Media Experience Program / política padrão de comissão).
   Confirme o valor vigente em **https://support.google.com/googleplay/android-developer/answer/112622**
   antes de fechar sua precificação — não é custo AWS, mas impacta a margem do "passe do dia".
5. Verificação bancária pode levar alguns dias (a Google faz um depósito de validação, às
   vezes).

> Sem o perfil de pagamentos ativo, o produto `day_pass` não pode ser publicado/vendido —
> planeje isso **antes** do passo 7.

---

## 3. Gerar o keystore de release e assinar o app

O projeto hoje **não tem `signingConfig` de release** em `app/build.gradle.kts` — o build
`assembleRelease` sairia sem assinatura. Resolva isso antes de gerar o primeiro AAB.

### 3.1 Gerar o keystore (uma vez, guarde para sempre)

```bash
keytool -genkey -v -keystore dollarblock-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias dollarblock-upload
```

- Guarde `dollarblock-upload.jks`, a senha do keystore e a senha do alias em local seguro
  (gerenciador de senhas / cofre). **Se perder, não há como atualizar o app publicado** —
  seria necessário publicar como um app novo.
- **Nunca commite o `.jks` no git.**

### 3.2 Play App Signing (recomendado, e hoje padrão obrigatório para apps novos)

Desde 2021 todo app novo na Play Store usa **Play App Signing**: você faz upload assinado
com sua *upload key* (a gerada acima), e a Google re-assina com a *chave de app* que ela
guarda com segurança. Isso protege contra perda da chave de upload (dá pra resetar via
suporte, diferente da chave de app).

- Isso é configurado automaticamente na primeira vez que você sobe um AAB assinado ao
  Console — não precisa de passo manual extra além de assinar localmente com a upload key.

### 3.3 Configurar no Gradle

Adicione ao `app/build.gradle.kts` (fora do escopo deste doc de tutorial, mas necessário
antes do passo 8) um `signingConfigs.release` lendo caminho/senhas de `local.properties`
(nunca hardcoded), e referencie em `buildTypes.release.signingConfig`. Gere o pacote com:

```bash
.\gradlew.bat :app:bundleRelease --console=plain --no-daemon
```

Saída: `app/build/outputs/bundle/release/app-release.aab` — é **este arquivo** (AAB, não
APK) que sobe no Play Console.

---

## 4. Criar o app no Play Console

1. **https://play.google.com/console** → **Criar app**.
2. Nome do app: `DollarBlock`.
3. Idioma padrão: **Português (Brasil)**.
4. Tipo: **App**. Gratuito ou pago: **Gratuito** (o "passe do dia" é compra in-app, não é
   um app pago).
5. Declarações iniciais (checkboxes): Política de Programa do Desenvolvedor, Leis de
   exportação dos EUA — aceitar após ler.
6. Isso cria o "app shell" — o `applicationId` (`com.dollarblock`) só é fixado quando você
   sobe o primeiro build; confirme que o pacote gerado localmente bate com esse valor.

---

## 5. Publicar a política de privacidade

O texto já está pronto em `docs/PLAYSTORE_PRIVACY_SUBMISSION.md` (seção 1). Falta só
publicar numa URL pública e estável.

**Recomendação (custo $0): GitHub Pages.**

1. No repositório, crie `docs/privacy.html` com o conteúdo da seção 1 de
   `PLAYSTORE_PRIVACY_SUBMISSION.md` (convertido para HTML simples).
2. Ative GitHub Pages: **Settings › Pages › Source: branch `master`, pasta `/docs`** (ou
   crie uma branch `gh-pages` dedicada, se preferir manter `docs/` só para markdown interno).
3. URL final: `https://⟨seu-usuario-github⟩.github.io/⟨nome-do-repo⟩/privacy.html`.
4. Antes de publicar, substitua os marcadores `⟨…⟩` no texto (data de "última atualização",
   e-mail de contato).
5. Cole essa URL em **dois lugares** no Play Console: ficha da loja (campo "Política de
   privacidade") e formulário Data Safety.

> Alternativa dentro do ecossistema AWS do projeto (S3 + CloudFront estático) também tem
> custo ~$0 sem tráfego, mas exige criar recursos novos — siga a regra de custo AWS do
> projeto (pedir autorização antes de criar bucket/distribution) se optar por essa via.
> GitHub Pages evita essa fricção inteiramente.

---

## 6. Data Safety, Acessibilidade e Ficha da Loja

Todo o conteúdo textual já está escrito e pronto para copiar/colar em
**`docs/PLAYSTORE_PRIVACY_SUBMISSION.md`**:

| Formulário no Play Console | Seção do doc |
|---|---|
| Política do app › Segurança dos dados (Data Safety) | §2 |
| Política do app › Permissões e APIs sensíveis › Acessibilidade | §3 |
| Presença na loja › Ficha principal da loja (nome, descrições, categoria) | §4 |

Passos práticos:

1. **Segurança dos dados**: `Play Console → seu app → Política → Segurança dos dados →
   Iniciar`. Siga o questionário respondendo conforme §2. Sem coleta/compartilhamento de
   dados pessoais (uso é 100% local; pagamento é do próprio Google Play Billing).
2. **Declaração de Acessibilidade**: `Política → Permissões e APIs que acessam informações
   sensíveis → Gerenciar → AccessibilityService`. Cole os textos de §3.1–3.3. Grave o vídeo
   curto descrito em §3.5 (20–40s, YouTube não listado) e cole o link.
3. **Ficha da loja**: `Presença na loja → Ficha principal da loja`. Nome, descrição curta e
   completa de §4.3, categoria (§4.4), screenshots reais do app (§4.5 — Home, Apps, tela de
   bloqueio, Extrato; mínimo 2, recomendado 4–6, formato PNG/JPG 16:9 ou 9:16 conforme o
   Console pedir), ícone 512×512, imagem de destaque (feature graphic) 1024×500.
4. **Classificação de conteúdo (IARC)**: `Política → Classificação de conteúdo →
   Iniciar questionário`. Responda conforme §4.6 — sem violência/conteúdo adulto, mas
   **declare "compras no aplicativo"** (o produto `day_pass`).
5. **Público-alvo e conteúdo**: declare público-alvo **18+** ou pelo menos não voltado a
   crianças (o app trata de dinheiro e trava outros apps — evite classificação infantil,
   simplifica a revisão e a política de anúncios/Families).
6. **Contato do desenvolvedor**: e-mail público + site (pode ser o link do GitHub Pages ou
   um domínio próprio, se tiver).

---

## 7. Criar o produto in-app "day_pass" (Google Play Billing)

Este é o passo específico da mudança recente (E16). Sem ele, o botão de desbloqueio no app
mostra "indisponível".

1. `Play Console → seu app → Monetizar → Produtos → Produtos no app`.
2. **Criar produto** → tipo **Consumível gerenciado** (managed product, consumable).
3. **ID do produto**: exatamente `day_pass` (tem que bater com
   `PaymentConfig.PLAY_PRODUCT_DAY_PASS` no código — case-sensitive).
4. **Nome**: algo como "Passe do dia" (visível internamente, não é o que o usuário vê no
   checkout — o checkout mostra nome do app + preço).
5. **Descrição**: "Libera o app bloqueado até a meia-noite."
6. **Preço**: **R$ 5,00** — precisa bater com `GooglePayConfig.DEFAULT_PRICE` no código
   (usado como fallback de exibição e no cálculo de "total economizado" quando o preço do
   Play ainda não carregou). Se mudar o preço aqui, atualize a constante no app também.
7. **Ativar** o produto (status precisa estar "Ativo", não "Rascunho").
8. Requer o **perfil de pagamentos** do passo 2 já configurado — sem ele, a opção de criar
   produtos in-app fica bloqueada/oculta.

---

## 8. Subir o primeiro build (Internal Testing)

Produtos in-app **só funcionam com o app instalado via Play Store** (mesmo em teste) — não
funcionam rodando direto por `adb install` de um APK debug. Por isso o primeiro teste real
de compra precisa passar por uma track do Console.

1. Gere o AAB assinado (passo 3.3): `app/build/outputs/bundle/release/app-release.aab`.
2. `Play Console → seu app → Testar e lançar → Teste → Teste interno (Internal testing)`.
3. **Criar uma versão** → faça upload do `.aab`.
4. Preencha as notas da versão (release notes) — pode ser simples: "Primeira versão de
   teste interno."
5. Salvar → Revisar versão → **Iniciar lançamento para o teste interno**.
6. Isso não passa por revisão manual completa da Google (testes internos são quase
   instantâneos) — mas os formulários de política (Data Safety, Acessibilidade, ficha da
   loja) já precisam estar preenchidos para conseguir avançar.

---

## 9. License testers e validação da compra real

1. `Play Console → Testar e lançar → Teste interno → Testers` (ou `Configurações → Teste de
   licença`, dependendo da versão do Console).
2. Adicione o e-mail Gmail da conta que vai testar (ex.: `⟨seu-email-de-teste⟩@gmail.com`)
   à lista de testers do canal de teste interno **e** à lista de "License testing" em
   `Configurações da conta de desenvolvedor → Teste de licença`.
3. Compartilhe o **link de opt-in** do teste interno (gerado na própria tela do canal) com
   esse e-mail; a conta precisa aceitar o convite e instalar o app **pela Play Store** (o
   link abre a ficha do app já linkada ao teste).
4. Com o app instalado assim, abra o fluxo de bloqueio → "Pagar o passe do dia": o produto
   `day_pass` deve aparecer com o preço real, e a compra por um **license tester** não gera
   cobrança de verdade (mostra "Test card, always approves" ou similar).
5. Confirme no app: o desbloqueio é concedido, o extrato registra o método "Google Play", e
   o preço exibido bate com o configurado no Console.

---

## 10. Itens finais antes da produção

- [ ] Testar o bloqueio/acessibilidade em pelo menos um aparelho com gestão agressiva de
      bateria (Xiaomi/MIUI, Samsung) — serviços de Acessibilidade podem ser mortos pelo
      sistema; é um ponto comum de reprovação/reclamação.
- [ ] Revisar `versionCode`/`versionName` — `versionCode` precisa **incrementar** a cada
      novo upload (hoje está em `1`; ok para o primeiro upload).
- [ ] Confirmar que `STRIPE_PUBLISHABLE_KEY` em `local.properties` não vaza no build público
      (é só `buildConfigField`, mas o caminho Stripe está desabilitado por `PaymentConfig` —
      confirme que não há chave `pk_live_` de verdade sobrando aí antes de gerar o release).
- [ ] Revisar Data Safety mais uma vez após qualquer mudança de código que toque em dados/
      permissões — respostas inconsistentes com o app real são a causa nº 1 de rejeição.

---

## 11. Promover para produção

1. `Play Console → Testar e lançar → Produção → Criar nova versão`.
2. Pode promover a **mesma versão** já validada no teste interno (opção "Promover versão")
   em vez de subir um novo AAB.
3. Escolha **rollout percentual** (ex.: começar em 20% dos usuários e subir aos poucos) ou
   100% direto — para o primeiro lançamento, 100% costuma ser aceitável dado o volume baixo
   inicial.
4. **Enviar para revisão.** A partir daqui é revisão manual da Google:
   - Prazo típico: horas a poucos dias; apps com Acessibilidade + pagamentos podem levar
     mais (até ~7 dias em casos de revisão adicional).
   - Acompanhe em `Play Console → Painel → Status de publicação`.
5. Se for **rejeitado**: o Console lista o motivo específico. Os itens mais prováveis de
   reprovação neste app são exatamente os cobertos em
   `docs/PLAYSTORE_PRIVACY_SUBMISSION.md` §0 (checklist) — revise essa lista antes de
   reenviar.

---

## Referências oficiais

- Play Console: https://play.google.com/console
- Cadastro de developer: https://play.google.com/console/signup
- Perfil de pagamentos: https://payments.google.com/business/console
- Comissão da Play Billing: https://support.google.com/googleplay/android-developer/answer/112622
- Play App Signing: https://developer.android.com/studio/publish/app-signing#app-signing-google-play
- Política de pagamentos (Payments Policy): https://support.google.com/googleplay/android-developer/answer/9858738
- Segurança dos dados (Data Safety): https://support.google.com/googleplay/android-developer/answer/10787469
- Uso de Acessibilidade (política): https://support.google.com/googleplay/android-developer/answer/10964491

## Documentos internos relacionados

- `docs/PLAYSTORE_PRIVACY_SUBMISSION.md` — textos prontos (política de privacidade, Data
  Safety, declaração de Acessibilidade, ficha da loja).
- `docs/specs/E16-compliance-play-store-pagamentos.md` — por que o pagamento migrou de
  Stripe/Google Pay para Google Play Billing, e o que ficou pendente no código.
- `docs/PAYMENTS_SETUP.md` — histórico do fluxo Google Pay/Stripe (E9), mantido desabilitado
  mas compilável.
