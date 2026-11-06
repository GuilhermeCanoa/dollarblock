# Spec: Mensuração — atribuição de anúncios (Meta) e funil de produto

**Status:** draft (aguardando aprovação do dono do produto — nada implementado)
**Épico:** E22
**Data de criação:** 2026-10-26
**Última atualização:** 2026-10-26

---

## Contexto

O marketing vai rodar Meta Ads (Instagram/Facebook), primeiro no Brasil e depois nos EUA. Hoje o
app não tem SDK de analytics nem de atribuição, o que gera dois problemas:

1. A Meta não consegue otimizar campanha por instalação/ativação/compra (campanha de app
   Advantage+). Só dá para otimizar por clique na loja, sem saber quem instalou por causa do
   anúncio.
2. Não sabemos se quem instala começa a usar: se concede as permissões, configura um limite,
   chega a ser bloqueado, compra o passe ou fecha sem pagar.

Decisão do dono do produto: **o app vai ter SDK**. O argumento de sigilo (E19) vira ajuste de
texto, não bloqueio. A implementação tem que ser a mais enxuta possível no que envia.

Estado atual relevante (lido no código em 2026-10-26):

- App em produção (`com.dollarblock`, `versionCode 11` / `0.1.10`), ~10 instalações.
- `INTERNET` já está no manifesto (Play Billing + caminho Stripe desligado).
- Release com `isMinifyEnabled = false` (sem R8). Isso pesa no tamanho do APK com SDK novo.
- Onboarding E21: 5 páginas (`Entry → Contract → Measurement → Lock → FinalSettings`), em
  `feature/onboarding/OnboardingFlow.kt`.
- Compra: `PlayBillingManager` expõe `priceAmount` (string "%.2f" derivada de
  `oneTimePurchaseOfferDetails.priceAmountMicros`) e `priceCurrency` (`priceCurrencyCode`) reais do
  Play; a implementação deve expor os micros direto. `BlockActivity.onPaymentSuccess` / `onCourtesyUnlock` /
  `goHome` são os três desfechos da tela de bloqueio.
- Textos hoje **literalmente** afirmam "não tem rastreador de anúncios nem de estatísticas" e "Seus
  dados não saem do seu celular" (`privacy_dialog_body`, `privacy_row_desc`, em en/pt/hi). Com
  este épico isso deixa de ser verdade e tem que mudar **no mesmo release** que liga os SDKs.

**Este épico não bloqueia o teste de Meta Ads com objetivo Tráfego** (só Android, Brasil): esse
objetivo otimiza clique no link da loja e não depende de SDK no app.

---

## Fontes consultadas (documentação oficial)

| # | O que comprova | URL |
|---|---|---|
| F1 | Chaves de manifesto `AutoLogAppEventsEnabled`, `AdvertiserIDCollectionEnabled`, `AutoInitEnabled`, `AutoLogAppMetadataEnabled`; `setAutoLogAppEventsEnabled`, `setAdvertiserIDCollectionEnabled`, `setAutoInitEnabled` + `fullyInitialize`; eventos automáticos (instalação, abertura, compra, relatórios de crash/ANR do SDK); log implícito de compra do Google Play (doc cita Billing v2–v7) | https://developers.facebook.com/docs/app-events/getting-started-app-events-android |
| F2 | Meta-data `ApplicationId` e `ClientToken`; comando de key hash (debug/release); "a partir da v13.0.0 o SDK adiciona automaticamente a permissão de Advertising ID"; remoção com `tools:node="remove"` | https://developers.facebook.com/docs/android/getting-started |
| F3 | Módulos do SDK: `com.facebook.android:facebook-core` = "Core (inclui Analytics)"; usar só os módulos necessários economiza espaço (sem números de tamanho) | https://developers.facebook.com/docs/android/componentsdks |
| F4 | Manifesto do `facebook-core` (branch main): declara `AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_CUSTOM_AUDIENCE`, `ACCESS_ADSERVICES_TOPICS`; `FacebookInitProvider` que inicializa o SDK sozinho; dois receivers de token | https://github.com/facebook/facebook-android-sdk/blob/main/facebook-core/src/main/AndroidManifest.xml |
| F5 | Dependências do `facebook-core`: já traz `com.android.installreferrer` (Google Play Install Referrer); Billing só por reflexão (testado contra GPBL 8) | https://github.com/facebook/facebook-android-sdk/blob/main/facebook-core/build.gradle.kts |
| F6 | `InstallReferrerUtil`: o SDK lê o Google Play Install Referrer uma vez e só repassa a URL se contiver "fb"/"facebook" | https://github.com/facebook/facebook-android-sdk/blob/main/facebook-core/src/main/java/com/facebook/internal/InstallReferrerUtil.kt |
| F7 | Meta Install Referrer: `<queries>` para `com.facebook.katana`, `com.instagram.android`, `com.facebook.lite`; código de ContentProvider só **lê** o referrer; decifrar exige a chave do GPIR; uso descrito é atribuição própria/MMP (view-through e clique multi-sessão); versões mínimas FB v428, IG v296, Lite v411. A página **não** diz que o SDK da Meta usa esse dado sozinho | https://developers.facebook.com/docs/app-ads/meta-install-referrer |
| F8 | Firebase Android: `firebase_analytics_collection_enabled=false` até o consentimento; `setAnalyticsCollectionEnabled(true/false)`; `google_analytics_adid_collection_enabled=false`; `google_analytics_default_allow_ad_personalization_signals=false`; `firebase_analytics_collection_deactivated` | https://firebase.google.com/docs/analytics/android/configure-data-collection |

