param([string]$BuildDir='E:\Codex\work\2026-10-24-track-reliability\tests', [string]$AppBuildDir='E:\Codex\work\2026-10-24-track-reliability\build', [string[]]$Instrumentation=@(), [string]$FixtureDir='')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$javaDir=(Get-ChildItem (Join-Path $PSScriptRoot 'android/jdk') -Directory | Select-Object -First 1).FullName
$tools=(Get-ChildItem (Join-Path $PSScriptRoot 'android/build-tools') -Directory | Select-Object -First 1).FullName
$platform=(Get-ChildItem (Join-Path $PSScriptRoot 'android/platform') -Directory | Select-Object -First 1).FullName
$build=$BuildDir
New-Item -ItemType Directory -Force -Path $build,(Join-Path $build 'classes'),(Join-Path $build 'dex') | Out-Null
if(!$FixtureDir){$FixtureDir=Join-Path $root 'test/fixtures'}
$manifestPath=Join-Path $root 'test/AndroidManifest.xml'
[xml]$testManifest=Get-Content -LiteralPath $manifestPath -Raw
$testRunners=@($testManifest.manifest.instrumentation)
if($Instrumentation.Count -gt 0){
  foreach($runner in $Instrumentation){
    if(!($testRunners | Where-Object { $_.GetAttribute('name','http://schemas.android.com/apk/res/android') -eq $runner -or $_.GetAttribute('name','http://schemas.android.com/apk/res/android') -eq "com.insight.quantlife.tests.$runner" })){throw "Unknown instrumentation: $runner"}
  }
  foreach($entry in $testRunners){$name=$entry.GetAttribute('name','http://schemas.android.com/apk/res/android');if($Instrumentation -notcontains $name -and $Instrumentation -notcontains ($name.Split('.')[-1])){[void]$testManifest.manifest.RemoveChild($entry)}}
  $manifestPath=Join-Path $build 'AndroidManifest.xml';$testManifest.Save($manifestPath)
}elseif($testRunners.Count -gt 50){throw 'Android supports at most 50 instrumentation declarations per APK. Select runners with -Instrumentation.'}
$env:JAVA_HOME=$javaDir
function Run([string]$program,[string[]]$argsList){& $program @argsList;if($LASTEXITCODE -ne 0){throw "Failed $program"}}
Run (Join-Path $tools 'aapt2.exe') @('link','-o',(Join-Path $build 'unsigned.apk'),'-I',(Join-Path $platform 'android.jar'),'--manifest',$manifestPath,'-A',$FixtureDir)
Run (Join-Path $javaDir 'bin/javac.exe') (@('-encoding','UTF-8','--release','8','-classpath',((Join-Path $platform 'android.jar')+';'+(Join-Path $AppBuildDir 'classes')+';'+(Join-Path $PSScriptRoot 'android/map-libs/*')),'-d',(Join-Path $build 'classes')) + @(Get-ChildItem (Join-Path $root 'test') -Filter '*.java' | ForEach-Object { $_.FullName }))
Run (Join-Path $javaDir 'bin/jar.exe') @('cf',(Join-Path $build 'classes.jar'),'-C',(Join-Path $build 'classes'),'.')
Run (Join-Path $tools 'd8.bat') @('--lib',(Join-Path $platform 'android.jar'),'--min-api','26','--output',(Join-Path $build 'dex'),(Join-Path $build 'classes.jar'))
Run (Join-Path $javaDir 'bin/jar.exe') @('uf',(Join-Path $build 'unsigned.apk'),'-C',(Join-Path $build 'dex'),'classes.dex')
$secure=Import-Clixml (Join-Path $PSScriptRoot 'signing-secret.xml');$ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try{$env:LIFE_SIGN_PASS=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)}
Run (Join-Path $tools 'apksigner.bat') @('sign','--ks',(Join-Path $PSScriptRoot 'quantlife-release.jks'),'--ks-pass','env:LIFE_SIGN_PASS','--out',(Join-Path $build 'tests.apk'),(Join-Path $build 'unsigned.apk'))
Remove-Item Env:LIFE_SIGN_PASS
Write-Output (Join-Path $build 'tests.apk')
