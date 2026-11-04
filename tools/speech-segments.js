// Recognize paired quotes before applying pronunciation substitutions.
// Apostrophes inside words are not quotation boundaries.
function speechSegments(text){
 const pattern=/"[^"\n]*(?:\n[^"\n]*)*"|“[^”]*”|‘(?:[^’]|’(?=\p{L}))*’|(?<![\p{L}\p{N}])'(?=\S)(?:[^']|'(?=\p{L}))*'(?![\p{L}\p{N}])/gu;
 const parts=[];let end=0;
 for(const match of text.matchAll(pattern)){
  if(match.index>end)parts.push({text:text.slice(end,match.index),role:'narration'});
  parts.push({text:match[0],role:'dialogue'});end=match.index+match[0].length;
 }
 if(end<text.length)parts.push({text:text.slice(end),role:'narration'});
 return parts;
}
function listenPlay(){
 const l=S.listen;
 if(!window.NWTTS?.available()){toast('No speech voice available. Install an offline TTS voice on the device.');return}
 if(l.paused){l.paused=false;l.playing=true;window.NWTTS.resume();syncListenUI();return}
 if(!l.queue.length)listenPrepare();const item=l.queue[l.index];if(!item){toast('No text to narrate');return}
 l.pid=item.id;listenBookmark();l.playing=true;l.paused=false;const token=++l.token;
 const segments=speechSegments(item.text).filter(p=>p.text.trim());let index=0;syncListenUI();
 function next(){
  if(token!==l.token||!l.playing||l.paused)return;
  if(index>=segments.length){listenAdvance(1,true);return}
  const part=segments[index];
  window.NWTTS.speak(listenSpokenText(part.text),{...speechPlaybackOptions(item.id,part.role),onDone:()=>{if(token!==l.token||!l.playing||l.paused)return;index++;next()},onError:message=>{if(token!==l.token||l.paused)return;listenStop();toast('Speech: '+message)}});
 }
 next();
}
