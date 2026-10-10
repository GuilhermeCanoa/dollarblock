# Gera a arte de primeiro plano da cena de praia do onboarding (página da Medição),
# app/src/main/res/drawable-nodpi/onb_lounge_fg.png, a partir da ilustração em
# docs/art/onb_lounge_source.webp (1672×471, já em verdes, com o letreiro "Dollar Block"
# pintado no guarda-sol e o personagem de braços atrás da nuca). Só roda à mão (Windows),
# fora do build:
#
#   powershell -File scripts/build-onb-lounge.ps1
#
# Recorta o primeiro plano (guarda-sol, mastro, personagem, cadeira, cooler, garrafa e as
# sombras deles na areia) com fundo transparente. O cenário de praia é desenhado no app
# (OnboardingLoungeScene.kt), por trás desta imagem. As coordenadas usadas lá (FG_W/FG_H,
# CanopyTop) dependem deste recorte; o script imprime o contorno de cima do guarda-sol
# para atualizar o CanopyTop se o recorte mudar.
param(
  [string]$Src = "$PSScriptRoot\..\docs\art\onb_lounge_source.webp",
  [string]$Out = "$PSScriptRoot\..\app\src\main\res\drawable-nodpi\onb_lounge_fg.png"
)
$Src = [IO.Path]::GetFullPath($Src); $Out = [IO.Path]::GetFullPath($Out)
Add-Type -AssemblyName PresentationCore

Add-Type -TypeDefinition @"
using System;
using System.Collections.Generic;
public static class LoungeFg {
    // Fundo de referência de cada linha (céu, mar ou areia na mesma altura): uma faixa sem
    // objetos em cada lado — a borda esquerda e um trecho à direita do recorte —, interpolada
    // na horizontal (o mar e a areia escurecem para a esquerda).
    const int LeftX0 = 0, LeftX1 = 8, RightX0 = 600, RightX1 = 640;
    // Diferença mínima (maior canal) para um pixel contar como objeto. Na areia, objetos e
    // sombras são bem escuros; lá também não se preenchem buracos (a areia entre as pernas
    // da cadeira continua areia).
    const int Threshold = 30, SandThreshold = 50, InterpY = 200, SeaY = 285, SandY = 380;
    const int MinBlob = 1200;         // pedaços menores que isso (espuma, ondas) saem
    const int FadeRows = 34;          // as sombras somem aos poucos na base do recorte

    static void Seed(bool[] fg, bool[] outside, Stack<int> stack, int cw, int x, int y) {
        int i = y * cw + x; if (!fg[i] && !outside[i]) { outside[i] = true; stack.Push(i); }
    }

    static double[,] Band(byte[] px, int st, int h, int x0, int x1) {
        var bg = new double[h, 3];
        for (int y = 0; y < h; y++) {
            for (int x = x0; x < x1; x++) { int o = y * st + x * 4; bg[y,0] += px[o+2]; bg[y,1] += px[o+1]; bg[y,2] += px[o]; }
            for (int k = 0; k < 3; k++) bg[y,k] /= (x1 - x0);
        }
        return bg;
    }

