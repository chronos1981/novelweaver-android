"""Bundle the current web editor for Android. Run with the web index.html path."""
from pathlib import Path
import runpy,re
root=Path(__file__).resolve().parents[1]
runpy.run_path(str(root/'tools/package-base.py'),run_name='__main__')
path=root/'app/src/main/assets/www/index.html'
s=path.read_text()
def replace(old,new):
 global s
 if old not in s: raise SystemExit('Android integration point changed: '+old[:90])
 s=s.replace(old,new)

replace('<head>', '<head>\n<meta http-equiv="Content-Security-Policy" content="default-src \'self\' data: blob:; script-src \'self\' \'unsafe-inline\'; style-src \'self\' \'unsafe-inline\'; font-src \'self\'; img-src \'self\' data: blob:; connect-src \'none\'; frame-src \'none\'; object-src \'none\'; base-uri \'none\'">')
replace('</head>', '<style>.save-status{max-width:28vw;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}</style></head>')
replace('</head>', '<style>.chapter-navigation{display:flex;gap:8px;flex-basis:100%;width:100%}.chapter-navigation button{flex:1;min-width:0}.chapter-navigation button:disabled{opacity:.45;cursor:default}</style></head>')
replace('</head>', '<style>.chapter-navigation-footer{margin:18px 0 56px}</style></head>')
replace('function renderWrite(){', (root/'tools/chapter-navigation.js').read_text()+'\nfunction renderWrite(){')
replace('async function desktopInitialize() {', (root/'tools/startup-choice.js').read_text()+'\nasync function desktopInitialize() {')
replace("const p=await dbGet('project'),terms=await dbGet('customWords');if(p)S.project=cleanProject(p);", "const terms=await dbGet('customWords');")
replace("const p=localStorage.getItem('novelweaver.project');if(p)S.project=cleanProject(JSON.parse(p));", '')
replace('data-action="add-paragraph">+ Paragraph</button>`)', 'data-action="add-paragraph">+ Paragraph</button>${writeChapterNavigation(ch)}`)')
replace('<div class="append-row"><button data-action="add-paragraph">+ Add a paragraph</button></div>`}', '<div class="append-row"><button data-action="add-paragraph">+ Add a paragraph</button></div><nav class="chapter-navigation-footer" aria-label="End of chapter">${writeChapterNavigation(ch)}</nav>`}')
replace(" case 'choose-chapter':", " case 'previous-chapter':moveWriteChapter(-1);break;\n case 'next-chapter':moveWriteChapter(1);break;\n case 'choose-chapter':")
replace("'Recovery saved · file pending'", "'File save pending'")
replace("'Recovery saved · choose Save'", "'On device · Save'")
replace("'File changed · choose version'", "'Choose version'")
replace("'File save failed · retry'", "'File save failed'")
replace('<h3>Project file</h3><p>', '<h3>Project file</h3><p class="item-sub">${desktop.path?\'Current file: \'+h(desktop.path):\'No file chosen · private recovery only\'}</p><p>')
replace('<div class="item-actions"><button class="primary-button" data-action="desktop-save">Save</button>', '<div class="item-actions"><button class="outline-button" data-action="new-project">New empty project</button><button class="primary-button" data-action="desktop-save">Save</button>')

