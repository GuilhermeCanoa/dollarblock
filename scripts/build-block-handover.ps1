# Gera a arte da tela de bloqueio (app/src/main/res/drawable-nodpi/block_handover.jpg) a
# partir de docs/art/block_handover_source.webp (1024×1536, o guardião entregando o recibo em
# branco). Só roda à mão (Windows), fora do build:
#
#   powershell -File scripts/build-block-handover.ps1
#
# Hoje é só a conversão para JPEG, sem recorte nem edição: o app depende das dimensões e da
# posição do recibo na arte (BlockHandover.kt: ART_W/ART_H e ArtReceipt, em px desta imagem).
# Se recortar, redimensionar ou trocar a arte, confira essas constantes.
param(
  [string]$Src = "$PSScriptRoot\..\docs\art\block_handover_source.webp",
  [string]$Out = "$PSScriptRoot\..\app\src\main\res\drawable-nodpi\block_handover.jpg",
  [int]$Quality = 90
)
$Src = [IO.Path]::GetFullPath($Src); $Out = [IO.Path]::GetFullPath($Out)
Add-Type -AssemblyName PresentationCore

# WIC lê WebP; o JPEG sai pelo encoder do WPF.
$fs = [IO.File]::OpenRead($Src)
$f = [Windows.Media.Imaging.BitmapDecoder]::Create($fs, 'PreservePixelFormat', 'OnLoad').Frames[0]
$fs.Close()
if ($f.PixelWidth -ne 1024 -or $f.PixelHeight -ne 1536) {
  Write-Warning "Arte com $($f.PixelWidth)x$($f.PixelHeight) (esperado 1024x1536): confira ART_W/ART_H e ArtReceipt em BlockHandover.kt"
}
$rgb = New-Object Windows.Media.Imaging.FormatConvertedBitmap($f, [Windows.Media.PixelFormats]::Bgr24, $null, 0)
$enc = New-Object Windows.Media.Imaging.JpegBitmapEncoder
$enc.QualityLevel = $Quality
$enc.Frames.Add([Windows.Media.Imaging.BitmapFrame]::Create($rgb))
$o = [IO.File]::Create($Out); $enc.Save($o); $o.Close()
Write-Host "OK: $Out ($([Math]::Round((Get-Item $Out).Length / 1KB)) KB)"
