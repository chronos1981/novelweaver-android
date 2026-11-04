function speechQuickPicker(p){
 const profiles=cleanSpeechProfiles(S.project.SpeechProfiles),tag=cleanSpeechTag(p.Speech);
 const settings={...(tag?.Role==='dialogue'?cleanSpeechSettings(tag):{}),...cleanSpeechSettings(tag?.Dialogue)};
 const custom=Object.keys(settings).some(key=>key!=='ProfileId'),selected=custom?'__keep__':settings.ProfileId||'';
 const options=`<option value="" ${!selected?'selected':''}>Use dialogue defaults</option>`+
  (custom?'<option value="__keep__" selected>Keep custom voice settings</option>':'')+
  (selected&&selected!=='__keep__'&&!profiles.some(v=>v.Id===selected)?`<option value="${h(selected)}" selected>Unavailable saved voice</option>`:'')+
  profiles.map(v=>`<option value="${h(v.Id)}" ${v.Id===selected?'selected':''}>${h(v.Name)} · ${v.Gender==='male'?'Male':v.Gender==='female'?'Female':'Other'}</option>`).join('');
 return `<div class="paragraph-quick-voice" role="group" aria-label="Quick dialogue voice"><label><span>Dialogue voice</span><select data-quick-voice="${h(p.Id)}" aria-label="Saved dialogue voice" ${!profiles.length?'disabled':''}>${options}</select></label><button class="outline-button" data-action="speech-quick-apply" data-id="${h(p.Id)}" aria-label="Apply saved dialogue voice" ${!profiles.length?'disabled':''}>Apply</button>${!profiles.length?'<button class="mini-link" data-action="speech-presets">Save a voice…</button>':''}</div>`;
}
function speechQuickTag(previous,profileId,profiles){
 const tag=cleanSpeechTag(previous)||{},profile=profiles.find(p=>p.Id===profileId);
 if(profileId&&!profile)throw Error('That saved voice is unavailable. Choose another preset.');
 const narration={...(tag.Role==='narration'?cleanSpeechSettings(tag):{}),...cleanSpeechSettings(tag.Narration)};
 return cleanSpeechTag({Narration:narration,Dialogue:profile?{ProfileId:profile.Id}:{},...(profile?{Speaker:profile.Name}:{}),...(tag.ImportedDefaults?{ImportedDefaults:tag.ImportedDefaults}:{})});
}
function speechQuickApply(pid){
 flushAllEdits();const f=locate(pid),select=$$('[data-quick-voice]').find(el=>el.dataset.quickVoice===pid);if(!f||!select)return;
 if(select.value==='__keep__'){toast('Custom voice settings kept');return}
 try{
  const next=speechQuickTag(f.paragraph.Speech,select.value,cleanSpeechProfiles(S.project.SpeechProfiles));
  if(JSON.stringify(cleanSpeechTag(f.paragraph.Speech))===JSON.stringify(next)){toast('Voice already applied');return}
  listenStop();f.paragraph.Speech=next;changed();
  const main=document.getElementById('main'),top=main?.scrollTop||0,y=window.scrollY||0;
  render();if(main)main.scrollTop=top;window.scrollTo(0,y);
  $$('[data-action="speech-quick-apply"]').find(el=>el.dataset.id===pid)?.focus({preventScroll:true});
  toast(select.value?'Dialogue voice applied':'Dialogue defaults applied');
 }catch(e){toast(e.message)}
}
