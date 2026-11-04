"""Package the current single-file web editor with desktop-only persistence.
No runtime dependency on GitHub, Google sign-in, or a local web server.
Usage: python tools/package-web.py path/to/web/index.html
"""
from pathlib import Path
import hashlib
import re
import sys

root = Path(__file__).resolve().parents[1]
source = Path(sys.argv[1]).read_text(encoding='utf-8')
s = source
def replace(old, new):
    global s
    if old not in s:
        raise SystemExit('Web source changed; desktop adapter needs review: ' + old[:100])
    s = s.replace(old, new)

replace('<script src="./novelweaver-config.js"></script>', '<script src="./desktop-host.js"></script>')
replace('<link rel="manifest" href="./manifest.webmanifest">', '')
replace('<link rel="icon" type="image/png" href="./icon-192.png">', '')
for weight in ('Regular','Bold'):
    replace(f'https://cdn.jsdelivr.net/gh/antijingoist/opendyslexic@main/compiled/OpenDyslexic-{weight}.woff2', f'./fonts/OpenDyslexic-{weight}.woff2')
# Remove the entire OAuth client and sync implementation, not just its controls.
a = s.index('// Google Drive integration.')
b = s.index('// End Google Drive integration.', a) + len('// End Google Drive integration.')
s = s[:a] + '''// Desktop storage uses ordinary files. Drive for desktop manages its folder.
function driveShowStatus(){desktopStatus()}
function driveQueueAutoSave(){}
function driveClearLink(){}
function drivePanel(){return `<div class="setting-card"><div class="desc"><h3>Project file</h3><p>Save your project in any Windows folder. Choose your Google Drive folder to let Drive for desktop sync the file. NovelWeaver keeps a recovery copy on this PC and checks for external file changes before saving.</p></div><div class="item-actions"><button class="primary-button" data-action="desktop-save">Save</button><button class="outline-button" data-action="desktop-copy">Save as…</button><button class="outline-button" data-action="open-project">Open…</button></div></div>`}
''' + s[b:]
# Dead Drive action cases would reference removed functions; remove them too.
s = re.sub(r" case 'drive-settings':.*?(?= case 'export-project':)", '', s, flags=re.S)
s = re.sub(r"document.addEventListener\('change',e=>\{if\(e.target.id==='drive-autosync'\)\{.*?setInterval\(driveShowStatus,30000\);", '', s, flags=re.S)
replace("await loadLocal();driveReadSettings();driveShowStatus();if(driveState.settings.clientId&&navigator.onLine&&location.protocol==='https:')driveLoadIdentity().catch(()=>{});", 'await loadLocal();await desktopInitialize();')
replace("if('serviceWorker'in navigator&&location.protocol!=='file:'){navigator.serviceWorker.register('./service-worker.js').catch(e=>console.warn('Offline install unavailable',e))}", '')
replace("(async()=>{await loadLocal();", (root/'tools/desktop-integration.js').read_text() + "\n(async()=>{await loadLocal();")

# The recovery snapshot and native file write complete before close/open proceeds.
a = s.index('async function saveLocal(){')
b = s.index('\nfunction changed()', a)
s = s[:a] + '''let desktopSaveTask=null;
async function saveLocal(){
 clearTimeout(saveTimer);
 if(saveBusy){saveAgain=true;return desktopSaveTask}
 saveBusy=true;
 desktopSaveTask=(async()=>{do{
  saveAgain=false;
  const savedCopy=JSON.stringify(S.project),wordsCopy=[...S.customWords];
  try{
   // The native recovery file is authoritative; IndexedDB also keeps UI data.
   await desktopAutosave(savedCopy);
   try{if(persistence==='indexeddb')await dbPut({project:JSON.parse(savedCopy),customWords:wordsCopy});else localStorage.setItem('novelweaver.project',savedCopy)}catch(e){console.warn(e)}
   localStorage.setItem('novelweaver.dictionary',JSON.stringify(wordsCopy));
   S.dirty=JSON.stringify(S.project)!==savedCopy;
   if(S.dirty)saveAgain=true;
   lastSaved=now();
  }catch(e){console.error(e);S.dirty=true;$('#save-status').textContent='Save failed · retry Save';toast(e.message);break}
 }while(saveAgain)})().finally(()=>{saveBusy=false;desktopStatus()});
 return desktopSaveTask;
}
''' + s[b:]

