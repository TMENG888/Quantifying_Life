(function(root){
  'use strict';
  const clamp=z=>Math.max(2,Math.min(18,z));
  // Coordinates are local CSS pixels, not physical screen pixels.
  function zoomAt(view,z,anchor,width,height){
    z=clamp(z);const factor=2**(z-view.z),dx=anchor.x-width/2,dy=anchor.y-height/2;
    return {z,x:(view.x+dx)*factor-dx,y:(view.y+dy)*factor-dy};
  }
  class Gesture {
    constructor(){this.points=new Map();this.base=null;this.suppressTap=false;}
    get active(){return this.points.size>0;}
    reset(view){
      const p=[...this.points.values()];if(!p.length){this.base=null;return;}
      this.base={view:{...view},mid:p.length>1?{x:(p[0].x+p[1].x)/2,y:(p[0].y+p[1].y)/2}:{...p[0]},distance:p.length>1?Math.max(1,Math.hypot(p[0].x-p[1].x,p[0].y-p[1].y)):0};
    }
    down(id,p,view){if(this.points.size>=2)return false;if(!this.active)this.suppressTap=false;this.points.set(id,{...p});if(this.points.size>1)this.suppressTap=true;this.reset(view);return true;}
    move(id,p,width,height){
      if(!this.points.has(id)||!this.base)return null;this.points.set(id,{...p});
      const points=[...this.points.values()],b=this.base;
      if(points.length===1){const dx=p.x-b.mid.x,dy=p.y-b.mid.y;if(Math.hypot(dx,dy)>5)this.suppressTap=true;return {...b.view,x:b.view.x-dx,y:b.view.y-dy};}
      const mid={x:(points[0].x+points[1].x)/2,y:(points[0].y+points[1].y)/2},distance=Math.max(1,Math.hypot(points[0].x-points[1].x,points[0].y-points[1].y));
      const view=zoomAt(b.view,b.view.z+Math.log2(distance/b.distance),b.mid,width,height);
      return {...view,x:view.x+b.mid.x-mid.x,y:view.y+b.mid.y-mid.y};
    }
    up(id,view,cancel=false){
      if(!this.points.has(id))return false;
      const tap=!cancel&&!this.suppressTap&&this.points.size===1;
      this.points.delete(id);if(cancel)this.suppressTap=true;this.reset(view);return tap;
    }
  }
  const api={Gesture,zoomAt};if(typeof module==='object'&&module.exports)module.exports=api;else root.LifeMapGestures=api;
})(typeof window!=='undefined'?window:globalThis);
