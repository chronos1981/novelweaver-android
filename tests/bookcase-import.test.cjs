const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict'),zlib=require('node:zlib');
const root=path.join(__dirname,'..'),html=fs.readFileSync(path.join(root,'app/src/main/assets/www/index.html'),'utf8');
const c=vm.createContext({console,Blob,Response,DecompressionStream,TextEncoder,TextDecoder,Uint8Array,Uint32Array,DataView,Map,Set,Error,JSON});
vm.runInContext(html.slice(html.indexOf('function zipStore('),html.indexOf('// Book artwork'))+fs.readFileSync(path.join(root,'tools/bookcase-import.js'),'utf8')+'\nglobalThis.api={bookcaseZip,bookcaseArchivePath,bookcaseCrc,zipStore};',c);
function zip(entries){
 const parts=[],directory=[];let offset=0;
 for(const entry of entries){const name=Buffer.from(entry.name),data=Buffer.from(entry.text||''),payload=entry.method===8?zlib.deflateRawSync(data):data,crc=c.api.bookcaseCrc(data),head=Buffer.alloc(30),central=Buffer.alloc(46);
  head.writeUInt32LE(0x04034b50);head.writeUInt16LE(entry.flags||0,6);head.writeUInt16LE(entry.method||0,8);head.writeUInt32LE(crc,14);head.writeUInt32LE(payload.length,18);head.writeUInt32LE(entry.claimed??data.length,22);head.writeUInt16LE(name.length,26);
  central.writeUInt32LE(0x02014b50);central.writeUInt16LE(entry.flags||0,8);central.writeUInt16LE(entry.method||0,10);central.writeUInt32LE(crc,16);central.writeUInt32LE(payload.length,20);central.writeUInt32LE(entry.claimed??data.length,24);central.writeUInt16LE(name.length,28);central.writeUInt32LE(offset,42);
  parts.push(head,name,payload);directory.push(central,name);offset+=head.length+name.length+payload.length;
 }
 const size=directory.reduce((s,p)=>s+p.length,0),end=Buffer.alloc(22);end.writeUInt32LE(0x06054b50);end.writeUInt16LE(entries.length,8);end.writeUInt16LE(entries.length,10);end.writeUInt32LE(size,12);end.writeUInt32LE(offset,16);
 return Buffer.concat([...parts,...directory,end]);
}
function array(bytes){return bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength)}
(async()=>{
 assert.equal(c.api.bookcaseArchivePath('EPUB/package.opf','text/chapter.xhtml#one'),'EPUB/text/chapter.xhtml');
 assert.equal(c.api.bookcaseArchivePath('EPUB/package.opf','../images/cover%20one.jpg'),'images/cover one.jpg');
 for(const href of ['https://example.com/book','file:///etc/passwd','../../escape','/%2e%2e','..\\escape','%00bad','%2fetc/passwd'])assert.throws(()=>c.api.bookcaseArchivePath('EPUB/package.opf',href));
 const stored=await c.api.zipStore([['mimetype','application/epub+zip'],['EPUB/chapter.xhtml','<p>Test &amp; text.</p>']]).arrayBuffer(),archive=c.api.bookcaseZip(stored);
 assert.equal(await archive.text('mimetype'),'application/epub+zip');assert.equal(await archive.text('EPUB/chapter.xhtml'),'<p>Test &amp; text.</p>');assert.strictEqual(await archive.read('mimetype'),await archive.read('mimetype'));await assert.rejects(archive.read('missing'),/missing/);
 const compressed=c.api.bookcaseZip(array(zip([{name:'text',text:'Compressed text.'.repeat(500),method:8}])));assert.equal((await compressed.text('text')).length,8000);
 const damaged=Buffer.from(stored);damaged[30+'mimetype'.length]^=1;await assert.rejects(c.api.bookcaseZip(array(damaged)).read('mimetype'),/integrity/);
 assert.throws(()=>c.api.bookcaseZip(array(zip([{name:'same',text:'one'},{name:'same',text:'two'}]))),/duplicate/);
 assert.throws(()=>c.api.bookcaseZip(array(zip([{name:'../escape',text:'no'}]))),/Invalid/);
 await assert.rejects(c.api.bookcaseZip(array(zip([{name:'encrypted',text:'x',flags:1}]))).read('encrypted'),/Encrypted/);
 await assert.rejects(c.api.bookcaseZip(array(zip([{name:'wrong-size',text:'longer',claimed:1}]))).read('wrong-size'),/size/);
 await assert.rejects(c.api.bookcaseZip(array(zip([{name:'bomb',text:'x'.repeat(20_000_001),method:8,claimed:1}]))).read('bomb'),/limit/);
 assert.throws(()=>c.api.bookcaseZip(new ArrayBuffer(2)),/not a standard/);
 for(const name of ['bookcase-import.js','bookcase.js','bookcase-inspect.js'])assert(html.includes(fs.readFileSync(path.join(root,'tools',name),'utf8')),name+' must match the bundled UI');
 assert.match(html,/go-write[^>]*>✎ Edit chapter<\/button><button[^>]*bookcase-show/);
 assert.match(html,/bookcase-series-input[^>]*multiple/);assert.match(html,/bookcase-books-input[^>]*multiple/);
 console.log('PASS: stored/deflated EPUB archives, CRC and size checks, archive limits, encrypted resources, traversal/remote URL rejection, packaged modules, Read button placement and multi-file inputs.');
})().catch(e=>{console.error(e);process.exit(1)});
