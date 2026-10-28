'use strict';
const $=id=>document.getElementById(id), NS='http://www.w3.org/2000/svg';
let csrf='',report=null,currentId='',frozen=null,previewUrl='',loading=false;
const colors={systolic:'#268b79',diastolic:'#c5a363',pulse:'#8199b8'};
function localDate(d){return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
function dateTime(d){return `${localDate(d)}T${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`;}
function formatDate(value,options={day:'numeric',month:'short'}){return new Date(value).toLocaleDateString('ru-RU',{...options,timeZone:report?.timezone||'Europe/Moscow'});}
function formatNumber(n){return n==null?'—':Number(n).toLocaleString('ru-RU',{maximumFractionDigits:1});}
function toast(message){$('toast').textContent=message;$('toast').hidden=false;setTimeout(()=>$('toast').hidden=true,6000);}
async function api(path,options={}){
 const headers={...(options.body instanceof Blob?{}:{'Content-Type':'application/json'}),'X-CSRF-Token':csrf,...options.headers};
 const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),210000);
 try{const response=await fetch(path,{...options,headers,signal:controller.signal,credentials:'same-origin'});
  const data=await response.json();if(!response.ok){if(response.status===401&&path!='/auth/login'){showLogin();}throw new Error(data.error||'Не удалось выполнить запрос');}return data;
 }finally{clearTimeout(timer);}
}
function showLogin(){$('app').hidden=true;$('login').hidden=false;report=null;$('rows').replaceChildren();}
async function init(){try{const state=await api('/auth/session');csrf=state.csrf||'';if(!state.authenticated){showLogin();return;}$('login').hidden=true;$('app').hidden=false;preset(30);await loadReport();}catch(e){showLogin();$('login-error').textContent='Сервер недоступен. Проверьте подключение.';}}
$('login-form').addEventListener('submit',async e=>{e.preventDefault();$('login-error').textContent='';const button=e.submitter;button.disabled=true;try{const state=await api('/auth/login',{method:'POST',body:JSON.stringify({token:$('token').value.trim()})});csrf=state.csrf;$('token').value='';await init();}catch(error){$('login-error').textContent=error.message;}finally{button.disabled=false;}});
$('logout').onclick=async()=>{try{await api('/auth/logout',{method:'POST',body:'{}'});sessionStorage.removeItem('omron-pending');showLogin();}catch(e){toast(e.message);}};
function preset(days){const end=new Date(),start=new Date();start.setDate(end.getDate()-days+1);$('from').value=localDate(start);$('to').value=localDate(end);document.querySelectorAll('[data-days]').forEach(b=>b.classList.toggle('selected',Number(b.dataset.days)===days));}
document.querySelectorAll('[data-days]').forEach(button=>button.onclick=()=>{preset(Number(button.dataset.days));loadReport();});
$('apply').onclick=()=>{document.querySelectorAll('[data-days]').forEach(b=>b.classList.remove('selected'));loadReport();};
async function loadReport(){if(loading)return;if(!$('from').value||!$('to').value){toast('Выберите обе даты');return;}loading=true;$('error').hidden=true;$('apply').disabled=true;
 try{report=await api(`/dashboard?from=${encodeURIComponent($('from').value)}&to=${encodeURIComponent($('to').value)}`);render();}catch(e){$('error').textContent=e.name==='AbortError'?'Время ожидания истекло. Повторите запрос.':e.message;$('error').hidden=false;}finally{loading=false;$('apply').disabled=false;}}
