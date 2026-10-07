const sharp=require('C:/Users/YS-Insight/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const path=require('node:path');
const output=path.resolve(__dirname,'../../../outputs');
(async()=>{
 const inputs=['platform-input-fixed-1.1.png','wiki-topic-1.1.png'];
 const images=await Promise.all(inputs.map(f=>sharp(path.join(output,f)).resize({width:320}).toBuffer()));
 await sharp({create:{width:656,height:569,channels:3,background:'#f7f6f0'}}).composite(images.map((input,i)=>({input,left:i*336,top:0}))).png().toFile(path.join(output,'zhishi-update-1.1.png'));
})().catch(e=>{console.error(e);process.exitCode=1});
