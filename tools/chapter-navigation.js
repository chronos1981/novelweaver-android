function writeChapterTargets(ch=activeChapter()) {
  const chapters=S.project.Chapters.filter(item=>item.BookId===ch?.BookId);
  const index=chapters.findIndex(item=>item.Id===ch?.Id);
  return {previous:index>0?chapters[index-1]:null,next:index>=0?chapters[index+1]||null:null};
}
function writeChapterNavigation(ch) {
  const {previous,next}=writeChapterTargets(ch);
  return `<div class="chapter-navigation" role="group" aria-label="Chapter navigation"><button type="button" class="outline-button" data-action="previous-chapter" ${previous?'':'disabled'} title="${h(previous?'Previous chapter: '+previous.Title:'First chapter in this book')}">← Previous chapter</button><button type="button" class="outline-button" data-action="next-chapter" ${next?'':'disabled'} title="${h(next?'Next chapter: '+next.Title:'Last chapter in this book')}">Next chapter →</button></div>`;
}
function moveWriteChapter(direction) {
  if(direction!==-1&&direction!==1)return false;
  flushAllEdits();
  const targets=writeChapterTargets(),target=direction===-1?targets.previous:targets.next;
  if(!target)return false;
  listenStop();S.editSession.clear();
  S.chapterId=target.Id;S.bookId=target.BookId;S.paraId=null;S.readChapterId=null;S.view='write';
  closeSidebar();render();
  if(quill.open)quillUpdateContext();
  $('#main')?.scrollTo?.({top:0,behavior:'instant'});window.scrollTo({top:0,behavior:'instant'});
  const heading=$('#main-body h1');if(heading){heading.setAttribute('tabindex','-1');heading.focus({preventScroll:true})}
  if(S.dirty)saveLocal();
  return true;
}
