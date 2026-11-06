// Run with Playwright installed. NOVELWEAVER_BROWSER_PATH can select a local
// Chromium headless-shell executable. No real files or AI services are opened.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{chromium}=require('playwright');
let debugPage;
(async()=>{
 const root=path.resolve(__dirname,'..');let html=fs.readFileSync(root+'/app/src/main/assets/www/index.html','utf8');
 const boot="(async()=>{await loadLocal();await desktopInitialize();listenLoad();readerLoad();quillMount();render();loadLexicon();if(!S.project.History.length&&S.project.Chapters.every(c=>c.Paragraphs.every(p=>!p.Text))){setTimeout(()=>toast('Welcome! Your writing saves on this device.'),650)}})();";
 assert(html.includes(boot));html=html.replace(boot,"window.qa={S,desktop,cleanProject,render,bookcase,bookcaseOpen,bookcaseReadFile,bookcaseProject,bookcaseArchivePath,buildBookEpub,buildBookDocx,bookcaseZip,zipStore,closeModal,bookCovers,bookcaseSelect,bookcaseDetails,cleanCover,mergeImportedBooks};saveLocal=async()=>{await DesktopHost.call('autosave',{json:JSON.stringify(S.project)});S.dirty=false};");html=html.replace(/<script\b[^>]*src=[^>]*><\/script>/g,'');
 const browser=await chromium.launch({headless:true,...(process.env.NOVELWEAVER_BROWSER_PATH?{executablePath:process.env.NOVELWEAVER_BROWSER_PATH}:{}),args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:430,height:1000},deviceScaleFactor:1}),errors=[],requests=[];page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(/^https?:/.test(r.url()))requests.push(r.url())});
 const capture=async name=>{const dir=process.env.NOVELWEAVER_SCREENSHOT_DIR;if(dir){fs.mkdirSync(dir,{recursive:true});await page.locator('.toast').evaluateAll(els=>els.forEach(e=>e.style.visibility='hidden'));await page.screenshot({path:path.join(dir,name),animations:'disabled'})}};
 debugPage=page;await page.setContent(html,{waitUntil:'load'});
 await page.evaluate(()=>{
  const copy=value=>JSON.parse(JSON.stringify(value));window.fake={entries:[],active:'',serial:0,calls:[],failPut:false};
  window.DesktopHost={async call(action,p={}){
   fake.calls.push({action,p});const entry=fake.entries.find(e=>e.id===p.id);
   if(action==='autosave'){fake.recovery=p.json;return {status:'pending',path:qa.desktop.path}}
   if(action==='save'){if(fake.active){const active=fake.entries.find(e=>e.id===fake.active);active.project=JSON.parse(p.json);active.revision=String(+active.revision+1)}else fake.sourceJSON=p.json;return {status:'saved',path:qa.desktop.path}}
   if(action==='bookcaseList')return {activeId:fake.active,warnings:[],shelves:fake.entries.map(e=>({id:e.id,title:e.project.Title,books:e.project.Books.map(b=>({id:b.Id,title:b.Title,author:b.Author,cover:b.FrontCover?.DataUrl||'',spine:b.SpineCover?.DataUrl||''}))}))};
   if(action==='bookcaseRead'){if(!entry)throw Error('Missing shelf');return {id:entry.id,revision:entry.revision,project:copy(entry.project)}}
   if(action==='bookcasePut'){
    if(fake.failPut){fake.failPut=false;throw Error('This shelf changed while importing')}
    const project=JSON.parse(p.json);if(p.id){if(!entry||p.revision!==entry.revision)throw Error('Revision conflict');entry.project=copy(project);entry.revision=String(+entry.revision+1);return {id:entry.id,status:'updated'}}
    const duplicate=fake.entries.find(e=>JSON.stringify(e.project)===JSON.stringify(project));if(duplicate)return {id:duplicate.id,status:'duplicate'};
    const id='shelf-'+(++fake.serial);fake.entries.push({id,revision:'1',project:copy(project)});return {id,status:'imported'};
   }
   if(action==='bookcaseOpen'){if(!entry)throw Error('Missing shelf');fake.active=entry.id;return {status:'opened',path:'Bookcase · '+entry.project.Title,bookcaseId:entry.id,project:copy(entry.project)}}
   if(['speechStop','mediaSettings','mediaState'].includes(action))return null;throw Error('Unexpected native action: '+action);
  }};
  window.NWTTS={available:()=>true,voices:()=>[],stop(){},speak(){}};
  function cover(title,color){const canvas=document.createElement('canvas');canvas.width=400;canvas.height=600;const c=canvas.getContext('2d');c.fillStyle=color;c.fillRect(0,0,400,600);c.strokeStyle='#e4d8b3';c.lineWidth=2;c.strokeRect(25,25,350,550);c.fillStyle='#fff8e6';c.textAlign='center';c.font='30px Georgia';title.split('|').forEach((line,i)=>c.fillText(line,200,220+i*45));c.font='19px sans-serif';c.fillText('Example Author',200,505);return {DataUrl:canvas.toDataURL(),Width:400,Height:600}}
  window.makeSeries=(key,title,titles)=>qa.cleanProject({Id:key,Title:title,Books:titles.map((t,i)=>({Id:key+'-b'+i,Title:t,Author:'Example Author',FrontCover:i===2?null:cover(t.replace(' ','|'),['#234954','#74563e','#4c3f65'][i%3]),SpineCover:i===0?cover('SPINE','#8b5d28'):null,BackCover:i===0?cover('BACK|'+t,'#35483e'):null})),Chapters:titles.map((t,i)=>({Id:key+'-c'+i,BookId:key+'-b'+i,Title:'Chapter 1',Paragraphs:[{Id:key+'-p'+i,Text:'The first chapter of '+t+'. “Keep the dialogue voice,” she said.',ThreadIds:[]}]})),Threads:[],History:[],Reviews:[],SpeechProfiles:[]});
  qa.S.project=makeSeries('workspace','Current writing',['The Lighthouse']);qa.S.bookId='workspace-b0';qa.S.chapterId='workspace-c0';qa.S.view='read';qa.desktop.path='Original.novel.json';qa.desktop.starting=false;fake.originalJSON=JSON.stringify(qa.S.project);fake.sourceJSON=fake.originalJSON;qa.render();
 });
 assert.equal(await page.locator('[data-action="go-write"]').evaluate(el=>el.nextElementSibling.dataset.action),'bookcase-show');
 await page.locator('[data-action="bookcase-show"]').click();await page.waitForFunction(()=>qa.S.view==='bookcase'&&!qa.bookcase.loading);assert.equal(await page.locator('.bookcase-shelf').count(),1);
 const series=await page.evaluate(()=>[makeSeries('dawn','Voyages of Dawn',['First Light','Silent Orbit','Beyond Dawn']),makeSeries('river','River Chronicles',['The Crossing','Distant Shores'])]);
 const seriesFiles=series.map((p,i)=>({name:'series-'+i+'.novel.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(p))}));
 await page.locator('#bookcase-series-input').setInputFiles(seriesFiles);await page.waitForFunction(()=>fake.entries.length===2&&!qa.bookcase.busy);assert.equal(await page.locator('.bookcase-shelf').count(),3);assert.equal(await page.evaluate(()=>qa.S.project.Id),'workspace');
 await page.locator('#bookcase-series-input').setInputFiles(seriesFiles.slice(0,1));await page.waitForFunction(()=>!qa.bookcase.busy);assert.equal(await page.evaluate(()=>fake.entries.length),2);
 // Selecting a book updates the 3D volume and action card without opening a file.
 const sourceBeforeDetails=await page.evaluate(()=>JSON.stringify(qa.S.project));
 await page.locator('[data-action="bookcase-select"][data-shelf="shelf-1"]').first().click();
 assert.equal(await page.evaluate(()=>qa.S.view),'bookcase');assert.equal(await page.locator('.bookcase-selection h2').innerText(),'First Light');
 assert.equal(await page.locator('[data-action="bookcase-select"][data-shelf="shelf-1"]').first().getAttribute('aria-pressed'),'true');
 await page.locator('[data-action="bookcase-orientation"]').click();assert(await page.locator('.bookcase-library').evaluate(el=>el.classList.contains('show-spines')));
 assert.equal(await page.locator('[data-shelf="shelf-1"] .bookcase-spine img').count(),1);await capture('bookcase-spines.png');
 await page.locator('[data-action="bookcase-details"]').click();await page.locator('.bookcase-back-detail img').waitFor();
 assert.equal(await page.locator('.bookcase-back-detail img').getAttribute('src'),series[0].Books[0].BackCover.DataUrl);await capture('bookcase-back-cover.png');
 await page.locator('[data-action="bookcase-back-zoom"]').click();assert.equal(await page.locator('[data-action="bookcase-back-zoom"]').getAttribute('aria-pressed'),'true');
 await page.locator('.modal-footer [data-modal-result="cancel"]').click();
 await page.locator('[data-action="bookcase-select"][data-shelf="shelf-1"]').nth(1).click();await page.locator('[data-action="bookcase-details"]').click();await page.locator('.bookcase-no-back').waitFor();assert.match(await page.locator('.bookcase-no-back').innerText(),/No back cover/);await page.locator('.modal-footer [data-modal-result="cancel"]').click();
 assert.equal(await page.evaluate(()=>JSON.stringify(qa.S.project)),sourceBeforeDetails);
 await page.locator('[data-action="bookcase-orientation"]').click();
 // A late detail response must not reopen a dialog the user closed.
 await page.evaluate(()=>{const original=DesktopHost.call;fake.originalCall=original;DesktopHost.call=async(action,p)=>action==='bookcaseRead'?new Promise(resolve=>{fake.resolveDetails=()=>original(action,p).then(resolve)}):original(action,p)});
 await page.locator('[data-action="bookcase-details"]').click();await page.getByRole('dialog',{name:'Back cover',exact:true}).waitFor();await page.locator('.modal-footer [data-modal-result="cancel"]').click();await page.evaluate(async()=>{await fake.resolveDetails();DesktopHost.call=fake.originalCall});assert.equal(await page.locator('[role="dialog"]').count(),0);
 // Add/replace/remove a narrow spine using the actual Manage covers handler.
 await page.evaluate(()=>qa.bookCovers());await page.getByRole('dialog',{name:'Manage covers'}).waitFor();assert.equal(await page.locator('[data-cover-side="SpineCover"]').count(),1);
 const spinePng=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=50;c.height=600;const x=c.getContext('2d');x.fillStyle='#ae7739';x.fillRect(0,0,50,600);return c.toDataURL().split(',')[1]});
 await page.locator('#cover-SpineCover').setInputFiles({name:'spine.png',mimeType:'image/png',buffer:Buffer.from(spinePng,'base64')});await page.waitForFunction(()=>qa.S.project.Books[0].SpineCover?.Width===50);await page.locator('.spine-cover-card img').waitFor();await capture('bookcase-manage-covers.png');
 assert.equal(await page.evaluate(()=>qa.cleanProject(JSON.parse(JSON.stringify(qa.S.project))).Books[0].SpineCover.Height),600);
 await page.locator('[data-action="remove-cover"][data-side="SpineCover"]').click();assert.equal(await page.evaluate(()=>qa.S.project.Books[0].SpineCover),null);
 await page.locator('#cover-SpineCover').setInputFiles({name:'spine.png',mimeType:'image/png',buffer:Buffer.from(spinePng,'base64')});await page.waitForFunction(()=>qa.S.project.Books[0].SpineCover?.Width===50);await page.locator('.modal-footer [data-modal-result="cancel"]').click();
 // Update the reference snapshot after this intentional, saved cover edit.
 await page.evaluate(()=>{fake.originalJSON=JSON.stringify(qa.S.project);fake.sourceJSON=fake.originalJSON});
 const exported=await page.evaluate(async()=>{
  const book={Id:'epub-book',Title:'The Horizon',Author:'A. Example',FrontCover:qa.S.project.Books[0].FrontCover},chapters=[{Id:'epub-c',BookId:'epub-book',Title:'Chapter One',Paragraphs:[{Id:'epub-p',Text:'EPUB reading order & cover stay intact.',ThreadIds:[]}]}];
  const bytes=await qa.buildBookEpub(book,chapters).arrayBuffer(),archive=qa.bookcaseZip(bytes),entries=[];
  for(const name of ['mimetype','META-INF/container.xml','EPUB/package.opf',...Array.from(new DOMParser().parseFromString(await archive.text('EPUB/package.opf'),'application/xml').getElementsByTagName('item')).map(e=>'EPUB/'+e.getAttribute('href'))]){let value=await archive.read(name);if(name.endsWith('.xhtml')){const text=new TextDecoder().decode(value);if(new DOMParser().parseFromString(text,'application/xml').querySelector('parsererror'))throw Error('Invalid exported XHTML: '+name);value=text.replace('<meta charset="utf-8"/>','<meta charset="utf-8">')}entries.push([name,value])}
  const legacy=await qa.bookcaseReadFile(new File([qa.zipStore(entries)],'legacy.epub'));if(!legacy.Chapters.some(c=>c.Paragraphs.some(p=>p.Text==='EPUB reading order & cover stay intact.')))throw Error('Legacy EPUB text lost');
  return {epub:Array.from(new Uint8Array(bytes)),docx:Array.from(new Uint8Array(await qa.buildBookDocx(book,chapters).arrayBuffer()))};
 });
 await page.locator('[data-action="bookcase-import-books"]').click();await page.getByRole('dialog',{name:'Import books',exact:true}).waitFor();await page.locator('#bookcase-target').selectOption('shelf-1');
 const choose=page.waitForEvent('filechooser');await page.locator('[data-modal-result="choose"]').click();await (await choose).setFiles([
  {name:'horizon.epub',mimeType:'application/epub+zip',buffer:Buffer.from(exported.epub)},
  {name:'word-book.docx',mimeType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',buffer:Buffer.from(exported.docx)},
  {name:'text-book.txt',mimeType:'text/plain',buffer:Buffer.from('Chapter 1\n\nA plain-text book imported onto the same shelf.')},
  {name:'unsupported.pdf',mimeType:'application/pdf',buffer:Buffer.from('not supported')}
 ]);
 await page.waitForFunction(()=>!qa.bookcase.busy&&fake.entries[0].project.Books.length===6);await page.getByRole('dialog',{name:'Bookcase import results'}).waitFor();assert.match(await page.locator('[role=dialog]').innerText(),/unsupported.pdf/);await page.locator('.modal-footer [data-modal-result="cancel"]').click();
 const epub=await page.evaluate(()=>{const p=fake.entries[0].project,b=p.Books.find(b=>b.Title==='The Horizon');return {id:b.Id,cover:b.FrontCover,text:p.Chapters.find(c=>c.BookId===b.Id).Paragraphs[0].Text}});assert(epub.cover.DataUrl.startsWith('data:image/'));assert.equal(epub.text,'EPUB reading order & cover stay intact.');assert.equal(await page.evaluate(()=>fake.sourceJSON),await page.evaluate(()=>fake.originalJSON));
 await page.locator('[data-action="bookcase-select"][data-book="'+epub.id+'"]').click();await page.locator('[data-action="bookcase-open"][data-book="'+epub.id+'"]').click();await page.waitForFunction(()=>qa.S.view==='read'&&qa.S.project.Id==='dawn');assert.equal(await page.evaluate(()=>qa.S.bookId),epub.id);assert.match(await page.locator('#main-body').innerText(),/EPUB reading order/);
 await page.locator('[data-action="bookcase-show"]').click();await page.waitForFunction(()=>!qa.bookcase.loading);await page.locator('[data-action="bookcase-import-books"]').click();await page.locator('#bookcase-target').selectOption('shelf-1');const chooseActive=page.waitForEvent('filechooser');await page.locator('[data-modal-result="choose"]').click();await (await chooseActive).setFiles([{name:'active-addition.txt',mimeType:'text/plain',buffer:Buffer.from('An added book on the currently open shelf.')}]);await page.waitForFunction(()=>!qa.bookcase.busy&&qa.S.project.Books.length===7);assert.equal(await page.evaluate(()=>qa.S.view),'bookcase');
 // Failed replacement retains the selected project's writing.
 const before=await page.evaluate(()=>JSON.stringify(qa.S.project));await page.evaluate(()=>fake.failPut=true);await page.locator('[data-action="bookcase-import-books"]').click();await page.locator('#bookcase-target').selectOption('shelf-1');const failChooser=page.waitForEvent('filechooser');await page.locator('[data-modal-result="choose"]').click();await (await failChooser).setFiles([{name:'blocked.txt',mimeType:'text/plain',buffer:Buffer.from('Do not overwrite.')}]);await page.getByRole('dialog',{name:'Bookcase import failed'}).waitFor();assert.equal(await page.evaluate(()=>JSON.stringify(qa.S.project)),before);await page.locator('.modal-footer [data-modal-result="cancel"]').click();
 await page.locator('[data-action="bookcase-select"][data-shelf="shelf-2"]').first().click();
 const screenshots=process.env.NOVELWEAVER_SCREENSHOT_DIR;if(screenshots){fs.mkdirSync(screenshots,{recursive:true});await page.locator('.toast').evaluateAll(els=>els.forEach(e=>e.style.visibility='hidden'));await page.screenshot({path:path.join(screenshots,'bookcase-mobile.png'),animations:'disabled'});await page.setViewportSize({width:1100,height:900});await page.screenshot({path:path.join(screenshots,'bookcase-desktop.png'),animations:'disabled'});await page.setViewportSize({width:430,height:1000})}
 // The current imported project is still saved before leaving; cancel an
 // unlinked draft's leave prompt rather than silently replacing it.
 await page.evaluate(()=>{qa.desktop.path=null;qa.S.project.Title='Unsaved draft';fake.active='';qa.bookcase.activeId=''});await page.locator('[data-action="bookcase-select"][data-shelf="shelf-2"]').first().click();await page.locator('[data-action="bookcase-open"][data-shelf="shelf-2"]').click();await page.getByRole('dialog',{name:'Save this project?'}).waitFor();await page.getByRole('button',{name:'Cancel',exact:true}).click();await page.waitForFunction(()=>!qa.bookcase.busy);assert.equal(await page.evaluate(()=>qa.S.project.Title),'Unsaved draft');
 await page.evaluate(()=>{qa.S.project.Books=[];qa.S.project.Chapters=[];qa.S.view='read';qa.render()});assert.equal(await page.locator('[data-action="bookcase-show"]').count(),1);
 assert.deepEqual(errors,[]);assert.deepEqual(requests,[]);await browser.close();console.log('PASS: 3D selection, spine browsing, full back cover Details/zoom, missing back covers, spine image upload/removal/persistence, mobile Bookcase placement, cover shelves, multiple series, duplicate handling, EPUB/DOCX/TXT import, partial failures, active shelf synchronization, workspace cancellation, empty Read view and no external requests.');
})().catch(async e=>{console.error(e);if(debugPage){console.error(await debugPage.evaluate(()=>({body:document.getElementById('main-body')?.innerText,modal:document.querySelector('[role=dialog]')?.innerText,entries:fake?.entries.map(e=>({id:e.id,title:e.project.Title,books:e.project.Books.map(b=>b.Title)})),calls:fake?.calls.map(c=>c.action)})).catch(()=>null));}process.exit(1)});
