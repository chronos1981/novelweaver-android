// Import plain manuscript data only. EPUB scripts, styles and external URLs are
// never inserted into the editor; every text and cover is rendered by our UI.
function bookcaseProject(title){return {Id:id(),Title:title||'Imported books',Books:[],Chapters:[],Threads:[],Reviews:[],History:[],SpeechProfiles:[],SpeechDefaults:{Narration:{},Dialogue:{}}}}
function bookcaseValidateProject(raw){
 const project=cleanProject(raw);
 if(!project.Books.length)throw Error('This file contains no books.');
 // Use the existing identity/reference validation without changing the source.
 mergeImportedBooks(bookcaseProject('Validation'),project);
 return project;
}
function bookcaseArchivePath(base,relative){
 if(typeof relative!=='string'||!relative||/^[a-z][a-z0-9+.-]*:|^\/|^\\/i.test(relative)||relative.includes('\\'))throw Error('Unsupported EPUB resource path.');
 const raw=relative.split('#')[0].split('?')[0];let decoded;try{decoded=decodeURIComponent(raw)}catch{throw Error('Invalid EPUB resource path.')}
 if(decoded.includes('\0')||decoded.startsWith('/')||decoded.includes('\\'))throw Error('Invalid EPUB resource path.');
 const parts=base?base.split('/').slice(0,-1):[];
 for(const part of decoded.split('/')){if(!part||part==='.')continue;if(part==='..'){if(!parts.length)throw Error('EPUB resource escapes its archive.');parts.pop()}else parts.push(part)}
 return parts.join('/');
}
const bookcaseCrcTable=Uint32Array.from({length:256},(_,n)=>{let c=n;for(let i=0;i<8;i++)c=c&1?0xedb88320^(c>>>1):c>>>1;return c>>>0});
function bookcaseCrc(bytes){let c=0xffffffff;for(const byte of bytes)c=bookcaseCrcTable[(c^byte)&255]^(c>>>8);return (c^0xffffffff)>>>0}
function bookcaseZip(buffer){
 const bytes=new Uint8Array(buffer),v=new DataView(buffer),decoder=new TextDecoder();let end=-1;
 for(let i=bytes.length-22;i>=Math.max(0,bytes.length-65557);i--)if(v.getUint32(i,true)===0x06054b50&&i+22+v.getUint16(i+20,true)===bytes.length){end=i;break}
 if(end<0)throw Error('This is not a standard EPUB ZIP archive.');
 if(v.getUint16(end+4,true)||v.getUint16(end+6,true))throw Error('Split EPUB archives are not supported.');
 const count=v.getUint16(end+10,true),centralSize=v.getUint32(end+12,true),central=v.getUint32(end+16,true);
 if(count>5000||central+centralSize>end)throw Error('EPUB archive is too large or invalid.');
 const entries=new Map(),cache=new Map();let p=central,total=0;
 for(let i=0;i<count;i++){
  if(p+46>end||v.getUint32(p,true)!==0x02014b50)throw Error('Invalid EPUB directory.');
  const crc=v.getUint32(p+16,true),flags=v.getUint16(p+8,true),method=v.getUint16(p+10,true),size=v.getUint32(p+20,true),length=v.getUint32(p+24,true),n=v.getUint16(p+28,true),extra=v.getUint16(p+30,true),comment=v.getUint16(p+32,true),offset=v.getUint32(p+42,true);
  if(p+46+n+extra+comment>end||size===0xffffffff||length===0xffffffff||offset===0xffffffff)throw Error('Invalid or unsupported EPUB archive.');
  const name=decoder.decode(bytes.subarray(p+46,p+46+n));p+=46+n+extra+comment;if(name.endsWith('/'))continue;
  if(name.startsWith('/')||name.split('/').includes('..')||name.includes('\\')||entries.has(name))throw Error('Invalid or duplicate EPUB entry.');
  entries.set(name,{crc,flags,method,size,length,offset});
 }
 return {has:name=>entries.has(name),async read(name){
  if(cache.has(name))return cache.get(name);const e=entries.get(name);if(!e)throw Error('EPUB resource missing: '+name);
  if(e.flags&1)throw Error('Encrypted EPUB resources are not supported.');
  if(e.length>20_000_000||total+e.length>60_000_000)throw Error('EPUB decompressed content exceeds the import limit.');
  if(e.offset+30>central||v.getUint32(e.offset,true)!==0x04034b50)throw Error('Invalid EPUB entry.');
  const start=e.offset+30+v.getUint16(e.offset+26,true)+v.getUint16(e.offset+28,true);
  if(start+e.size>central)throw Error('Truncated EPUB resource.');
  let output;
  if(e.method===0)output=bytes.slice(start,start+e.size);
  else if(e.method===8){
   if(typeof DecompressionStream==='undefined')throw Error('Update Android System WebView to import compressed EPUB files.');
   const reader=new Blob([bytes.subarray(start,start+e.size)]).stream().pipeThrough(new DecompressionStream('deflate-raw')).getReader(),chunks=[];let used=0;
   try{while(true){const {done,value}=await reader.read();if(done)break;used+=value.length;if(used>20_000_000||total+used>60_000_000)throw Error('EPUB decompressed content exceeds the import limit.');chunks.push(value)}}catch(error){await reader.cancel().catch(()=>{});throw error}
   output=new Uint8Array(used);let at=0;for(const chunk of chunks){output.set(chunk,at);at+=chunk.length}
  }else throw Error('Unsupported EPUB compression method.');
  if(output.length!==e.length)throw Error('EPUB resource size does not match its directory.');
  if(bookcaseCrc(output)!==e.crc)throw Error('An EPUB resource failed its integrity check.');
  total+=output.length;cache.set(name,output);return output;
 },async text(name){return decoder.decode(await this.read(name))}};
}
function bookcaseXml(text,label){const doc=new DOMParser().parseFromString(text,'application/xml');if(doc.querySelector('parsererror'))throw Error('Could not read '+label+'.');return doc}
function bookcaseElements(doc,name){return [...doc.getElementsByTagNameNS('*',name)]}
function bookcaseMarkup(markup){const template=document.createElement('template');template.innerHTML=markup;return {root:template.content,body:template.content.querySelector('body')||template.content,title:template.content.querySelector('title')?.textContent.trim()||''}}
async function bookcaseReadEpub(file){
 const archive=bookcaseZip(await file.arrayBuffer());
 if(archive.has('META-INF/rights.xml')||archive.has('META-INF/license.lcpl'))throw Error('DRM-protected EPUB books cannot be imported.');
 if(archive.has('META-INF/encryption.xml')){const encryption=bookcaseXml(await archive.text('META-INF/encryption.xml'),'EPUB encryption information');if(bookcaseElements(encryption,'EncryptionMethod').some(e=>!['http://www.idpf.org/2008/embedding','http://ns.adobe.com/pdf/enc#RC'].includes(e.getAttribute('Algorithm'))))throw Error('Encrypted EPUB books cannot be imported.')}
 const container=bookcaseXml(await archive.text('META-INF/container.xml'),'EPUB container');
 const rootfile=bookcaseElements(container,'rootfile').find(n=>n.getAttribute('media-type')==='application/oebps-package+xml')||bookcaseElements(container,'rootfile')[0];
 if(!rootfile)throw Error('The EPUB has no package document.');
 const packagePath=bookcaseArchivePath('',rootfile.getAttribute('full-path')),opf=bookcaseXml(await archive.text(packagePath),'EPUB package');
 const manifest=new Map(bookcaseElements(opf,'item').map(e=>[e.getAttribute('id'),{href:e.getAttribute('href'),type:e.getAttribute('media-type')||'',properties:(e.getAttribute('properties')||'').split(/\s+/)}]));
 const title=bookcaseElements(opf,'title')[0]?.textContent.trim()||file.name.replace(/\.epub$/i,''),author=bookcaseElements(opf,'creator').map(e=>e.textContent.trim()).filter(Boolean).join(', ');
 const project=bookcaseProject(title),book=mkBook(title);book.Author=author;project.Books.push(book);
 const coverId=bookcaseElements(opf,'meta').find(e=>e.getAttribute('name')==='cover')?.getAttribute('content');
 let cover=[...manifest.values()].find(i=>i.properties.includes('cover-image'))||manifest.get(coverId);
 if(!cover){try{const guide=bookcaseElements(opf,'reference').find(e=>(e.getAttribute('type')||'').split(/\s+/).includes('cover'));if(guide){const coverPath=bookcaseArchivePath(packagePath,guide.getAttribute('href'));const doc=bookcaseMarkup(await archive.text(coverPath)).root,image=doc.querySelector('img[src], image');const href=image?.getAttribute('src')||image?.getAttribute('href')||image?.getAttribute('xlink:href');if(href){const path=bookcaseArchivePath(coverPath,href);cover={path,type:/\.png$/i.test(path)?'image/png':/\.webp$/i.test(path)?'image/webp':'image/jpeg'}}}}catch(error){console.warn('EPUB cover unavailable:',error.message)}}
 if(cover){try{const imagePath=cover.path||bookcaseArchivePath(packagePath,cover.href);if(['image/jpeg','image/png','image/webp'].includes(cover.type))book.FrontCover=await coverFromFile(new Blob([await archive.read(imagePath)],{type:cover.type}))}catch(error){console.warn('EPUB cover unavailable:',error.message)}}
 const spine=bookcaseElements(opf,'itemref').filter(e=>e.getAttribute('linear')!=='no');
 for(const ref of spine){
  const item=manifest.get(ref.getAttribute('idref'));if(!item)throw Error('The EPUB reading order refers to a missing document.');
  if(item.properties.includes('nav'))continue;if(!/^(application\/xhtml\+xml|text\/html)$/.test(item.type))continue;
  const path=bookcaseArchivePath(packagePath,item.href),markup=await archive.text(path),parsed=bookcaseMarkup(markup),doc=parsed.root,body=parsed.body;
  doc.querySelectorAll('title,head,meta,link,script,style,noscript,nav,svg').forEach(e=>e.remove());doc.querySelectorAll('br').forEach(e=>e.replaceWith(body.ownerDocument.createTextNode('\n')));
  if(body.querySelector('[epub\\:type="titlepage"], [epub\\:type="cover"]'))continue;
  const heading=body.querySelector('h1,h2'),chapterTitle=heading?.textContent.trim()||parsed.title||'Chapter '+(project.Chapters.length+1);
  const selector='p,h1,h2,h3,h4,h5,h6,li,blockquote,pre';let paragraphs=[...body.querySelectorAll(selector)].filter(e=>e!==heading&&!e.querySelector(selector)).map(e=>e.textContent.trim()).filter(Boolean);
  if(!paragraphs.length){const text=body.textContent.trim();if(text&&text!==heading?.textContent.trim())paragraphs=[text]}
  if(!paragraphs.length)continue;const chapter=mkChapter(book.Id,chapterTitle);chapter.Paragraphs=paragraphs.map(text=>mkPara(text));project.Chapters.push(chapter);
 }
 if(!project.Chapters.length)throw Error('The EPUB contains no readable text chapters.');
 return bookcaseValidateProject(project);
}
async function bookcaseReadFile(file,seriesOnly=false){
 if(file.size>60_000_000)throw Error('File exceeds the 60 MB import limit.');
 if(/\.json$/i.test(file.name))return bookcaseValidateProject(JSON.parse(await file.text()));
 if(seriesOnly)throw Error('Choose .novel.json series files.');
 if(/\.epub$/i.test(file.name))return bookcaseReadEpub(file);
 if(!/\.(txt|docx)$/i.test(file.name))throw Error('Choose EPUB, DOCX, TXT, or .novel.json files.');
 if(file.size>25_000_000)throw Error('Text/Word files must be smaller than 25 MB.');
 const sections=/\.docx$/i.test(file.name)?parseDocx(await unzipEntry(await file.arrayBuffer(),'word/document.xml')):parseTxt(await file.text());
 if(!sections.length)throw Error('No readable paragraphs were found.');
 const title=file.name.replace(/\.(txt|docx)$/i,''),project=bookcaseProject(title),book=mkBook(title);project.Books.push(book);
 for(const section of sections){const chapter=mkChapter(book.Id,section.title);chapter.Paragraphs=section.paragraphs.map(text=>mkPara(text));project.Chapters.push(chapter)}
 return bookcaseValidateProject(project);
}
