param([string]$BuildDir='E:\Codex\work\2026-10-17-zhishi-track-recovery\filter-test')
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$javaDir=(Get-ChildItem (Join-Path $PSScriptRoot 'android/jdk') -Directory | Select-Object -First 1).FullName
$classes=$BuildDir
New-Item -ItemType Directory -Force -Path $classes|Out-Null
& (Join-Path $javaDir 'bin/javac.exe') -encoding UTF-8 --release 8 -d $classes (Join-Path $projectRoot 'app/src/main/java/com/insight/quantlife/TrackFilter.java') (Join-Path $projectRoot 'app/src/main/java/com/insight/quantlife/TrackVariants.java') (Join-Path $projectRoot 'app/src/main/java/com/insight/quantlife/TrackDwell.java') (Join-Path $projectRoot 'app/src/main/java/com/insight/quantlife/TrackFusion.java') (Join-Path $projectRoot 'app/src/main/java/com/insight/quantlife/TrackDisplay.java') (Join-Path $projectRoot 'test/TrackFusionRegression.java') (Join-Path $projectRoot 'test/TrackFilterRegression.java') (Join-Path $projectRoot 'test/TrackVariantsRegression.java') (Join-Path $projectRoot 'test/TrackDwellRegression.java') (Join-Path $projectRoot 'test/TrackReplay.java')
if($LASTEXITCODE -ne 0){throw 'Filter test compilation failed'}
& (Join-Path $javaDir 'bin/java.exe') -cp $classes com.insight.quantlife.tests.TrackFilterRegression
if($LASTEXITCODE -ne 0){throw 'Filter regression failed'}
& (Join-Path $javaDir 'bin/java.exe') -cp $classes com.insight.quantlife.tests.TrackVariantsRegression
if($LASTEXITCODE -ne 0){throw 'Comparison regression failed'}
& (Join-Path $javaDir 'bin/java.exe') -cp $classes com.insight.quantlife.tests.TrackDwellRegression
if($LASTEXITCODE -ne 0){throw 'Conservative D dwell regression failed'}
& (Join-Path $javaDir 'bin/java.exe') -cp $classes com.insight.quantlife.tests.TrackFusionRegression
if($LASTEXITCODE -ne 0){throw 'Motion regression failed'}
& (Join-Path $javaDir 'bin/javac.exe') -encoding UTF-8 --release 8 -cp $classes -d $classes (Join-Path $projectRoot 'app/src/main/java/com/insight/quantlife/RoadMatcher.java') (Join-Path $projectRoot 'test/RoadMatcherRegression.java')
if($LASTEXITCODE -ne 0){throw 'Road matcher compilation failed'}
& (Join-Path $javaDir 'bin/java.exe') -cp $classes com.insight.quantlife.tests.RoadMatcherRegression
if($LASTEXITCODE -ne 0){throw 'Road matcher regression failed'}
