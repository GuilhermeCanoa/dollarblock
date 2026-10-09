# Gera a arte da 1ª página do onboarding (app/src/main/res/drawable-nodpi/onb_hero.jpg)
# a partir do mockup em docs/art/onb_hero_mockup.webp. Só roda à mão (Windows), fora do build:
#
#   powershell -File scripts/build-onb-hero.ps1
#
# Etapas: recorte da ilustração (sem a barra de status) + 130 px de teto esticado; luminária
# 1.2x e mais alta; lixeira 85% x 80% e 30 px à esquerda com os ícones intactos; natureza no
# lugar dos prédios da janela. As animações (ventilador, luz, vapor, brilho) NÃO estão aqui:
# ficam em OnboardingHero.kt, cujas coordenadas dependem desta arte — se mexer em posição ou
# tamanho aqui, confira as constantes de lá (SocialTiles, LAMP_*, STEAM_*, FAN_*).
param(
  [string]$Src = "$PSScriptRoot\..\docs\art\onb_hero_mockup.webp",
  [string]$Out = "$PSScriptRoot\..\app\src\main\res\drawable-nodpi\onb_hero.jpg",
  [string]$Tmp = "$env:TEMP\onb_hero_ext.png"
)
$Src = [IO.Path]::GetFullPath($Src); $Out = [IO.Path]::GetFullPath($Out)
Add-Type -AssemblyName PresentationCore
Add-Type -AssemblyName System.Drawing

# 1) Recorte + teto esticado (WIC lê WebP)
$fs = [IO.File]::OpenRead($Src)
$f = [Windows.Media.Imaging.BitmapDecoder]::Create($fs, 'PreservePixelFormat', 'OnLoad').Frames[0]
$fs.Close()
$conv = New-Object Windows.Media.Imaging.FormatConvertedBitmap($f, [Windows.Media.PixelFormats]::Bgra32, $null, 0)
$W = 941; $Y0 = 78; $H = 612; $EXT = 130
$stride = $W * 4
$art = New-Object byte[] ($stride * $H)
$conv.CopyPixels((New-Object Windows.Int32Rect(0, $Y0, $W, $H)), $art, $stride, 0)
$buf = New-Object byte[] ($stride * ($H + $EXT))
$row = New-Object byte[] $stride
for ($i = 0; $i -lt $stride; $i++) { $row[$i] = [byte](([int]$art[$i] + $art[$stride + $i] + $art[2*$stride + $i]) / 3) }
for ($y = 0; $y -lt $EXT; $y++) { [Array]::Copy($row, 0, $buf, $y * $stride, $stride) }
[Array]::Copy($art, 0, $buf, $EXT * $stride, $art.Length)
$bs = [Windows.Media.Imaging.BitmapSource]::Create($W, $H + $EXT, 96, 96, [Windows.Media.PixelFormats]::Bgra32, $null, $buf, $stride)
$enc = New-Object Windows.Media.Imaging.PngBitmapEncoder
$enc.Frames.Add([Windows.Media.Imaging.BitmapFrame]::Create($bs))
$o = [IO.File]::Create($Tmp); $enc.Save($o); $o.Close()

