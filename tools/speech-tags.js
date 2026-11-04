// Speech metadata belongs to the project, never to the manuscript text.
function cleanSpeechSettings(raw){
 if(!raw||typeof raw!=='object')return {};
 const s={};if(typeof raw.Voice==='string'&&raw.Voice)s.Voice=raw.Voice.slice(0,500);
 for(const key of ['Rate','Pitch'])if(typeof raw[key]==='number'&&Number.isFinite(raw[key]))s[key]=Math.max(.5,Math.min(2,raw[key]));
 return s;
}
function cleanSpeechTag(raw){
 if(!raw||typeof raw!=='object')return null;
 const s=cleanSpeechSettings(raw);s.Role=raw.Role==='dialogue'?'dialogue':'narration';
 if(typeof raw.Speaker==='string'&&raw.Speaker.trim())s.Speaker=raw.Speaker.trim().slice(0,100);
 return s;
}
function cleanSpeechDefaults(raw){return {Narration:cleanSpeechSettings(raw?.Narration),Dialogue:cleanSpeechSettings(raw?.Dialogue)}}
function speechImportedTag(p,defaults){
 const tag=cleanSpeechTag(p.Speech),preset=cleanSpeechSettings(defaults?.[tag?.Role==='dialogue'?'Dialogue':'Narration']);
 return Object.keys(preset).length?cleanSpeechTag({...preset,...tag}):tag;
}
function speechLabel(tag){return tag?(tag.Role==='dialogue'?'Dialogue':'Narration')+(tag.Speaker?' · '+tag.Speaker:''):'Narration · defaults'}
function speechBadge(p){return p.Speech?`<button class="chip-button" data-action="paragraph-speech" data-id="${h(p.Id)}" aria-label="Speech settings: ${h(speechLabel(p.Speech))}">♫ ${h(speechLabel(p.Speech))}</button>`:''}
function speechResolve(p,listen=S.listen,defaults=S.project.SpeechDefaults){
 const tag=cleanSpeechTag(p?.Speech),role=tag?.Role||'narration',preset=cleanSpeechSettings(defaults?.[role==='dialogue'?'Dialogue':'Narration']);
 const s={...preset,...cleanSpeechSettings(tag)};
 return {voice:s.Voice||listen.voice||'',rate:s.Rate||listen.rate||1,pitch:s.Pitch||1};
}
function speechPlaybackOptions(pid){
 const p=locate(pid)?.paragraph,options=speechResolve(p),voices=window.NWTTS?.voices()||[];
 if(options.voice&&!voices.some(v=>v.id===options.voice)){
  const unavailable=options.voice;options.voice=voices.some(v=>v.id===S.listen.voice)?S.listen.voice:'';
  if(speechPlaybackOptions.warned!==unavailable){speechPlaybackOptions.warned=unavailable;toast('Tagged voice is not installed. Using the Listen voice or device default.')}
 }
 return options;
}
function speechFields(s={}){
 const voices=window.NWTTS?.voices()||[],missing=s.Voice&&!voices.some(v=>v.id===s.Voice);
 return `<label class="form-label" for="speech-voice">Voice</label><select class="text-input" id="speech-voice"><option value="">Use defaults</option>${missing?`<option value="${h(s.Voice)}" selected>Unavailable on this device · ${h(s.Voice)}</option>`:''}${voices.map(v=>`<option value="${h(v.id)}" ${s.Voice===v.id?'selected':''}>${h(v.name)}${v.lang?' ('+h(v.lang)+')':''}</option>`).join('')}</select><div class="reader-toolbar"><label>Speed (0.5–2×)<input class="text-input" type="number" id="speech-rate" min="0.5" max="2" step="0.05" placeholder="Use defaults" value="${s.Rate||''}"></label><label>Pitch (0.5–2)<input class="text-input" type="number" id="speech-pitch" min="0.5" max="2" step="0.05" placeholder="Use defaults" value="${s.Pitch||''}"></label></div>`;
}
function speechReadFields(){
 const s={},voice=$('#speech-voice').value;if(voice)s.Voice=voice;
 for(const key of ['Rate','Pitch']){const input=$('#speech-'+key.toLowerCase()),value=input.value.trim();if(value){const n=Number(value);if(!Number.isFinite(n)||n<.5||n>2)throw Error(key+' must be between 0.5 and 2.');s[key]=n;}}
 return s;
}
function paragraphSpeech(pid,error='',draft=null){
 const f=locate(pid);if(!f)return;flushAllEdits();listenStop();const s=draft||cleanSpeechTag(f.paragraph.Speech)||{};
 modal('Paragraph speech tag',`<p class="item-sub">${h(short(f.paragraph.Text||'(empty paragraph)',140))}</p>${error?`<p role="alert">${h(error)}</p>`:''}<label class="form-label" for="speech-role">Read as</label><select class="text-input" id="speech-role"><option value="narration" ${s.Role!=='dialogue'?'selected':''}>Narration</option><option value="dialogue" ${s.Role==='dialogue'?'selected':''}>Dialogue</option></select><label class="form-label" for="speech-speaker">Speaker (optional label)</label><input class="text-input" id="speech-speaker" maxlength="100" placeholder="e.g. Niels" value="${h(s.Speaker||'')}">${speechFields(s)}<p class="tiny">Blank settings inherit the narration or dialogue defaults in Listen. The tag applies to this whole paragraph. Split mixed narration and dialogue to give each its own voice. Tags are saved in your project; they are not spoken or printed.</p>`,[{text:'Cancel',value:'cancel'},{text:'Remove tag',value:'remove'},{text:'Save tag',value:'save',primary:true}],v=>{
  if(v==='cancel')return;
  if(v==='remove'){delete f.paragraph.Speech;changed();render();return}
  const draft={Role:$('#speech-role').value,Speaker:$('#speech-speaker').value};
  try{f.paragraph.Speech=cleanSpeechTag({...speechReadFields(),...draft});changed();render();toast('Speech tag saved')}catch(e){paragraphSpeech(pid,e.message,{...s,...draft})}
 });
}
function speechDefaultMenu(){modal('Narration & dialogue voices','<p>Project defaults apply to paragraphs by role. Untagged paragraphs use narration. Blank values fall back to the Listen voice and speed; pitch falls back to 1.</p><div class="detail-grid"><button class="outline-button" data-action="speech-defaults-narration">Narration defaults</button><button class="outline-button" data-action="speech-defaults-dialogue">Dialogue defaults</button></div>',[{text:'Close',value:'cancel'}])}
function speechDefaultEdit(role,error=''){
 const key=role==='dialogue'?'Dialogue':'Narration',s=cleanSpeechDefaults(S.project.SpeechDefaults)[key];listenStop();
 modal(key+' defaults',`${error?`<p role="alert">${h(error)}</p>`:''}${speechFields(s)}<p class="tiny">Saved with this project. Installed voice names can differ between devices. Unavailable voices fall back to the Listen voice or device default.</p>`,[{text:'Cancel',value:'cancel'},{text:'Reset',value:'reset'},{text:'Save defaults',value:'save',primary:true}],v=>{if(v==='cancel')return;try{const settings=v==='reset'?{}:speechReadFields();S.project.SpeechDefaults=cleanSpeechDefaults(S.project.SpeechDefaults);S.project.SpeechDefaults[key]=settings;changed();render();toast(key+' defaults saved')}catch(e){speechDefaultEdit(role,e.message)}});
}
