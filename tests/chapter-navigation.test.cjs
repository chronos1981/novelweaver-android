const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/www/index.html'),'utf8');
const project={Chapters:[
 {Id:'front',BookId:'a',Title:'Front Matter',Paragraphs:[{Text:'Before'}]},
 {Id:'other',BookId:'b',Title:'Other book',Paragraphs:[{Text:'Untouched'}]},
 {Id:'one',BookId:'a',Title:'Chapter 1',Paragraphs:[{Text:'Chapter one'}]},
 {Id:'last',BookId:'a',Title:'Final chapter',Paragraphs:[{Text:'The end'}]}
],History:[]};
let pending=null,flushes=0,stops=0,renders=0,snapshots=[],focuses=0,contexts=[];
const heading={setAttribute(){},focus(){focuses++}},S={project,chapterId:'front',bookId:'a',paraId:'p',readChapterId:'old',view:'write',editSession:new Map([['p',{}]]),dirty:false};
const context=vm.createContext({S,h:String,quill:{open:true},quillUpdateContext(){contexts.push(S.chapterId)},activeChapter:()=>project.Chapters.find(c=>c.Id===S.chapterId),
 flushAllEdits(){flushes++;if(pending!==null){project.Chapters.find(c=>c.Id===S.chapterId).Paragraphs[0].Text=pending;pending=null;S.dirty=true;project.History.push({After:project.Chapters.find(c=>c.Id===S.chapterId).Paragraphs[0].Text})}},
 listenStop(){stops++},closeSidebar(){},render(){renders++},$:selector=>selector==='#main-body h1'?heading:{scrollTo(){}},window:{scrollTo(){}},
 saveLocal(){snapshots.push(JSON.parse(JSON.stringify(project)));S.dirty=false}
});
vm.runInContext(html.slice(html.indexOf('function writeChapterTargets('),html.indexOf('function renderWrite('))+'\nglobalThis.api={writeChapterTargets,writeChapterNavigation,moveWriteChapter};',context);
const {writeChapterTargets,writeChapterNavigation,moveWriteChapter}=context.api;
assert.equal(writeChapterTargets().previous,null);assert.equal(writeChapterTargets().next.Id,'one');
assert.match(writeChapterNavigation(project.Chapters[0]),/data-action="previous-chapter" disabled/);
pending='Latest unsaved typing';assert.equal(moveWriteChapter(1),true);
assert.equal(S.chapterId,'one','Skip chapters belonging to other books');assert.equal(S.bookId,'a');assert.equal(S.paraId,null);assert.equal(S.readChapterId,null);assert.equal(S.editSession.size,0);
assert.equal(project.Chapters[0].Paragraphs[0].Text,'Latest unsaved typing','Flush edits in the original chapter before changing selection');
assert.equal(snapshots.length,1);assert.equal(snapshots[0].Chapters[0].Paragraphs[0].Text,'Latest unsaved typing');assert.equal(project.History.length,1);
assert.equal(moveWriteChapter(1),true);assert.equal(S.chapterId,'last');
assert.equal(writeChapterTargets().next,null);assert.match(writeChapterNavigation(project.Chapters[3]),/data-action="next-chapter" disabled/);
assert.equal(moveWriteChapter(1),false);assert.equal(S.chapterId,'last');assert.equal(moveWriteChapter(-1),true);assert.equal(S.chapterId,'one');assert.equal(moveWriteChapter(-1),true);assert.equal(S.chapterId,'front');assert.equal(moveWriteChapter(-1),false);
assert.equal(project.History.length,1,'Navigation itself must not count as an edit');assert.equal(snapshots.length,1);assert.equal(renders,4);assert.equal(stops,4);assert.equal(focuses,4);
assert.deepEqual(contexts,['one','last','one','front'],'An open Quill context preview must follow the selected chapter');
S.chapterId='other';S.bookId='b';assert.equal(writeChapterTargets().previous,null);assert.equal(writeChapterTargets().next,null);assert.equal(moveWriteChapter(1),false);
const count=flushes;assert.equal(moveWriteChapter(2),false);assert.equal(flushes,count);
assert.match(html,/case 'previous-chapter':moveWriteChapter\(-1\)/);assert.match(html,/case 'next-chapter':moveWriteChapter\(1\)/);
console.log('PASS: ordered same-book navigation, disabled boundaries, one-chapter books, pending edit preservation, save snapshots, stopped narration, focus and unchanged edit counts.');
