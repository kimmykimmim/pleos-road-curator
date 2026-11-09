import { mountVehicleShell } from './vehicle-shell.js';
import { mascot } from './visuals.js';
import { CONTEXTS, createMissionState, advanceMission, setMissionContext } from './mission-engine.js';
import { MissionVoice } from './mission-voice.js';

const $=id=>document.getElementById(id);
const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const paths={
  explore:'<path d="m7 5-3 8v7h6V10H7m10-5 3 8v7h-6V10h3M10 13h4M6 5h3m6 0h3"/><circle cx="7" cy="17" r="3"/><circle cx="17" cy="17" r="3"/>',
  sound:'<path d="m11 4-6 5H2v6h3l6 5V4Zm4 4a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14"/>',
  shape:'<circle cx="8" cy="9" r="5"/><path d="m16 10 6 11H10l6-11Z"/>',
  color:'<path d="M12 2S4 10 4 15a8 8 0 0 0 16 0c0-5-8-13-8-13Z"/><path d="M8 15c0 3 2 4 4 4"/>',
  nature:'<path d="M12 22V10m0 6-4-4m4 2 4-4M4 14C0 9 6 3 12 2c6 1 12 7 8 12-3 4-13 4-16 0Z"/>',
  sky:'<path d="M6 18h12a4 4 0 0 0 0-8 6 6 0 0 0-11-2 5 5 0 0 0-1 10Z"/>',
  light:'<circle cx="12" cy="12" r="4"/><path d="M12 1v3m0 16v3M1 12h3m16 0h3M4 4l2 2m12 12 2 2M4 20l2-2M18 6l2-2"/>',
  change:'<path d="M3 9h16l-4-4M21 15H5l4 4"/>',
  check:'<path d="m5 12 4 4L20 5"/>',
  skip:'<path d="M4 7h8a7 7 0 0 1 0 14H8m4-18 4 4-4 4"/>',
  next:'<path d="M4 12h16m-6-6 6 6-6 6"/>',
  pause:'<path d="M8 5v14M16 5v14" stroke-width="3"/>',
  play:'<path d="m8 4 12 8-12 8V4Z"/>',
  hint:'<path d="M8 15a7 7 0 1 1 8 0v3H8v-3Zm1 6h6M8 18h8"/>',
  mic:'<rect x="8" y="2" width="8" height="13" rx="4"/><path d="M5 11v1a7 7 0 0 0 14 0v-1m-7 8v3m-4 0h8"/>'
};
const icon=name=>'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'+(paths[name]||paths.explore)+'</svg>';
const s={mission:createMissionState(),started:false,paused:false,narration:true,volume:.6,gentle:true,consent:false,hint:'',hintShown:false,voiceState:'idle'};
let generation=0,reminder=null,toastTimer=null,lastResponse=-Infinity,disposed=false;
let undoStack=[];
const cleanupShell=mountVehicleShell();
$('guide').innerHTML=icon('sound');
$('undo-button').innerHTML=icon('skip');
const voice=new MissionVoice({
  onState(value){s.voiceState=value;updateVoice();},
  onAction(action){if(!s.started||s.paused||document.hidden||document.querySelector('dialog[open]'))return;handleAction(action);},
  onError(message){toast(message);updateVoice();},
  onText(){}
});
function toast(message){
  if(disposed)return;
  clearTimeout(toastTimer);$('status').textContent=message;$('status').classList.add('show');
  toastTimer=setTimeout(()=>$('status').classList.remove('show'),4500);
}
function clearReminder(){clearTimeout(reminder);reminder=null;}
function active(){return s.started&&!s.paused&&!document.hidden&&!document.querySelector('dialog[open]')&&!disposed;}
function updateVoice(){
  const listening=['starting','listening'].includes(s.voiceState);
  const label=$('mission-state');
  if(label)label.textContent=s.paused?'준비되면 다시 시작해':s.voiceState==='speaking'?'미션을 듣고 있어':listening?'말로 알려줘도 좋아':'천천히 창밖을 살펴봐';
  $('voice-wave')?.classList.toggle('active',listening||s.voiceState==='speaking');
  const button=$('voice-button');
  if(button){button.setAttribute('aria-pressed',String(voice.enabled));button.innerHTML=icon('mic')+(voice.enabled?'말로 알림 켜짐':'말로 알려주기');}
  $('parent-voice').textContent=voice.enabled?'음성 알림 끄기':'음성 알림 켜기';
}
function renderContext(){
  const stamps=s.mission.discoveries.slice(-3);
  $('scene-panel').innerHTML='<div class="context-brand"><div class="context-logo">'+icon('explore')+'</div><div><strong>창밖 탐험</strong><p>밖에서 발견하는 작은 즐거움</p></div></div>'+
    '<div class="context-main"><div class="context-window-wrap"><div class="window-art" aria-hidden="true">'+icon('explore')+'</div><div class="context-buddy">'+mascot()+'</div></div><div class="context-caption"><strong>진짜 창밖에서<br>찾아볼까?</strong><p>미션은 두리가 읽어줄게</p></div></div>'+
    '<div class="discovery-tray" aria-label="내가 발견했다고 알려준 것">'+stamps.map(item=>'<div class="discovery-stamp"><div>'+icon(item.theme)+'</div><span>'+esc(item.label)+'</span></div>').join('')+'</div>'+
    '<div class="context-footer"><span><i></i>듣기</span><span><i></i>살피기</span><span><i></i>발견</span></div>';
}
function render(){
  renderContext();
  const m=s.mission.current;
  const heading=!s.started?'창밖으로 탐험 갈까?':s.paused?'잠깐 쉬어가자':m.kind==='detail'?'조금 더 살펴볼까?':m.kind==='change'?'풍경이 달라질까?':'이번엔 무엇을 찾을까?';
  let content='';
  if(!s.started){
    content='<section class="mission-card"><div class="welcome-symbols" aria-hidden="true"><span>'+icon('sound')+'</span><span>'+icon('explore')+'</span><span>'+icon('check')+'</span></div><p class="mission-sentence">미션을 듣고,<br>창밖에서 하나씩 찾아봐.</p><p class="quiet-note">없어도 괜찮아. 다른 걸 찾으면 돼.</p></section><button id="start-button" class="button primary mission-primary" type="button">'+icon('play')+'탐험 시작</button><button id="voice-button" class="text-button" type="button" aria-pressed="false">'+icon('mic')+'말로 알려주기</button>';
  }else if(s.paused){
    content='<section class="mission-card"><div class="pause-art">'+icon('pause')+'</div><p class="mission-sentence">준비되면<br>이어서 살펴보자.</p><p class="quiet-note">마이크와 미션이 쉬고 있어.</p></section><button id="resume-button" class="button primary mission-primary" type="button">'+icon('play')+'이어서 살펴보기</button>';
  }else{
    const mainLabel=m.kind==='detail'?'봤어':m.kind==='change'?'달라졌어':'찾았어';
    const primary='<button id="found-button" class="button primary mission-primary" type="button">'+icon('check')+mainLabel+'</button>';
    content='<section class="mission-card" aria-label="현재 관찰 미션"><div class="mission-card-top"><span class="mission-kind">'+icon(m.theme)+esc(m.label)+'</span><span class="mission-badge">창밖에서</span></div><p class="mission-sentence" aria-live="polite" aria-atomic="true">'+esc(m.prompt)+'</p><div class="mission-card-footer"><span><i class="state-dot"></i><span id="mission-state"></span></span><div id="voice-wave" class="voice-wave" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div></div></section>'+
      (m.kind==='change'?'<div class="action-pair">'+primary+'<button id="same-button" class="button secondary" type="button">비슷해</button></div>':primary)+
      '<div class="mission-secondary"><button id="skip-button" class="button secondary" type="button">'+icon('skip')+'안 보여</button><button id="changed-button" class="button secondary" type="button">'+icon('next')+'지나갔어</button></div>'+
      '<div class="mission-tools"><button id="hint-button" class="text-button" type="button">'+icon('hint')+'힌트</button><button id="voice-button" class="text-button" type="button" aria-pressed="'+voice.enabled+'">'+icon('mic')+'말로 알려주기</button><button id="pause-button" class="text-button" type="button">'+icon('pause')+'쉬기</button></div>'+
      (s.hint?'<p class="hint-note" role="status">'+esc(s.hint)+'</p>':'');
  }
  $('app').innerHTML='<div class="mission-stage"><div class="mission-heading"><div class="mission-logo">'+icon(s.paused?'pause':'explore')+'</div><div><p class="mission-eyebrow">눈으로 발견하는 작은 모험</p><h1>'+heading+'</h1></div></div>'+content+'</div>';
  $('start-button')?.addEventListener('click',start);
  $('resume-button')?.addEventListener('click',resume);
  $('found-button')?.addEventListener('click',()=>respond('found'));
  $('same-button')?.addEventListener('click',()=>respond('same'));
  $('skip-button')?.addEventListener('click',()=>respond('skip'));
  $('changed-button')?.addEventListener('click',()=>respond('changed'));
  $('hint-button')?.addEventListener('click',showHint);
  $('pause-button')?.addEventListener('click',pause);
  $('voice-button')?.addEventListener('click',toggleVoice);
  updateVoice();
  $('guide').disabled=s.started&&s.paused;
  $('undo-button').disabled=!undoStack.length||s.paused;
}
function scheduleReminder(run){
  clearReminder();
  if(!active()||!s.gentle||s.hintShown)return;
  reminder=setTimeout(()=>{
    if(run!==generation||!active())return;
    s.hintShown=true;s.hint=s.mission.current.hint;render();
    voice.say(s.hint,{enabled:s.narration,volume:s.volume});
  },25000);
}
async function announce(prefix=''){
  clearReminder();const run=++generation;
  const line=prefix+(s.mission.current?.prompt||'미션을 듣고 창밖에서 하나씩 찾아볼까?');
  await voice.say(line,{enabled:s.narration,volume:s.volume});
  if(run===generation&&active())scheduleReminder(run);
}
function start(){
  if(s.started){resume();return;}
  s.mission=advanceMission(s.mission,'start');s.started=true;s.paused=false;s.hint='';s.hintShown=false;
  render();announce();
}
function resume(){
  s.paused=false;render();announce('이어서 살펴보자. ');
}
function pause(){
  generation++;clearReminder();voice.suspend();s.paused=true;render();
}
function respond(result){
  if(!active())return;
  const now=performance.now();if(now-lastResponse<650)return;
  if(result==='same'&&s.mission.current.kind!=='change'){toast('찾았거나 봤다면 “봤어”라고 알려줘.');return;}
  lastResponse=now;
  const oldKind=s.mission.current.kind;
  undoStack.push(s.mission);if(undoStack.length>10)undoStack.shift();
  s.mission=advanceMission(s.mission,result);s.hint='';s.hintShown=false;
  const prefix=result==='skip'?'안 보여도 괜찮아. ':result==='changed'?'지금 보이는 걸로 해보자. ':result==='same'?'비슷하게 이어지는구나. ':oldKind==='detail'?'살펴봤구나. ':oldKind==='change'?'달라진 걸 알아챘구나. ':'찾았구나. ';
  render();announce(prefix);
}
function showHint(){
  if(!active())return;
  clearReminder();s.hintShown=true;s.hint=s.mission.current.hint;generation++;render();
  voice.say(s.hint,{enabled:s.narration,volume:s.volume});
}
function handleAction(action){
  if(action==='undo'){undo();return;}
  if(action==='pause'){pause();return;}
  if(action==='hint'){showHint();return;}
  if(action==='repeat'){announce();return;}
  if(action==='different'){respond(s.mission.current.kind==='change'?'found':'changed');return;}
  if(['found','skip','same','changed'].includes(action))respond(action);
}
function undo(){
  if(!active()||!undoStack.length)return;
  s.mission=undoStack.pop();s.hint='';s.hintShown=false;lastResponse=performance.now();
  render();announce('괜찮아. 다시 살펴보자. ');
}
function toggleVoice(){
  if(voice.enabled){voice.suspend();updateVoice();return;}
  if(!voice.supportedRecognition){toast('이 브라우저에서는 버튼으로 알려주세요. 미션은 소리로 들을 수 있어요.');return;}
  if(!s.consent){pause();$('voice-dialog').showModal();return;}
  activateVoice();
}
function activateVoice(){
  if(!s.started){s.mission=advanceMission(s.mission,'start');s.started=true;}
  s.paused=false;render();
  announce('찾았거나 안 보이면 말로 알려줘. ');
  voice.enable();updateVoice();
}
function syncParent(){
  $('context-options').innerHTML=CONTEXTS.map(context=>'<button type="button" class="context-option" data-context="'+context.id+'" aria-pressed="'+(context.id===s.mission.context)+'">'+esc(context.label)+'</button>').join('');
  document.querySelectorAll('[data-context]').forEach(button=>button.addEventListener('click',()=>{
    if(s.started)s.mission=setMissionContext(s.mission,button.dataset.context);
    else s.mission=createMissionState(button.dataset.context);
    undoStack=[];
    s.hint='';s.hintShown=false;syncParent();render();
  }));
  const outcomes={found:'발견했다고 알려줌',same:'비슷하다고 알려줌',skip:'다른 미션으로',changed:'지나간 뒤 다음 미션'};
  $('mission-log').innerHTML=s.mission.history.length?s.mission.history.slice(-6).reverse().map(entry=>'<p><span>'+esc(entry.label)+'</span><small>'+esc(outcomes[entry.result]||'다음 관찰')+'</small></p>').join(''):'<span>탐험을 시작하면 아이가 알려준 결과가 여기에 남아요.</span>';
  $('voice-support-note').textContent=voice.supportedRecognition?'음성 인식은 브라우저와 주변 소음에 따라 달라집니다.':'이 브라우저는 음성 알림을 지원하지 않아요. 버튼으로 알려주세요.';
  $('parent-voice').disabled=!voice.supportedRecognition;updateVoice();
}
$('guide').addEventListener('click',()=>{
  if(s.started&&s.paused)return;
  if(s.started)announce();else voice.say('미션을 듣고 창밖에서 하나씩 찾아볼까?',{enabled:s.narration,volume:s.volume});
});
$('undo-button').addEventListener('click',undo);
$('parent-button').addEventListener('click',()=>{pause();syncParent();$('parent-dialog').showModal();});
for(const [button,dialog] of [['parent-close','parent-dialog'],['parent-done','parent-dialog'],['voice-close','voice-dialog']])$(button).addEventListener('click',()=>$(dialog).close());
$('voice-enable').addEventListener('click',()=>{s.consent=true;$('voice-dialog').close();activateVoice();});
$('parent-voice').addEventListener('click',()=>{$('parent-dialog').close();toggleVoice();});
$('narration').addEventListener('change',event=>{s.narration=event.target.checked;});
$('volume').addEventListener('input',event=>{s.volume=Number(event.target.value);});
$('gentle-hint').addEventListener('change',event=>{s.gentle=event.target.checked;});
$('reset-missions').addEventListener('click',()=>{
  pause();s.mission=createMissionState(s.mission.context);s.started=false;s.paused=false;s.hint='';s.hintShown=false;lastResponse=-Infinity;undoStack=[];
  $('parent-dialog').close();render();
});
document.addEventListener('visibilitychange',()=>{if(document.hidden){pause();}});
window.addEventListener('pagehide',event=>{pause();clearTimeout(toastTimer);if(event.persisted)return;disposed=true;voice.dispose();cleanupShell?.();});
render();syncParent();