function svgEl(tag,attrs={},text){const el=document.createElementNS(NS,tag);for(const [k,v]of Object.entries(attrs))el.setAttribute(k,String(v));if(text!==undefined)el.textContent=text;return el;}
function chart(id,keys,spark=false){const svg=$(id);svg.replaceChildren();const rows=report.measurements;
 const theme=getComputedStyle(document.documentElement);
 for(const key of Object.keys(colors))colors[key]=theme.getPropertyValue('--plot-'+key).trim()||colors[key];
 const width=spark?100:Math.max(300,svg.parentElement.clientWidth),height=spark?38:svg.clientHeight||240;
 svg.setAttribute('viewBox',`0 0 ${width} ${height}`);if(!rows.length){if(!spark)svg.nextElementSibling.hidden=false;return;}if(!spark)svg.nextElementSibling.hidden=true;
 const margin=spark?{l:2,r:2,t:4,b:4}:{l:38,r:20,t:17,b:34};
 const times=rows.map(r=>new Date(r.measured_at).getTime()),first=times[0],last=times.at(-1);let low=Math.min(...rows.flatMap(r=>keys.map(k=>r[k]))),high=Math.max(...rows.flatMap(r=>keys.map(k=>r[k])));
 if(!spark){low=Math.max(0,Math.floor((low-8)/10)*10);high=Math.ceil((high+8)/10)*10;}else{low-=3;high+=3;}if(low===high)high+=10;
 const x=i=>margin.l+(last===first ? .5 : (times[i]-first)/(last-first))*(width-margin.l-margin.r),y=v=>height-margin.b-(v-low)/(high-low)*(height-margin.t-margin.b);
 if(!spark){for(let i=0;i<=4;i++){const value=low+(high-low)*i/4,py=y(value);svg.append(svgEl('line',{x1:margin.l,x2:width-margin.r,y1:py,y2:py,stroke:theme.getPropertyValue('--plot-grid').trim()||'#edf1eb','stroke-dasharray':'3 4'}));svg.append(svgEl('text',{x:margin.l-9,y:py+3,'text-anchor':'end'},Math.round(value)));}
  const indexes=[...new Set([0,Math.floor((rows.length-1)/2),rows.length-1])];indexes.forEach(i=>svg.append(svgEl('text',{x:x(i),y:height-10,'text-anchor':i===0?'start':i===rows.length-1?'end':'middle'},formatDate(rows[i].measured_at))));
 }
 keys.forEach(key=>{const points=rows.map((r,i)=>`${x(i)},${y(r[key])}`).join(' ');
  svg.append(svgEl('polyline',{points,fill:'none',stroke:colors[key],'stroke-width':spark?2:2.2,'stroke-linejoin':'round','stroke-linecap':'round'}));
  if(!spark)rows.forEach((r,i)=>{const dot=svgEl('circle',{cx:x(i),cy:y(r[key]),r:rows.length>100?2:3.5,fill:colors[key],stroke:theme.getPropertyValue('--plot-border').trim()||'white','stroke-width':1.5});dot.append(svgEl('title',{},`${formatDate(r.measured_at,{day:'numeric',month:'long',year:'numeric'})} ${new Date(r.measured_at).toLocaleTimeString('ru-RU',{timeZone:report.timezone})}: ${r[key]}`));svg.append(dot);});
 });
}
function render(){const rows=report.measurements,count=rows.length;
 $('period-title').textContent=`${formatDate(report.period.from+'T12:00:00',{day:'numeric',month:'long',year:'numeric'})} — ${formatDate(report.period.to+'T12:00:00',{day:'numeric',month:'long',year:'numeric'})}`;
 $('sync-status').textContent=!report.google_configured?'◷ Google: требуется подключение':report.pending_sync?`◷ Ожидают Google: ${report.pending_sync}`:'✓ Все записи синхронизированы';$('sync-status').classList.toggle('waiting',report.pending_sync>0||!report.google_configured);
 for(const [prefix,key]of [['sys','systolic'],['dia','diastolic'],['pulse','pulse']]){const s=report.summary[key];$(prefix+'-mean').textContent=formatNumber(s.mean);$(prefix+'-range').textContent=count?`Диапазон: ${s.min}–${s.max}`:'Среднее за период';chart(prefix+'-spark',[key],true);}
 $('count').textContent=count;const days=new Set(rows.map(r=>formatDate(r.measured_at,{day:'2-digit',month:'2-digit',year:'numeric'}))).size;$('days-count').textContent=`Дней с измерениями: ${days}`;
 $('journal-subtitle').textContent=`${count} измерений · время: ${report.timezone.replace('Europe/Moscow','Москва (UTC+3)')}`;
 $('printed-at').textContent=`Отчёт сформирован ${new Date().toLocaleString('ru-RU')} · Показатели внесены пользователем и не являются диагнозом.`;
 $('sheet-link').hidden=true;if(report.spreadsheet_url){try{const link=new URL(report.spreadsheet_url);if(link.protocol==='https:'&&link.hostname==='docs.google.com'){$('sheet-link').href=link.href;$('sheet-link').hidden=false;}}catch{}}
 $('rows').replaceChildren();for(const r of [...rows].reverse()){const tr=document.createElement('tr'),date=document.createElement('td');date.textContent=formatDate(r.measured_at,{day:'2-digit',month:'short',year:'numeric'});const time=document.createElement('span');time.className='time';time.textContent=new Date(r.measured_at).toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit',timeZone:report.timezone});date.append(time);tr.append(date);
  for(const [key,css]of [['systolic','sys-text'],['diastolic','dia-text'],['pulse','pulse-text']]){const td=document.createElement('td'),span=document.createElement('span');span.className=`reading ${css}`;span.textContent=r[key];td.append(span);tr.append(td);}
  const note=document.createElement('td');note.className='note-cell';note.textContent=r.note||'—';tr.append(note);const sync=document.createElement('td');sync.className='sync-cell';const status=document.createElement('span');status.className=r.synced?'sync-check':'sync-wait';status.textContent=r.synced?'✓ Сохранено':'◷ В очереди';sync.append(status);tr.append(sync);$('rows').append(tr);
 }
 $('empty').hidden=Boolean(count);chart('pressure-chart',['systolic','diastolic']);chart('pulse-chart',['pulse']);
}
window.addEventListener('diary-theme-change',()=>{if(report)render();});
let resizeTimer;window.addEventListener('resize',()=>{clearTimeout(resizeTimer);resizeTimer=setTimeout(()=>{if(report)render();},150);});
window.addEventListener('beforeprint',()=>{if(report)render();});window.addEventListener('afterprint',()=>{if(report)render();});
$('print').onclick=()=>window.print();
function csvValue(value){let s=String(value??'');if(/^[\s]*[=+@\-]/.test(s))s="'"+s;return '"'+s.replaceAll('"','""')+'"';}
$('csv').onclick=()=>{if(!report)return;const lines=[['Дата','Время','Верхнее','Нижнее','Пульс','Примечание']];for(const r of report.measurements)lines.push([formatDate(r.measured_at,{day:'2-digit',month:'2-digit',year:'numeric'}),new Date(r.measured_at).toLocaleTimeString('ru-RU',{timeZone:report.timezone}),r.systolic,r.diastolic,r.pulse,r.note]);const blob=new Blob(['\ufeff'+lines.map(row=>row.map(csvValue).join(';')).join('\r\n')],{type:'text/csv;charset=utf-8'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=`pressure-${report.period.from}-${report.period.to}.csv`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
function freezeFields(locked){for(const id of ['systolic','diastolic','pulse','measured','note','confirmed','photo'])$(id).disabled=locked;}
function newMeasurement(){frozen=null;currentId=crypto.randomUUID();$('measurement-form').reset();$('measured').value=dateTime(new Date());$('save-error').textContent='';$('recognition-status').textContent='';$('preview').hidden=true;freezeFields(false);$('save').textContent='Проверил — сохранить';
 try{const pending=JSON.parse(sessionStorage.getItem('omron-pending')||'null');if(pending){frozen=pending;currentId=pending.id;for(const key of ['systolic','diastolic','pulse','note'])$(key).value=pending[key];$('measured').value=dateTime(new Date(pending.measured_at));$('confirmed').checked=true;freezeFields(true);$('save').textContent='Повторить сохранение';$('save-error').textContent='Есть запись с неподтверждённым ответом сервера. Повтор безопасен: используется тот же ID.';}}catch{}
 $('measurement').showModal();}
$('new').onclick=newMeasurement;$('empty-new').onclick=newMeasurement;
$('close-dialog').onclick=()=>{$('measurement').close();if(previewUrl)URL.revokeObjectURL(previewUrl);};
$('photo').onchange=async()=>{const photo=$('photo').files[0];if(!photo)return;if(photo.size>10*1024*1024){$('recognition-status').textContent='Фото больше 10 МБ. Выберите меньший файл.';return;}if(previewUrl)URL.revokeObjectURL(previewUrl);previewUrl=URL.createObjectURL(photo);$('preview').src=previewUrl;$('preview').hidden=false;freezeFields(true);$('save').disabled=true;$('close-dialog').disabled=true;$('recognition-status').textContent='Распознаём экран… Это может занять до трёх минут.';
 try{const values=await api('/recognize',{method:'POST',body:photo,headers:{'Content-Type':'application/octet-stream'}});for(const key of ['systolic','diastolic','pulse'])$(key).value=values[key];$('confirmed').checked=false;$('recognition-status').textContent='Сверьте все три числа с фото. При необходимости исправьте.';}catch(e){$('recognition-status').textContent=e.name==='AbortError'?'Модель не успела ответить. Введите значения вручную.':e.message;}finally{freezeFields(false);$('save').disabled=false;$('close-dialog').disabled=false;}};
$('measurement-form').onsubmit=async e=>{e.preventDefault();$('save-error').textContent='';if(!frozen){const d=new Date($('measured').value);if(Number.isNaN(d.getTime()))return;const offset=-d.getTimezoneOffset(),sign=offset>=0?'+':'-',tz=sign+String(Math.floor(Math.abs(offset)/60)).padStart(2,'0')+':'+String(Math.abs(offset)%60).padStart(2,'0');frozen={id:currentId,systolic:Number($('systolic').value),diastolic:Number($('diastolic').value),pulse:Number($('pulse').value),measured_at:dateTime(d)+tz,note:$('note').value,confirmed:$('confirmed').checked};if(frozen.systolic<=frozen.diastolic){frozen=null;$('save-error').textContent='Проверьте верхнее и нижнее давление';return;}sessionStorage.setItem('omron-pending',JSON.stringify(frozen));}
 freezeFields(true);$('save').disabled=true;$('close-dialog').disabled=true;
 try{const result=await api('/measurements',{method:'POST',body:JSON.stringify(frozen)});if(!result.saved)throw new Error('Сервер не подтвердил сохранение');sessionStorage.removeItem('omron-pending');frozen=null;$('measurement').close();toast(result.synced?'Сохранено на сервере и в Google':'Сохранено. Google Таблица обновится автоматически.');await loadReport();}catch(e){$('save-error').textContent=e.name==='AbortError'?'Ответ не получен. Повторите сохранение с тем же ID.':e.message;$('save').textContent='Повторить сохранение';}finally{$('save').disabled=false;$('close-dialog').disabled=false;}};
setInterval(()=>{if(report&&!document.hidden&&!$('measurement').open)loadReport();},60000);
init();