**Não verificado nesta rodada** (as ferramentas de busca bateram limite de sessão no meio da pesquisa).
Cada item abaixo está marcado **[A VERIFICAR]** no texto e vira a tarefa T0, antes de qualquer
código:

- V1: regra do Play Console para a declaração de Advertising ID / `AD_ID` em apps com target 13+
  (central de ajuda do Play Console, "Advertising ID").
- V2: definições de "coleta" × "compartilhamento" e a exceção de "provedor de serviço" no Data
  Safety (central de ajuda do Play Console, "Data safety").
- V3: se o Firebase Analytics adiciona `AD_ID` sozinho ao manifesto e quais eventos ele registra
  automaticamente (`first_open`, `session_start`, `user_engagement`, `screen_view`) e a chave que
  desliga `screen_view` automático.
- V4: custo do Firebase Analytics no plano Spark (página de preços do Firebase).
- V5: se a Meta ainda oferece algum relatório de funil/retenção por coorte para eventos de app
  (o antigo "Facebook Analytics" foi descontinuado; confirmar o que o Events Manager mostra hoje).
- V6: nomes exatos das constantes (`AppEventsConstants.EVENT_NAME_ACTIVATED_APP` =
  `fb_mobile_activate_app`, `EVENT_NAME_COMPLETED_REGISTRATION` = `fb_mobile_complete_registration`)
  e se `AppEventsLogger.activateApp(application)` dispara mesmo com auto-logging desligado.
- V7: se os relatórios de crash/ANR do SDK da Meta (F1) param com `AutoLogAppEventsEnabled=false`.
- V8: requisitos de uma campanha de app Advantage+ (eventos mínimos, volume, SDK × Conversions API).
- V9: Limited Data Use (LDU) da Meta para estados dos EUA — necessário antes de ir para os EUA.
- V10: tamanho real adicionado ao APK/AAB (nenhuma das fontes publica número — **medir**, ver
  "Impacto no tamanho").

---

## Decisão de SDK (recomendação)

### Frente A — atribuição: Meta SDK, só `facebook-core`

`com.facebook.android:facebook-core` (F3), nunca `facebook-android-sdk` completo. Traz o App Events
e já inclui o Google Play Install Referrer (F5) — não precisa adicionar
`com.android.installreferrer` à mão.

Configuração enxuta (F1, F2, F4):

- `AutoInitEnabled=false` — o SDK não inicializa antes do consentimento (o `FacebookInitProvider`
  do F4 inicializaria sozinho).
- `AutoLogAppEventsEnabled=false` **permanente**, mesmo depois do consentimento. Os 3 eventos são
  enviados à mão. Motivo: o auto-logging também manda compra implícita (risco de duplicar o
  `fb_mobile_purchase` manual) e relatórios de crash/ANR (F1), e a doc só garante o log implícito
  até o Billing v7 — o app está no v8.
- `AdvertiserIDCollectionEnabled=false` no manifesto; `setAdvertiserIDCollectionEnabled(true)` só
  após consentimento de **anúncios**.
- `AutoLogAppMetadataEnabled=false` (sem metadados de telas/app links).
- Remover do manifesto final, com `tools:node="remove"`, os 4 `ACCESS_ADSERVICES_*` (Privacy
  Sandbox, F4) e os 2 receivers de token de login (não usamos Login). **Manter `AD_ID`**: sem
  ele a atribuição de instalação fica mais fraca (ver P3).

**Meta Install Referrer (F7): fica fora desta fase.** A doc oficial descreve o código como leitura
para atribuição própria ou via MMP, com a decifragem a cargo do app/MMP. Não temos MMP nem
servidor, e a página não diz que o SDK da Meta usa esse dado. Adicionar `<queries>` para
Facebook/Instagram/Lite + ContentProvider seria código e dado sem destino. Reavaliar se entrar um
MMP (ver P4).

### Frente B — funil e retenção: Firebase Analytics (GA4), só eventos de funil

Comparação:

