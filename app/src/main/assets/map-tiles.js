(function(root){
  'use strict';
  function visible(view,width,height){
    const z=Math.floor(view.z),scale=2**(view.z-z),size=256*scale,tiles=[],left=view.x-width/2,top=view.y-height/2,n=2**z;
    for(let x=Math.floor(left/size);x<=Math.ceil((left+width)/size)-1;x++)for(let y=Math.floor(top/size);y<=Math.ceil((top+height)/size)-1;y++)if(y>=0&&y<n)tiles.push({z,x:((x%n)+n)%n,y,px:x*size-left,py:y*size-top,size});
    return tiles;
  }
  function crop(tile,source,width=256,height=256){
    const z=source.sourceZ??tile.z,x=source.sourceX??tile.x,y=source.sourceY??tile.y,scale=2**(tile.z-z);
    if(!Number.isInteger(z)||z<0||z>tile.z||!Number.isInteger(x)||!Number.isInteger(y)||Math.floor(tile.x/scale)!==x||Math.floor(tile.y/scale)!==y)throw Error('地图图块范围不匹配');
    return {sx:(tile.x-x*scale)*width/scale,sy:(tile.y-y*scale)*height/scale,sw:width/scale,sh:height/scale,coarse:z<tile.z,sourceZ:z};
  }
  class Queue {
    constructor(limit=2){this.limit=limit;this.active=0;this.pending=[];}
    run(task,current=()=>true){return new Promise((resolve,reject)=>{this.pending.push({task,current,resolve,reject});this.pump();});}
    pump(){while(this.active<this.limit&&this.pending.length){const job=this.pending.shift();if(!job.current()){job.reject(Error('地图视图已更新'));continue;}this.active++;Promise.resolve().then(job.task).then(job.resolve,job.reject).finally(()=>{this.active--;this.pump();});}}
  }
  const api={visible,crop,Queue};if(typeof module==='object'&&module.exports)module.exports=api;else root.LifeMapTiles=api;
})(typeof window!=='undefined'?window:globalThis);
