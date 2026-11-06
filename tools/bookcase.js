const bookcase={shelves:[],activeId:'',loading:false,busy:false,error:'',warnings:[],importTarget:'',importTitle:'Imported books',selection:null,spines:false,detailsToken:0,inspection:null,readerReturn:null};
function bookcaseCoverUrl(value){return typeof value==='string'&&/^data:image\/(?:png|jpeg);base64,[A-Za-z0-9+/]+={0,2}$/.test(value)?value:''}
function bookcaseWorkspaceShelf(){return {id:'__workspace__',title:S.project.Title||'Current series',workspace:true,books:S.project.Books.map(b=>({id:b.Id,title:b.Title,author:b.Author||'',cover:bookcaseCoverUrl(b.FrontCover?.DataUrl),spine:bookcaseCoverUrl(b.SpineCover?.DataUrl)}))}}
function bookcaseShelves(){
 const shelves=[...bookcase.shelves].sort((a,b)=>a.title.localeCompare(b.title));
 if(bookcase.activeId){const at=shelves.findIndex(s=>s.id===bookcase.activeId);if(at>=0)shelves[at]={...bookcaseWorkspaceShelf(),id:bookcase.activeId,workspace:false}}
 else if(S.project.Books.length)shelves.unshift(bookcaseWorkspaceShelf());
 return shelves;
}
function bookcaseSelected(shelves=bookcaseShelves()){
 let shelf=shelves.find(s=>s.id===bookcase.selection?.shelf),book=shelf?.books.find(b=>b.id===bookcase.selection?.book);
 if(!book){shelf=shelves.find(s=>s.books.length);book=shelf?.books[0]}
 bookcase.selection=book?{shelf:shelf.id,book:book.id}:null;return book?{shelf,book}:null;
}
function bookcaseBook(book,shelf){
 const cover=bookcaseCoverUrl(book.cover),spine=bookcaseCoverUrl(book.spine),colors=['#294e59','#5e4d65','#6c5344','#3d6257','#4c5878'],color=colors[[...book.title].reduce((sum,c)=>sum+c.charCodeAt(0),0)%colors.length],selected=bookcase.selection?.shelf===shelf.id&&bookcase.selection?.book===book.id;
 return `<button type="button" class="bookcase-book ${selected?'is-selected':''}" data-action="bookcase-select" data-shelf="${h(shelf.id)}" data-book="${h(book.id)}" ${bookcase.busy?'disabled':''} aria-label="Select ${h(book.title)}" aria-pressed="${selected}"><span class="bookcase-volume" style="--book-color:${color}"><span class="bookcase-cover">${cover?`<img src="${h(cover)}" alt="" loading="lazy" decoding="async">`:`<span class="bookcase-fallback"><span>${h(book.title)}</span><small>${h(book.author||'NovelWeaver')}</small></span>`}</span><span class="bookcase-spine">${spine?`<img src="${h(spine)}" alt="" loading="lazy" decoding="async">`:`<span>${h(book.title)}</span><small>${h(book.author||'NovelWeaver')}</small>`}</span><span class="bookcase-pages" aria-hidden="true"></span><span class="bookcase-book-top" aria-hidden="true"></span></span><span class="bookcase-book-title">${h(book.title)}</span></button>`;
}
function bookcaseSelectionPanel(selected){
 if(!selected)return '<div class="bookcase-selection-empty">Import your first series to begin.</div>';
 const {book,shelf}=selected,cover=bookcaseCoverUrl(book.cover);
 return `<div class="bookcase-selection-art">${cover?`<img src="${h(cover)}" alt="">`:'<span aria-hidden="true">NW</span>'}</div><div class="bookcase-selection-text"><h2>${h(book.title)}</h2><p>${h(book.author||shelf.title)}</p></div><div class="bookcase-selection-actions"><button class="primary-button" data-action="bookcase-open" data-shelf="${h(shelf.id)}" data-book="${h(book.id)}" ${bookcase.busy?'disabled':''}>Open book</button><button class="outline-button" data-action="bookcase-details" data-shelf="${h(shelf.id)}" data-book="${h(book.id)}" ${bookcase.busy?'disabled':''}>Details</button></div>`;
}
function bookcaseVacantBays(count){
 return Array.from({length:Math.max(0,3-count)},(_,i)=>`<section class="bookcase-bay bookcase-vacant-bay" aria-label="Empty shelf"><header><h2>${i===0?'Room for another story':'A shelf of possibilities'}</h2></header><div class="bookcase-vacant-content"><button class="bookcase-add-volume" data-action="${i===0?'bookcase-vacant-series':'bookcase-vacant-books'}"><span aria-hidden="true">+</span><strong>${i===0?'Import a series':'Add books'}</strong></button><p>${i===0?'Bring another collection into your library.':'Individual books can have a shelf of their own.'}</p></div></section>`).join('');
}
function renderBookcase(){
 const shelves=bookcaseShelves(),selected=bookcaseSelected(shelves);
 return `<div class="bookcase-library ${bookcase.spines?'show-spines':''}"><header class="bookcase-heading"><div class="bookcase-heading-top"><button class="ghost-button" data-action="bookcase-back">← Read</button><span>YOUR READING ROOM</span><button class="ghost-button" data-action="bookcase-orientation" aria-pressed="${bookcase.spines}" aria-label="${bookcase.spines?'Show front covers':'Show book spines'}">${bookcase.spines?'Covers':'Spines'}</button></div><h1>Your Library</h1><p>Choose a book to begin reading</p><div class="bookcase-toolbar"><button class="outline-button" data-action="bookcase-import-series" ${bookcase.busy?'disabled':''}>+ Import series</button><button class="outline-button" data-action="bookcase-import-books" ${bookcase.busy?'disabled':''}>+ Import books</button></div></header>`+
 (bookcase.loading?'<p class="bookcase-status" role="status">Loading saved shelves…</p>':'')+(bookcase.error?`<div class="callout warning" role="alert">${h(bookcase.error)} <button class="outline-button" data-action="bookcase-refresh">Retry</button></div>`:'')+
 bookcase.warnings.map(message=>`<p class="callout warning">${h(message)}</p>`).join('')+
 `<div class="bookcase-room"><span class="bookcase-ivy" aria-hidden="true"></span>${shelves.map(s=>`<section class="bookcase-bay bookcase-shelf" aria-label="${h(s.title)} series"><header><h2>${h(s.title)}</h2><span>${s.books.length} book${s.books.length===1?'':'s'}${s.workspace?' · Current project':''}</span></header><div class="bookcase-books" tabindex="0" role="group" aria-label="Books in ${h(s.title)}; swipe to browse">${s.books.map(b=>bookcaseBook(b,s)).join('')||'<p class="bookcase-empty-shelf">This shelf is ready for more books.</p>'}</div><div class="bookcase-plank" aria-hidden="true"></div></section>`).join('')}${bookcaseVacantBays(shelves.length)}</div><div class="bookcase-browse-hint"><span aria-hidden="true">●　●　●</span> Swipe to browse</div><div class="bookcase-selection" aria-label="Selected book">${bookcaseSelectionPanel(selected)}</div>${!bookcase.activeId&&S.project.Books.length?`<div class="bookcase-workspace-actions"><button class="ghost-button" data-action="bookcase-keep" ${bookcase.busy?'disabled':''}>Keep current series on this bookcase</button></div>`:''}<p class="bookcase-storage-note">Imported copies stay on this device. Use Save as for a separate backup.</p></div>`;
}
function bookcaseSelect(shelfId,bookId){
 if(bookcase.busy)return;
 const shelf=bookcaseShelves().find(s=>s.id===shelfId),book=shelf?.books.find(b=>b.id===bookId);if(!book)return;
 bookcase.selection={shelf:shelfId,book:bookId};
 const root=document.querySelector('.bookcase-library');if(!root)return;
 root.querySelectorAll('.bookcase-book').forEach(el=>{const chosen=el.dataset.shelf===shelfId&&el.dataset.book===bookId;el.classList.toggle('is-selected',chosen);el.setAttribute('aria-pressed',String(chosen))});
 const panel=root.querySelector('.bookcase-selection');panel.innerHTML=bookcaseSelectionPanel({shelf,book});
}
async function bookcaseDetails(shelfId,bookId){return bookcaseInspect(shelfId,bookId)}
async function bookcaseRefresh(){
 bookcase.loading=true;bookcase.error='';if(S.view==='bookcase')render();
 try{const value=await DesktopHost.call('bookcaseList');bookcase.shelves=value.shelves||[];bookcase.activeId=value.activeId||'';bookcase.warnings=value.warnings||[]}
 catch(error){bookcase.error=error.message}
 finally{bookcase.loading=false;if(S.view==='bookcase')render()}
}
async function bookcaseShow(){flushAllEdits();await saveLocal();S.view='bookcase';closeSidebar();render();await bookcaseRefresh()}
function bookcaseIsEmptyWorkspace(){return S.project.Title==='Untitled Series'&&S.project.Books.length<=1&&S.project.Books.every(b=>b.Title==='Book One'&&!b.Author&&!b.FrontCover&&!b.BackCover&&!b.SpineCover)&&!S.project.Threads.length&&!S.project.History.length&&!S.project.Reviews.length&&!S.project.SpeechProfiles?.length&&S.project.Chapters.every(c=>c.Title==='Chapter 1'&&c.Paragraphs.every(p=>!p.Text.trim()&&!p.Speech))}
// A bookcase reading trip is UI state; it never goes into the project file.
function bookcaseCaptureReadingReturn(shelfId,bookId){
 return {shelfId,bookId,y:window.scrollY||0,main:document.getElementById('main')?.scrollTop||0,shelves:[...document.querySelectorAll('.bookcase-shelf .bookcase-books')].map(el=>({id:el.querySelector('.bookcase-book')?.dataset.shelf,left:el.scrollLeft}))};
}
function bookcaseReturnFromReader(){
 const trip=bookcase.readerReturn;if(!trip)return false;bookcase.readerReturn=null;
 bookcase.selection={shelf:trip.shelfId,book:trip.bookId};S.view='bookcase';render();
 requestAnimationFrame(()=>{
  if(S.view!=='bookcase')return;
  for(const el of document.querySelectorAll('.bookcase-shelf .bookcase-books')){const saved=trip.shelves.find(s=>s.id===el.querySelector('.bookcase-book')?.dataset.shelf);if(saved)el.scrollLeft=saved.left}
  const main=document.getElementById('main');if(main)main.scrollTop=trip.main;window.scrollTo(0,trip.y);
  document.querySelector('.bookcase-shelf .bookcase-book.is-selected')?.focus({preventScroll:true});
 });return true;
}
function bookcaseReaderBack(){
 if(!bookcase.readerReturn)return false;
 // The native fullscreenchange event and the explicit exit may race. Clearing
 // the trip before rendering makes returning idempotent in both cases.
 ebookExitFullscreen().then(()=>{if(bookcase.readerReturn)bookcaseReturnFromReader()}).catch(()=>bookcaseReturnFromReader());return true;
}
async function bookcaseOpen(shelfId,bookId){
 if(bookcase.busy||desktop.busy)return;const readingReturn=bookcaseCaptureReadingReturn(shelfId,bookId);let readingStarted=false;bookcase.busy=true;desktop.busy=true;
 try{
  if(shelfId!=='__workspace__'&&shelfId!==bookcase.activeId){
   if(!bookcaseIsEmptyWorkspace()&&!await desktopConfirmLeave())return;
   if(bookcaseIsEmptyWorkspace()){flushAllEdits();await saveLocal()}
   const result=await DesktopHost.call('bookcaseOpen',{id:shelfId});
   if(!desktopHandleResult(result))return;desktopAdopt(result.project);bookcase.activeId=result.bookcaseId||shelfId;
  }
  const book=S.project.Books.find(b=>b.Id===bookId)||S.project.Books[0];if(!book)throw Error('This shelf has no books.');
  listenStop();S.bookId=book.Id;S.chapterId=S.project.Chapters.find(c=>c.BookId===book.Id)?.Id||null;S.readChapterId=null;S.reader.page=0;S.reader.mode='ebook';bookcase.readerReturn={...readingReturn,bookId:book.Id,projectId:S.project.Id};readingStarted=true;bookcase.busy=false;desktop.busy=false;S.view='read';readerSave();render();await saveLocal();await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));if(S.view==='read'&&bookcase.readerReturn)await ebookEnterFullscreen();
 }catch(error){modal('Could not open book',`<p role="alert">${h(error.message)}</p>`)}
 finally{bookcase.busy=false;desktop.busy=false;if(S.view==='bookcase'&&!readingStarted)render()}
}
async function bookcaseCheckpoint(){flushAllEdits();await saveLocal();if(desktop.blocked)throw Error('Resolve the current project file conflict before importing books.');if(desktop.path&&!await desktopSave())throw Error('Save the current project before importing books.')}
async function bookcaseKeepCurrent(){
 if(bookcase.busy)return;bookcase.busy=true;render();
 try{flushAllEdits();await saveLocal();const value=await DesktopHost.call('bookcasePut',{json:JSON.stringify(S.project)});toast(value.status==='duplicate'?'This series is already on your bookcase':'Series kept on your bookcase');await bookcaseRefresh()}
 catch(error){modal('Could not keep series',`<p role="alert">${h(error.message)}</p>`)}finally{bookcase.busy=false;if(S.view==='bookcase')render()}
}
async function bookcaseImportDialog(kind){
 if(bookcase.busy)return;
 if(kind==='series'){const input=$('#bookcase-series-input');input.value='';input.click();return}
 await bookcaseRefresh();
 modal('Import books',`<p>Choose multiple EPUB, DOCX, TXT, or .novel.json files. Their books will be copied onto one shelf.</p><label class="form-label" for="bookcase-target">Series shelf</label><select class="text-input" id="bookcase-target"><option value="">Create a new shelf</option>${bookcase.shelves.map(s=>`<option value="${h(s.id)}">${h(s.title)}</option>`).join('')}</select><label class="form-label" for="bookcase-new-title">New series name (for a new shelf)</label><input class="text-input" id="bookcase-new-title" maxlength="200" value="Imported books">`,[{text:'Cancel',value:'cancel'},{text:'Choose books…',value:'choose',primary:true}],value=>{if(value!=='choose')return;bookcase.importTarget=$('#bookcase-target').value;bookcase.importTitle=$('#bookcase-new-title').value.trim()||'Imported books';const input=$('#bookcase-books-input');input.value='';input.click()});
}
function bookcaseBusyOverlay(show){const old=document.getElementById('bookcase-busy-overlay');if(old)old.remove();if(!show)return;const el=document.createElement('div');el.id='bookcase-busy-overlay';el.className='bookcase-busy-overlay';el.setAttribute('role','status');el.innerHTML='<span>Importing your books…</span>';document.body.append(el)}
async function bookcaseImportFiles(files,seriesOnly){
 if(!files.length||bookcase.busy)return;bookcase.busy=true;bookcaseBusyOverlay(true);render();const failures=[];let imported=0,duplicates=0;
 try{
  await bookcaseCheckpoint();
  if(seriesOnly){
   for(const file of files){try{const project=await bookcaseReadFile(file,true),result=await DesktopHost.call('bookcasePut',{json:JSON.stringify(project)});if(result.status==='duplicate')duplicates++;else imported++}catch(error){failures.push(file.name+': '+error.message)}}
  }else{
   const target=bookcase.importTarget,stored=target?await DesktopHost.call('bookcaseRead',{id:target}):null;
   let project=stored?cleanProject(stored.project):bookcaseProject(bookcase.importTitle);
   for(const file of files){try{const incoming=await bookcaseReadFile(file),merged=mergeImportedBooks(project,incoming);project=merged.project;imported+=merged.count}catch(error){failures.push(file.name+': '+error.message)}}
   if(imported){
    const result=await DesktopHost.call('bookcasePut',{id:target,json:JSON.stringify(project),revision:stored?.revision});
    if(result.id===bookcase.activeId){const opened=await DesktopHost.call('bookcaseOpen',{id:result.id});desktopHandleResult(opened);desktopAdopt(opened.project);S.view='bookcase';await saveLocal()}
   }
  }
  await bookcaseRefresh();
  const summary=`${imported} ${seriesOnly?'series':'book(s)'} imported${duplicates?`; ${duplicates} already on the bookcase`:''}.`;
  if(failures.length)modal('Bookcase import results',`<p>${h(summary)}</p><p>The following files were not imported:</p><ul>${failures.map(message=>`<li>${h(message)}</li>`).join('')}</ul>`);else toast(summary);
 }catch(error){modal('Bookcase import failed',`<p role="alert">${h(error.message)}</p>`)}
 finally{bookcaseBusyOverlay(false);bookcase.busy=false;$('#bookcase-series-input').value='';$('#bookcase-books-input').value='';if(S.view==='bookcase')render()}
}
document.addEventListener('click',event=>{
 const button=event.target.closest('[data-action^="bookcase-"]');if(!button)return;event.preventDefault();
 const action=button.dataset.action;
 if(action==='bookcase-show')bookcaseShow().catch(error=>toast(error.message));
 else if(action==='bookcase-back'){S.view='read';render()}
 else if(action==='bookcase-refresh')bookcaseRefresh();
 else if(action==='bookcase-keep')bookcaseKeepCurrent();
 else if(action==='bookcase-select')bookcaseSelect(button.dataset.shelf,button.dataset.book);
 else if(action==='bookcase-inspect-close')bookcaseCloseDetails();
 else if(action==='bookcase-inspect-zoom')bookcaseZoomDetails();
 else if(action==='bookcase-details')bookcaseDetails(button.dataset.shelf,button.dataset.book);
 else if(action==='bookcase-orientation'){bookcase.spines=!bookcase.spines;const root=document.querySelector('.bookcase-library');root?.classList.toggle('show-spines',bookcase.spines);button.textContent=bookcase.spines?'Covers':'Spines';button.setAttribute('aria-pressed',String(bookcase.spines));button.setAttribute('aria-label',bookcase.spines?'Show front covers':'Show book spines')}
 else if(action==='bookcase-open')bookcaseOpen(button.dataset.shelf,button.dataset.book);
 else if(action==='bookcase-vacant-series')bookcaseImportDialog('series');
 else if(action==='bookcase-vacant-books')bookcaseImportDialog('books');
 else if(action==='bookcase-import-series')bookcaseImportDialog('series');
 else if(action==='bookcase-import-books')bookcaseImportDialog('books');
});
document.addEventListener('change',event=>{if(event.target.id==='bookcase-series-input')bookcaseImportFiles([...event.target.files],true);if(event.target.id==='bookcase-books-input')bookcaseImportFiles([...event.target.files],false)});