# 2) Luminária: copia cúpula + topo do cone, aumenta 1.2x e sobe; a base se funde no cone original.
$base = [Drawing.Bitmap]::FromFile($Tmp)
$bmp = New-Object Drawing.Bitmap $base
$base.Dispose()
$srcRect = New-Object Drawing.Rectangle 10, 180, 140, 200      # x 10..150, y 180..380
$scale = 1.2
$dw = [int]($srcRect.Width * $scale); $dh = [int]($srcRect.Height * $scale)
$dx = 85 - [int]($dw / 2)                                      # mantém o eixo do fio (x≈85)
$dy = 112                                                      # topo novo (antes 180)
$lamp = New-Object Drawing.Bitmap $dw, $dh
$gl = [Drawing.Graphics]::FromImage($lamp)
$gl.InterpolationMode = 'HighQualityBicubic'; $gl.PixelOffsetMode = 'HighQuality'
$gl.DrawImage($bmp, (New-Object Drawing.Rectangle 0, 0, $dw, $dh), $srcRect, 'Pixel')
$gl.Dispose()
# Mistura pixel a pixel: opaco sobre a cúpula antiga (x<=155, y<=300), esmaecendo à direita,
# à esquerda e embaixo para não deixar emenda com o cone/parede originais.
function Clamp01($v) { if ($v -lt 0) { 0.0 } elseif ($v -gt 1) { 1.0 } else { $v } }
$fmt = [Drawing.Imaging.PixelFormat]::Format32bppArgb
$rB = New-Object Drawing.Rectangle 0, 0, $bmp.Width, $bmp.Height
$rL = New-Object Drawing.Rectangle 0, 0, $dw, $dh
$bdB = $bmp.LockBits($rB, 'ReadWrite', $fmt); $bdL = $lamp.LockBits($rL, 'ReadOnly', $fmt)
$pb = New-Object byte[] ($bdB.Stride * $bmp.Height); [Runtime.InteropServices.Marshal]::Copy($bdB.Scan0, $pb, 0, $pb.Length)
$pl = New-Object byte[] ($bdL.Stride * $dh); [Runtime.InteropServices.Marshal]::Copy($bdL.Scan0, $pl, 0, $pl.Length)
for ($y = 0; $y -lt $dh; $y++) {
  $iy = $dy + $y
  $fy = Clamp01 (1.0 - ($iy - 300) / 52.0)
  for ($x = 0; $x -lt $dw; $x++) {
    $ix = $dx + $x
    if ($ix -lt 0) { continue }
    $fx = [math]::Min((Clamp01 (1.0 - ($ix - 155) / 14.0)), (Clamp01 (($ix - 1) / 12.0)))
    $a = [math]::Min($fx, $fy)
    if ($a -le 0) { continue }
    $o = $iy * $bdB.Stride + $ix * 4; $l = $y * $bdL.Stride + $x * 4
    for ($c = 0; $c -lt 3; $c++) { $pb[$o + $c] = [byte]([math]::Round($pl[$l + $c] * $a + $pb[$o + $c] * (1 - $a))) }
  }
}
[Runtime.InteropServices.Marshal]::Copy($pb, 0, $bdB.Scan0, $pb.Length)
$bmp.UnlockBits($bdB); $lamp.UnlockBits($bdL); $lamp.Dispose()

