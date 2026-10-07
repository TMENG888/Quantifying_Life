const fs=require('node:fs');const path=require('node:path');const esbuild=require('esbuild');
const root=path.resolve(__dirname,'../app/src/main/assets');
const banner=fs.readFileSync(path.join(root,'compatibility.js'),'utf8');
for(const name of ['pdf.min.js','pdf.worker.min.js']){
  const file=path.join(root,'vendor',name);
  esbuild.buildSync({entryPoints:[file],outfile:file,target:'chrome83',minify:true,allowOverwrite:true,legalComments:'inline',banner:{js:banner}});
  console.log('Transpiled for Chrome83: '+name);
}
