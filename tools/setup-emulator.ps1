$ErrorActionPreference='Stop'
$root=Join-Path $PSScriptRoot 'android'
$items=@(
 @{File='emulator.zip';Url='https://dl.google.com/android/repository/emulator-windows_x64-16433917.zip';Hash='6ac24315017357bb8d7bfeb64ca653829a57b891';Dir='emulator-tools'},
 @{File='image.zip';Url='https://dl.google.com/android/repository/sys-img/android/x86_64-30_r11.zip';Hash='5e4de3946d46f88856c35efcc4d797b381456347';Dir='image'}
)
foreach($item in $items){
 $file=Join-Path $root $item.File
 if(!(Test-Path $file)){Invoke-WebRequest $item.Url -OutFile $file -NoProxy -TimeoutSec 300}
 if((Get-FileHash $file -Algorithm SHA1).Hash.ToLower() -ne $item.Hash){throw 'Checksum mismatch'}
 $dir=Join-Path $root $item.Dir
 if(!(Test-Path $dir)){Expand-Archive -LiteralPath $file -DestinationPath $dir}
 Write-Output "Ready: $($item.File)"
}
