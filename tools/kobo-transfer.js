let koboPreparing=false;
function koboTransferDialog(){
 flushAllEdits();
 if(koboPreparing){toast('Preparing Kobo ebooks…');return}
 const books=S.project.Books.slice();
 if(!books.length){toast('Add a book first');return}
 const current=activeBook();
 modal('Send to Kobo',`<p>Choose books to share over your local Wi-Fi. The Kobo downloads the EPUB files using its built-in browser.</p><div style="display:grid;gap:10px;max-height:40vh;overflow:auto">${books.map((b,i)=>`<label style="display:flex;align-items:center;gap:10px"><input type="checkbox" data-kobo-index="${i}" ${b.Id===current?.Id?'checked':''}> <span>${h(b.Title)}</span></label>`).join('')}</div><p><label><input type="checkbox" id="kobo-select-all"> Select all books</label></p><p class="item-sub">Standard EPUB · 32 books maximum · 128 MB total · no internet required.</p>`,[{text:'Cancel',value:'cancel'},{text:'Start transfer',value:'start',primary:true}],v=>{
  if(v!=='start')return;
  const selected=Array.from(document.querySelectorAll('#modal-host [data-kobo-index]:checked'),el=>books[Number(el.dataset.koboIndex)]);
  if(!selected.length){toast('Select at least one book');return}
  if(selected.length>32){toast('Choose up to 32 books');return}
  prepareKoboTransfer(selected);
 });
 const all=document.getElementById('kobo-select-all');
 if(all)all.addEventListener('change',()=>document.querySelectorAll('#modal-host [data-kobo-index]').forEach(el=>{el.checked=all.checked}));
}
async function prepareKoboTransfer(books){
 if(koboPreparing)return;
 koboPreparing=true;
 const tokens=[];let total=0;
 try{
  flushAllEdits();
  const snapshots=books.map(b=>({name:safeName(b.Title)+'.epub',blob:buildBookEpub(b,S.project.Chapters.filter(c=>c.BookId===b.Id))}));
  const names=new Set();
  for(const item of snapshots){
   const original=item.name;let suffix=2;
   while(names.has(item.name.toLowerCase()))item.name=original.slice(0,-5)+'-'+suffix+++'.epub';
   names.add(item.name.toLowerCase());
   total+=item.blob.size;
   if(item.blob.size>64000000||total>128000000)throw Error('Selected ebooks exceed the 64 MB per book or 128 MB total limit.');
  }
  for(let index=0;index<snapshots.length;index++){
   const item=snapshots[index];toast('Preparing Kobo ebook '+(index+1)+' of '+snapshots.length+'…');
   const token=(await DesktopHost.call('exportStart',{name:item.name,mime:'application/epub+zip'})).token;tokens.push(token);
   for(let offset=0;offset<item.blob.size;offset+=65536){
    const bytes=new Uint8Array(await item.blob.slice(offset,offset+65536).arrayBuffer());let binary='';
    for(let i=0;i<bytes.length;i++)binary+=String.fromCharCode(bytes[i]);
    await DesktopHost.call('exportChunk',{token,data:btoa(binary)});
   }
  }
  await DesktopHost.call('koboStart',{tokens});
 }catch(error){
  for(const token of tokens)await DesktopHost.call('exportCancel',{token}).catch(()=>{});
  modal('Kobo transfer failed',`<p>${h(error.message||String(error))}</p>`);
 }finally{koboPreparing=false}
}
