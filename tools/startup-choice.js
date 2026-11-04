async function androidChooseStartup(info) {
  let error=info.recoveryError||'';
  while(desktop.starting){
    const buttons=[{text:'New empty project',value:'new',primary:true}];
    if(info.hasLast)buttons.push({text:'Open last project from file',value:'last'});
    buttons.push({text:'Open another project',value:'open'});
    if(info.recoveries?.length)buttons.push({text:'Recovery copies…',value:'recover'});
    buttons.push({text:'Close app',value:'close'});
    const choice=await new Promise(resolve=>modal('Start NovelWeaver',`<p>Start empty or read a project from its saved file.</p>${info.hasLast?`<p><b>Last file:</b> ${h(info.lastPath||'Last saved project')}</p>`:''}<p class="tiny">Recovery copies are available separately and never loaded automatically.</p>${error?`<p role="alert">${h(error)}</p>`:''}`,buttons,resolve));
    if(choice==='close'||choice==='cancel'){await DesktopHost.call('close');await new Promise(()=>{});return}
    try{
      let result,project;
      if(choice==='new'){project=defaultProject();result=await DesktopHost.call('new',{json:JSON.stringify(project)})}
      else if(choice==='last'){result=await DesktopHost.call('openLast');project=result.project}
      else if(choice==='open'){result=await DesktopHost.call('open');if(result.status==='cancelled')continue;project=result.project}
      else if(choice==='recover'){
        const copies=info.recoveries||[];
        const selected=await new Promise(resolve=>modal('Recovery copies','<p>These are separate backups from this device. Open one as an unlinked workspace, then use Save as to choose a file.</p>',[...copies.slice(0,10).map(copy=>({text:(copy.title||'Recovery copy')+' · '+(copy.modified?new Date(copy.modified).toLocaleString():new Date(copy.savedAt).toLocaleString()),value:copy.id})),{text:'Back',value:'cancel'}],resolve));
        if(selected==='cancel')continue;
        result=await DesktopHost.call('openRecovery',{id:selected});project=result.project;
      }else continue;
      if(!project)throw Error('The file did not contain a project. Choose another file or start empty.');
      desktopHandleResult(result,false);desktopAdopt(project);S.dirty=false;
      desktop.starting=false;closeModal();
    }catch(e){error=e.message||'The file could not be opened. Choose another file or start empty.'}
  }
}
