param([Parameter(Mandatory=$true)][string]$Path)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
# Read archive entries directly, never extract paths or print locations/notes.
$zip=[IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Path).Path)
try{
 $names=@($zip.Entries|ForEach-Object FullName)
 if($names.Count -ne 3 -or @($names|Sort-Object -Unique).Count -ne 3 -or @($names|Where-Object {$_ -notin @('header.json','raw-points.jsonl','manifest.json')}).Count){throw '反馈包文件清单不完整或格式不支持'}
 $entry=$zip.GetEntry('manifest.json');if($entry.Length -gt 8MB){throw '反馈清单过大'}
 $reader=[IO.StreamReader]::new($entry.Open(),[Text.Encoding]::UTF8)
 try{$manifest=$reader.ReadToEnd()|ConvertFrom-Json}catch{throw '无法读取反馈包清单'}finally{$reader.Dispose()}
 if($manifest.format -ne 'zhishi-track-feedback' -or $manifest.version -ne 1 -or $manifest.complete -ne $true -or $manifest.rawTruncated -ne $false){throw '这不是完整的知时定位反馈包'}
 $hash=[Security.Cryptography.SHA256]::Create()
 $crypto=[Security.Cryptography.CryptoStream]::new($zip.GetEntry('raw-points.jsonl').Open(),$hash,[Security.Cryptography.CryptoStreamMode]::Read)
 $reader=[IO.StreamReader]::new($crypto,[Text.UTF8Encoding]::new($false,$true))
 $count=0L;$lastId=0L;$days=@{}
 try{
  while($null -ne ($line=$reader.ReadLine())){
   try{$point=$line|ConvertFrom-Json}catch{throw '原始点数据损坏，未输出任何坐标'}
   if([long]$point.id -le $lastId -or [long]$point.id -gt [long]$manifest.snapshotMaxId -or $point.date -notmatch '^\d{4}-\d{2}-\d{2}$'){throw '原始点顺序或范围不一致'}
   $lastId=[long]$point.id;$count++
   if(!$days.ContainsKey($point.date)){$days[$point.date]=@{points=0L;freshMotion=0L;poorAccuracy=0L}}
   $day=$days[$point.date];$day.points++
   if([double]$point.accuracy -gt 65){$day.poorAccuracy++}
   if($null -ne $point.telemetry.motionRms -and $null -ne $point.telemetry.motionAgeMs -and [long]$point.telemetry.motionAgeMs -ge 0 -and [long]$point.telemetry.motionAgeMs -le 3000){$day.freshMotion++}
  }
  $digest=([BitConverter]::ToString($hash.Hash)).Replace('-','').ToLowerInvariant()
  if($digest -ne $manifest.rawSha256 -or $count -ne [long]$manifest.totalRawPoints -or $count -ne [long]$manifest.snapshotRawPoints -or $days.Count -ne [int]$manifest.dateCount){throw '完整性校验失败，请重新导出反馈包'}
  foreach($day in $manifest.dates){if(!$days.ContainsKey($day.date) -or $days[$day.date].points -ne [long]$day.rawPoints){throw '日期汇总与原始点数不一致'}}
  [ordered]@{verified=$true;appVersion=$manifest.appVersion;rawPoints=$count;dateCount=$days.Count;rawSha256=$digest;dates=@($days.Keys|Sort-Object|ForEach-Object {[ordered]@{date=$_;rawPoints=$days[$_].points;freshMotionPoints=$days[$_].freshMotion;accuracyOver65=$days[$_].poorAccuracy}})}|ConvertTo-Json -Depth 5
 }finally{$reader.Dispose();$hash.Dispose()}
}finally{$zip.Dispose()}
