// Chapter narration runs entirely in the native service, including all quote/voice changes.
const androidListenPlay=listenPlay;
listenPlay=async function(){
 if(!window.DesktopHost)return androidListenPlay();
 const l=S.listen;
 try{
  if(l.paused){await DesktopHost.call('speechResume');return}
  flushAllEdits();listenPrepare();const item=l.queue[l.index];if(!item){toast('No text to narrate');return}
  l.pid=item.id;listenBookmark();
  await DesktopHost.call('mediaPlay',{json:JSON.stringify(S.project),chapterId:item.chapterId,pid:item.id,wholeBook:l.scope==='book',options:{voice:l.voice,rate:l.rate,rules:l.rules}});
 }catch(e){l.playing=l.paused=false;syncListenUI();toast('Speech: '+e.message)}
};
const androidListenSave=listenSave;
listenSave=function(){androidListenSave();if(window.DesktopHost)DesktopHost.call('mediaSettings',{voice:S.listen.voice,rate:S.listen.rate,rules:S.listen.rules}).catch(console.warn)};
window.AndroidHost?.on(event=>{
 const m=event.data;if(m.type!=='mediaState')return;
 const l=S.listen,chapter=S.project.Chapters.find(c=>c.Id===m.chapterId);
 l.playing=!!m.playing;l.paused=!!m.paused;
 if(chapter){
  l.pid=m.pid;
  l.queue=S.project.Chapters.filter(c=>c.BookId===chapter.BookId).flatMap(c=>c.Paragraphs.filter(p=>p.Text.trim()).map(p=>({id:p.Id,chapter:c.Title,chapterId:c.Id,text:p.Text})));
  l.index=Math.max(0,l.queue.findIndex(p=>p.id===m.pid));
  (l.bookmarks||={})[chapter.BookId]=m.pid;androidListenSave();
  if(S.view==='listen'){S.bookId=chapter.BookId;S.chapterId=chapter.Id;const picker=$('#listen-chapter');if(picker)picker.value=chapter.Id}
 }
 syncListenUI();if(m.error)toast('Speech: '+m.error);
});
// Refresh state after returning from the car or lock screen, without starting playback.
document.addEventListener('visibilitychange',()=>{if(!document.hidden)DesktopHost.call('mediaState').catch(console.warn)});