| Opção | Custo | Funil + retenção por coorte | Dados enviados | Nº SDKs |
|---|---|---|---|---|
| **Só Meta App Events** | zero | **Não** como relatório pronto [A VERIFICAR — V5]. Events Manager mostra contagem por evento; retenção D1/D7 teria que virar evento custom e conta manual | Tudo do funil vai para a Meta, que usa para anúncios | 1 |
| **Meta (3 eventos) + Firebase (funil)** | zero [A VERIFICAR — V4] | Sim: explorações de funil e coorte no GA4; retenção calculada pelo próprio Firebase | Meta recebe só 3 eventos; funil vai para o Google, sem Advertising ID | 2 |
| Outra (PostHog, Amplitude, Mixpanel etc.) | free tier com limite | Sim | Mais um terceiro, sem ganho sobre o Firebase | 2 |
| Sem SDK de produto (só contadores locais) | zero | Não — o app não tem servidor para agregar | Nada | 1 |

**Recomendação: Meta `facebook-core` (Frente A) + Firebase Analytics (Frente B), sem Crashlytics,
sem Remote Config, sem nada além de `firebase-analytics`.** Separar destino por evento:

- **Meta recebe só o que a campanha otimiza:** ativação, onboarding concluído, compra. Nada de
  permissões, limites ou bloqueios.
- **Firebase recebe o funil**, com `google_analytics_adid_collection_enabled=false` e
  personalização de anúncios desligada (F8): o funil não precisa de Advertising ID.

**O que se perde se o dono preferir um SDK só (só Meta):** funil e retenção por coorte prontos
(V5). Seria preciso mandar os 8 eventos de funil para a Meta, que é exatamente a empresa que mais
usa dado para anúncio. A conta de "menos SDK" piora a conta de "menos dados para quem anuncia".
Se mesmo assim a escolha for 1 SDK, a spec continua válida: os eventos de funil passam a ir para
a Meta como eventos custom, e retenção D1/D7 vira um evento `ativo_dia` com `dia=1|7`.

Detalhe de implementação: um único ponto de saída, `core/analytics/Analytics.kt` (interface) +
`MetaSink` + `FirebaseSink`, chamado pelos features. Desligar um destino = remover um sink. Nenhum
feature importa SDK diretamente; nenhum parâmetro livre (só enums/inteiros fechados na
assinatura — impossível passar `packageName` por engano).

---

## Regras de privacidade (o que nunca sai)

- Nunca: nome de pacote ou nome de app, minutos/milissegundos de uso, horário de uso, limites em
  minutos, conteúdo de tela, qualquer coisa vinda do `DollarBlockAccessibilityService`, salário de
  referência, valores do "prejuízo".
- No máximo: `qtd_apps_com_limite` (inteiro, agrupado em faixas `1`, `2`, `3-5`, `6+`).
- Nenhum `setUserId`, nenhuma user property com dado do usuário, nenhum Advanced Matching
  (e-mail/telefone) na Meta — o app nem tem esses dados.
- `DollarBlockAccessibilityService` não ganha dependência de `Analytics` (regra verificável:
  teste de arquitetura ou grep no `pre-release-check.sh`). O evento de bloqueio é disparado pela
  `BlockActivity`, não pelo serviço.
- Sem consentimento: SDKs não inicializam e nada é enfileirado para depois.

---

## Tabela de eventos

Nomes de evento do Firebase em `snake_case`, sem acento (limite de caracteres do GA4 [A VERIFICAR
— V3]). Um evento por ocorrência; os "uma vez" são controlados por flag em DataStore
(`AnalyticsPreferences`).

| # | Evento | Quando dispara | Parâmetros | Meta | Firebase |
|---|---|---|---|---|---|
| E1 | `fb_mobile_activate_app` (via `AppEventsLogger.activateApp`) | Toda abertura do app com consentimento de anúncios | — (o SDK anexa dados de dispositivo) | Sim | — |
| E2 | `onboarding_iniciado` | No momento do consentimento dado no onboarding (ver "Consentimento") | — | — | Sim |
| E3 | `permissao_concedida` | Primeira vez que cada uma das 4 permissões passa a concedida, durante ou depois do onboarding | `permissao`: `uso` \| `acessibilidade` \| `sobreposicao` \| `notificacoes` | — | Sim |
| E4 | `onboarding_concluido` | `completeOnboarding()` | `obrigatorias_ok`: `true`/`false` | — | Sim |
| E4m | `fb_mobile_complete_registration` | Mesmo momento de E4, uma vez por instalação | — | Sim | — |
| E5 | `primeiro_limite_configurado` | Primeira vez que um app passa a ter `dailyLimitMinutes` não nulo | `qtd_apps_com_limite` (faixa) | — | Sim |
| E6 | `bloqueio_exibido` | `BlockActivity` aberta por limite diário (uma vez por abertura) | `primeiro`: `true`/`false` | — | Sim |
| E7 | `passe_comprado` | `onPaymentSuccess(PLAY_BILLING)` | `value` (decimal), `currency` (ISO 4217 do Play) | — | Sim |
| E7m | `fb_mobile_purchase` (via `logPurchase(BigDecimal, Currency)`) | Mesmo momento de E7, só com valor e moeda vindos do Play (`priceAmountMicros / 1e6`, `priceCurrencyCode`: BRL/USD/INR) | `value`, `currency` | Sim | — |
| E8 | `passe_cortesia` | `onCourtesyUnlock()` (falha de cobrança, E17) | — | — | Sim |
| E9 | `fechou_sem_pagar` | Saída da tela de bloqueio sem compra nem cortesia (`goHome` / voltar) | `tentou_pagar`: `true`/`false` (abriu o fluxo do Play e cancelou) | — | Sim |
| — | Ativo dia 1 / dia 7 | **Não é evento nosso.** O Firebase calcula retenção a partir da própria atividade do app [A VERIFICAR — V3] | — | — | automático |

