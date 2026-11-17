#Requires -Version 7.0
param([string]$BuiltApk='E:\Codex\outputs\2026-11-16-ui-motion\zhishi-1.25.2.apk', [string]$OutputDirectory='E:\Codex\outputs\2026-11-16-ui-motion')
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$outputRoot=(Resolve-Path $OutputDirectory).Path
$builtApk=$BuiltApk
$deliveryApk=Join-Path $outputRoot 'zhishi-1.25.2.apk'
if(!(Test-Path -LiteralPath $builtApk)){throw 'Build the signed APK before publishing.'}
if([IO.Path]::GetFullPath($builtApk) -ne [IO.Path]::GetFullPath($deliveryApk)){Copy-Item -LiteralPath $builtApk -Destination $deliveryApk}
if((Get-FileHash -LiteralPath $builtApk).Hash -ne (Get-FileHash -LiteralPath $deliveryApk).Hash){throw 'Published APK differs from the tested build.'}
# User prefers direct APK delivery; do not create delivery/source ZIPs.
Get-FileHash -LiteralPath $deliveryApk -Algorithm SHA256|Select-Object Hash,Path
