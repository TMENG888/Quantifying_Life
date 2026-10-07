$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$outputRoot=(Resolve-Path (Join-Path $projectRoot '../../outputs')).Path
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
Copy-Item -LiteralPath (Join-Path $projectRoot 'build/quantlife-1.4.3.apk') -Destination (Join-Path $outputRoot 'zhishi-1.4.3.apk')
function Write-Archive([string]$target,[object[]]$items){
  if(Test-Path -LiteralPath $target){throw "Refusing to overwrite existing archive: $target"}
  $zip=[IO.Compression.ZipFile]::Open($target,[IO.Compression.ZipArchiveMode]::Create)
  try{foreach($item in $items){[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$item.Path,$item.Name.Replace('\','/'),[IO.Compression.CompressionLevel]::Optimal)|Out-Null}}finally{$zip.Dispose()}
}
$delivery=@('zhishi-1.4.3.apk','知时-1.4.3-升级与使用说明.md','知时-1.4.3-验证记录.md','知时-1.4.3-用户使用说明.md')|ForEach-Object{@{Path=(Join-Path $outputRoot $_);Name=$_}}
Write-Archive (Join-Path $outputRoot 'zhishi-wechat-1.4.3.zip') $delivery
$sourceFiles=@(Get-ChildItem -LiteralPath $projectRoot -File)
foreach($dir in @('app','test','docs')){$sourceFiles+=@(Get-ChildItem -LiteralPath (Join-Path $projectRoot $dir) -File -Recurse)}
$sourceFiles+=@(Get-ChildItem -LiteralPath $PSScriptRoot -File|Where-Object{$_.Extension -in @('.ps1','.cjs')})
$source=@($sourceFiles|Where-Object{$_.Name -ne 'local.properties' -and $_.Extension -notin @('.jks','.keystore','.xml')}|ForEach-Object{@{Path=$_.FullName;Name=('quant-life/'+$_.FullName.Substring($projectRoot.Length+1).Replace('\','/'))}})
# app/test XML are source, not signing secrets.
$source+=@($sourceFiles|Where-Object{$_.Extension -eq '.xml' -and ($_.FullName.StartsWith((Join-Path $projectRoot 'app')+'\') -or $_.FullName.StartsWith((Join-Path $projectRoot 'test')+'\'))}|ForEach-Object{@{Path=$_.FullName;Name=('quant-life/'+$_.FullName.Substring($projectRoot.Length+1).Replace('\','/'))}})
Write-Archive (Join-Path $outputRoot 'zhishi-source-1.4.3.zip') $source
foreach($file in @('zhishi-1.4.3.apk','zhishi-wechat-1.4.3.zip','zhishi-source-1.4.3.zip')){Get-FileHash -LiteralPath (Join-Path $outputRoot $file) -Algorithm SHA256|Select-Object Hash,Path}
