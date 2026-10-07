$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$javaDir=(Get-ChildItem (Join-Path $PSScriptRoot 'android/jdk') -Directory | Select-Object -First 1).FullName
$classes=Join-Path $projectRoot 'build/filter-test'
New-Item -ItemType Directory -Force -Path $classes|Out-Null
& (Join-Path $javaDir 'bin/javac.exe') -encoding UTF-8 --release 8 -d $classes (Join-Path $projectRoot 'app/src/main/java/com/insight/quantlife/TrackFilter.java') (Join-Path $projectRoot 'test/TrackFilterRegression.java') (Join-Path $projectRoot 'test/TrackReplay.java')
if($LASTEXITCODE -ne 0){throw 'Filter test compilation failed'}
& (Join-Path $javaDir 'bin/java.exe') -cp $classes com.insight.quantlife.tests.TrackFilterRegression
if($LASTEXITCODE -ne 0){throw 'Filter regression failed'}