a=s.index('function download(filename,data,mime){');b=s.index('\nfunction backup()',a)
s=s[:a]+'''async function download(filename,data,mime){
 const blob=data instanceof Blob?data:new Blob([data],{type:mime});
 if(blob.size>64000000){modal('Export too large','<p>Choose a smaller book or reduce cover image sizes. The export limit is 64 MB.</p>');return}
 let token;
 try{
  token=(await DesktopHost.call('exportStart',{name:filename,mime:blob.type||mime||'application/octet-stream'})).token;
  toast('Preparing '+filename+'…');
  for(let offset=0;offset<blob.size;offset+=65536){
   const bytes=new Uint8Array(await blob.slice(offset,offset+65536).arrayBuffer());let binary='';
   for(let i=0;i<bytes.length;i++)binary+=String.fromCharCode(bytes[i]);
   await DesktopHost.call('exportChunk',{token,data:btoa(binary)});
  }
  const result=await DesktopHost.call('exportFinish',{token});
  toast(result.status==='cancelled'?'Export cancelled':filename+' saved');
 }catch(error){if(token)DesktopHost.call('exportCancel',{token}).catch(()=>{});modal('Export failed',`<p>${h(error.message)}</p>`)}
}
''' +s[b:]
s=s.replace("toast('EPUB ebook exported')", "/* The export adapter reports completion after Android's save picker. */")
s=s.replace("toast('Word manuscript exported')", "/* The export adapter reports completion after Android's save picker. */")
replace('Save your project in any Windows folder. Choose your Google Drive folder to let Drive for desktop sync the file.', 'Choose a project file through Android’s file picker. For cloud storage, choose Google Drive in the picker if the Drive app provides it.')
replace('Drive for desktop can sync that folder.', 'Android’s file picker can save to local storage or an available cloud provider.')
replace('The key is encrypted for your Windows account. Anyone using your Windows account can use it.', 'The key is encrypted with Android Keystore. Anyone with access to this unlocked app can use it.')
replace('encrypted for your Windows account and remembered after restarting the app.', 'encrypted with Android Keystore and remembered after restarting the app.')
replace('Settings belong to this Windows account', 'Settings belong to this Android app')
replace('keeps your encrypted key on this PC across restarts.', 'keeps your encrypted key on this device across restarts.')
replace('The previous file contents are also kept as <code>.bak</code>.', 'The previous file contents are kept as an app-private backup; export it from More when needed.')
for old,new in [('File → Save as','More → Save as'),('File → Save As','More → Save as'),('File → Save','More → Save'),('File → Open / Save','More → Open / Save'),('File → Open','More → Open'),('Windows app','Android app'),('for Windows','for Android'),('Windows networking','Android networking'),('Windows connects','Android connects'),('trusted by Windows','trusted by Android'),('this Windows app profile','this Android app'),('this Windows app','this Android app'),('Windows project file + recovery copy','Project file + recovery copy'),('this PC','this device'),('This PC','This device'),('this computer','this device')]:s=s.replace(old,new)
s=s.replace('Windows file dialogs','Android’s file picker')
replace("new Date(result.fileTime).toLocaleString()", "result.fileTime?new Date(result.fileTime).toLocaleString():'Unknown'")
replace('Your writing saves to a Windows project file, with a recovery copy on this PC.', 'Your writing autosaves a recovery copy on this device. After choosing a project file, file updates follow shortly while this app is open.') if 'Your writing saves to a Windows project file, with a recovery copy on this PC.' in s else None
s=s.replace('Your writing saves to a Windows project file, with a recovery copy on this device.','Your writing autosaves a recovery copy on this device. After choosing a file, file updates follow shortly while this app is open.')
# Android's previous-file backup is exportable without exposing private paths.
s=s.replace('${drivePanel()}', '${drivePanel()}<div class="setting-card"><div class="desc"><h3>Previous file backup</h3><p>Save the previous document version kept before the last file update.</p></div><button class="outline-button" data-action="android-previous">Export backup</button></div>')
a=s.index(" {id:'drive',");b=s.index("\n {id:",a+1)
s=s[:a]+''' {id:'drive',category:'saving',title:'Save projects on Android and Google Drive',summary:'File picker, autosave, local recovery and cloud providers.',keywords:'android google drive open save autosave recovery conflict backup cloud',body:`<p>Open <b>More → Project file → Open</b> to choose a <code>.novel.json</code> project using Android’s file picker. Choose <b>Save</b> for the first file, or <b>Save as</b> for a separate copy. The picker can offer local storage and Google Drive when the Drive app is installed and signed in.</p><p>Writing has a private recovery copy on this device. After choosing a project file, updates are sent to that file shortly after you pause editing, while the app is open. Saving to a cloud provider can take longer and may require internet. A saved-file status means the provider accepted the write; it does not guarantee its cloud upload has finished. There is no OAuth setup inside NovelWeaver.</p><p>If the provider’s file differs from the version last read, file saving pauses. Choose this device’s writing, the file, or a separate copy. The previous file version is retained privately and can be exported under More. SAF providers do not offer the same atomic replacement as local Windows files; keep separate backups and avoid editing on two devices at once.</p><p>If permission expires or the document is moved, choose Open again or Save as. Uninstalling the app removes its private recovery, preferences and key; saved project files in your document provider remain separate. Some devices do not offer Drive as a writable provider. In that case, save to Documents and upload/share the file through Drive.</p>`},''' +s[b:]
# Android keeps the same shared HTTP policy, including private Tailscale IPs.
replace("if(a===127)return 'loopback';if(a===10", "if(a===127)return 'loopback';if(a===100&&b>=64&&b<=127)return 'local';if(a===10")
s=s.replace("window.NWDesktop = {command:desktopCommand};", """document.addEventListener('click',async event=>{if(!event.target.closest('[data-action="android-previous"]'))return;try{const item=await DesktopHost.call('previousBackup');await DesktopHost.call('exportFinish',item)}catch(e){modal('Backup',`<p>${h(e.message)}</p>`)}});
window.NWDesktop = {command:desktopCommand};""")
path.write_text(s)
(root/'WEB-SOURCE.txt').write_text((root/'WEB-SOURCE.txt').read_text().replace('Desktop adapter:','Android adapter:')+'\nAndroid host uses the same paragraph, threads, history, reader, covers, exports and Quill engine.\n')
print('Android editor packaged:',len(s.encode()),'bytes')
