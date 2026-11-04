// Voice browsing preferences are local to the device, separate from saved voices.
function voiceLanguage(voice){
 const lang=String(voice.lang||'').trim().replace(/_/g,'-');
 try{return Intl.getCanonicalLocales(lang)[0]||''}catch(e){return lang}
}
function voiceConnection(voice){
 const local=typeof voice.local==='boolean'?voice.local:voice.localService;
 return local===true?'local':local===false?'online':'unknown';
}
function voiceFiltersState(){
 if(!voiceFiltersState.value){let saved={};try{saved=JSON.parse(localStorage.getItem('novelweaver.voiceFilters.v1')||'{}')}catch(e){}
  voiceFiltersState.value={language:typeof saved.language==='string'?saved.language:'',connection:['local','online','unknown'].includes(saved.connection)?saved.connection:''};
 }
 return voiceFiltersState.value;
}
function voiceMatches(voice,filters=voiceFiltersState()){
 return (!filters.language||voiceLanguage(voice).toLowerCase()===filters.language.toLowerCase())&&(!filters.connection||voiceConnection(voice)===filters.connection);
}
function voiceOptions(selected='',placeholder='Use defaults'){
 const voices=window.NWTTS?.voices()||[],list=voices.filter(v=>voiceMatches(v)),current=voices.find(v=>v.id===selected);
 const option=(v,outside=false)=>`<option value="${h(v.id)}" ${v.id===selected?'selected':''}>${h(v.name)} · ${h(voiceLanguage(v)||'Language unknown')} · ${voiceConnection(v)==='local'?'Local':voiceConnection(v)==='online'?'Online':'Connection unknown'}${outside?' · selected, outside filter':''}</option>`;
 return `<option value="" ${!selected?'selected':''}>${h(placeholder)}</option>`+
  (selected&&!current?`<option value="${h(selected)}" selected>Unavailable on this device · ${h(selected)}</option>`:'')+
  (current&&!voiceMatches(current)?option(current,true):'')+list.map(v=>option(v)).join('')+
  (!list.length?'<option disabled>No voices match these filters</option>':'');
}
function voiceFilterControls(){
 const filters=voiceFiltersState(),voices=window.NWTTS?.voices()||[],languages=[...new Set(voices.map(voiceLanguage).filter(Boolean))];
 if(filters.language&&!languages.includes(filters.language))languages.push(filters.language);
 languages.sort((a,b)=>a.localeCompare(b));
 return `<div class="reader-toolbar voice-filter-controls" data-voice-filters><label>Language<select data-voice-filter="language"><option value="">All languages</option>${languages.map(lang=>`<option value="${h(lang)}" ${lang===filters.language?'selected':''}>${h(lang)}</option>`).join('')}</select></label><label>Connection<select data-voice-filter="connection"><option value="">Local &amp; online</option>${[['local','Local (offline)'],['online','Online (network)'],['unknown','Unknown']].map(([value,label])=>`<option value="${value}" ${filters.connection===value?'selected':''}>${label}</option>`).join('')}</select></label></div>`;
}
function refreshVoiceSelectors(){
 for(const control of $$('[data-voice-filters]'))control.outerHTML=voiceFilterControls();
 for(const select of $$('select[data-speech-voice],#listen-voice')){
  const selected=select.value;select.innerHTML=voiceOptions(selected,select.id==='listen-voice'?'Device default':'Use defaults');select.value=selected;
 }
}
document.addEventListener('change',event=>{
 const key=event.target.dataset.voiceFilter;if(!['language','connection'].includes(key))return;
 const state=voiceFiltersState();state[key]=event.target.value;
 try{localStorage.setItem('novelweaver.voiceFilters.v1',JSON.stringify(state))}catch(e){}
 refreshVoiceSelectors();
});
window.addEventListener('nw-voices',refreshVoiceSelectors);
