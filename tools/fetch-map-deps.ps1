$ErrorActionPreference='Stop'
$dir=Join-Path $PSScriptRoot 'android/map-libs'
New-Item -ItemType Directory -Force $dir | Out-Null
$deps=@(
 @('org/mapsforge/mapsforge-core/0.25.0/mapsforge-core-0.25.0.jar','72D7782B1D1CCB534060C8BE3AEE03C2C192F85F78C68E143C056E2B4CB7E383'),
 @('org/mapsforge/mapsforge-map/0.25.0/mapsforge-map-0.25.0.jar','A070A7BEAA5E1B32424F4ADC1604E74B7AA7949794CD22E6B8CF2BBC13180714'),
 @('org/mapsforge/mapsforge-map-reader/0.25.0/mapsforge-map-reader-0.25.0.jar','F821F519F07E0DCBE46FEC01CA4D54EA20749B0B1FD0D806084F3EE017543DD9'),
 @('org/mapsforge/mapsforge-map-android/0.25.0/mapsforge-map-android-0.25.0.jar','D929FCAE12AA8400DE6CB72B9CC960D513E38A8A4D6CD643EA8B5C53BB777802'),
 @('org/mapsforge/mapsforge-themes/0.25.0/mapsforge-themes-0.25.0.jar','7385993826463765EC4BD35D00F41A8E55587F1424582DEC60BE72A8C84DCBBA'),
 @('com/caverock/androidsvg/1.4/androidsvg-1.4.jar','729C67EF475F19F112D01E4FC8E55B1F23841912F8E5090DB27992801A04F79D')
)
foreach($dep in $deps){
 $target=Join-Path $dir (Split-Path $dep[0] -Leaf)
 if(!(Test-Path $target)){Invoke-WebRequest ('https://repo.maven.apache.org/maven2/'+$dep[0]) -OutFile $target}
 if((Get-FileHash $target -Algorithm SHA256).Hash -ne $dep[1]){throw ('Dependency checksum mismatch: '+$target)}
}
return @(Get-ChildItem $dir -Filter '*.jar' | ForEach-Object {$_.FullName})
