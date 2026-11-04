const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.join(__dirname,'..'),html=fs.readFileSync(path.join(root,'app/src/main/assets/www/index.html'),'utf8');
const project={Books:[],Chapters:[{Id:'c',Paragraphs:[{Id:'p',Text:'He said, “Hello.” Then he left.',ThreadIds:[],Speech:{Role:'dialogue',Speaker:'Niels',ProfileId:'male'}},{Id:'q',Text:'Narration.',ThreadIds:[]}]}],Threads:[],History:[],SpeechDefaults:{Narration:{Voice:'narrator',Rate:.9,Pitch:1.1},Dialogue:{ProfileId:'female'}},SpeechProfiles:[{Id:'male',Name:'Niels',Gender:'male',Voice:'character',Rate:1.2,Pitch:.8},{Id:'female',Name:'Torill',Gender:'female',Voice:'female-voice',Rate:1.1,Pitch:1.05}]};
let spoken=[],notices=[],advances=0,resumes=0;const l={voice:'global',rate:1.3,queue:[{id:'p',text:project.Chapters[0].Paragraphs[0].Text},{id:'q',text:'Narration.'}],index:0,token:0};
const c=vm.createContext({document:{addEventListener(){}},S:{project,listen:l},window:{NWTTS:{available:()=>true,voices:()=>[{id:'narrator'},{id:'character'},{id:'female-voice'},{id:'global'}],speak:(text,options)=>spoken.push({text,options}),resume:()=>resumes++,pause(){},stop(){}}},h:String,short:String,id:()=> 'id',now:()=> 'date',cleanCover:()=>null,flushAllEdits(){},listenStop(){l.token++;l.playing=l.paused=false},listenBookmark(){},listenAdvance(){advances++},syncListenUI(){},listenSpokenText:x=>x,toast:x=>notices.push(x)});
vm.runInContext(html.slice(html.indexOf('function cleanSpeechSettings('),html.indexOf('const bookById='))+fs.readFileSync(path.join(root,'tools/speech-segments.js'),'utf8')+html.slice(html.indexOf('function listenPause('),html.indexOf('function listenPronunciations('))+'globalThis.api={cleanProject,cleanSpeechTag,cleanSpeechSettings,speechResolve,speechPlaybackOptions,speechImportedTag,speechSegments,listenPlay,listenPause};',c);
const api=c.api,copy=api.cleanProject(JSON.parse(JSON.stringify(project)));
assert.equal(copy.SpeechProfiles[0].Gender,'male');assert.equal(copy.SpeechProfiles[1].Gender,'female');assert.equal(copy.Chapters[0].Paragraphs[0].Speech.ProfileId,'male');
assert.equal(api.cleanSpeechSettings({Rate:8,Pitch:.1}).Rate,2);assert.equal(api.cleanSpeechSettings({Rate:8,Pitch:.1}).Pitch,.5);assert.equal(api.cleanSpeechSettings({Rate:'1.2',Pitch:NaN}).Rate,undefined);
assert.equal(api.speechResolve(project.Chapters[0].Paragraphs[0],l,project.SpeechDefaults,'dialogue').voice,'character');
assert.equal(api.speechResolve(project.Chapters[0].Paragraphs[0],l,project.SpeechDefaults,'narration').voice,'narrator');
assert.equal(api.speechResolve({},l,project.SpeechDefaults,'dialogue').voice,'female-voice');
assert.equal(api.speechResolve({Speech:{Role:'dialogue',ProfileId:'male',Rate:1.5}},l,project.SpeechDefaults,'dialogue').rate,1.5);
assert.equal(api.speechResolve({},l,{}).voice,'global');assert.equal(api.speechResolve({},l,{}).pitch,1);
for(const text of ['He said, "Hello." Then left.','She said, “I don’t know.” He nodded.',"He said 'I can't.' Then left.",'She said, ‘I don’t know.’ Then left.','“One.” He paused. “Two.”']){
 const parts=api.speechSegments(text);assert.equal(parts.map(x=>x.text).join(''),text);assert(parts.some(x=>x.role==='dialogue'));
}
for(const text of ["It's his brother's book.",'He didn’t ask.', 'An unmatched “quote.', 'An unmatched "quote.'])assert(api.speechSegments(text).every(x=>x.role==='narration'));
api.listenPlay();assert.equal(spoken[0].options.voice,'narrator');spoken[0].options.onDone();assert.equal(spoken[1].options.voice,'character');assert.equal(spoken[1].options.pitch,.8);spoken[1].options.onDone();assert.equal(spoken[2].options.voice,'narrator');assert.equal(spoken[2].options.pitch,1.1);spoken[2].options.onDone();assert.equal(advances,1);
assert.equal(spoken.map(x=>x.text).join(''),project.Chapters[0].Paragraphs[0].Text);
spoken=[];api.listenPlay();api.listenPause();assert.equal(l.paused,true);api.listenPlay();assert.equal(resumes,1);spoken[0].options.onDone();assert.equal(spoken[1].options.voice,'character');c.listenStop();spoken[1].options.onDone();assert.equal(spoken.length,2);
project.Chapters[0].Paragraphs[0].Speech.Voice='not-installed';assert.equal(api.speechPlaybackOptions('p','dialogue').voice,'global');assert.equal(notices.length,1);api.speechPlaybackOptions('p','dialogue');assert.equal(notices.length,1);
delete project.Chapters[0].Paragraphs[0].Speech.Voice;
const tag=api.speechImportedTag(project.Chapters[0].Paragraphs[0],project.SpeechDefaults,project.SpeechProfiles);
assert.equal(api.speechResolve({Speech:tag},l,{},'dialogue',[]).voice,'character');assert.equal(api.speechResolve({Speech:tag},l,{},'narration',[]).voice,'narrator');
const again=api.speechImportedTag({Speech:tag},{},[]);assert.equal(api.speechResolve({Speech:again},l,{},'dialogue',[]).voice,'character');
assert.equal(project.History.length,0);assert.equal(project.Chapters[0].Paragraphs[0].Text,'He said, “Hello.” Then he left.');
assert.match(html,/newp.Speech=cleanSpeechTag\(f.paragraph.Speech\)/);assert.match(html,/Speech tags differ/);assert.match(html,/speech-preset-new/);
console.log('PASS: durable male/female presets, role inheritance, custom overrides, mixed-quote voice/rate/pitch switching, apostrophes, unmatched quotes, pause/resume, Stop, missing voices, import/reimport and unchanged text/history.');
