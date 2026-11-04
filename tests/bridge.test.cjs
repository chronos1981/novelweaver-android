const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const sent=[],events=[];
const context=vm.createContext({console,Map,Promise,Error,JSON,CustomEvent:class {constructor(type,options){this.type=type;this.detail=options.detail}},Event:class {constructor(type){this.type=type}},
 document:{createElement:()=>({style:{},setAttribute(){},remove(){}}),body:{append(){}}},
 AndroidBridge:{postMessage(value){sent.push(JSON.parse(value))}}});
context.window=context;context.dispatchEvent=e=>events.push(e);
vm.runInContext(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/www/desktop-host.js'),'utf8'),context);
(async()=>{
 let task=context.DesktopHost.call('key',{key:'dummy',remember:true});const request=sent.at(-1);
 context.AndroidHost.receive({id:request.id,ok:true,result:{saved:true}});assert.equal((await task).saved,true);
 task=context.DesktopHost.call('open');const open=sent.at(-1);context.AndroidHost.receive({id:open.id,ok:false,error:'Not a project'});await assert.rejects(()=>task,/Not a project/);
 const controller=new AbortController();task=context.DesktopHost.call('http',{url:'https://private/api/models'},controller.signal);const http=sent.at(-1);controller.abort();await assert.rejects(()=>task,/Request stopped/);assert.equal(sent.at(-1).action,'cancel');assert.equal(sent.at(-1).payload.requestId,http.id);
 context.AndroidHost.receive({id:http.id,ok:true,result:'late response'});
 context.AndroidHost.receive({type:'fileStatus',result:{status:'conflict',path:'book.novel.json'}});assert.equal(events.at(-1).type,'nw-file-status');assert.equal(events.at(-1).detail.status,'conflict');
 context.AndroidHost.receive({type:'voices',voices:[{id:'voice'}]});assert.equal(context.AndroidVoices.length,1);assert.equal(events.at(-1).type,'nw-voices');
 const android=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/novelweaver/android/MainActivity.java'),'utf8');
 assert.match(android,/if\(!mainFrame\|\|!ORIGIN\.equals\(origin\.toString\(\)\)\)return/);
 assert.doesNotMatch(android,/addJavascriptInterface\s*\(/);
 assert.match(android,/handler\.cancel\(\)/);
 assert.match(android,/takePersistableUriPermission/);
 const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/www/index.html'),'utf8');
 assert.match(html,/offset\+=65536/);assert.match(html,/exportFinish/);assert.match(html,/Android Keystore/);assert.match(html,/connect-src 'none'/);
 console.log('PASS: Android RPC responses, native errors, Stop cancellation, late replies, asynchronous file/voice events, origin-bound bridge, TLS rejection and chunked exports.');
})().catch(e=>{console.error(e);process.exitCode=1});
