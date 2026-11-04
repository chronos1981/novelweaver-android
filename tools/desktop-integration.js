// Included inside the editor closure, so the desktop and web use the same engine.
const desktop = {path: null, blocked: false, conflict: null, busy: false, revision: null, pending:false, error:'',starting:true};
function desktopAdopt(project) {
  listenStop();
  S.project = cleanProject(project);
  S.editSession.clear();
  S.bookId = S.chapterId = S.readChapterId = S.threadId = S.paraId = null;
  S.view = 'write';
  quill.selection = quill.proposal = quill.undo = null;
  quill.messages = []; quill.sessions.clear();
  swapChapter(); render();
}
function desktopStatus() {
  const el = $('#save-status');
  if (!el) return;
  el.textContent = desktop.blocked ? 'File changed · choose version' : S.dirty ? 'Saving…' : desktop.error ? 'File save failed · retry' : desktop.pending ? 'Recovery saved · file pending' : desktop.path ? 'Saved to file' : 'Recovery saved · choose Save';
  el.title = desktop.path || 'Use File → Save to choose a project file, including your Google Drive folder.';
  el.style.cursor = 'pointer';
  el.onclick = () => desktop.blocked ? desktopConflict(desktop.conflict) : desktopCommand('save');
}
async function desktopInitialize() {
  S.project=defaultProject();S.bookId=S.chapterId=null;
  const initial = await DesktopHost.call('initialize');
  if (initial.keyError) setTimeout(() => toast(initial.keyError), 700);
  desktopVault = initial.key || '';
  if (initial.speechAvailable) {
    window.NovelWeaverNative = {
      ttsVoices: () => JSON.stringify(window.AndroidVoices || initial.voices || []),
      ttsSpeak: (text, voice, rate, key, pitch=1) => DesktopHost.call('speak', {text,voice,rate,key,pitch}).catch(e => NWTTS.nativeComplete(key,e.message)),
      ttsStop: () => DesktopHost.call('speechStop').catch(console.warn),
      ttsPause: () => DesktopHost.call('speechPause').catch(console.warn)
    };
    NWTTS.resume = () => DesktopHost.call('speechResume').catch(console.warn);
    window.dispatchEvent(new Event('nw-voices'));
  }
  await androidChooseStartup(initial.startup||{});
  swapChapter(); desktopStatus();
}
function desktopConflict(result) {
  if (!result) return;
  const disk = result.project;
  const date = p => p?.ModifiedAtUtc ? new Date(p.ModifiedAtUtc).toLocaleString() : 'Unknown';
  modal('Choose project version', `<p>The open file ${disk ? 'changed outside NovelWeaver' : 'is missing or unavailable'}. Automatic file saving is paused; your current writing has a recovery copy on this PC.</p><p class="tiny">${h(result.path || '')}</p><p><b>This PC’s edits:</b> ${h(date(S.project))}</p>${disk ? `<p><b>File’s edits:</b> ${h(date(disk))}<br><b>File save time:</b> ${h(new Date(result.fileTime).toLocaleString())}</p><p>Dates are a guide. Choosing This PC replaces the file and keeps a .bak of its previous contents. Choosing File loads it from disk. Save a copy preserves the current workspace in a separate file.</p>` : '<p>Check that your Drive folder is available, or save this workspace to a new location.</p>'}<div class="item-actions"><button class="outline-button" data-action="desktop-copy">Save a copy</button>${disk ? '<button class="outline-button" data-action="desktop-disk">Use file</button><button class="primary-button" data-action="desktop-local">Use this PC</button>' : ''}</div>`, [{text:'Decide later',value:'cancel'}]);
}
function desktopHandleResult(result, showConflict = true) {
  if (result.status === 'cancelled') return false;
  desktop.path = result.path;
  if(result.status==='pending'||result.status==='paused'){
    desktop.pending=result.status==='pending';desktopStatus();return !desktop.blocked;
  }
  if(result.status==='error'){
    desktop.error=result.message||'The selected file could not be saved.';desktop.pending=false;
    desktopStatus();toast(desktop.error+' Your recovery copy remains on this device.');return false;
  }
  if (result.status === 'conflict' || result.status === 'missing') {
    const first = !desktop.blocked;
    desktop.blocked = true; desktop.conflict = result;
    if (showConflict && first) desktopConflict(result);
    desktopStatus(); return false;
  }
  desktop.blocked = false; desktop.conflict = null;desktop.pending=false;desktop.error='';
  desktopStatus(); return true;
}
async function desktopAutosave(json) {
  if(desktop.starting)return;
  // Keep recovery current while an external-file decision is pending, without
  // repeatedly reopening the choice dialog on every keystroke.
  const result = await DesktopHost.call('autosave', {json});
  desktopHandleResult(result);
}
async function desktopSave(saveAs = false, revision = null) {
  flushAllEdits();
  const result = await DesktopHost.call('save', {json: JSON.stringify(S.project), name: safeName(S.project.Title), saveAs, revision});
  return desktopHandleResult(result);
}
async function desktopConfirmLeave() {
  flushAllEdits(); await saveLocal();
  if (desktop.blocked) { desktopConflict(desktop.conflict); return false; }
  if (desktop.path) return desktopSave();
  const choice = await new Promise(resolve => modal('Save this project?', '<p>Save your current project before replacing the workspace?</p>', [{text:'Cancel',value:'cancel'},{text:'Discard workspace',value:'discard'},{text:'Save',value:'save',primary:true}],resolve));
  if (choice === 'save') return desktopSave();
  return choice === 'discard';
}
async function desktopCommand(command) {
  if(desktop.starting)return;
  if (desktop.busy) return;
  desktop.busy = true;
  try {
    switch (command) {
      case 'save': case 'saveAs':
        if (desktop.blocked && command !== 'saveAs') desktopConflict(desktop.conflict);
        else await desktopSave(command === 'saveAs');
        break;
      case 'open':
        if (!await desktopConfirmLeave()) break;
        { const result = await DesktopHost.call('open');
          if (result.status === 'cancelled') break;
          desktopHandleResult(result); desktopAdopt(result.project); await saveLocal(); }
        break;
      case 'new': case 'sample':
        if (!await desktopConfirmLeave()) break;
        { const project = command === 'sample' ? exampleProject() : defaultProject();
          const result = await DesktopHost.call('new', {json:JSON.stringify(project)});
          desktopHandleResult(result); desktopAdopt(project); await saveLocal(); }
        break;
      case 'disk':
        { const result = await DesktopHost.call('reloadDisk');
          desktopHandleResult(result); desktopAdopt(result.project); closeModal(); await saveLocal(); }
        break;
      case 'local':
        { const revision = desktop.conflict?.revision;
          // If the disk changes again, re-open the choice with its current data.
          desktop.blocked = false; closeModal(); await desktopSave(false, revision); }
        break;
      case 'copy':
        if (await desktopSave(true)) closeModal();
        break;
      case 'close':
        flushAllEdits(); await saveLocal();
        if (S.dirty) throw Error('The recovery copy could not be saved. Save a project file before closing.');
        if(desktop.path&&!desktop.blocked&&!await desktopSave())break;
        if (desktop.blocked) {
          const choice = await new Promise(resolve => modal('File saving is paused', '<p>Your writing is in this PC’s recovery copy. Close now to keep it for your next session, or cancel to choose a file version.</p>', [{text:'Cancel',value:'cancel'},{text:'Close with recovery',value:'close'}],resolve));
          if (choice !== 'close') break;
        }
        await DesktopHost.call('close'); break;
      case 'tools': S.view='more'; render(); break;
      case 'help': S.view='help'; render(); break;
    }
  } catch (error) { modal('NovelWeaver', `<p>${h(error.message)}</p>`); }
  finally { desktop.busy = false; desktopStatus(); }
}
window.NWDesktop = {command:desktopCommand};
window.addEventListener('nw-file-status',event=>desktopHandleResult(event.detail));
window.NWAndroid={
  pause(){if(desktop.starting)return;if(S.listen.playing)listenPause();flushAllEdits();saveLocal()},
  back(){if($('#modal-host .modal')){const fn=modalResolve;closeModal();fn?.('cancel')}else if(quill.open)quillClose();else if(S.sidebarOpen)closeSidebar();else desktopCommand('close')}
};
document.addEventListener('keydown', event => {
  if (!(event.ctrlKey || event.metaKey)) return;
  if (event.key.toLowerCase() === 's' || event.key.toLowerCase() === 'o') {
    event.preventDefault(); event.stopImmediatePropagation();
    desktopCommand(event.key.toLowerCase() === 'o' ? 'open' : event.shiftKey ? 'saveAs' : 'save');
  }
}, true);
document.addEventListener('click', event => {
  const button = event.target.closest('[data-action]');
  const command = { 'open-project':'open', 'export-project':'saveAs', 'new-project':'new', 'load-sample':'sample', 'desktop-save':'save', 'desktop-copy':'copy', 'desktop-disk':'disk', 'desktop-local':'local' }[button?.dataset.action];
  if (!command) return;
  event.preventDefault(); event.stopImmediatePropagation(); desktopCommand(command);
}, true);
