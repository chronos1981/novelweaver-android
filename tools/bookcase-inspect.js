// Move the selected live book into a viewport-sized inspection scene, then
// return that same element to its reserved shelf slot. No dialog or file open.
function bookcaseInspectionTarget(state,ratio){
 const view=window.visualViewport,w=view?.width||innerWidth,ht=view?.height||innerHeight,x=view?.offsetLeft||0,y=view?.offsetTop||0;
 const maxWidth=Math.max(100,w-28),maxHeight=Math.max(100,ht-116),height=Math.min(maxHeight,maxWidth/ratio),width=height*ratio;
 state.target={left:x+(w-width)/2,top:y+58+(maxHeight-height)/2,width,height};
 if(!state.closing)Object.assign(state.book.style,Object.fromEntries(Object.entries(state.target).map(([k,v])=>[k,v+'px'])));
}
function bookcaseInspectionBack(state,book,error=''){
 if(bookcase.inspection!==state||state.closing)return;
 const cover=cleanCover(book?.BackCover),front=cleanCover(book?.FrontCover),spine=cleanCover(book?.SpineCover);
 const frontImage=state.book.querySelector('.bookcase-cover img'),spineImage=state.book.querySelector('.bookcase-spine img');
 if(front&&frontImage)frontImage.src=front.DataUrl;if(spine&&spineImage)spineImage.src=spine.DataUrl;
 state.cover=!!cover;state.back.innerHTML=cover?`<img src="${h(cover.DataUrl)}" alt="Back cover of ${h(book.Title)}" draggable="false">`:`<span class="bookcase-inspect-missing"><strong>${h(book?.Title||state.title)}</strong><span>${h(error||'No back cover has been added for this book.')}</span><small>${error?'Return to the shelf and try again.':'Add one in Manage covers.'}</small></span>`;
 state.book.setAttribute('aria-label',cover?'Zoom back cover':'Return book to shelf');state.book.setAttribute('aria-busy','false');
 state.hint.textContent=cover?'Tap to zoom · Back to return':'Tap the book or Back to return';
 state.ratio=cover?cover.Width/cover.Height:2/3;bookcaseInspectionTarget(state,state.ratio);
}
async function bookcaseInspect(shelfId,bookId){
 if(bookcase.busy||bookcase.inspection||S.view!=='bookcase')return;
 const origin=[...document.querySelectorAll('.bookcase-shelf .bookcase-book')].find(el=>el.dataset.shelf===shelfId&&el.dataset.book===bookId);if(!origin)return;
 const bounds=origin.getBoundingClientRect(),volume=origin.querySelector('.bookcase-volume'),matrix=getComputedStyle(volume).transform,slot=document.createElement('span');
 slot.className='bookcase-inspect-slot';Object.assign(slot.style,{flex:'0 0 '+bounds.width+'px',width:bounds.width+'px',height:bounds.height+'px'});origin.before(slot);
 const stage=document.createElement('section');stage.className='bookcase-inspection';stage.setAttribute('aria-label','Inspect back cover');stage.innerHTML='<button type="button" class="bookcase-inspect-shade" data-action="bookcase-inspect-close" aria-label="Return book to shelf" tabindex="-1"></button><button type="button" class="bookcase-inspect-return" data-action="bookcase-inspect-close">← Return to shelf</button><p class="bookcase-inspect-hint" role="status">Loading back cover…</p>';
 const state={book:origin,volume,slot,stage,back:document.createElement('span'),hint:stage.querySelector('.bookcase-inspect-hint'),title:origin.getAttribute('aria-label').replace(/^Select /,''),focusBefore:document.activeElement,projectId:S.project.Id,token:++bookcase.detailsToken,originalStyle:origin.getAttribute('style'),volumeStyle:volume.getAttribute('style'),action:origin.dataset.action,label:origin.getAttribute('aria-label'),pressed:origin.getAttribute('aria-pressed'),matrix,closing:false,cover:false,ratio:2/3,zoomed:false,panX:50,panY:50,inert:[],scroll:document.body.style.overflow};
 bookcase.inspection=state;state.back.className='bookcase-book-back';state.back.innerHTML='<span class="bookcase-inspect-missing" role="status">Loading back cover…</span>';volume.append(state.back);
 origin.classList.add('bookcase-inspected');origin.dataset.action='bookcase-inspect-zoom';origin.setAttribute('aria-label','Loading back cover');origin.setAttribute('aria-busy','true');origin.setAttribute('aria-pressed','false');volume.style.transform=matrix;
 Object.assign(origin.style,{left:bounds.left+'px',top:bounds.top+'px',width:bounds.width+'px',height:bounds.height+'px'});stage.append(origin);document.body.append(stage);
 for(const el of document.body.children){if(el!==stage){state.inert.push([el,el.inert]);el.inert=true}}
 document.body.style.overflow='hidden';stage.querySelector('.bookcase-inspect-return').focus({preventScroll:true});
 state.resize=()=>bookcaseInspectionTarget(state,state.ratio);window.addEventListener('resize',state.resize);window.visualViewport?.addEventListener('resize',state.resize);
 origin.addEventListener('pointerdown',state.down=event=>{if(!state.zoomed)return;state.suppressClick=false;state.drag={x:event.clientX,y:event.clientY,panX:state.panX,panY:state.panY};origin.setPointerCapture(event.pointerId)});
 origin.addEventListener('pointermove',state.move=event=>{if(!state.drag)return;const dx=event.clientX-state.drag.x,dy=event.clientY-state.drag.y;if(Math.abs(dx)+Math.abs(dy)>7)state.suppressClick=true;state.panX=Math.max(0,Math.min(100,state.drag.panX-dx/state.target.width*100));state.panY=Math.max(0,Math.min(100,state.drag.panY-dy/state.target.height*100));state.back.style.setProperty('--pan-x',state.panX+'%');state.back.style.setProperty('--pan-y',state.panY+'%')});
 origin.addEventListener('pointerup',state.up=()=>{state.drag=null});origin.addEventListener('pointercancel',state.up);
 // Commit the shelf geometry before the enlargement and rotation begin.
 origin.getBoundingClientRect();requestAnimationFrame(()=>{if(bookcase.inspection!==state||state.closing)return;bookcaseInspectionTarget(state,state.ratio);volume.style.transform='rotateY(-180deg)';stage.classList.add('is-open')});
 try{
  const project=shelfId==='__workspace__'||shelfId===bookcase.activeId?S.project:(await DesktopHost.call('bookcaseRead',{id:shelfId})).project;
  if(bookcase.inspection!==state||state.closing||state.token!==bookcase.detailsToken||state.projectId!==S.project.Id)return;
  const book=project.Books.find(b=>b.Id===bookId);if(!book)throw Error('This book is no longer on the shelf.');bookcaseInspectionBack(state,book);
 }catch(error){if(bookcase.inspection===state&&!state.closing)bookcaseInspectionBack(state,null,error.message)}
}
function bookcaseZoomDetails(){
 const state=bookcase.inspection;if(!state||state.closing)return;
 if(state.suppressClick){state.suppressClick=false;return}if(!state.cover){bookcaseCloseDetails();return}
 state.zoomed=!state.zoomed;state.panX=50;state.panY=50;state.back.style.setProperty('--pan-x','50%');state.back.style.setProperty('--pan-y','50%');state.back.classList.toggle('is-zoomed',state.zoomed);state.book.setAttribute('aria-pressed',String(state.zoomed));state.book.setAttribute('aria-label',state.zoomed?'Reset back cover zoom':'Zoom back cover');state.hint.textContent=state.zoomed?'Drag to read · Tap to reset zoom · Back to return':'Tap to zoom · Back to return';
}
function bookcaseCloseDetails(immediate=false){
 const state=bookcase.inspection;if(!state)return false;if(state.closing&&!immediate)return true;
 state.closing=true;++bookcase.detailsToken;clearTimeout(state.timer);state.stage.classList.remove('is-open');state.back.classList.remove('is-zoomed');state.book.style.pointerEvents='none';state.volume.style.transform=state.matrix;
 const bounds=state.slot.getBoundingClientRect();Object.assign(state.book.style,{left:bounds.left+'px',top:bounds.top+'px',width:bounds.width+'px',height:bounds.height+'px'});
 const finish=()=>{
  clearTimeout(state.timer);window.removeEventListener('resize',state.resize);window.visualViewport?.removeEventListener('resize',state.resize);
  state.book.removeEventListener('pointerdown',state.down);state.book.removeEventListener('pointermove',state.move);state.book.removeEventListener('pointerup',state.up);state.book.removeEventListener('pointercancel',state.up);
  state.back.remove();state.book.classList.remove('bookcase-inspected');for(const [el,key,value] of [[state.book,'style',state.originalStyle],[state.volume,'style',state.volumeStyle],[state.book,'aria-label',state.label],[state.book,'aria-pressed',state.pressed]]){if(value===null)el.removeAttribute(key);else el.setAttribute(key,value)}
  state.book.removeAttribute('aria-busy');state.book.dataset.action=state.action;
  if(state.slot.isConnected)state.slot.replaceWith(state.book);else state.book.remove();state.stage.remove();for(const [el,inert] of state.inert)el.inert=inert;document.body.style.overflow=state.scroll;
  if(bookcase.inspection===state)bookcase.inspection=null;if(state.focusBefore?.isConnected)state.focusBefore.focus({preventScroll:true});
 };
 if(immediate||matchMedia('(prefers-reduced-motion: reduce)').matches)finish();else state.timer=setTimeout(finish,560);return true;
}
document.addEventListener('keydown',event=>{
 const state=bookcase.inspection;if(!state)return;
 if(event.key==='Escape'){event.preventDefault();event.stopImmediatePropagation();bookcaseCloseDetails()}
 else if((event.ctrlKey||event.metaKey)&&['o','s','n'].includes(event.key.toLowerCase())){event.preventDefault();event.stopImmediatePropagation()}
 else if(event.key==='Tab'){const items=[state.stage.querySelector('.bookcase-inspect-return'),state.book];event.preventDefault();items[(items.indexOf(document.activeElement)+(event.shiftKey?-1:1)+items.length)%items.length].focus()}
},true);
