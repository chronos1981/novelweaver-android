"""Apply paragraph speech tags to the generated Android editor."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=root/'app/src/main/assets/www/index.html'
s=p.read_text()
def replace(old,new):
 global s
 if old not in s:raise SystemExit('Speech integration point changed: '+old[:90])
 s=s.replace(old,new)
replace('function cleanProject(raw){',(root/'tools/voice-filters.js').read_text()+'\n'+(root/'tools/speech-tags.js').read_text()+'\nfunction cleanProject(raw){')
replace('Books:[],Chapters:[],Threads:[],Reviews:[],History:[]};\n p.Books=', 'Books:[],Chapters:[],Threads:[],Reviews:[],History:[],SpeechDefaults:cleanSpeechDefaults(raw.SpeechDefaults),SpeechProfiles:cleanSpeechProfiles(raw.SpeechProfiles)};\n p.Books=')
replace('ThreadIds:Array.isArray(q.ThreadIds)?q.ThreadIds.map(String):[]}', 'ThreadIds:Array.isArray(q.ThreadIds)?q.ThreadIds.map(String):[],Speech:cleanSpeechTag(q.Speech)}')
replace('ttsSpeak(text,options.voice||\'\',options.rate||1,String(key))', 'ttsSpeak(text,options.voice||\'\',options.rate||1,String(key),options.pitch||1)')
replace('u.rate=options.rate||1;', 'u.rate=options.rate||1;u.pitch=options.pitch||1;')
replace('ttsSpeak(active.text,active.options.voice||\'\',active.options.rate||1,String(active.key))', 'ttsSpeak(active.text,active.options.voice||\'\',active.options.rate||1,String(active.key),active.options.pitch||1)')
a=s.index('function listenPlay(){');b=s.index('function listenPause()',a)
s=s[:a]+(root/'tools/speech-segments.js').read_text()+'\n'+s[b:]
replace('<div class="para-meta">${names.map', '<div class="para-meta">${speechBadge(p)}${names.map')
replace("$('.para-meta',card).innerHTML=names.map", "$('.para-meta',card).innerHTML=speechBadge(f.paragraph)+names.map")
replace('<button class="outline-button" data-action="listen-this" data-id="${h(pid)}">', '<button class="outline-button" data-action="paragraph-speech" data-id="${h(pid)}">♫ Voice &amp; speech tag</button><button class="outline-button" data-action="listen-this" data-id="${h(pid)}">')
replace(" case 'paragraph-menu':", " case 'paragraph-speech':paragraphSpeech(x);break;\n case 'speech-defaults':speechDefaultMenu();break;\n case 'speech-presets':speechPresetMenu();break;\n case 'speech-preset-new':speechPresetEdit();break;\n case 'speech-preset-edit':speechPresetEdit(x);break;\n case 'speech-preview':speechPreview(x||'speech');break;\n case 'speech-defaults-narration':speechDefaultEdit('narration');break;\n case 'speech-defaults-dialogue':speechDefaultEdit('dialogue');break;\n case 'paragraph-menu':")
replace('<button class="outline-button" data-action="listen-pronunciations">Pronunciations</button>', '<button class="outline-button" data-action="listen-pronunciations">Pronunciations</button><button class="outline-button" data-action="paragraph-speech" data-id="${h(cur?.id||\'\')}">Paragraph speech tag</button><button class="outline-button" data-action="speech-defaults">Narration, dialogue &amp; saved voices</button>')
replace("if($('#listen-chapter-label'))$('#listen-chapter-label').textContent=c?.chapter||'';", "if($('#listen-chapter-label'))$('#listen-chapter-label').textContent=c?.chapter||'';const tagButton=$('[data-action=\"paragraph-speech\"]',document.getElementById('main'));if(tagButton&&c)tagButton.dataset.id=c.id;")
replace('Bookmark and voice settings are saved on this device, not in the .novel.json file.', 'Listen voice, speed and bookmarks are saved on this device. Paragraph speech tags and narration/dialogue defaults are saved in the project. Blank tag settings inherit those defaults; pitch 1 is normal.')
replace('const newp=mkPara(after,f.paragraph.ThreadIds);', 'const newp=mkPara(after,f.paragraph.ThreadIds);if(f.paragraph.Speech)newp.Speech=cleanSpeechTag(f.paragraph.Speech);')
replace('...p,Id:remap(p.Id),ThreadIds:p.ThreadIds.map(remap)', '...p,Speech:speechImportedTag(p,incoming.SpeechDefaults,incoming.SpeechProfiles),Id:remap(p.Id),ThreadIds:p.ThreadIds.map(remap)')
replace('const prev=f.chapter.Paragraphs[f.index-1];editParagraph', 'const prev=f.chapter.Paragraphs[f.index-1];if(JSON.stringify(cleanSpeechTag(prev.Speech))!==JSON.stringify(cleanSpeechTag(f.paragraph.Speech))){toast(\'Speech tags differ. Give both paragraphs the same tag before merging.\');return}editParagraph')
replace('const HELP_TOPICS=[', '''const HELP_TOPICS=[
 {id:'speech-tags',category:'writing',title:'Narration and dialogue speech tags',summary:'Assign paragraph roles, installed voices, speed and pitch.',keywords:'tts voice speech dialogue narration speaker pitch speed tags',body:`<p>In Write, open a paragraph’s <b>⋯ → Voice &amp; speech tag</b>. Set the Narrator (outside quotes) and Dialogue (inside quotes) independently. Each can use a saved preset or an installed voice, speed and pitch. Save voices. Blank values inherit the matching project defaults. Its badge opens both voice settings again. Remove tag returns to narration defaults.</p><p>Use <b>Listen → Narration &amp; dialogue defaults</b> to set shared voices and settings. Blank paragraph fields inherit that role’s settings. Blank role settings inherit the Listen voice and speed; normal pitch is 1. Untagged text uses narration outside quotes and dialogue inside quotes.</p><p>Matched straight or curly quotes use dialogue voices; narration outside quotes uses narrator settings in the same paragraph. Contractions and apostrophes are not dialogue. Unmatched quotes stay with the narrator. Open Saved voices to preview and save named Male/Female presets, then reuse them in paragraph tags or role defaults. Splitting copies the tag; merging requires matching tags. A speaker name is a label; choose a saved preset or installed voice. Speech tags travel with the .novel.json project, while voice availability depends on the device. Missing voices fall back with a warning. TXT, DOCX and EPUB exports contain the manuscript text without speech tags.</p>`},''')
a=s.index('function listenVisibleVoices(){');b=s.index('function renderListen()',a)
s=s[:a]+"function listenVisibleVoices(){return voiceOptions(S.listen.voice,'Device default')}\n"+s[b:]
replace('<label>Installed voice<select id="listen-voice">', '${voiceFilterControls()}<label>Installed voice<select id="listen-voice">')
replace('({id:v.voiceURI,name:v.name,lang:v.lang})','({id:v.voiceURI,name:v.name,lang:v.lang,local:v.localService})')
p.write_text(s)