Regras:

- `fb_mobile_purchase` nunca em cortesia, nunca com o preço de fallback (`dayPassPrice`) e nunca
  no caminho Stripe desligado. Sem `priceAmountMicros` → não envia.
- Usar `priceAmountMicros` (long) e não a string `priceAmount` formatada.
- Eventos automáticos do Firebase que não estão na lista (ex.: `screen_view`) ficam desligados se
  houver chave para isso [A VERIFICAR — V3]. `screen_view` revela nome de tela, não dado do
  usuário, mas não precisamos dele.

Funil no GA4: E2 → E3 (`uso`) → E3 (`acessibilidade`) → E3 (`sobreposicao`) → E4 → E5 → E6 →
E7 | E8 | E9; retenção D1/D7 por coorte de `first_open`.

**Limite honesto do funil:** só mede quem consentiu. Quem recusou não aparece em lugar nenhum.
O total de instalações continua vindo do Play Console, e a razão "instalações × consentimentos"
é a taxa de opt-in.

---

## Fluxo de consentimento

Duas finalidades, duas escolhas (LGPD exige consentimento para finalidade determinada; misturar
anúncio e estatística numa caixa só enfraquece o consentimento):

- **Estatística de uso** → Firebase.
- **Medir anúncios** → Meta + Advertising ID.

### No onboarding

Sem página nova (o E21 enxugou para 5). Uma folha curta abre logo **depois** de "Assinar o
contrato" (página 2), antes do Acesso de uso. Dois interruptores, **desligados por padrão**, e
um botão "Continuar" que funciona com qualquer combinação. "Recusar tudo" com o mesmo peso
visual de "Autorizar tudo". O contrato não depende disso.

Texto proposto (pt — voz da casa: seco, sem emoji, sem exclamação):

> **Cláusula opcional: o gerente quer ver os números**
>
> Para saber se o DollarBlock funciona e se o anúncio que te trouxe valeu o dinheiro, podemos
> registrar etapas: se você deu as permissões, configurou um limite, foi bloqueado, pagou ou
> saiu sem pagar. Nunca quais apps você usa, por quanto tempo, nem o que aparece na tela.
>
> [ ] **Estatística de uso** — etapas do app, enviadas ao Google Firebase. Sem ID de anúncio.
> [ ] **Medir anúncios** — instalação, cadastro concluído e compra do passe, enviados à Meta
> (Facebook/Instagram) com o ID de publicidade do Android.
>
> Dá para mudar em Perfil › Preferências quando quiser. Recusar não muda nada no app.
>
> [ Continuar ]  [ Recusar tudo ]  · Política de privacidade

en (rascunho): *"Optional clause: the manager wants the numbers"* — "Usage statistics — app steps,
sent to Google Firebase. No advertising ID." / "Ad measurement — install, sign-up completed and
day pass purchase, sent to Meta (Facebook/Instagram) with Android's advertising ID." — hi a
traduzir.

### Em Perfil › Preferências

Dois `SettingSwitchRow` novos com os mesmos rótulos. Desligar:

- Firebase: `setAnalyticsCollectionEnabled(false)` (F8).
- Meta: `setAdvertiserIDCollectionEnabled(false)` (F1) e o `Analytics` para de chamar a Meta.
  O SDK não tem chamada documentada para "desinicializar"; ficar inicializado sem receber eventos
  é aceitável [A VERIFICAR — V6].
- Linha de texto abaixo: "Desligar para de enviar daqui para frente. O que já foi enviado fica
  com o Google e a Meta pelo prazo de retenção deles."

### Persistência e ordem no cold start

`ConsentPreferences` (DataStore): `analyticsConsent`, `adsConsent` (`null` = não perguntado).
Em `DollarBlockApp.onCreate`: ler consentimento → se `adsConsent == true`,
`FacebookSdk.setAutoInitEnabled(true)` + `fullyInitialize()` + `setAdvertiserIDCollectionEnabled(true)`
+ `AppEventsLogger.activateApp(this)`; se `analyticsConsent == true`,
`setAnalyticsCollectionEnabled(true)`. Usuários que já instalaram (os ~10 atuais): `null` →
perguntar uma vez, na próxima abertura, com a mesma folha.

### GDPR