    public static byte[] Build(byte[] px, int w, int h, int cx, int cy, int cw, int ch, out string canopy) {
        int st = w * 4;
        var left = Band(px, st, h, LeftX0, LeftX1);
        var right = Band(px, st, h, RightX0, RightX1);
        double lx = (LeftX0 + LeftX1) / 2.0, rx = (RightX0 + RightX1) / 2.0;
        // 1. Candidatos a objeto: longe da cor do fundo naquele ponto.
        var fg = new bool[cw * ch];
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) {
            int X = x + cx, Y = y + cy; int o = Y * st + X * 4;
            // No alto o céu é uniforme na horizontal; perto do horizonte, no mar e na areia, não.
            double t = Y < InterpY ? 1 : Math.Min(1, Math.Max(0, (X - lx) / (rx - lx)));
            double r = left[Y,0] + (right[Y,0] - left[Y,0]) * t;
            double g = left[Y,1] + (right[Y,1] - left[Y,1]) * t;
            double b = left[Y,2] + (right[Y,2] - left[Y,2]) * t;
            double d = Math.Max(Math.Abs(px[o+2] - r), Math.Max(Math.Abs(px[o+1] - g), Math.Abs(px[o] - b)));
            fg[y * cw + x] = d > (Y >= SandY ? SandThreshold : Threshold);
        }
        // Linhas finas na altura do mar (espuma, cristas de onda) não são objeto: exige
        // pelo menos 7 px de altura ali.
        var thick = (bool[])fg.Clone();
        for (int y = 3; y < ch - 3; y++) {
            int Y = y + cy; if (Y < SeaY || Y >= SandY + 8) continue;
            for (int x = 0; x < cw; x++) { int i = y * cw + x; if (fg[i] && !(fg[i - 3 * cw] && fg[i + 3 * cw])) thick[i] = false; }
        }
        // ...e devolve a borda dos objetos grossos (abertura: afina, depois engorda de volta).
        var opened = (bool[])fg.Clone();
        for (int y = 3; y < ch - 3; y++) {
            int Y = y + cy; if (Y < SeaY || Y >= SandY + 8) continue;
            for (int x = 0; x < cw; x++) {
                int i = y * cw + x;
                opened[i] = fg[i] && (thick[i - 3 * cw] || thick[i - 2 * cw] || thick[i - cw] || thick[i] || thick[i + cw] || thick[i + 2 * cw] || thick[i + 3 * cw]);
            }
        }
        fg = opened;
        // 2. Tira os pedacinhos soltos.
        var label = new int[cw * ch]; int next = 0; var stack = new Stack<int>();
        for (int i = 0; i < fg.Length; i++) {
            if (!fg[i] || label[i] != 0) continue;
            next++; var members = new List<int>(); stack.Push(i); label[i] = next;
            while (stack.Count > 0) {
                int p = stack.Pop(); members.Add(p); int px0 = p % cw, py0 = p / cw;
                for (int k = 0; k < 4; k++) {
                    int nx = px0 + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = py0 + (k == 2 ? 1 : k == 3 ? -1 : 0);
                    if (nx < 0 || ny < 0 || nx >= cw || ny >= ch) continue;
                    int q = ny * cw + nx; if (fg[q] && label[q] == 0) { label[q] = next; stack.Push(q); }
                }
            }
            if (members.Count < MinBlob) foreach (int p in members) fg[p] = false;
        }
        // 3. Preenche os buracos: fundo é só o que se liga à borda do recorte.
        var outside = new bool[cw * ch];
        for (int x = 0; x < cw; x++) { Seed(fg, outside, stack, cw, x, 0); Seed(fg, outside, stack, cw, x, ch - 1); }
        for (int y = 0; y < ch; y++) { Seed(fg, outside, stack, cw, 0, y); Seed(fg, outside, stack, cw, cw - 1, y); }
        while (stack.Count > 0) {
            int p = stack.Pop(); int px0 = p % cw, py0 = p / cw;
            for (int k = 0; k < 4; k++) {
                int nx = px0 + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = py0 + (k == 2 ? 1 : k == 3 ? -1 : 0);
                if (nx < 0 || ny < 0 || nx >= cw || ny >= ch) continue;
                int q = ny * cw + nx; if (!fg[q] && !outside[q]) { outside[q] = true; stack.Push(q); }
            }
        }
        // Na faixa da areia não há buraco a preencher: o que não é objeto é chão.
        for (int y = Math.Max(0, SandY - cy); y < ch; y++) for (int x = 0; x < cw; x++) if (!fg[y * cw + x]) outside[y * cw + x] = true;
        // 4. Borda suave (média 3x3 da máscara) e cor original.
        var outp = new byte[cw * ch * 4];
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) {
            double s = 0; int n = 0;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy; if (nx < 0 || ny < 0 || nx >= cw || ny >= ch) continue;
                n++; if (!outside[ny * cw + nx]) s++;
            }
            double a = s / n;
            a *= Math.Min(1.0, (ch - 1 - y) / (double)FadeRows);
            if (a <= 0) continue;
            int o = (y + cy) * st + (x + cx) * 4, oo = (y * cw + x) * 4;
            outp[oo] = px[o]; outp[oo+1] = px[o+1]; outp[oo+2] = px[o+2]; outp[oo+3] = (byte)(a * 255);
        }
        // Contorno de cima do guarda-sol (para o CanopyTop do app): 1º pixel de objeto por coluna.
        var sb = new System.Text.StringBuilder();
        for (int x = 10; x < cw; x += 20) {
            for (int y = 0; y < 230 && y < ch; y++) if (!outside[y * cw + x]) { sb.AppendFormat("({0},{1}) ", x, y); break; }
        }
        canopy = sb.ToString();
        return outp;

    }
}
"@

$fs = [IO.File]::OpenRead($Src)
$f = [Windows.Media.Imaging.BitmapDecoder]::Create($fs, 'PreservePixelFormat', 'OnLoad').Frames[0]
$fs.Close()
$conv = New-Object Windows.Media.Imaging.FormatConvertedBitmap($f, [Windows.Media.PixelFormats]::Bgra32, $null, 0)
$W = $conv.PixelWidth; $H = $conv.PixelHeight; $st = $W * 4
$px = New-Object byte[] ($st * $H)
$conv.CopyPixels($px, $st, 0)

$CX = 0; $CY = 8; $CW = 560; $CH = 460
$canopy = ""
$fg = [LoungeFg]::Build($px, $W, $H, $CX, $CY, $CW, $CH, [ref]$canopy)
$bs = [Windows.Media.Imaging.BitmapSource]::Create($CW, $CH, 96, 96, [Windows.Media.PixelFormats]::Bgra32, $null, $fg, $CW * 4)
$enc = New-Object Windows.Media.Imaging.PngBitmapEncoder
$enc.Frames.Add([Windows.Media.Imaging.BitmapFrame]::Create($bs))
$o = [IO.File]::Create($Out); $enc.Save($o); $o.Close()
"fg ${CW}x${CH}"
"topo do guarda-sol: $canopy"