# Save connection settings separately from an encrypted per-Windows-user key.
replace('function quillRead(){', "let desktopVault='';\nfunction quillRead(){")
a=s.index(" quill.key='';if(!quill.remember)return;",s.index('function quillRead(){'))
b=s.index('\nfunction quillAddressSpace(',a)
s=s[:a]+" quill.key=quill.remember?desktopVault:'';\n}\n"+s[b:]
replace('function quillSaveSettings(){','async function quillSaveSettings(){')
replace("try{if(quill.remember)localStorage.setItem('novelweaver.quill.openwebui.key',key);else localStorage.removeItem('novelweaver.quill.openwebui.key')}catch{stored=false}", "try{await DesktopHost.call('key',{key,remember:quill.remember});desktopVault=quill.remember?key:''}catch{stored=false}")
replace('function quillForgetConnection(){', "async function quillForgetConnection(){await DesktopHost.call('key',{key:'',remember:false});desktopVault='';")
replace("$('#quill-save').onclick=()=>{try{quillSaveSettings();", "$('#quill-save').onclick=async()=>{try{await quillSaveSettings();")
replace("$('#quill-forget').onclick=quillForgetConnection;", "$('#quill-forget').onclick=()=>quillForgetConnection().catch(e=>quillStatus(e.message));")
replace('Anyone using this browser profile can use the saved key.', 'The key is encrypted for your Windows account. Anyone using your Windows account can use it.')
replace('keeps your key in this browser profile across restarts.', 'keeps your encrypted key on this PC across restarts.')

# Native HTTP avoids browser CORS and mixed-content restrictions. TLS validation
# remains enabled in the C# HttpClient. Cancellation has no automatic time limit.
a=s.index(' const networkOptions=await quillNetworkOptions(base);',s.index('async function quillRequest'))
b=s.index('\n if(!response.ok)',a)
s=s[:a]+''' const raw=await DesktopHost.call('http',{url:base+path,method:body?'POST':'GET',key,body:body?JSON.stringify(body):''},controller.signal);
 const response={ok:raw.status>=200&&raw.status<300,status:raw.status,json:async()=>JSON.parse(raw.body)};
 const stopped=()=>Error('Request stopped. The server may finish processing it.');
''' +s[b:]

# User-visible copy and help match desktop file storage and networking.
replace('Your writing saves locally and automatically syncs to Drive when linked and connected.', 'Your writing saves to a Windows project file, with a recovery copy on this PC.')
replace('Browser storage can be deleted if site data is cleared or the browser is uninstalled. Export a .novel.json backup frequently and keep a copy elsewhere.', 'Use File → Save to choose a .novel.json project file. Drive for desktop can sync that folder. Keep a separate backup too; synchronization also copies deletions and unwanted changes.')
replace('NovelWeaver Mobile · Optional Google Drive connection · No ads · Offline editing still works.', 'NovelWeaver for Windows · Local project files · Offline editing · No Google sign-in required.')
replace('Stored on this device', 'Windows project file + recovery copy')
replace('Project backup downloaded', 'Project backup saved')
replace('Local server? Enter its full address, for example http://192.168.1.100:3000. Test / load models may ask your browser for permission to reach your local network.', 'Local server? Enter its full address and port, for example http://192.168.0.101:8080. Windows connects directly; no browser CORS setup is needed. For remote access, use your private Tailscale HTTPS address.')
replace('Enable access from <b>${h(location.origin)}</b> on your server or your own gateway (CORS). GitHub Pages cannot proxy requests. Local HTTP addresses such as http://192.168.1.100:3000 are supported in browsers with local-network access. Press Test / load models and allow the browser’s local-network prompt. Both devices must be on the same network. If previously blocked, allow access in browser Site settings. Public servers need HTTPS. Localhost means this browser’s device, not your server.', 'This Windows app connects directly, so browser CORS and mixed-content settings do not apply. Both devices must be reachable on your LAN or Tailscale network. Use HTTPS for remote access. Localhost means this PC. HTTPS certificates must be trusted by Windows; the app keeps certificate validation enabled.')
replace('Settings saved for this browser.', 'Settings saved for this Windows app.')
replace('remembered on this device, including after restarting the browser.', 'encrypted for your Windows account and remembered after restarting the app.')
replace('Settings belong to this browser profile and are not included in books, backups, or Drive sync.', 'Settings belong to this Windows account and are not included in project files or exports.')
replace('This context exceeds 100,000 characters.', 'This context exceeds 100,000 characters.')

