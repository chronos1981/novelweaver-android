const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.join(__dirname,'..');
function fixture(profiles=[{Id:'livit',Name:'Livit',Gender:'male',Voice:'voice-a'}]){
 const paragraph={Id:'p',Text:'Narration. “Dialogue.”',Speech:{Narration:{Voice:'narrator',Rate:.9},Dialogue:{ProfileId:'livit'},Speaker:'Livit',ImportedDefaults:{Narration:{Pitch:.8}}}};
 const project={SpeechProfiles:profiles,History:[]},select={dataset:{quickVoice:'p'},value:'__create_voice__',focus(){state.focuses++}},main={scrollTop:450};
 const fields={};for(const [key,value] of Object.entries({'speech-preset-name':'New speaker','speech-gender':'female','speech-voice':'voice-b','speech-rate':'1.2','speech-pitch':'.9'}))fields['#'+key]={value};
 const state={saves:0,renders:0,focuses:0,current:paragraph,dialog:null};let onChange,serial=0,c;
 c=vm.createContext({console,document:{addEventListener(type,fn){if(type==='change')onChange=fn},getElementById:()=>main},S:{project,listen:{}},window:{scrollY:700,scrollTo(x,y){assert.equal(y,700)},NWTTS:{stop(){}}},
  h:s=>String(s).replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;'),$:s=>fields[s],$$:()=>[select],flushAllEdits(){},locate:()=>state.current?{paragraph:state.current}:null,
  listenStop(){},changed(){state.saves++},render(){state.renders++;main.scrollTop=0;select.value=c.api.speechQuickSelection(paragraph)},toast(){},id:()=>`created-${++serial}`,
  voiceFilterControls:()=>'',voiceOptions:v=>`<option value="${v}" selected>${v}</option>`,modal(title,html,buttons,resolve){state.dialog={title,html,buttons,resolve}}
 });
 vm.runInContext(fs.readFileSync(path.join(root,'tools/speech-tags.js'),'utf8')+fs.readFileSync(path.join(root,'tools/quick-voices.js'),'utf8')+'\nglobalThis.api={speechQuickPicker,speechQuickSelection,speechPresetEdit};',c);
 return {c,state,paragraph,project,fields,select,main,open(){onChange({target:{closest:()=>select}})}};
}
{
 const f=fixture([]);assert.match(f.c.api.speechQuickPicker(f.paragraph),/Create new voice/);assert.doesNotMatch(f.c.api.speechQuickPicker(f.paragraph),/disabled/);
}
{
 const f=fixture();f.open();assert.equal(f.state.dialog.title,'Create dialogue voice');assert.equal(f.select.value,'livit');assert.equal(f.state.saves,0);
 f.state.dialog.resolve('cancel');assert.equal(f.state.saves,0);assert.equal(f.project.SpeechProfiles.length,1);assert.equal(f.paragraph.Speech.Dialogue.ProfileId,'livit');assert.equal(f.state.focuses,1);
}
{
 const f=fixture();f.open();f.state.dialog.resolve('save');assert.equal(f.state.saves,1);assert.equal(f.state.renders,1);assert.equal(f.project.SpeechProfiles.length,2);
 const voice=f.project.SpeechProfiles[1];assert.equal(voice.Name,'New speaker');assert.equal(voice.Gender,'female');assert.equal(voice.Voice,'voice-b');assert.equal(voice.Rate,1.2);assert.equal(voice.Pitch,.9);
 assert.equal(f.paragraph.Speech.Dialogue.ProfileId,voice.Id);assert.equal(f.paragraph.Speech.Speaker,'New speaker');assert.equal(f.paragraph.Speech.Narration.Voice,'narrator');assert.equal(f.paragraph.Speech.Narration.Rate,.9);assert.equal(f.paragraph.Speech.ImportedDefaults.Narration.Pitch,.8);
 assert.equal(f.paragraph.Text,'Narration. “Dialogue.”');assert.equal(f.project.History.length,0);assert.equal(f.main.scrollTop,450);assert.equal(f.state.focuses,1);assert.equal(f.select.value,voice.Id);
 assert.match(f.c.api.speechQuickPicker(f.paragraph),/New speaker/);
}
{
 const f=fixture();f.open();f.fields['#speech-preset-name'].value='';f.state.dialog.resolve('save');assert.equal(f.state.saves,0);assert.match(f.state.dialog.html,/Enter a preset name/);assert.match(f.state.dialog.html,/value="voice-b"/);assert.match(f.state.dialog.html,/value="1.2"/);
 f.fields['#speech-preset-name'].value='Retried speaker';f.state.dialog.resolve('save');assert.equal(f.state.saves,1);assert.equal(f.paragraph.Speech.Speaker,'Retried speaker');assert.equal(f.project.SpeechProfiles.length,2);
}
{
 const f=fixture();f.open();f.fields['#speech-rate'].value='3';f.state.dialog.resolve('save');assert.equal(f.state.saves,0);assert.match(f.state.dialog.html,/Rate must be between/);
 f.fields['#speech-rate'].value='1';f.state.dialog.resolve('save');assert.equal(f.state.saves,1);assert.equal(f.paragraph.Speech.Dialogue.ProfileId,'created-1');
}
{
 const f=fixture();f.open();f.fields['#speech-voice'].value='';f.state.dialog.resolve('save');assert.equal(f.state.saves,0);assert.equal(f.project.SpeechProfiles.length,1);assert.match(f.state.dialog.html,/Choose an installed voice/);
}
for(const invalidate of [f=>{f.state.current=null},f=>{f.c.S.project={SpeechProfiles:[]}}]){
 const f=fixture();f.open();invalidate(f);f.state.dialog.resolve('save');assert.equal(f.state.saves,0);assert.equal(f.project.SpeechProfiles.length,1);assert.equal(f.paragraph.Speech.Dialogue.ProfileId,'livit');assert.match(f.state.dialog.html,/original paragraph is no longer available/);
}
{
 const f=fixture();f.c.api.speechPresetEdit();f.state.dialog.resolve('save');assert.equal(f.state.dialog.title,'Saved voices');assert.equal(f.state.saves,1);assert.equal(f.paragraph.Speech.Dialogue.ProfileId,'livit');
}
const bundle=fs.readFileSync(path.join(root,'app/src/main/assets/www/index.html'),'utf8');
for(const name of ['speech-tags.js','quick-voices.js'])assert.ok(bundle.includes(fs.readFileSync(path.join(root,'tools',name),'utf8')),`${name} is packaged unchanged`);
console.log('PASS: create dialogue voice, empty preset list, cancellation, atomic save/apply, narrator preservation, retry drafts, invalid fields, stale target guards, scroll/focus and existing saved-voice workflow.');
