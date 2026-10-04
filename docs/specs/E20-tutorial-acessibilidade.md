# Spec: Tutorial visual para ligar a Acessibilidade

**Status:** done
**Épico:** E20 (adhoc — UX de onboarding, sobre o E18)
**Data de criação:** 2026-10-04
**Última atualização:** 2026-10-04

---

## Contexto

Ligar o serviço de acessibilidade exige uma sequência exata de toques nas Configurações do
Android. É a permissão em que mais gente se perde — e é obrigatória para o bloqueio.

## Requisitos

- R1: Na página de Acessibilidade do onboarding (E18), enquanto não concedida, um link
  "Ver como ativar" abre um pop-up pequeno.
- R2: O pop-up é só imagem, sem texto: 4 quadros recortados das Configurações, com o alvo de
  cada toque contornado em verde, alternando em loop como um GIF (1,4 s por quadro) + pontos de passo.
- R3: Sem dependência nova (sem Coil/GIF decoder): quadros WebP em `drawable-nodpi` e
  `Crossfade` no Compose — funciona do minSdk 26 em diante.

## Passos mostrados

1. Lista de Acessibilidade → **DollarBlock** (em "Apps baixados")
2. Chave **Usar DollarBlock**
3. **Permitir** no diálogo de controle total
4. Chave ligada

## Notas / Decisões

- Capturas feitas no emulador (Pixel, API 36) com as Configurações em pt-BR
  (`cmd locale set-app-locales com.android.settings --locales pt-BR`). Em fabricantes como
  Samsung/Xiaomi o caminho muda um pouco ("Apps instalados"), mas os três toques são os mesmos.
- Para regenerar os quadros: `scripts/a11y-tutorial/make_frames.py` (screenshots-fonte na mesma pasta).
