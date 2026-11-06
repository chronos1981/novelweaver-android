const bookcase={shelves:[],activeId:'',loading:false,busy:false,error:'',warnings:[],importTarget:'',importTitle:'Imported books'};
function bookcaseCoverUrl(value){return typeof value==='string'&&/^data:image\/(?:png|jpeg);base64,[A-Za-z0-9+/]+={0,2}$/.test(value)?value:''}
function bookcaseWorkspaceShelf(){return {id:'__workspace__',title:S.project.Title||'Current series',workspace:true,books:S.project.Books.map(b=>({id:b.Id,title:b.Title,author:b.Author||'',cover:bookcaseCoverUrl(b.FrontCover?.DataUrl)}))}}
function bookcaseBook(book,shelf){
 const cover=bookcaseCoverUrl(book.cover),colors=['#294e59','#5e4d65','#6c5344','#3d6257','#4c5878'],color=colors[[...book.title].reduce((sum,c)=>sum+c.charCodeAt(0),0)%colors.length];
 return `<button type="button" class="bookcase-book" data-action="bookcase-open" data-shelf="${h(shelf.id)}" data-book="${h(book.id)}" ${bookcase.busy?'disabled':''} aria-label="Read ${h(book.title)}"><span class="bookcase-cover" style="--book-color:${color}">${cover?`<img src="${h(cover)}" alt="" loading="lazy" decoding="async">`:`<span class="bookcase-fallback"><span>${h(book.title)}</span><small>${h(book.author||'NovelWeaver')}</small></span>`}</span><span class="bookcase-book-title">${h(book.title)}</span>${book.author?`<span class="bookcase-author">${h(book.author)}</span>`:''}</button>`;
}
function renderBookcase(){
 const shelves=[...bookcase.shelves].sort((a,b)=>a.title.localeCompare(b.title));
 if(bookcase.activeId){const at=shelves.findIndex(s=>s.id===bookcase.activeId);if(at>=0)shelves[at]={...bookcaseWorkspaceShelf(),id:bookcase.activeId,workspace:false}}
 else if(S.project.Books.length)shelves.unshift(bookcaseWorkspaceShelf());
 return pageTitle('YOUR READING ROOM','Bookcase','A shelf for every series. Open a book by its cover.',`<button class="outline-button" data-action="bookcase-back">← Back to Read</button>`)+
 `<div class="bookcase-toolbar"><button class="primary-button" data-action="bookcase-import-series" ${bookcase.busy?'disabled':''}>+ Import series</button><button class="outline-button" data-action="bookcase-import-books" ${bookcase.busy?'disabled':''}>+ Import books</button>${!bookcase.activeId&&S.project.Books.length?`<button class="outline-button" data-action="bookcase-keep" ${bookcase.busy?'disabled':''}>Keep current series</button>`:''}</div><p class="item-sub">Imported copies stay on this device. Series use .novel.json; individual books can be EPUB, DOCX, TXT, or .novel.json. Use Save as to keep a separate backup.</p>`+
 (bookcase.busy?'<p class="bookcase-status" role="status">Working on your bookcase…</p>':'')+(bookcase.loading?'<p role="status">Loading saved shelves…</p>':'')+
 (bookcase.error?`<div class="callout warning" role="alert">${h(bookcase.error)} <button class="outline-button" data-action="bookcase-refresh">Retry</button></div>`:'')+
 bookcase.warnings.map(message=>`<p class="callout warning">${h(message)}</p>`).join('')+
 `<div class="bookcase-room">${shelves.map(s=>`<section class="bookcase-shelf" aria-label="${h(s.title)} series"><header><h2>${h(s.title)}</h2><span>${s.books.length} book${s.books.length===1?'':'s'}${s.workspace?' · Current project':''}</span></header><div class="bookcase-books">${s.books.map(b=>bookcaseBook(b,s)).join('')}</div><div class="bookcase-plank" aria-hidden="true"></div></section>`).join('')||'<div class="empty"><h3>Your bookcase is ready</h3>Import a series or some books to fill the first shelf.</div>'}</div>`;
}
async function bookcaseRefresh(){
 bookcase.loading=true;bookcase.error='';if(S.view==='bookcase')render();
 try{const value=await DesktopHost.call('bookcaseList');bookcase.shelves=value.shelves||[];bookcase.activeId=value.activeId||'';bookcase.warnings=value.warnings||[]}
 catch(error){bookcase.error=error.message}
 finally{bookcase.loading=false;if(S.view==='bookcase')render()}
}
async function bookcaseShow(){flushAllEdits();await saveLocal();S.view='bookcase';closeSidebar();render();await bookcaseRefresh()}
function bookcaseIsEmptyWorkspace(){return S.project.Title==='Untitled Series'&&S.project.Books.length<=1&&S.project.Books.every(b=>b.Title==='Book One'&&!b.Author&&!b.FrontCover&&!b.BackCover)&&!S.project.Threads.length&&!S.project.History.length&&!S.project.Reviews.length&&!S.project.SpeechProfiles?.length&&S.project.Chapters.every(c=>c.Title==='Chapter 1'&&c.Paragraphs.every(p=>!p.Text.trim()&&!p.Speech))}
async function bookcaseOpen(shelfId,bookId){
 if(bookcase.busy||desktop.busy)return;bookcase.busy=true;desktop.busy=true;
 try{
  if(shelfId!=='__workspace__'&&shelfId!==bookcase.activeId){
   if(!bookcaseIsEmptyWorkspace()&&!await desktopConfirmLeave())return;
   if(bookcaseIsEmptyWorkspace()){flushAllEdits();await saveLocal()}
   const result=await DesktopHost.call('bookcaseOpen',{id:shelfId});
   if(!desktopHandleResult(result))return;desktopAdopt(result.project);bookcase.activeId=result.bookcaseId||shelfId;
  }
  const book=S.project.Books.find(b=>b.Id===bookId)||S.project.Books[0];if(!book)throw Error('This shelf has no books.');
  listenStop();S.bookId=book.Id;S.chapterId=S.project.Chapters.find(c=>c.BookId===book.Id)?.Id||null;S.readChapterId=null;S.reader.page=0;S.view='read';render();await saveLocal();
 }catch(error){modal('Could not open book',`<p role="alert">${h(error.message)}</p>`)}
 finally{bookcase.busy=false;desktop.busy=false;if(S.view==='bookcase')render()}
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
 else if(action==='bookcase-open')bookcaseOpen(button.dataset.shelf,button.dataset.book);
 else if(action==='bookcase-import-series')bookcaseImportDialog('series');
 else if(action==='bookcase-import-books')bookcaseImportDialog('books');
});
document.addEventListener('change',event=>{if(event.target.id==='bookcase-series-input')bookcaseImportFiles([...event.target.files],true);if(event.target.id==='bookcase-books-input')bookcaseImportFiles([...event.target.files],false)});
