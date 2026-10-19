#Requires -Version 7.0
param([string]$BuiltApk='E:\Codex\work\2026-10-19-zhishi-visual-editor\build\quantlife-1.10.1.apk', [string]$OutputDirectory='E:\Codex\outputs\2026-10-19-zhishi-visual-editor')
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$outputRoot=(Resolve-Path $OutputDirectory).Path
$builtApk=$BuiltApk
$deliveryApk=Join-Path $outputRoot 'zhishi-1.10.1.apk'
if(!(Test-Path -LiteralPath $builtApk)){throw 'Build the signed APK before publishing.'}
Copy-Item -LiteralPath $builtApk -Destination $deliveryApk
if((Get-FileHash -LiteralPath $builtApk).Hash -ne (Get-FileHash -LiteralPath $deliveryApk).Hash){throw 'Published APK differs from the tested build.'}
# User prefers direct APK delivery; do not create delivery/source ZIPs.
Get-FileHash -LiteralPath $deliveryApk -Algorithm SHA256|Select-Object Hash,Path
