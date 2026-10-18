param([Parameter(Mandatory=$true)][string]$DiagnosticPath)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$jdk=(Get-ChildItem (Join-Path $PSScriptRoot 'android/jdk') -Directory|Select-Object -First 1).FullName
$classes=Join-Path $root 'build/filter-test'
& (Join-Path $jdk 'bin/javac.exe') -encoding UTF-8 --release 8 -cp $classes -d $classes (Join-Path $root 'test/TrackMotionReplay.java')
if($LASTEXITCODE -ne 0){throw 'Replay compilation failed'}
$diagnostic=Get-Content -Raw -LiteralPath $DiagnosticPath|ConvertFrom-Json
function NumberOrNaN($value){if($null -eq $value){return 'NaN'}return ([double]$value).ToString('R',[Globalization.CultureInfo]::InvariantCulture)}
$diagnostic.rawPoints|ForEach-Object { $p=$_;$t=$p.telemetry
 $eligible=if($null -eq $t.eligible){'true'}else{([bool]$t.eligible).ToString().ToLowerInvariant()}
 $motionAge=if($null -eq $t.motionAgeMs){-1}else{$t.motionAgeMs};$gpsAge=if($null -eq $t.gpsAgeMs){-1}else{$t.gpsAgeMs}
 @($p.id,$p.time,(NumberOrNaN $p.lat),(NumberOrNaN $p.lon),(NumberOrNaN $p.accuracy),(NumberOrNaN $p.speed),(NumberOrNaN $p.speedAccuracy),$p.session,$p.provider,$eligible,(NumberOrNaN $t.motionRms),$motionAge,$gpsAge) -join "`t"
}|& (Join-Path $jdk 'bin/java.exe') -Xmx256m -cp $classes com.insight.quantlife.tests.TrackMotionReplay
if($LASTEXITCODE -ne 0){throw 'Replay failed'}
