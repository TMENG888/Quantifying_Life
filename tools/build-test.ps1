$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$javaDir=(Get-ChildItem (Join-Path $PSScriptRoot 'android/jdk') -Directory | Select-Object -First 1).FullName
$tools=(Get-ChildItem (Join-Path $PSScriptRoot 'android/build-tools') -Directory | Select-Object -First 1).FullName
$platform=(Get-ChildItem (Join-Path $PSScriptRoot 'android/platform') -Directory | Select-Object -First 1).FullName
$build=Join-Path $root 'build/test'
New-Item -ItemType Directory -Force -Path $build,(Join-Path $build 'classes'),(Join-Path $build 'dex') | Out-Null
$env:JAVA_HOME=$javaDir
function Run([string]$program,[string[]]$argsList){& $program @argsList;if($LASTEXITCODE -ne 0){throw "Failed $program"}}
Run (Join-Path $tools 'aapt2.exe') @('link','-o',(Join-Path $build 'unsigned.apk'),'-I',(Join-Path $platform 'android.jar'),'--manifest',(Join-Path $root 'test/AndroidManifest.xml'),'-A',(Join-Path $root 'test/fixtures'))
Run (Join-Path $javaDir 'bin/javac.exe') (@('-encoding','UTF-8','--release','8','-classpath',((Join-Path $platform 'android.jar')+';'+(Join-Path $root 'build/classes')),'-d',(Join-Path $build 'classes')) + @(Get-ChildItem (Join-Path $root 'test') -Filter '*.java' | ForEach-Object { $_.FullName }))
Run (Join-Path $javaDir 'bin/jar.exe') @('cf',(Join-Path $build 'classes.jar'),'-C',(Join-Path $build 'classes'),'.')
Run (Join-Path $tools 'd8.bat') @('--lib',(Join-Path $platform 'android.jar'),'--min-api','26','--output',(Join-Path $build 'dex'),(Join-Path $build 'classes.jar'))
Run (Join-Path $javaDir 'bin/jar.exe') @('uf',(Join-Path $build 'unsigned.apk'),'-C',(Join-Path $build 'dex'),'classes.dex')
$secure=Import-Clixml (Join-Path $PSScriptRoot 'signing-secret.xml');$ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try{$env:LIFE_SIGN_PASS=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)}
Run (Join-Path $tools 'apksigner.bat') @('sign','--ks',(Join-Path $PSScriptRoot 'quantlife-release.jks'),'--ks-pass','env:LIFE_SIGN_PASS','--out',(Join-Path $build 'tests.apk'),(Join-Path $build 'unsigned.apk'))
Remove-Item Env:LIFE_SIGN_PASS
Write-Output (Join-Path $build 'tests.apk')
