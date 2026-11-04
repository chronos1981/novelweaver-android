// Speech metadata belongs to the project, never to the manuscript text.
function cleanSpeechSettings(raw){
 if(!raw||typeof raw!=='object')return {};
 const s={};if(typeof raw.ProfileId==='string'&&raw.ProfileId)s.ProfileId=raw.ProfileId.slice(0,100);if(typeof raw.Voice==='string'&&raw.Voice)s.Voice=raw.Voice.slice(0,500);
 for(const key of ['Rate','Pitch'])if(typeof raw[key]==='number'&&Number.isFinite(raw[key]))s[key]=Math.max(.5,Math.min(2,raw[key]));
 return s;
}
function cleanSpeechTag(raw){
 if(!raw||typeof raw!=='object')return null;
 const s=cleanSpeechSettings(raw);s.Role=raw.Role==='dialogue'?'dialogue':'narration';
 if(raw.ImportedDefaults&&typeof raw.ImportedDefaults==='object')s.ImportedDefaults=cleanSpeechDefaults(raw.ImportedDefaults);
 if(typeof raw.Speaker==='string'&&raw.Speaker.trim())s.Speaker=raw.Speaker.trim().slice(0,100);
 return s;
}
function cleanSpeechDefaults(raw){return {Narration:cleanSpeechSettings(raw?.Narration),Dialogue:cleanSpeechSettings(raw?.Dialogue)}}
function cleanSpeechProfiles(raw){
 if(!Array.isArray(raw))return [];
 const seen=new Set();return raw.filter(p=>p&&typeof p.Id==='string'&&p.Id&&!seen.has(p.Id)&&(seen.add(p.Id),true)).slice(0,200).map(p=>({Id:p.Id.slice(0,100),Name:String(p.Name||'Voice preset').slice(0,100),Gender:['male','female'].includes(p.Gender)?p.Gender:'other',...cleanSpeechSettings({...p,ProfileId:null})}));
}
function speechProfileSettings(settings,profiles=S.project.SpeechProfiles){
 const s=cleanSpeechSettings(settings),profile=cleanSpeechProfiles(profiles).find(p=>p.Id===s.ProfileId);
 const result={...cleanSpeechSettings(profile),...s};delete result.ProfileId;return result;
}
function speechImportedTag(p,defaults,profiles){
 const tag=cleanSpeechTag(p.Speech),out=tag?{...tag,...speechProfileSettings(tag,profiles)}:null;
 if(out)delete out.ProfileId;
 // Freeze both roles on imported paragraphs so importing another book cannot change its voices.
 const imported={Narration:speechProfileSettings(tag?.ImportedDefaults?.Narration||defaults?.Narration,profiles),Dialogue:speechProfileSettings(tag?.ImportedDefaults?.Dialogue||defaults?.Dialogue,profiles)};
 if(out)imported[out.Role==='dialogue'?'Dialogue':'Narration']={...imported[out.Role==='dialogue'?'Dialogue':'Narration'],...speechProfileSettings(out,profiles)};
 if(Object.values(imported).some(v=>Object.keys(v).length))return {...(out||{Role:'narration'}),ImportedDefaults:imported};
 return out;
}
function speechLabel(tag){return tag?(tag.Role==='dialogue'?'Dialogue':'Narration')+((tag.Speaker||S.project.SpeechProfiles?.find(p=>p.Id===tag.ProfileId)?.Name)?' · '+(tag.Speaker||S.project.SpeechProfiles.find(p=>p.Id===tag.ProfileId).Name):''):'Narration · defaults'}
function speechBadge(p){return p.Speech?`<button class="chip-button" data-action="paragraph-speech" data-id="${h(p.Id)}" aria-label="Speech settings: ${h(speechLabel(p.Speech))}">♫ ${h(speechLabel(p.Speech))}</button>`:''}
function speechResolve(p,listen=S.listen,defaults=S.project.SpeechDefaults,role=null,profiles=S.project.SpeechProfiles){
 const tag=cleanSpeechTag(p?.Speech);role=role||tag?.Role||'narration';
 const roleKey=role==='dialogue'?'Dialogue':'Narration';
 const preset=speechProfileSettings(tag?.ImportedDefaults?.[roleKey]||defaults?.[roleKey],profiles);
 const overrides=tag?.Role===role?speechProfileSettings(tag,profiles):{};
 const s={...preset,...overrides};return {voice:s.Voice||listen.voice||'',rate:s.Rate||listen.rate||1,pitch:s.Pitch||1};
}
function speechAvailableOptions(options){
 const voices=window.NWTTS?.voices()||[];
 if(options.voice&&!voices.some(v=>v.id===options.voice)){
  const unavailable=options.voice;options.voice=voices.some(v=>v.id===S.listen.voice)?S.listen.voice:'';
  if(speechAvailableOptions.warned!==unavailable){speechAvailableOptions.warned=unavailable;toast('Tagged voice is not installed. Using the Listen voice or device default.')}
 }
 return options;
}
function speechPlaybackOptions(pid,role=null){return speechAvailableOptions(speechResolve(locate(pid)?.paragraph,S.listen,S.project.SpeechDefaults,role))}
function speechFields(s={},presets=true){
 const voices=window.NWTTS?.voices()||[],missing=s.Voice&&!voices.some(v=>v.id===s.Voice);
 return `${presets?`<label class="form-label" for="speech-profile">Saved voice preset</label><select class="text-input" id="speech-profile"><option value="">Use defaults / custom</option>${s.ProfileId&&!S.project.SpeechProfiles?.some(p=>p.Id===s.ProfileId)?`<option selected value="${h(s.ProfileId)}">Unavailable preset</option>`:''}${(S.project.SpeechProfiles||[]).map(p=>`<option value="${h(p.Id)}" ${s.ProfileId===p.Id?'selected':''}>${h(p.Gender==='male'?'Male':p.Gender==='female'?'Female':'Other')} · ${h(p.Name)}</option>`).join('')}</select><p class="tiny">Choose a saved male/female voice, or set custom overrides below.</p>`:''}<label class="form-label" for="speech-voice">Voice</label><select class="text-input" id="speech-voice"><option value="">Use defaults</option>${missing?`<option value="${h(s.Voice)}" selected>Unavailable on this device · ${h(s.Voice)}</option>`:''}${voices.map(v=>`<option value="${h(v.id)}" ${s.Voice===v.id?'selected':''}>${h(v.name)}${v.lang?' ('+h(v.lang)+')':''}</option>`).join('')}</select><div class="reader-toolbar"><label>Speed (0.5–2×)<input class="text-input" type="number" id="speech-rate" min="0.5" max="2" step="0.05" placeholder="Use defaults" value="${s.Rate||''}"></label><label>Pitch (0.5–2)<input class="text-input" type="number" id="speech-pitch" min="0.5" max="2" step="0.05" placeholder="Use defaults" value="${s.Pitch||''}"></label></div><div class="item-actions"><button class="outline-button" data-action="speech-preview">Preview voice</button>${presets?'<button class="outline-button" data-action="speech-presets">Manage saved voices</button>':''}</div>`;
}
function speechReadFields(){
 const s={},voice=$('#speech-voice').value,profile=$('#speech-profile')?.value;if(profile)s.ProfileId=profile;if(voice)s.Voice=voice;
 for(const key of ['Rate','Pitch']){const input=$('#speech-'+key.toLowerCase()),value=input.value.trim();if(value){const n=Number(value);if(!Number.isFinite(n)||n<.5||n>2)throw Error(key+' must be between 0.5 and 2.');s[key]=n;}}
 return s;
}
function paragraphSpeech(pid,error='',draft=null){
 const f=locate(pid);if(!f)return;flushAllEdits();listenStop();const s=draft||cleanSpeechTag(f.paragraph.Speech)||{};
 modal('Paragraph speech tag',`<p class="item-sub">${h(short(f.paragraph.Text||'(empty paragraph)',140))}</p>${error?`<p role="alert">${h(error)}</p>`:''}<label class="form-label" for="speech-role">Apply these settings to</label><select class="text-input" id="speech-role"><option value="narration" ${s.Role!=='dialogue'?'selected':''}>Narration outside quotes</option><option value="dialogue" ${s.Role==='dialogue'?'selected':''}>Dialogue inside quotes</option></select><label class="form-label" for="speech-speaker">Speaker (optional label)</label><input class="text-input" id="speech-speaker" maxlength="100" placeholder="e.g. Niels" value="${h(s.Speaker||'')}">${speechFields(s)}<p class="tiny">Blank settings inherit the narration or dialogue defaults in Listen. Only quoted text uses dialogue settings. Text outside quotes uses narration settings, even in the same paragraph. Use matched straight or curly quotation marks. Tags are saved in your project; they are not spoken or printed.</p>`,[{text:'Cancel',value:'cancel'},{text:'Remove tag',value:'remove'},{text:'Save tag',value:'save',primary:true}],v=>{
  window.NWTTS?.stop();if(v==='cancel')return;
  if(v==='remove'){delete f.paragraph.Speech;changed();render();return}
  const draft={Role:$('#speech-role').value,Speaker:$('#speech-speaker').value};
  try{f.paragraph.Speech=cleanSpeechTag({...speechReadFields(),...draft,...(s.ImportedDefaults?{ImportedDefaults:s.ImportedDefaults}:{})});changed();render();toast('Speech tag saved')}catch(e){paragraphSpeech(pid,e.message,{...s,...draft})}
 });
}
function speechDefaultMenu(){modal('Narration & dialogue voices','<p>Project defaults apply to paragraphs by role. Text outside quotes uses narration; quoted text uses dialogue. Blank values fall back to the Listen voice and speed; pitch falls back to 1.</p><div class="detail-grid"><button class="outline-button" data-action="speech-presets">Saved male/female voices</button><button class="outline-button" data-action="speech-defaults-narration">Narration defaults</button><button class="outline-button" data-action="speech-defaults-dialogue">Dialogue defaults</button></div>',[{text:'Close',value:'cancel'}])}
function speechDefaultEdit(role,error=''){
 const key=role==='dialogue'?'Dialogue':'Narration',s=cleanSpeechDefaults(S.project.SpeechDefaults)[key];listenStop();
 modal(key+' defaults',`${error?`<p role="alert">${h(error)}</p>`:''}${speechFields(s)}<p class="tiny">Saved with this project. Installed voice names can differ between devices. Unavailable voices fall back to the Listen voice or device default.</p>`,[{text:'Cancel',value:'cancel'},{text:'Reset',value:'reset'},{text:'Save defaults',value:'save',primary:true}],v=>{window.NWTTS?.stop();if(v==='cancel')return;try{const settings=v==='reset'?{}:speechReadFields();S.project.SpeechDefaults=cleanSpeechDefaults(S.project.SpeechDefaults);S.project.SpeechDefaults[key]=settings;changed();render();toast(key+' defaults saved')}catch(e){speechDefaultEdit(role,e.message)}});
}