Público atual: BR, depois EUA (e o app já tem preço em INR). Não há plano para UE. O desenho
acima (opt-in explícito, granular, revogável) já atende o padrão do GDPR se a UE entrar. Para os
EUA, o ponto é Limited Data Use da Meta para estados com lei própria [A VERIFICAR — V9] —
pendência antes da campanha nos EUA, não deste release.

---

## Impacto no tamanho do APK

Nenhuma fonte oficial consultada publica o tamanho do `facebook-core` ou do `firebase-analytics`
(F3 só diz que módulos menores economizam espaço). **Não vou estimar de memória.** Medir:

1. Antes: `bundleRelease` atual → `bundletool get-size total` (ou APK Analyzer do Android Studio)
   no AAB de hoje.
2. Depois: mesmo comando com os dois SDKs. Registrar os dois números aqui.
3. Critério de aceite proposto: aumento no download ≤ 1,5 MB; se passar, decidir P6 (ligar R8).

Contexto: o release hoje roda sem R8 (`isMinifyEnabled = false`), então o SDK entra inteiro. Ligar
R8 reduz, mas é mudança de build com risco próprio (reflexão do Billing no SDK da Meta, Hilt, Room)
e fica como decisão separada (P6).

---

## Mudanças em textos do app (mesmo release dos SDKs)

| String | Hoje | Proposto (pt) |
|---|---|---|
| `privacy_row_desc` | Seus dados não saem do seu celular | Seu uso não sai do seu celular |
| `privacy_dialog_body` | "…não tem rastreador de anúncios nem de estatísticas…" | ver abaixo |
| `onb_trust_lock_1..3` | (Acessibilidade) | **sem mudança** — continua verdade |
| `privacy_payment_note` | Cobrança feita pelo Google Play. O DollarBlock não vê o seu cartão. | **sem mudança** |

`privacy_dialog_body` proposto (pt):

> O DollarBlock não tem servidor para guardar os seus dados. Não tem conta, não tem login, não
> pede nome, e-mail nem telefone.
>
> Fica só no seu celular: o tempo de uso de cada app, quais apps você limitou, os limites e os
> recibos de bloqueio e resgate. Nem o backup do Google leva esses dados.
>
> Se você autorizou, o app envia etapas ao Google Firebase (deu permissão, configurou limite, foi
> bloqueado, pagou ou não) e, separado, instalação, cadastro e compra do passe à Meta, com o ID de
> publicidade. Nunca quais apps, nunca minutos, nunca a tela. Autorização em Perfil › Preferências.
>
> O passe do dia é cobrado pelo Google Play. O DollarBlock não vê o seu cartão — só recebe o "pago".
>
> Para apagar o que está no celular, desinstale o app ou limpe os dados dele. O que foi enviado
> com a sua autorização fica com o Google e a Meta pelo prazo de retenção deles.

en e hi: mesma estrutura.

---

## Proposta: política de privacidade (`docs/PLAYSTORE_PRIVACY_SUBMISSION.md`)

**Não aplicado nesta rodada.** Trechos a substituir:

§1 "Resumo honesto" — trocar os três itens por:

> - **Seus dados de uso de aplicativos ficam 100% no seu aparelho.** Quais apps você usa, quanto
>   tempo, os limites e o histórico de bloqueios nunca são enviados a ninguém.
> - **Não temos conta, login, cadastro nem coleta de e-mail, nome ou telefone.** Não temos servidor
>   próprio que receba dados seus.
> - **Com a sua autorização, e só com ela**, o app envia (a) etapas de uso do app ao Google Firebase
>   Analytics e (b) instalação, conclusão do cadastro e compra do passe à Meta Platforms, com o ID de
>   publicidade do Android. Você pode recusar ou desligar a qualquer momento (seção 4-A).
> - O pagamento do passe do dia é processado pelo **Google Play**.

Nova §4-A "Medição (opcional, com consentimento)":

> **Base legal:** consentimento (LGPD art. 7º, I). Duas autorizações independentes, desligadas por
> padrão, pedidas no primeiro uso e alteráveis em Perfil › Preferências.
>
> **Estatística de uso (Google Firebase Analytics).** Enviamos: início e conclusão do cadastro no
> app; qual das quatro permissões foi concedida (só o nome da permissão); primeiro limite
> configurado e a faixa de quantidade de apps com limite (1, 2, 3–5, 6+); exibição da tela de
> bloqueio; compra do passe (valor e moeda), desbloqueio de cortesia ou saída sem pagar. O Firebase
> associa esses eventos a um identificador de instalação gerado por ele e a dados técnicos do
> aparelho (modelo, versão do Android, idioma, país aproximado). Coleta do ID de publicidade
> desligada. Política do Google: https://policies.google.com/privacy
>
> **Medir anúncios (Meta).** Enviamos: abertura/instalação do app, conclusão do cadastro e compra do
> passe (valor e moeda), junto com o ID de publicidade do Android e dados técnicos do aparelho, para
> que a Meta saiba se um anúncio dela levou à instalação ou à compra. A Meta usa esses dados também
> para os próprios fins de publicidade, conforme a política dela:
> https://www.facebook.com/privacy/policy
>
> **Nunca enviamos:** nomes ou pacotes dos apps que você usa ou limita, minutos de uso, horários,
> conteúdo de tela ou qualquer dado do serviço de Acessibilidade.
>
> **Como desligar:** Perfil › Preferências. Desligar para os envios dali em diante. Você também pode
> zerar o ID de publicidade nas configurações do Android.

