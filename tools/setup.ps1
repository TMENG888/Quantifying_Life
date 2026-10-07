$ErrorActionPreference = 'Stop'
$toolDir = Join-Path $PSScriptRoot 'android'
New-Item -ItemType Directory -Force -Path $toolDir | Out-Null
$downloads = @(
  @{ Name='zulu-jdk.zip'; Url='https://cdn.azul.com/zulu/bin/zulu17.62.17-ca-jdk17.0.17-win_x64.zip'; Hash='bd8a942bb543f109a28d3eadf3ec2f29a3ee28ab53506e31d2858292f63c6949'; Algorithm='SHA256'; Dir='jdk' },
  @{ Name='platform.zip'; Url='https://dl.google.com/android/repository/platform-35_r02.zip'; Hash='0bb560a90a7a2cbd0dd8348224d518b638fe7949'; Algorithm='SHA1'; Dir='platform' },
  @{ Name='build-tools.zip'; Url='https://dl.google.com/android/repository/build-tools_r35_windows.zip'; Hash='af059bb67cf7786f45ee0db85e2d24985df1b4b6'; Algorithm='SHA1'; Dir='build-tools' }
)
foreach ($item in $downloads) {
  $archive = Join-Path $toolDir $item.Name
  if (!(Test-Path $archive)) { Invoke-WebRequest -Uri $item.Url -OutFile $archive -NoProxy -TimeoutSec 240 }
  if ((Get-FileHash -LiteralPath $archive -Algorithm $item.Algorithm).Hash.ToLower() -ne $item.Hash) { throw "Checksum mismatch: $($item.Name)" }
  $target = Join-Path $toolDir $item.Dir
  if (!(Test-Path $target)) { Expand-Archive -LiteralPath $archive -DestinationPath $target }
  Write-Output "Verified and extracted $($item.Name)"
}
$assets = Join-Path $PSScriptRoot '../app/src/main/assets/vendor'
New-Item -ItemType Directory -Force -Path $assets | Out-Null
foreach ($name in @('pdf.min.js','pdf.worker.min.js')) {
  $file = Join-Path $assets $name
  if (!(Test-Path $file)) { & (Join-Path $PSScriptRoot 'refresh-pdf.ps1'); break }
}
if (!(Test-Path (Join-Path $assets 'LICENSE'))) { Invoke-WebRequest 'https://cdn.jsdelivr.net/npm/pdfjs-dist@3.11.174/LICENSE' -OutFile (Join-Path $assets 'LICENSE') -NoProxy -TimeoutSec 60 }
Write-Output 'Android build dependencies ready.'
