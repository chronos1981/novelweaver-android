'use strict';
// Origin-bound Android WebMessageListener transport; no JavascriptInterface.
window.AndroidHost=(()=>{
 const listeners=[];
 return {receive(message){for(const fn of listeners)fn({data:message})},on(fn){listeners.push(fn)}};
})();
window.DesktopHost=(()=>{
 const pending=new Map();let sequence=0;
 AndroidHost.on(event=>{
  const m=event.data;
  if(m.type==='speechComplete'){window.NWTTS?.nativeComplete(m.key,m.error);return}
  if(m.type==='voices'){window.AndroidVoices=m.voices||[];window.dispatchEvent(new Event('nw-voices'));return}
  if(m.type==='fileStatus'){window.dispatchEvent(new CustomEvent('nw-file-status',{detail:m.result}));return}
  const task=pending.get(m.id);if(!task)return;pending.delete(m.id);task.cleanup();
  m.ok?task.resolve(m.result):task.reject(Error(m.error||'Android operation failed.'));
 });
 function call(action,payload={},signal){return new Promise((resolve,reject)=>{
  if(signal?.aborted){reject(Error('Request stopped.'));return}
  const id=String(++sequence),abort=()=>{pending.delete(id);AndroidBridge.postMessage(JSON.stringify({id:String(++sequence),action:'cancel',payload:{requestId:id}}));reject(Error('Request stopped. The server may finish processing it.'))};
  let blocker=null;
  if(['open','openLast','openRecovery','reloadDisk','new','bookcaseOpen'].includes(action)){
   blocker=document.createElement('div');blocker.setAttribute('role','status');blocker.textContent='Opening project…';
   blocker.style.cssText='position:fixed;inset:0;z-index:2147483647;display:grid;place-items:center;background:rgba(19,35,48,.92);color:white;font:18px sans-serif';document.body.append(blocker);
  }
  pending.set(id,{resolve,reject,cleanup:()=>{signal?.removeEventListener('abort',abort);blocker?.remove()}});
  signal?.addEventListener('abort',abort,{once:true});
  AndroidBridge.postMessage(JSON.stringify({id,action,payload}));
 })}
 return {call};
})();
