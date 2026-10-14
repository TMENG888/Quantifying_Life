#Requires -Version 7.0
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$outputRoot=(Resolve-Path (Join-Path $projectRoot '../../outputs')).Path
$builtApk=Join-Path $projectRoot 'build/quantlife-1.7.8.apk'
$deliveryApk=Join-Path $outputRoot 'zhishi-1.7.8.apk'
if(!(Test-Path -LiteralPath $builtApk)){throw 'Build the signed APK before publishing.'}
Copy-Item -LiteralPath $builtApk -Destination $deliveryApk
if((Get-FileHash -LiteralPath $builtApk).Hash -ne (Get-FileHash -LiteralPath $deliveryApk).Hash){throw 'Published APK differs from the tested build.'}
# User prefers direct APK delivery; do not create delivery/source ZIPs.
Get-FileHash -LiteralPath $deliveryApk -Algorithm SHA256|Select-Object Hash,Path