# 2b) Lixeira menor (85% x 80%, ancorada na borda), ícones intactos.
# Região do balde: trapézio da borda (y 514) ao fundo (y 708), menos a zona dos ícones
# (x 48..268 acima de y 528, onde os ícones cobrem a borda de trás).
$TX0 = 14; $TX1 = 294; $BX0 = 58; $BX1 = 256; $TY = 496; $BY = 710
$CX = 155.0; $SXF = 0.85; $SYF = 0.80; $AY = 528  # âncora: a borda da frente fica na mesma altura
# contorno real de cada ícone (traçado na arte); acima da borda eles ficam intactos
$TilePolys = @(
  @(@(36,480),@(44,466),@(112,445),@(124,450),@(141,528),@(60,534),@(38,490)),   # Instagram
  @(@(129,452),@(138,440),@(206,449),@(214,460),@(207,528),@(131,528)),           # TikTok
  @(@(203,472),@(212,458),@(278,480),@(287,492),@(275,534),@(196,534))            # Facebook
)
function InPoly($poly, [double]$x, [double]$y) {
  $in = $false; $n = $poly.Count; $j = $n - 1
  for ($i = 0; $i -lt $n; $i++) {
    $xi = $poly[$i][0]; $yi = $poly[$i][1]; $xj = $poly[$j][0]; $yj = $poly[$j][1]
    if ((($yi -gt $y) -ne ($yj -gt $y)) -and ($x -lt ($xj - $xi) * ($y - $yi) / ($yj - $yi) + $xi)) { $in = -not $in }
    $j = $i
  }
  return $in
}
function InTile([double]$x, [double]$y) {
  foreach ($p in $TilePolys) { foreach ($o in @(@(0,0),@(2,0),@(-2,0),@(0,2),@(0,-2))) {
    if (InPoly $p ($x + $o[0]) ($y + $o[1])) { return $true } } }
  return $false
}
function InBucket([double]$x, [double]$y, [double]$pad) {
  if ($y -lt $TY - $pad -or $y -gt $BY + $pad) { return $false }
  $t = ($y - $TY) / ($BY - $TY); if ($t -lt 0) { $t = 0 }
  $l = $TX0 + $t * ($BX0 - $TX0) - $pad; $r = $TX1 + $t * ($BX1 - $TX1) + $pad
  if ($x -lt $l -or $x -gt $r) { return $false }
  if ($y -lt 528 -and (($x -gt 60 -and $x -lt 250) -or (InTile $x $y))) { return $false }
  return $true
}
# Deslocamento do conjunto (balde + ícones) para a esquerda: o Instagram quase encosta na borda.
$SHIFT = 30
# zona "ícones" (copiada intacta do original): contorno de cada ícone até onde a borda os
# cobre (y 538) + o miolo entre eles acima da borda (onde aparece a borda de trás)
function InIcons([double]$x, [double]$y) {
  (($y -lt 538) -and (InTile $x $y)) -or (($y -ge $TY) -and ($y -lt $AY) -and ($x -gt 60) -and ($x -lt 250))
}
# o que se apaga na posição original: balde + ícones
function InErase([double]$x, [double]$y) { (InBucket $x $y 3) -or (InIcons $x $y) }
$bdB = $bmp.LockBits($rB, 'ReadWrite', $fmt); $st = $bdB.Stride
$orig = New-Object byte[] ($st * $bmp.Height); [Runtime.InteropServices.Marshal]::Copy($bdB.Scan0, $orig, 0, $orig.Length)
$px = $orig.Clone()
# apaga: cada trecho contínuo da linha vira um degradê entre os vizinhos logo antes e
# logo depois dele (média de 3 px), sem degrau nas bordas.
for ($y = 436; $y -le $BY + 3; $y++) {
  $x = 4
  while ($x -le 300) {
    if (-not (InErase $x $y)) { $x++; continue }
    $s = $x; while ($x -le 300 -and (InErase $x $y)) { $x++ }; $e = $x - 1
    $L = @(0,0,0); $R = @(0,0,0)
    for ($k = 1; $k -le 3; $k++) { for ($c = 0; $c -lt 3; $c++) {
      $L[$c] += $orig[$y*$st + ($s-$k)*4 + $c] / 3.0; $R[$c] += $orig[$y*$st + ($e+$k)*4 + $c] / 3.0 } }
    for ($i = $s; $i -le $e; $i++) {
      $u = ($i - $s + 1) / ($e - $s + 2.0)
      for ($c = 0; $c -lt 3; $c++) { $px[$y*$st + $i*4 + $c] = [byte]($L[$c] * (1 - $u) + $R[$c] * $u) }
    }
  }
}
# sombra suave no chão sob o balde novo
$shY = $AY + ($BY - $AY) * $SYF - 4
for ($y = [int]$shY - 12; $y -le [int]$shY + 12; $y++) { for ($x = 4; $x -le 270; $x++) {
  $e = [math]::Pow(($x - ($CX - $SHIFT)) / 118.0, 2) + [math]::Pow(($y - $shY) / 11.0, 2)
  if ($e -ge 1) { continue }
  $k = 1 - 0.45 * (1 - $e)
  for ($c = 0; $c -lt 3; $c++) { $i = $y*$st + $x*4 + $c; $px[$i] = [byte]($px[$i] * $k) }
} }
# redesenha deslocado: ícones copiados do original; balde reduzido (bilinear) no resto
for ($y = 436; $y -le $BY; $y++) { for ($x = 0; $x -le 296; $x++) {
  $ox = $x + $SHIFT   # coordenada equivalente sem deslocamento
  if (InIcons $ox $y) {
    for ($c = 0; $c -lt 3; $c++) { $px[$y*$st + $x*4 + $c] = $orig[$y*$st + $ox*4 + $c] }
    continue
  }
  $sx = $CX + ($ox - $CX) / $SXF; $sy = $AY + ($y - $AY) / $SYF
  if ($sy -gt $BY) { continue }
  if (-not (InBucket $sx $sy 0)) { continue }
  $x0 = [math]::Floor($sx); $y0 = [math]::Floor($sy); $fx = $sx - $x0; $fy = $sy - $y0
  for ($c = 0; $c -lt 3; $c++) {
    $v = $orig[$y0*$st + $x0*4 + $c] * (1-$fx)*(1-$fy) + $orig[$y0*$st + ($x0+1)*4 + $c] * $fx*(1-$fy) +
         $orig[($y0+1)*$st + $x0*4 + $c] * (1-$fx)*$fy + $orig[($y0+1)*$st + ($x0+1)*4 + $c] * $fx*$fy
    $px[$y*$st + $x*4 + $c] = [byte]$v
  }
} }
[Runtime.InteropServices.Marshal]::Copy($px, 0, $bdB.Scan0, $px.Length)
$bmp.UnlockBits($bdB)

