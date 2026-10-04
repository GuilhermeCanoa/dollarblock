"""Gera os quadros do tutorial de Acessibilidade (res/drawable-nodpi/a11y_tutorial_N.webp).

Entrada: 01.png..04.png — screenshots 1080x2400 do emulador (API 36, Configurações em pt-BR via
`adb shell cmd locale set-app-locales com.android.settings --locales pt-BR`), tiradas a partir de
`am start -a android.settings.ACCESSIBILITY_SETTINGS`: lista → tela do DollarBlock →
diálogo "Permitir" → chave ligada. Ver docs/specs/E20-tutorial-acessibilidade.md.
Uso: python make_frames.py <pasta-dos-pngs> <pasta-de-saida>
"""
import sys
from PIL import Image, ImageDraw

# (arquivo, topo do recorte, retângulo do alvo em px da tela)
STEPS = [
    ("01.png", 200, (24, 355, 1056, 545)),
    ("02.png", 200, (830, 270, 1003, 422)),
    ("03.png", 1040, (70, 1565, 1010, 1712)),
    ("04.png", 200, (830, 270, 1003, 422)),
]
GREEN = (0, 230, 118)

src, out = sys.argv[1], sys.argv[2]
for i, (name, top, (x1, y1, x2, y2)) in enumerate(STEPS, 1):
    im = Image.open(f"{src}/{name}").convert("RGB").crop((0, top, 1080, top + 1000))
    ImageDraw.Draw(im).rounded_rectangle((x1, y1 - top, x2, y2 - top), radius=40, outline=GREEN, width=12)
    im.resize((540, 500), Image.LANCZOS).save(f"{out}/a11y_tutorial_{i}.webp", "WEBP", quality=82)
