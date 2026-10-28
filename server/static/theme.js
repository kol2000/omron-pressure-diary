'use strict';
(()=>{
 const system=window.matchMedia('(prefers-color-scheme: dark)');
 function saved(){try{return localStorage.getItem('diary-theme');}catch{return null;}}
 function apply(theme,persist=false){
  const selected=theme==='dark'?'dark':'light';
  document.documentElement.dataset.theme=selected;
  if(persist){try{localStorage.setItem('diary-theme',selected);}catch{}}
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content',selected==='dark'?'#17231f':'#126b60');
  for(const id of ['theme-toggle','login-theme-toggle']){
   const button=document.getElementById(id);if(!button)continue;
   button.textContent=selected==='dark'?'☀ Светлая тема':'☾ Тёмная тема';
   button.setAttribute('aria-pressed',String(selected==='dark'));
   button.setAttribute('aria-label',selected==='dark'?'Включить светлую тему':'Включить тёмную тему');
  }
  window.dispatchEvent(new Event('diary-theme-change'));
 }
 const initial=saved();apply(initial==='dark'||initial==='light'?initial:system.matches?'dark':'light');
 system.addEventListener('change',()=>{if(!saved())apply(system.matches?'dark':'light');});
 document.addEventListener('DOMContentLoaded',()=>{
  apply(document.documentElement.dataset.theme);
  for(const id of ['theme-toggle','login-theme-toggle'])document.getElementById(id)?.addEventListener('click',()=>apply(document.documentElement.dataset.theme==='dark'?'light':'dark',true));
 });
})();
