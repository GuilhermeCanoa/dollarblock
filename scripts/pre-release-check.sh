#!/bin/sh
#
# DollarBlock — verificação pré-release.
#
# Roda tudo o que precisa estar verde antes de subir um build na Play Store,
# sem teste manual e sem emulador. Uso:
#
#   sh scripts/pre-release-check.sh          # testes + checagens de release
#   sh scripts/pre-release-check.sh --bundle # + gera o AAB assinado
#
# Smoke test no emulador (opcional, ~4 min, precisa de um emulador rodando):
#   powershell -File scripts/smoke-test-emulator.ps1
#
# Sai com status != 0 se qualquer etapa falhar — dá para usar em CI.

set -e

cd "$(dirname "$0")/.."

BUILD_BUNDLE=0
[ "$1" = "--bundle" ] && BUILD_BUNDLE=1

# Aponta JAVA_HOME para a JBR do Android Studio se não houver JDK no PATH.
if [ -z "$JAVA_HOME" ]; then
  if [ -d "/c/Program Files/Android/Android Studio/jbr" ]; then
    export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
  elif [ -d "$HOME/android-studio/jbr" ]; then
    export JAVA_HOME="$HOME/android-studio/jbr"
  fi
fi

case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*) GRADLEW="./gradlew.bat" ;;
  *)                        GRADLEW="./gradlew" ;;
esac

GRADLE_ARGS="--console=plain --no-daemon"

step() { echo ""; echo "───────────────────────────────────────────"; echo "▶ $1"; echo "───────────────────────────────────────────"; }

# ——— 1. Testes unitários (inclui os de cortesia/E17 e os de DAO via Robolectric) ———
step "Testes unitários"
"$GRADLEW" :app:testDebugUnitTest $GRADLE_ARGS

# Resumo por suíte a partir do relatório XML.
if [ -d app/build/test-results/testDebugUnitTest ]; then
  echo ""
  echo "Suítes executadas:"
  grep -oh 'testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
    app/build/test-results/testDebugUnitTest/*.xml 2>/dev/null \
    | sed 's/testsuite //' | sed 's/^/  /'
  TOTAL=$(grep -oh 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml 2>/dev/null \
    | grep -o '[0-9]*' | awk '{s+=$1} END {print s+0}')
  echo "  → $TOTAL testes no total"
fi

# ——— 2. Sanidade do release: versão bumpada e assinatura configurada ———
step "Checagens de release"

VERSION_CODE=$(grep -o 'versionCode = [0-9]*' app/build.gradle.kts | grep -o '[0-9]*')
VERSION_NAME=$(grep -o 'versionName = "[^"]*"' app/build.gradle.kts | sed 's/.*"\(.*\)"/\1/')
echo "  versionCode = $VERSION_CODE / versionName = $VERSION_NAME"

# A Play Store recusa um versionCode já usado. Compara com a última tag de release,
# se existir (formato v<versionName>).
LAST_TAG=$(git tag --list 'v*' --sort=-v:refname | head -1)
if [ -n "$LAST_TAG" ]; then
  echo "  última tag de release: $LAST_TAG"
  if [ "v$VERSION_NAME" = "$LAST_TAG" ]; then
    echo "  ❌ versionName $VERSION_NAME já foi publicado (tag $LAST_TAG). Faça o bump."
    exit 1
  fi
fi

if grep -q 'RELEASE_STORE_FILE' local.properties 2>/dev/null; then
  echo "  ✅ chaves de assinatura presentes em local.properties"
else
  echo "  ❌ RELEASE_STORE_FILE ausente em local.properties — o AAB sairia sem assinatura."
  exit 1
fi

# local.properties guarda senha de keystore: nunca pode ir para o git.
if git ls-files --error-unmatch local.properties >/dev/null 2>&1; then
  echo "  ❌ local.properties está versionado no git — contém segredos. Remova do índice."
  exit 1
fi
echo "  ✅ local.properties fora do controle de versão"

# ——— 3. AAB assinado (opcional) ———
if [ "$BUILD_BUNDLE" = "1" ]; then
  step "Bundle de release assinado"
  "$GRADLEW" :app:bundleRelease $GRADLE_ARGS

  AAB=app/build/outputs/bundle/release/app-release.aab
  if [ ! -f "$AAB" ]; then
    echo "  ❌ AAB não foi gerado em $AAB"
    exit 1
  fi

  # Um AAB não assinado sobe na Play e é recusado só depois do upload — checar aqui.
  if unzip -l "$AAB" | grep -q 'META-INF/.*\.RSA'; then
    echo "  ✅ AAB assinado: $AAB"
  else
    echo "  ❌ AAB SEM assinatura: $AAB"
    exit 1
  fi
fi

echo ""
echo "═══════════════════════════════════════════"
echo "✅ Tudo verde — pode subir na Play Store."
echo "═══════════════════════════════════════════"
echo ""
echo "Opcional (valida a UI de ponta a ponta num emulador rodando):"
echo "  powershell -File scripts/smoke-test-emulator.ps1"
