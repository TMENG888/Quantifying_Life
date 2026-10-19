param([string]$Output = '', [string]$BuildDir = 'E:\Codex\work\2026-10-19-zhishi-visual-editor\build')
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$build = $BuildDir
if(!(Test-Path 'E:\')){throw 'E drive is unavailable; build output was not redirected to C.'}
$javaDir = (Get-ChildItem (Join-Path $PSScriptRoot 'android/jdk') -Directory | Select-Object -First 1).FullName
$platform = (Get-ChildItem (Join-Path $PSScriptRoot 'android/platform') -Directory | Select-Object -First 1).FullName
$tools = (Get-ChildItem (Join-Path $PSScriptRoot 'android/build-tools') -Directory | Select-Object -First 1).FullName
$androidJar = Join-Path $platform 'android.jar'
$mapJars = @(& (Join-Path $PSScriptRoot 'fetch-map-deps.ps1'))
$env:JAVA_HOME = $javaDir
$env:PATH = (Join-Path $javaDir 'bin') + ';' + $env:PATH
New-Item -ItemType Directory -Force -Path $build,(Join-Path $build 'classes'),(Join-Path $build 'dex'),(Join-Path $build 'generated') | Out-Null
function Invoke-Checked([string]$Program,[string[]]$Arguments) {
  & $Program @Arguments
  if ($LASTEXITCODE -ne 0) { throw "Failed: $Program (exit $LASTEXITCODE)" }
}
Invoke-Checked (Join-Path $tools 'aapt2.exe') @('compile','--dir',(Join-Path $root 'app/src/main/res'),'-o',(Join-Path $build 'resources.zip'))
Invoke-Checked (Join-Path $tools 'aapt2.exe') @('link','-o',(Join-Path $build 'unsigned.apk'),'-I',$androidJar,'--manifest',(Join-Path $root 'app/src/main/AndroidManifest.xml'),'--min-sdk-version','26','--target-sdk-version','35','--version-code','41','--version-name','1.10.1','-A',(Join-Path $root 'app/src/main/assets'),'--java',(Join-Path $build 'generated'),(Join-Path $build 'resources.zip'))
# Windows aapt2 may use backslashes for nested assets; Android AssetManager expects slash paths.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$assetZip = [IO.Compression.ZipFile]::Open((Join-Path $build 'unsigned.apk'),[IO.Compression.ZipArchiveMode]::Update)
try {
  foreach ($entry in @($assetZip.Entries | Where-Object { $_.FullName.Contains('\') })) {
    $stream = $entry.Open(); $buffer = [IO.MemoryStream]::new()
    try { $stream.CopyTo($buffer) } finally { $stream.Dispose() }
    $safeName = $entry.FullName.Replace('\','/'); $entry.Delete()
    $fixed = $assetZip.CreateEntry($safeName); $out = $fixed.Open()
    try { $buffer.Position=0; $buffer.CopyTo($out) } finally { $out.Dispose(); $buffer.Dispose() }
  }
} finally { $assetZip.Dispose() }
# Preserve upstream theme resources in their original APK paths (ClassLoader resources).
$mapZip = [IO.Compression.ZipFile]::Open((Join-Path $build 'unsigned.apk'),[IO.Compression.ZipArchiveMode]::Update)
try {
 foreach($lib in $mapJars){$z=[IO.Compression.ZipFile]::OpenRead($lib);try{foreach($e in $z.Entries){
  if($e.FullName.StartsWith('assets/') -and !$e.FullName.EndsWith('/')){
   $existing=$mapZip.GetEntry($e.FullName);if($existing){$existing.Delete()}
   $entry=$mapZip.CreateEntry($e.FullName);$src=$e.Open();$dst=$entry.Open();try{$src.CopyTo($dst)}finally{$src.Dispose();$dst.Dispose()}
  }
 }}finally{$z.Dispose()}}
}finally{$mapZip.Dispose()}
$sources = @(Get-ChildItem (Join-Path $root 'app/src/main/java') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }) + @(Get-ChildItem (Join-Path $build 'generated') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
Invoke-Checked (Join-Path $javaDir 'bin/javac.exe') (@('-encoding','UTF-8','--release','8','-classpath',($androidJar+';'+($mapJars -join ';')),'-d',(Join-Path $build 'classes')) + $sources)
Invoke-Checked (Join-Path $javaDir 'bin/jar.exe') @('cf',(Join-Path $build 'classes.jar'),'-C',(Join-Path $build 'classes'),'.')
Invoke-Checked (Join-Path $tools 'd8.bat') (@('--lib',$androidJar,'--min-api','26','--output',(Join-Path $build 'dex'),(Join-Path $build 'classes.jar')) + $mapJars)
Invoke-Checked (Join-Path $javaDir 'bin/jar.exe') @('uf',(Join-Path $build 'unsigned.apk'),'-C',(Join-Path $build 'dex'),'classes.dex')
Invoke-Checked (Join-Path $tools 'zipalign.exe') @('-f','-p','4',(Join-Path $build 'unsigned.apk'),(Join-Path $build 'aligned.apk'))
$keyPath = Join-Path $PSScriptRoot 'quantlife-release.jks'
$passwordPath = Join-Path $PSScriptRoot 'signing-secret.xml'
if (Test-Path $passwordPath) {
  $secure = Import-Clixml -LiteralPath $passwordPath
  $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
  try { $env:LIFE_SIGN_PASS = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
} else {
  if(Test-Path $keyPath) { throw 'Signing key exists but its password file is missing. Restore the signing-secret.xml backup.' }
  $env:LIFE_SIGN_PASS = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
  ConvertTo-SecureString $env:LIFE_SIGN_PASS -AsPlainText -Force | Export-Clixml -LiteralPath $passwordPath
  Invoke-Checked (Join-Path $javaDir 'bin/keytool.exe') @('-genkeypair','-keystore',$keyPath,'-alias','quantlife','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=QuantLife Personal, OU=Personal Apps, O=Insight, C=CN','-storepass:env','LIFE_SIGN_PASS','-keypass:env','LIFE_SIGN_PASS')
}
if (!$Output) { $Output = Join-Path $build 'quantlife-1.10.1.apk' }
Invoke-Checked (Join-Path $tools 'apksigner.bat') @('sign','--ks',$keyPath,'--ks-key-alias','quantlife','--ks-pass','env:LIFE_SIGN_PASS','--key-pass','env:LIFE_SIGN_PASS','--out',$Output,(Join-Path $build 'aligned.apk'))
Remove-Item Env:LIFE_SIGN_PASS
Invoke-Checked (Join-Path $tools 'apksigner.bat') @('verify','--verbose','--print-certs',$Output)
Invoke-Checked (Join-Path $tools 'aapt2.exe') @('dump','badging',$Output)
Get-FileHash -LiteralPath $Output -Algorithm SHA256 | Format-List
