(function(root){
  'use strict';
  const $=id=>document.getElementById(id);
  let env,editing=null,menuMessage=null,pressTimer=0,modalityBound=false;
  function text(message){
    if(typeof message.promptText==='string')return message.promptText;
    const value=String(message.text||'');
    return /附件文本（对话资料|\[附件：/.test(message.contextText||'')?value.replace(/\n\[附件：[^\n]*\]$/,''):value;
  }
  function close(){const dialog=$('chat-message-menu');if(!dialog?.open)return false;if(dialog.classList.contains('selecting'))root.getSelection?.().removeAllRanges();dialog.close();return true;}
  function cancel(){
    if(!editing)return false;
    const before=editing.before;editing=null;
    const input=$('chat-input');if(input){input.value=before.text;input.setSelectionRange(before.start,before.end);env.resize();env.remember();}
    paint();return true;
  }
  function paint(){
    let bar=$('chat-edit-banner');
    if(!editing){bar?.remove();document.querySelectorAll('.chat-message.is-editing').forEach(n=>n.classList.remove('is-editing'));env?.resize();return;}
    if(!bar){bar=document.createElement('div');bar.id='chat-edit-banner';bar.className='chat-edit-banner';bar.innerHTML='<span>修改输入</span><button type="button" aria-label="取消修改，恢复原输入">×</button>';document.querySelector('.chat-shell .composer')?.prepend(bar);}
    bar.querySelector('button').onclick=cancel;
    env.resize();
  }
  function edit(message){
    close();if(!message||message.role!=='user')return;
    const input=$('chat-input');if(!input)return;
    if(!editing)editing={before:{text:input.value,start:input.selectionStart,end:input.selectionEnd}};
    Object.assign(editing,{id:message.id,session:env.state.chatSession?.id});
    input.value=text(message);paint();input.focus({preventScroll:true});input.setSelectionRange(input.value.length,input.value.length);env.remember();
  }
  async function copy(message){
    const value=text(message);
    if(typeof root.LifeNative!=='undefined')await env.api('copyText',{text:value});
    else if(navigator.clipboard?.writeText)await navigator.clipboard.writeText(value);
    else throw Error('无法访问剪贴板，请选择文本后复制');
    env.toast('已复制');
  }
  function select(message){
    const dialog=$('chat-message-menu');dialog.classList.add('selecting');dialog.style.removeProperty('top');dialog.style.removeProperty('left');
    dialog.setAttribute('aria-label','选择文本');
    dialog.innerHTML='<header><strong>选择文本</strong><button type="button" class="chat-menu-close" aria-label="关闭选择文本"><svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="m6 6 12 12M18 6 6 18"></path></svg></button></header><div class="chat-select-scroll"><div id="chat-select-text" aria-label="已发送的消息，可长按选择文字"></div></div>';
    // Android supplies selection handles and Copy; never interpret messages as HTML.
    $('chat-select-text').textContent=text(message);
    dialog.querySelector('.chat-menu-close').onclick=close;
    dialog.querySelector('.chat-menu-close').focus({preventScroll:true});
  }
  function showMenu(message,anchor){
    clearTimeout(pressTimer);if(!message||message.role!=='user')return;
    close();menuMessage=message;
    const dialog=$('chat-message-menu');dialog.classList.remove('selecting');
    dialog.setAttribute('aria-label','消息操作');
    const icons={copy:'M9 9h11v12H9zM5 15H3V3h11v2',select:'M7 4H4v16h3M17 4h3v16h-3M10 7h4M12 7v10',edit:'m4 16 11-11 4 4L8 20H4zM13 7l4 4'};
    dialog.innerHTML=[['copy','复制'],['select','选择文本'],['edit','修改']].map(([action,label])=>`<button data-message-action="${action}"><svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="${icons[action]}"></path></svg><span>${label}</span></button>`).join('');
    dialog.showModal();
    const r=anchor.getBoundingClientRect(),w=dialog.offsetWidth,h=dialog.offsetHeight;
    dialog.style.left=Math.max(12,Math.min(innerWidth-w-12,r.right-w))+'px';dialog.style.top=Math.max(12,Math.min(innerHeight-h-12,r.bottom+8))+'px';
    dialog.querySelectorAll('[data-message-action]').forEach(b=>b.onclick=()=>env.safe(async()=>{const action=b.dataset.messageAction,m=menuMessage;if(action==='select'){select(m);return;}close();if(action==='edit')edit(m);if(action==='copy')await copy(m);}));
  }
  function bind(value){
    env=value;clearTimeout(pressTimer);
    if(!modalityBound){
      modalityBound=true;
      document.addEventListener('keydown',event=>{if(event.key==='Tab')document.documentElement.classList.add('chat-keyboard-navigation');},true);
      document.addEventListener('pointerdown',()=>document.documentElement.classList.remove('chat-keyboard-navigation'),true);
    }
    if(editing&&(editing.session!==env.state.chatSession?.id||!env.state.messages.some(m=>m.id===editing.id&&m.role==='user')))editing=null;
    const shell=document.querySelector('.chat-shell');if(!shell)return;
    if(!$('chat-message-menu')){const dialog=document.createElement('dialog');dialog.id='chat-message-menu';dialog.className='chat-message-menu';dialog.setAttribute('aria-label','消息操作');shell.appendChild(dialog);dialog.addEventListener('click',event=>{if(event.target===dialog){const r=dialog.getBoundingClientRect();if(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom)close();}});dialog.addEventListener('cancel',event=>{event.preventDefault();close();});}
    document.querySelectorAll('.chat-message.user .message-content').forEach(bubble=>{
      const message=env.state.messages.find(m=>m.id===bubble.closest('[data-message-id]').dataset.messageId);if(!message||message.role!=='user')return;
      bubble.tabIndex=0;bubble.setAttribute('role','button');bubble.setAttribute('aria-label','修改已发送消息；长按显示更多操作');bubble.setAttribute('aria-haspopup','dialog');
      let start=null,suppress=false;
      const stop=()=>{clearTimeout(pressTimer);pressTimer=0;};
      bubble.onpointerdown=event=>{stop();suppress=false;if(event.button!==0)return;if(start){start=null;suppress=true;return;}start={x:event.clientX,y:event.clientY};pressTimer=setTimeout(()=>{if(start){suppress=true;showMenu(message,bubble);}},480);};
      bubble.onpointermove=event=>{if(start&&Math.hypot(event.clientX-start.x,event.clientY-start.y)>8){stop();suppress=true;start=null;}};
      bubble.onpointerup=()=>{stop();start=null;};bubble.onpointercancel=()=>{stop();start=null;suppress=true;};bubble.onlostpointercapture=stop;
      bubble.oncontextmenu=event=>{event.preventDefault();stop();suppress=true;showMenu(message,bubble);};
      bubble.onclick=event=>{if(suppress){event.preventDefault();suppress=false;return;}if(root.getSelection?.().toString())return;edit(message);};
      bubble.onkeydown=event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();edit(message);}if(event.key==='ContextMenu'||event.shiftKey&&event.key==='F10'){event.preventDefault();showMenu(message,bubble);}};
    });paint();
  }
  root.ChatMessageActions={bind,close,cancel,text,sent:()=>{editing=null;paint();}};
})(window);
