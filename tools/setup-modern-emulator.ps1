$ErrorActionPreference='Stop'
$sdkRoot=(Resolve-Path (Join-Path $PSScriptRoot 'android')).Path
$archive=Join-Path $sdkRoot 'image-35.zip'
if(!(Test-Path $archive)){Invoke-WebRequest 'https://dl.google.com/android/repository/sys-img/android/x86_64-35_r02.zip' -OutFile $archive -NoProxy -TimeoutSec 900}
if((Get-FileHash -LiteralPath $archive -Algorithm SHA1).Hash.ToLower() -ne '2d857d170c0d1b827149565da34b3383e5306f7f'){throw 'API35 official archive checksum mismatch'}
$imageDir=Join-Path $sdkRoot 'image-35'
if(!(Test-Path $imageDir)){Expand-Archive -LiteralPath $archive -DestinationPath $imageDir}
$avdDir=Join-Path $sdkRoot 'avd/quantlife-modern.avd'
New-Item -ItemType Directory -Path $avdDir -Force|Out-Null
$template=Get-Content -Raw -LiteralPath (Join-Path $sdkRoot 'avd/quantlife-test.avd/config.ini')
$config=$template.Replace('quantlife-test','quantlife-modern').Replace('QuantLife Test','QuantLife Android15 Test').Replace((Join-Path $sdkRoot 'image/x86_64'),(Join-Path $sdkRoot 'image-35/x86_64')).Replace('disk.dataPartition.size = 6442450944','disk.dataPartition.size = 1073741824')
[IO.File]::WriteAllText((Join-Path $avdDir 'config.ini'),$config,[Text.UTF8Encoding]::new($false))
$registry="avd.ini.encoding=UTF-8`npath=$avdDir`ntarget=android-35`n"
[IO.File]::WriteAllText((Join-Path $sdkRoot 'avd/quantlife-modern.ini'),$registry,[Text.UTF8Encoding]::new($false))
Write-Output 'API35 AVD ready; launch quantlife-modern with emulator port5582.'
