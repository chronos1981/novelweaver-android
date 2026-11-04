const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.join(__dirname,'..'),html=fs.readFileSync(path.join(root,'app/src/main/assets/www/index.html'),'utf8');
const source=html.slice(html.indexOf('async function androidChooseStartup('),html.indexOf('async function desktopInitialize()'));
async function run(choices,info,handlers={}){
 const calls=[],dialogs=[],desktop={starting:true},S={project:{Title:'Cached manuscript'},dirty:false};
 const c=vm.createContext({desktop,S,Date,Error,JSON,h:String,defaultProject:()=>({Title:'Blank',Chapters:[]}),
  modal(title,body,buttons,done){dialogs.push({title,body,buttons});if(!choices.length)throw Error('Unexpected prompt');done(choices.shift())},
  DesktopHost:{async call(action,payload){calls.push(action);if(handlers[action])return handlers[action](payload);if(action==='new')return {status:'recovery',path:null};throw Error('Unexpected native action '+action)}},
  desktopHandleResult(r){desktop.path=r.path},desktopAdopt(p){S.project=p},closeModal(){}
 });
 vm.runInContext(source+'globalThis.start=androidChooseStartup;',c);await c.start(info);return {calls,dialogs,desktop,S};
}
(async()=>{
 const info={hasLast:true,lastPath:'My Book.novel.json',recoveries:[{id:'copy',title:'Unsaved writing',savedAt:1}]};
 let r=await run(['new'],info);assert.deepEqual(r.calls,['new']);assert.equal(r.S.project.Title,'Blank');assert.equal(r.desktop.path,null);assert.equal(r.desktop.starting,false);
 r=await run(['last'],info,{openLast:()=>({status:'opened',path:'My Book.novel.json',project:{Title:'Fresh file contents'}})});
 assert.deepEqual(r.calls,['openLast']);assert.equal(r.S.project.Title,'Fresh file contents');assert.equal(r.S.dirty,false);
 r=await run(['last','new'],info,{openLast:()=>{throw Error('File is unavailable')}});
 assert.equal(r.S.project.Title,'Blank');assert.match(r.dialogs[1].body,/File is unavailable/);assert.deepEqual(r.calls,['openLast','new']);
 r=await run(['open','last'],info,{open:()=>({status:'cancelled'}),openLast:()=>({status:'opened',project:{Title:'Disk'},path:'Book'})});assert.equal(r.S.project.Title,'Disk');
 r=await run(['recover','copy'],info,{openRecovery:()=>({status:'recovery',path:null,project:{Title:'Recovered only by choice'}})});
 assert.deepEqual(r.calls,['openRecovery']);assert.equal(r.desktop.path,null);assert.equal(r.S.project.Title,'Recovered only by choice');
 r=await run(['new'],{hasLast:false,recoveries:[]});assert.equal(r.dialogs[0].buttons.some(b=>b.value==='last'),false);
 assert.doesNotMatch(html,/dbGet\('project'\)|localStorage\.getItem\('novelweaver\.project'\)/);
 const native=fs.readFileSync(path.join(root,'app/src/main/java/com/novelweaver/android/MainActivity.java'),'utf8');
 const initialize=native.slice(native.indexOf('case "initialize":'),native.indexOf('case "autosave":'));
 assert.match(initialize,/session\.startupInfo\(\)/);assert.doesNotMatch(initialize,/session\.restore\(|queueAuto\(/);
 console.log('PASS: blank/file startup choices, fresh-file adoption, missing-file retry without cached fallback, cancelled picker, explicit unlinked recovery, no startup autosave or IndexedDB manuscript restore.');
})().catch(e=>{console.error(e);process.exitCode=1});