# Replace only the Drive help article; retain the complete rest of the guide.
a=s.index(" {id:'drive',")
b=s.index("\n {id:",a+1)
s=s[:a]+''' {id:'drive',category:'saving',title:'Save in your Google Drive desktop folder',summary:'Ordinary Windows files, recovery copies, backups and external changes.',keywords:'google drive save autosave recovery conflict desktop folder backup',body:`<p>Choose <b>File → Save as</b> and select a folder managed by Google Drive for desktop. The app saves a complete <code>.novel.json</code> file. Google Drive for desktop handles uploading and downloading; there is no OAuth connection inside NovelWeaver.</p><p>After you choose a file, edits autosave to it. A separate recovery copy is kept under your Windows account. The previous file contents are also kept as <code>.bak</code>. Before the first save, writing has a recovery copy on this PC; choose Save to create a portable project file.</p><p>If the open file changes outside the app, file saving pauses and asks whether to keep this PC’s edits, load the file, or save a separate copy. Choosing later leaves writing in recovery. Ordinary saves do not reopen this prompt unless the disk file changes again.</p><p>Avoid editing the same project on two devices at once. Let Drive finish syncing before opening on another PC. The file check protects against changes already visible on disk, but cannot prevent two devices making simultaneous offline edits.</p><p>The web app keeps its own workspace. Export a .novel.json from the web app, open it here, and import/export it again when moving back. This desktop app does not read the web app’s OAuth-managed Drive files automatically.</p>`},''' +s[b:]
# Desktop-specific guidance takes precedence in all network articles.
replace('function renderHelp(){return', '''function renderHelp(){return `<div class="callout"><b>Windows app:</b> Use File → Open / Save for project files. Quill connects through Windows networking, so browser CORS and mixed-content steps below apply only to the web edition. For a private remote connection, use your Tailscale HTTPS address. HTTPS certificates must be trusted by Windows. API endpoint permissions still apply.</div>`+''')
s=s.replace('this browser profile','this Windows app profile')
replace('if(location.protocol===\'https:\'&&u.protocol===\'http:\'&&!quillAddressSpace(u.href))', "if(u.protocol==='http:'&&!quillAddressSpace(u.href))")
replace('new Event(\'nw-voices\')', "new Event('nw-voices')")
(root/'app/src/main/assets/www/index.html').write_text(s,encoding='utf-8')
(root/'WEB-SOURCE.txt').write_text('Web source: chronos1981/novelweaver\nCommit: 14b6e3706ef1e2a51fe520f5d162fbefd4e139b2\nSource SHA256: '+hashlib.sha256(source.encode()).hexdigest()+'\nDesktop adapter: tools/package-web.py and tools/desktop-integration.js\n',encoding='utf-8')
print('Packaged desktop editor:', len(s.encode()), 'bytes')
