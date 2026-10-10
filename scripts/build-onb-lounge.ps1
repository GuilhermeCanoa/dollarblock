# Gera a arte de primeiro plano da cena de praia do onboarding (página da Medição),
# app/src/main/res/drawable-nodpi/onb_lounge_fg.png, a partir da ilustração original em
# docs/art/onb_lounge_source.webp. Só roda à mão (Windows), fora do build:
#
#   powershell -File scripts/build-onb-lounge.ps1
#
# As coordenadas usadas no app (OnboardingLoungeScene.kt: CanopyTop, BandWords) dependem
# deste recorte; se mudar o recorte, confira as de lá.
param(
  [string]$Src = "$PSScriptRoot\..\docs\art\onb_lounge_source.webp",
  [string]$Out = "$PSScriptRoot\..\app\src\main\res\drawable-nodpi\onb_lounge_fg.png"
)
$Src = [IO.Path]::GetFullPath($Src); $Out = [IO.Path]::GetFullPath($Out)
# Gera onb_lounge_fg.png: só o primeiro plano da ilustração original (guarda-sol, mastro,
# personagem, cadeira, cooler, garrafa), com fundo transparente, tudo em tons de verde (mapa
# de luminância) — menos a lona listrada da cadeira, que fica colorida. O cenário de praia é
# desenhado no app (OnboardingLoungeScene.kt), por trás desta imagem.
Add-Type -AssemblyName PresentationCore

Add-Type -TypeDefinition @"
using System;
public static class LoungeFg {
    static bool InPoly(double[,] p, double x, double y) {
        bool inside = false; int n = p.GetLength(0);
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = p[i,0], yi = p[i,1], xj = p[j,0], yj = p[j,1];
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) inside = !inside;
        }
        return inside;
    }
    static readonly double[,] Canopy = {
        {565,368},{665,367},{735,375},{812,398},{823,420},{815,425},{795,458},{785,463},
        {706,502},{615,532},{595,536},{560,540},{488,549},{478,546},{437,542},{435,528},
        {447,507},{479,455},{511,417},{538,392}
    };
    static readonly double[,] Body = {
        {627,626},{727,626},{738,630},{762,652},{792,673},{827,675},{843,692},{850,721},
        {854,755},{870,771},{866,782},{842,790},{840,796},{640,796},{625,700},{620,640}
    };
    static readonly double[,] Fabric = { {636,626},{728,626},{772,750},{670,750} };
    static double PoleX(double y) { return y < 541 ? 565 + 38 * (y - 370) / 171.0 : 603 + (y - 541) * 0.215; }

    static bool Foreground(double x, double y) {
        if (InPoly(Canopy, x, y)) return true;
        if (y > 372 && y < 796 && Math.Abs(x - PoleX(y)) < 8) return true;
        double hx = (x - 705) / 44.0, hy = (y - 590) / 37.0;
        if (hx * hx + hy * hy <= 1) return true;                              // cabeça
        if (InPoly(Body, x, y)) return true;                                  // corpo + cadeira
        if (x >= 499 && x <= 640 && y >= 690 && y <= 790) return true;         // cooler
        if (x >= 551 && x <= 579 && y >= 650 && y <= 700) return true;         // garrafa
        return false;
    }

    // Mapa de verdes por luminância: do verde-escuro do app ao menta.
    static readonly double[] Stops = { 0.0, 0.35, 0.62, 0.85, 1.0 };
    static readonly int[,] Greens = { {5,22,15}, {16,74,50}, {38,150,94}, {140,226,170}, {232,255,238} };
    static void MapGreen(double l, out int r, out int g, out int b) {
        int k = 0; while (k < Stops.Length - 2 && l > Stops[k + 1]) k++;
        double t = Math.Min(1, Math.Max(0, (l - Stops[k]) / (Stops[k + 1] - Stops[k])));
        r = (int)(Greens[k,0] + (Greens[k+1,0] - Greens[k,0]) * t);
        g = (int)(Greens[k,1] + (Greens[k+1,1] - Greens[k,1]) * t);
        b = (int)(Greens[k,2] + (Greens[k+1,2] - Greens[k,2]) * t);
    }

    public static byte[] Build(byte[] px, int w, int cx, int cy, int cw, int ch) {
        int st = w * 4;
        byte[] outp = new byte[cw * ch * 4];
        double[] alpha = new double[cw * ch];
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) {
            int X = x + cx, Y = y + cy;
            if (!Foreground(X, Y)) continue;
            int o = Y * st + X * 4; int B = px[o], G = px[o+1], R = px[o+2];
            // Chão de pedra entre as pernas da cadeira e sob o cooler: fica transparente.
            if (Y > 748 && R > B + 30 && G > B + 8 && !(InPoly(Fabric, X, Y))) {
                int mx = Math.Max(R, Math.Max(G, B)), mn = Math.Min(R, Math.Min(G, B));
                if (mx - mn > 28) continue;
            }
            alpha[y * cw + x] = 1;
        }
        // Borda suave (média 3x3 da máscara).
        double[] soft = new double[cw * ch];
        for (int y = 1; y < ch - 1; y++) for (int x = 1; x < cw - 1; x++) {
            double s = 0; for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) s += alpha[(y + dy) * cw + x + dx];
            soft[y * cw + x] = s / 9.0;
        }
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) {
            double a = soft[y * cw + x]; if (a <= 0) continue;
            int X = x + cx, Y = y + cy;
            int o = Y * st + X * 4; int B = px[o], G = px[o+1], R = px[o+2];
            int mx = Math.Max(R, Math.Max(G, B)), mn = Math.Min(R, Math.Min(G, B));
            int r, g, b;
            bool chairColor = InPoly(Fabric, X, Y) && mx > 60 && (mx - mn) > mx * 0.35;
            if (chairColor) { r = R; g = G; b = B; }
            else MapGreen((0.299 * R + 0.587 * G + 0.114 * B) / 255.0, out r, out g, out b);
            int oo = (y * cw + x) * 4;
            outp[oo] = (byte)b; outp[oo+1] = (byte)g; outp[oo+2] = (byte)r; outp[oo+3] = (byte)(a * 255);
        }
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

$CX = 425; $CY = 335; $CW = 460; $CH = 460
$fg = [LoungeFg]::Build($px, $W, $CX, $CY, $CW, $CH)
$bs = [Windows.Media.Imaging.BitmapSource]::Create($CW, $CH, 96, 96, [Windows.Media.PixelFormats]::Bgra32, $null, $fg, $CW * 4)
$enc = New-Object Windows.Media.Imaging.PngBitmapEncoder
$enc.Frames.Add([Windows.Media.Imaging.BitmapFrame]::Create($bs))
$o = [IO.File]::Create($Out); $enc.Save($o); $o.Close()
"fg ${CW}x${CH}"