# 3) Janela: troca os prédios (folha direita, x 694..802, abaixo da lua) por natureza.
$g = [Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'
function C($hex) { [Drawing.ColorTranslator]::FromHtml($hex) }
function P($pts) { [Drawing.PointF[]]($pts | ForEach-Object { New-Object Drawing.PointF $_[0], $_[1] }) }
# céu continuando o degradê de cima (clareia rumo ao horizonte); a emenda com o céu
# original (y 244..268) entra em transparência, contornando a lua.
$moon = New-Object Drawing.Drawing2D.GraphicsPath; $moon.AddEllipse(741, 180, 84, 85)
$seam = New-Object Drawing.Region (New-Object Drawing.RectangleF 694, 240, 108, 32); $seam.Exclude($moon)
$g.SetClip($seam, [Drawing.Drawing2D.CombineMode]::Replace)
$fadeIn = New-Object Drawing.Drawing2D.LinearGradientBrush (New-Object Drawing.PointF 0, 239), (New-Object Drawing.PointF 0, 262), ([Drawing.Color]::FromArgb(0, (C '#8BBE57'))), (C '#8EBF57')
$g.FillRectangle($fadeIn, 694, 240, 108, 32)
$g.SetClip((New-Object Drawing.RectangleF 694, 268, 108, 163))
$sky = New-Object Drawing.Drawing2D.LinearGradientBrush (New-Object Drawing.PointF 0, 267), (New-Object Drawing.PointF 0, 335), (C '#8EBF57'), (C '#AACB5C')
$g.FillRectangle($sky, 694, 268, 108, 163)
# serra ao fundo, com luar na crista
$far = P @(@(694,318),@(708,304),@(720,311),@(737,290),@(752,302),@(764,296),@(780,312),@(792,303),@(802,309),@(802,431),@(694,431))
$g.FillPolygon((New-Object Drawing.SolidBrush (C '#6A9C45')), $far)
$g.DrawLines((New-Object Drawing.Pen (C '#C2D96E'), 1.4), $far[0..8])
# morros do meio e da frente (curvas suaves)
$mid = New-Object Drawing.Drawing2D.GraphicsPath
$mid.AddBezier(694, 348, 722, 326, 760, 330, 802, 344); $mid.AddLine(802, 344, 802, 431); $mid.AddLine(802, 431, 694, 431); $mid.CloseFigure()
$g.FillPath((New-Object Drawing.SolidBrush (C '#467F37')), $mid)
$near = New-Object Drawing.Drawing2D.GraphicsPath
$near.AddBezier(694, 392, 730, 370, 770, 372, 802, 386); $near.AddLine(802, 386, 802, 431); $near.AddLine(802, 431, 694, 431); $near.CloseFigure()
$g.FillPath((New-Object Drawing.SolidBrush (C '#2C6A31')), $near)
# pinheiros: três triângulos empilhados + tronco
function Pine($cx, $base, $h, $col) {
  $w = $h * 0.46
  $g.FillRectangle((New-Object Drawing.SolidBrush (C '#173A1F')), $cx - 1.5, $base - $h * 0.18, 3, $h * 0.18)
  $b = New-Object Drawing.SolidBrush (C $col)
  foreach ($k in 0..2) {
    $tb = $base - $h * (0.15 + 0.25 * $k); $tw = $w * (1 - 0.22 * $k); $tt = $tb - $h * 0.42
    $g.FillPolygon($b, (P @(@(($cx - $tw/2), $tb), @(($cx + $tw/2), $tb), @($cx, $tt))))
  }
}
Pine 709 400 50 '#1E5228'
Pine 724 404 36 '#24592C'
Pine 790 392 56 '#1E5228'
Pine 775 398 38 '#24592C'
Pine 750 352 20 '#3A7436'
# chão
$g.FillRectangle((New-Object Drawing.SolidBrush (C '#22612F')), 694, 416, 108, 15)
$g.ResetClip()
# dois passarinhos no céu
$bird = New-Object Drawing.Pen (C '#3F6E34'), 1.6
$g.DrawLines($bird, (P @(@(712,236),@(717,240),@(722,236))))
$g.DrawLines($bird, (P @(@(726,228),@(730,231),@(734,228))))
$g.Dispose()

# 4) JPEG q92
$codec = [Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
$ep = New-Object Drawing.Imaging.EncoderParameters 1
$ep.Param[0] = New-Object Drawing.Imaging.EncoderParameter ([Drawing.Imaging.Encoder]::Quality), 92L
$bmp.Save($Out, $codec, $ep)
$bmp.Dispose()
"lamp dst: x $dx..$($dx+$dw) y $dy..$($dy+$dh)"