§3 (Acessibilidade): sem mudança. §7 "Seus direitos": acrescentar que dados enviados com
autorização podem ser objeto de pedido pelo e-mail de contato (ver P7). §8: atualizar a data.

Ficha da loja §4.3: trocar "Seus dados de uso ficam no seu aparelho — não enviamos nada para lugar
nenhum." por "Seus dados de uso ficam no seu aparelho. Estatística e medição de anúncio só com a sua
autorização." "Sem anúncios" continua verdade (o app não mostra anúncio).

---

## Proposta: Data Safety (Play Console)

**Não aplicado.** As definições exatas de "coletado" e "compartilhado" e a exceção de provedor de
serviço estão [A VERIFICAR — V2]; a proposta abaixo segue o princípio de declarar a mais em caso de
dúvida.

- **O app coleta ou compartilha dados?** → **Sim** (deixa de ser "Nenhum dado coletado").
- **Criptografado em trânsito?** → Sim (os dois SDKs enviam por HTTPS) [confirmar na doc de cada SDK].
- **Usuário pode pedir exclusão?** → ver P7.

| Tipo de dado (Play) | Coletado | Compartilhado | Opcional? | Finalidade |
|---|---|---|---|---|
| IDs do dispositivo ou outros IDs (ID de publicidade → Meta; ID de instalação do Firebase) | Sim | Sim (Meta) | Sim (consentimento) | Publicidade ou marketing; Análise |
| Atividade no app › Interações com o app (etapas do funil) | Sim | Não (Firebase como provedor [V2]) | Sim | Análise |
| Informações financeiras › Histórico de compras (valor/moeda do passe → Meta e Firebase) | Sim | Sim (Meta) | Sim | Publicidade ou marketing; Análise |
| Informações e desempenho do app › Registros de falhas / Diagnóstico | Só se V7 mostrar que o SDK da Meta envia crash/ANR mesmo com auto-log desligado | — | — | — |
| Uso de apps / histórico | **Não** (continua só no aparelho) | — | — | — |

Texto livre sugerido: "Uso de aplicativos, limites e histórico ficam só no aparelho. Com autorização
do usuário, o app envia etapas de uso ao Google Firebase Analytics e eventos de instalação, cadastro
e compra à Meta, com o ID de publicidade. Nunca envia quais apps o usuário usa nem tempo de uso."

## Proposta: declaração de Advertising ID (Play Console)

[A VERIFICAR — V1 para o texto exato do formulário.] Resposta: **"Sim, o app usa o ID de
publicidade"**, finalidades **Publicidade ou marketing** e **Análise**. O manifesto final passa a ter
`com.google.android.gms.permission.AD_ID` (vem do `facebook-core`, F2/F4). A declaração precisa ser
enviada **antes ou junto** do release que contém a permissão.

---

## Proposta: nota no E19 (texto para acrescentar em `E19-sigilo-bancario-privacidade.md`)

**Não aplicado.** Seção nova "Revisão pelo E22 (mensuração)":

> **O que continua verdade**
> - Não há servidor do DollarBlock, conta, login, nem coleta de nome, e-mail ou telefone.
> - Quais apps a pessoa usa, por quanto tempo, os limites e o extrato ficam só no aparelho e fora do
>   backup em nuvem.
> - O serviço de Acessibilidade só vê o nome do app aberto e nada dele sai do celular
>   (`onb_trust_lock_*` não muda).
> - O Google Play processa o cartão; o DollarBlock não o vê.
> - Sem autorização, o app continua não enviando nada.
>
> **O que deixa de ser verdade**
> - "Não tem rastreador de anúncios nem de estatísticas." Agora tem dois: Meta App Events e Firebase
>   Analytics, desligados até a autorização.
> - "Seus dados não saem do seu celular" como frase absoluta. Com autorização, saem etapas do funil,
>   o ID de publicidade e o valor das compras do passe.
> - O selo "Nenhum dado coletado" do Data Safety. Some da ficha.
> - "Não sobra cópia nossa em lugar nenhum, porque nunca houve uma." Para quem autorizou, há cópia de
>   eventos no Google e na Meta, sob a retenção deles — e a Meta usa o dado também para publicidade
>   dela, não só para medir o nosso anúncio.
> - A pendência "remover `INTERNET` do manifesto" como prova verificável fica cancelada.
>
> **Como o argumento fica:** "Sigilo bancário" passa a significar "o seu extrato não sai daqui; a
> medição é opcional e não leva o extrato". É mais fraco que antes e deve ser dito assim, sem
> maquiar. A tela de bloqueio e o Perfil não podem mais dizer "nada sai do celular".

