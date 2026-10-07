$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$assets = Join-Path $root 'app/src/main/assets/vendor'
Push-Location $root
try {
  & npm.cmd ci --registry=https://registry.npmjs.org --no-audit --no-fund
  if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
  foreach ($name in @('pdf.min.js','pdf.worker.min.js')) {
    $file = Join-Path $assets $name
    Invoke-WebRequest "https://cdn.jsdelivr.net/npm/pdfjs-dist@3.11.174/legacy/build/$name" -OutFile $file -NoProxy -TimeoutSec 120
  }
  & node (Join-Path $PSScriptRoot 'transpile-pdf.cjs')
  if ($LASTEXITCODE -ne 0) { throw 'PDF transpilation failed' }
} finally { Pop-Location }