function speechPresetMenu(){
 listenStop();const profiles=cleanSpeechProfiles(S.project.SpeechProfiles);
 modal('Saved voices',`<p>Save voices once, then select them for later paragraphs or narration/dialogue defaults. Android does not reliably label voice gender: preview a voice and label it Male or Female yourself.</p><div class="option-list">${profiles.map(p=>`<div class="option-row"><span>${h(p.Gender==='male'?'Male':p.Gender==='female'?'Female':'Other')} · ${h(p.Name)}</span><button class="outline-button" data-action="speech-preset-edit" data-id="${h(p.Id)}">Edit</button></div>`).join('')||'<p>No saved voices yet.</p>'}</div><button class="primary-button" data-action="speech-preset-new">+ Save a voice</button>`,[{text:'Close',value:'cancel'}],()=>window.NWTTS?.stop());
}
function speechPresetEdit(pid=null,error=''){
 listenStop();const preset=S.project.SpeechProfiles?.find(p=>p.Id===pid)||{Name:'',Gender:'male'};
 modal(pid?'Edit saved voice':'Save a voice',`${error?`<p role="alert">${h(error)}</p>`:''}<label class="form-label" for="speech-preset-name">Preset name</label><input class="text-input" id="speech-preset-name" maxlength="100" placeholder="e.g. Niels or Female narrator" value="${h(preset.Name)}"><label class="form-label" for="speech-gender">Voice label</label><select class="text-input" id="speech-gender"><option value="male" ${preset.Gender==='male'?'selected':''}>Male</option><option value="female" ${preset.Gender==='female'?'selected':''}>Female</option><option value="other" ${preset.Gender==='other'?'selected':''}>Other</option></select>${speechFields(preset,false)}<p class="tiny">Choose an installed voice and preview it. These labels organize your presets; they do not change the speech engine’s voice. Presets are saved with the project.</p>`,[{text:'Cancel',value:'cancel'},{text:'Save voice',value:'save',primary:true}],v=>{
  window.NWTTS?.stop();if(v!=='save')return;
  try{const settings=speechReadFields(),name=$('#speech-preset-name').value.trim();if(!name)throw Error('Enter a preset name.');if(!settings.Voice)throw Error('Choose an installed voice to save.');const value={Id:pid||id(),Name:name,Gender:$('#speech-gender').value,...settings};S.project.SpeechProfiles=cleanSpeechProfiles(S.project.SpeechProfiles);const ix=S.project.SpeechProfiles.findIndex(p=>p.Id===value.Id);if(ix>=0)S.project.SpeechProfiles[ix]=value;else S.project.SpeechProfiles.push(value);changed();render();speechPresetMenu()}catch(e){speechPresetEdit(pid,e.message)}
 });
}
function speechPreview(){
 try{if(!window.NWTTS?.available())throw Error('No speech engine is available.');const role=$('#speech-role')?.value||'narration';const tag={Role:role,...speechReadFields()};const options=speechAvailableOptions(speechResolve({Speech:tag},S.listen,S.project.SpeechDefaults,role));window.NWTTS.speak('This is a preview of the selected voice.',{...options,onError:message=>toast('Speech: '+message)})}catch(e){toast(e.message)}
}
document.addEventListener('change',event=>{if(event.target.id==='speech-profile'){for(const key of ['voice','rate','pitch'])$('#speech-'+key).value='';}});