---

## Requisitos

- R1: `facebook-core` (só ele) e `firebase-analytics` (só ele) adicionados; nenhum outro módulo da
  Meta ou do Firebase.
- R2: Sem consentimento, nenhum dos dois SDKs inicializa nem envia nada — verificado por tráfego de
  rede (proxy ou `adb logcat` com logs de debug dos SDKs) num aparelho limpo.
- R3: Consentimento granular (estatística / anúncios), desligado por padrão, pedido no onboarding e
  uma vez para quem já instalou; revogável em Perfil › Preferências.
- R4: Eventos exatamente os da tabela; nenhum parâmetro fora dela; nenhum `packageName`, nome de
  app, duração ou dado da Acessibilidade em qualquer evento.
- R5: `fb_mobile_purchase` com valor e moeda reais do Play Billing (BRL/USD/INR), só em compra real.
- R6: Manifesto final: `AD_ID` presente; `ACCESS_ADSERVICES_*` e receivers de token removidos;
  `AutoInit`, `AutoLogAppEvents`, `AdvertiserIDCollection`, `AutoLogAppMetadata` = `false`;
  `firebase_analytics_collection_enabled`, `google_analytics_adid_collection_enabled`,
  `google_analytics_default_allow_ad_personalization_signals` = `false`.
- R7: Textos do app (en/pt/hi), política, Data Safety e declaração de Advertising ID atualizados
  **no mesmo release**. Nenhum texto do app afirma algo que deixou de ser verdade.
- R8: Testes unitários para a lógica pura (regras "uma vez", faixa de `qtd_apps_com_limite`,
  conversão micros→BigDecimal, decisão "envia/não envia" por consentimento).

## Tarefas

- [ ] T0: Verificar V1–V10 na documentação oficial e corrigir esta spec onde divergir.
- [ ] T1: Painel (dono) — ver checklist manual. Gera App ID, Client Token e `google-services.json`.
- [ ] T2: Dependências + plugin `com.google.gms.google-services`; App ID / Client Token fora do
  repositório (`local.properties` → `resValue`), `google-services.json` no `.gitignore` [decidir P5].
- [ ] T3: Manifesto (R6) e conferência do manifesto mesclado (`processReleaseManifest`).
- [ ] T4: `core/analytics/` — interface `Analytics`, `MetaSink`, `FirebaseSink`, `NoOpSink`;
  `ConsentPreferences` e `AnalyticsPreferences` (flags "uma vez"); módulo Hilt próprio
  (`di/AnalyticsModule.kt`).
- [ ] T5: Folha de consentimento após o Contrato + pergunta única para usuários existentes.
- [ ] T6: Dois switches em Perfil › Preferências.
- [ ] T7: Instrumentar E2–E9 nos pontos: `OnboardingViewModel` (permissões, conclusão),
  `AppsViewModel` (primeiro limite), `BlockActivity` (`onCreate`, `onPaymentSuccess`,
  `onCourtesyUnlock`, `goHome`).
- [ ] T8: Strings (en/pt/hi) da folha, dos switches e de `privacy_*`.
- [ ] T9: Aplicar as propostas em `PLAYSTORE_PRIVACY_SUBMISSION.md` e no E19.
- [ ] T10: Medir tamanho antes/depois e registrar aqui.
- [ ] T11: Testes unitários (R8) + grep no `pre-release-check.sh` impedindo import de
  `com.facebook`/`com.google.firebase` fora de `core/analytics/`.
- [ ] T12: Validar em aparelho real: Firebase DebugView e Meta "Test Events" (Events Manager)
  recebem só os eventos da tabela; com consentimento recusado, nada chega.
- [ ] T13: `CHANGELOG.md`; release só com os dois scripts verdes (regra do `CLAUDE.md`) e com
  autorização do dono.

## Critérios de aceite

- CA1: `:app:assembleDebug` e `:app:testDebugUnitTest` verdes; `pre-release-check.sh --bundle` e
  smoke test verdes no release.
- CA2: Aparelho limpo, recusar tudo: zero requisições para domínios da Meta e do Firebase durante
  onboarding, limite, bloqueio e compra.
- CA3: Aceitar só estatística: DebugView mostra E2–E9; Meta Test Events não recebe nada.
- CA4: Aceitar só anúncios: Meta Test Events recebe E1, E4m, E7m (com BRL e valor do Play); Firebase
  nada.
- CA5: Nenhum evento contém nome de pacote, nome de app ou duração (inspeção no DebugView/Test
  Events e teste unitário das assinaturas).
- CA6: Desligar no Perfil para os envios na hora, sem reiniciar o app.
- CA7: Aumento de tamanho medido e registrado.

---

## Checklist manual do dono (painéis)

Nada disso gera custo por si; **nenhuma campanha é criada aqui**.

**Meta for Developers** (developers.facebook.com)
1. Criar app (tipo para eventos de app/anúncios) ligado ao Business Manager da empresa — não à conta
   pessoal do Rafael. Observação: o app Meta 1662908845474347, usado pelo marketing para publicar,
   está com "API access blocked"; decidir se reaproveita ou cria outro (P1).
2. Configurações › Básico: e-mail de contato, URL da política de privacidade, categoria.
3. Adicionar plataforma **Android**: package `com.dollarblock`, classe `com.dollarblock.MainActivity`.
4. Key hashes (F2) — três:
   - Debug: `keytool -exportcert -alias androiddebugkey -keystore %HOMEPATH%\.android\debug.keystore | openssl sha1 -binary | openssl base64`
   - Upload/release local: mesmo comando com o alias e o keystore de `local.properties`.
   - **Play App Signing** (é a chave que assina o que a Play Store entrega): Play Console › Configuração
     › Integridade do app › Assinatura do app → copiar o SHA-1 da "chave de assinatura do app" e
     converter para base64 [conversão SHA-1 hex → base64 não coberta pela F2; comando a confirmar
     em T0].
5. Copiar **App ID** e **Client Token** (Configurações › Avançado) e passar por canal seguro, nunca
   em arquivo versionado.
6. Events Manager: ligar o app à conta de anúncios; abrir "Test Events" para a validação T12.
7. Desligar no painel qualquer "automatic event logging" / "log in-app events automatically"
   (F1 diz que o toggle do painel também controla).
8. Install Referrer Decryption Key (Configurações › Básico › Android, F7): **não necessário nesta
   fase** (ver Meta Install Referrer acima).

**Firebase** (console.firebase.google.com)
1. Criar projeto (plano Spark, sem cartão) [custo V4]. Ao criar, vincular ao Google Analytics.
2. Adicionar app Android `com.dollarblock`; SHA-1 opcional para Analytics. Baixar
   `google-services.json`.
3. GA4 › Admin › Retenção de dados: escolher o **menor** prazo disponível.
4. GA4 › Admin › Coleta de dados: deixar **Google Signals desligado**; não vincular Google Ads.
5. Não habilitar Crashlytics, Remote Config, Messaging, BigQuery.

**Play Console**
1. Política do app › **ID de publicidade**: declarar uso (Publicidade/marketing + Análise) [V1].
2. Política do app › **Segurança dos dados**: refazer conforme a proposta acima [V2].
3. Atualizar a política de privacidade publicada (mesma URL) antes de enviar o release.
4. Copiar o SHA-1 da chave do Play App Signing para o passo 4 da Meta.
5. Só depois disso: enviar o release (decisão do dono; regra dos dois scripts).

---

## Perguntas em aberto (decisão do dono antes de implementar)

- P1: App Meta novo para o DollarBlock (no Business Manager da empresa) ou reaproveitar o
  1662908845474347, que está bloqueado para API?
- P2: Aceita **dois SDKs** (Meta para os 3 eventos de anúncio + Firebase para o funil)? Ou prefere um
  SDK só (Meta para tudo), perdendo funil/retenção prontos e mandando o funil inteiro para a Meta?
- P3: Manter `AD_ID` (atribuição melhor, declaração de Advertising ID, dado compartilhado com a Meta)?
  A alternativa — remover — reduz o que a Meta consegue atribuir e não está documentada como
  suportada para campanha de app [V8].
- P4: Meta Install Referrer fica fora agora (sem MMP, sem servidor). Concorda? Reabrir se entrar
  AppsFlyer/Adjust etc.
- P5: Onde guardar App ID/Client Token e `google-services.json`: fora do git (`local.properties` +
  arquivo ignorado, igual às chaves de assinatura) ou versionados? (O Client Token não é segredo
  forte, mas a regra do repo de marketing é não versionar credenciais.)
- P6: Se o aumento de tamanho passar do limite, autoriza ligar R8 (`isMinifyEnabled = true`) no
  release? É mudança de build com risco e teste próprios.
- P7: Exclusão de dados enviados: oferecer pedido por e-mail (e usar as APIs de exclusão do
  Firebase/Meta à mão) ou declarar no Data Safety que não há exclusão? Qual e-mail de contato?
- P8: Folha de consentimento logo após o Contrato (proposta) ou no fim do onboarding? Depois do
  Contrato mede mais o funil; no fim pede menos atenção num momento em que a pessoa ainda está
  desconfiada das permissões.
- P9: Os ~10 usuários atuais: perguntar uma vez na próxima abertura (proposta) ou deixar só a opção
  em Preferências?
- P10: Enviar `passe_comprado` com valor também ao Firebase (tabela atual) ou só à Meta?
- P11: EUA: tratar Limited Data Use [V9] já neste épico ou só quando a campanha americana for
  aprovada?
