// Deterministic patch: original index.html -> v10 index.html. Each anchor must match exactly once.
const fs=require('fs');
const [src,out]=process.argv.slice(2);
let h=fs.readFileSync(src,'utf8');
function rep(a,b,n=1){const c=h.split(a).length-1;if(c!==n)throw new Error('anchor count '+c+' (want '+n+'): '+a.slice(0,90));h=h.split(a).join(b);}
/* ---- 1/2: no top-menu layout; bottom bar only (search overlay works without it) ---- */
rep("try{if(localStorage.getItem('annoor_tabnav_v6')!=='0')document.documentElement.setAttribute('data-an6nav','1')}catch(e){}",
 "try{localStorage.setItem('annoor_tabnav_v6','0');document.documentElement.removeAttribute('data-an6nav')}catch(e){}");
rep("</style>\n  </head>",
`/* v10: search works with the bottom bar only */
#an5-bar.srch{display:flex;position:fixed;top:0;left:0;right:0;z-index:99991;height:calc(56px + env(safe-area-inset-top));padding:env(safe-area-inset-top) 6px 0 10px;box-sizing:border-box;align-items:center;justify-content:space-between;background:var(--bg-header);border-bottom:1px solid var(--border-subtle)}
#an5-res{display:block;position:fixed;left:0;right:0;top:calc(56px + env(safe-area-inset-top));bottom:0;z-index:99990;overflow:auto;background:var(--bg-app);padding:4px 10px calc(12px + env(safe-area-inset-bottom))}
#an5-bar.srch #an5-dots{display:flex;align-items:center;justify-content:center}
</style>
  </head>`);
/* ---- 3/4/6: settings overlay cards ---- */
rep(`<div class="dayb"><span>Shop open / close &amp; day history</span><button type="button" class="an5b" data-d="1">Open</button></div>`,'');
rep("card.querySelector('[data-d]').onclick=openSheet;","");
rep("if(!h2){if(card)card.remove();return}","if(true){if(card)card.remove();return}"); // removes Company card (Manage companies + top-menu toggle) from Settings
rep("window.__an6x={readState:","window.__anCo=openCo;window.__an6x={readState:");
/* ---- 4: shop day: mid-day closes + clearer history ---- */
rep("function openSheet(){",
`function brkTxt(x){var b=x&&x.breaks;if(!b||!b.length)return'';return'<br>Breaks: '+b.map(function(k){return f12(k.from)+(k.to?' \\u2013 '+f12(k.to):' (open)')}).join(', ')}
function breaksUI(d){if(d.leave||d.closed||!d.open)return'';var b=d.breaks||[],last=b[b.length-1],on=last&&!last.to;
 var h='<div class="hist" style="margin-top:10px"><div style="font-size:11px;font-weight:800;color:var(--text-secondary);text-transform:uppercase;letter-spacing:.05em;margin-bottom:4px">Mid-day closes</div>';
 h+=b.map(function(k){return'<div class="hrow"><span>Closed '+f12(k.from)+'</span><span>'+(k.to?'Reopened '+f12(k.to):'<span class="badge y">still closed</span>')+'</span></div>'}).join('');
 h+=on?'<button class="an5b pri wide" id="an5bkend">Reopen shop now</button>':'<button class="an5b wide" id="an5bk">Close shop for a break</button>';
 return h+'</div>'}
function openSheet(){`);
rep("+'<div class=\"hist\"><div style=\"font-size:11px;font-weight:800;color:var(--text-secondary);text-transform:uppercase;letter-spacing:.05em;margin-bottom:4px\">History</div>';",
    "+breaksUI(d)+'<div class=\"hist\"><div style=\"font-size:11px;font-weight:800;color:var(--text-secondary);text-transform:uppercase;letter-spacing:.05em;margin-bottom:4px\">Open / close history</div>';");
rep("(hrs(x)?' \\u00B7 '+hrs(x):'')+","(hrs(x)?' \\u00B7 '+hrs(x):'')+brkTxt(x)+");
rep("if($('an5re'))$('an5re').onclick=function(){setDay(t,{closed:false});draw()};",
 "if($('an5re'))$('an5re').onclick=function(){setDay(t,{closed:false});draw()};\n   if($('an5bk'))$('an5bk').onclick=function(){var c=dayOf(t),b=(c.breaks||[]).slice();b.push({from:nowT(),fromTs:Date.now()});setDay(t,{breaks:b});draw();render()};\n   if($('an5bkend'))$('an5bkend').onclick=function(){var c=dayOf(t),b=(c.breaks||[]).slice(),k=b[b.length-1];if(k&&!k.to){k.to=nowT();k.toTs=Date.now()}setDay(t,{breaks:b});draw();render()};");
/* ---- React: Quick Create sheet ---- */
rep(`o=t||(e?e.serviceCreate:!0),s=`,`o=(t||(e?e.serviceCreate:!0))&&window.__anSvc(),s=`);
rep(`icon:Is,accentClass:"text-emerald-400 bg-emerald-500/15 border-emerald-500/40"}]:[]];if(u.length===0)return null`,
 `icon:Is,accentClass:"text-emerald-400 bg-emerald-500/15 border-emerald-500/40"}]:[],...(t||(e?e.stockEdit:!1)?[{id:"new-item",label:"Add Stock",subtitle:"Add SKU or update qty",icon:ri,accentClass:"text-violet-400 bg-violet-500/15 border-violet-500/40"}]:[]),{id:"shop-day",label:"Shop Day",subtitle:"Open, close & history",icon:Ui,accentClass:"text-cyan-400 bg-cyan-500/15 border-cyan-500/40"}];{let __o={"new-sale":0,"new-purchase":1,"new-item":2,"new-repair":3,"new-expense":4,"shop-day":5};u.sort((p,q)=>__o[p.id]-__o[q.id])}if(u.length===0)return null`);
rep(`U==="new-item"&&(me||je.stockEdit)&&(Pe(U),At("items"))},hl=`,`U==="new-item"&&(me||je.stockEdit)?(Pe(U),At("items")):U==="shop-day"&&window.__an5&&window.__an5.openSheet()},hl=`);
/* ---- React: menu (All Shop Modules) gets Shop Day & Manage Companies ---- */
rep(`Ia=t.settings.language||"en",at=[{id:"dashboard"`,`Ia=t.settings.language||"en",__bt=(window.__anSvcOn=(()=>{const b=t.settings.businessTypes;return!Array.isArray(b)||b.length===0||b.includes("mobile")||b.includes("repair")})()),at=[{id:"dashboard"`);
rep(`{id:"settings",label:ql(Ia,"settings"),icon:bw}].filter(U=>zt(U.id)),Tt=`,`{id:"settings",label:ql(Ia,"settings"),icon:bw},{id:"__day",label:"Shop Day",icon:Ui},...(me?[{id:"__co",label:"Companies",icon:lw}]:[])].filter(U=>U.id[0]==="_"||zt(U.id)),Tt=`);
rep(`aa=U=>{zt(U)&&(At(U),`,`aa=U=>{if(U==="__day"){et(!1),window.__an5&&window.__an5.openSheet();return}if(U==="__co"){et(!1),window.__anCo&&window.__anCo();return}zt(U)&&(At(U),`);
rep(`zt=(0,$t.useCallback)(U=>{if(me)return!0`,`zt=(0,$t.useCallback)(U=>{if((U==="repairs"||U==="service_status")&&!window.__anSvc())return!1;if(me)return!0`);
rep(`(u||c.serviceCreate)&&(0,se.jsxs)("button",{type:"button",onClick:()=>n("new-repair")`,`(u||c.serviceCreate)&&window.__anSvc()&&(0,se.jsxs)("button",{type:"button",onClick:()=>n("new-repair")`);
/* ---- React: welcome card removed ---- */
rep(`ve&&(0,se.jsxs)("section",{className:"glass-card-glow p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4",children:[(0,se.jsxs)("div",{children:[(0,se.jsxs)("h2",{className:"text-base font-extrabold text-[var(--text-primary)]",children:["Welcome to "`,`!1&&(0,se.jsxs)("section",{className:"glass-card-glow p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4",children:[(0,se.jsxs)("div",{children:[(0,se.jsxs)("h2",{className:"text-base font-extrabold text-[var(--text-primary)]",children:["Welcome to "`);
/* ---- React: Settings page ---- */
rep(`,{id:"debug",label:"Debug",icon:sw}].map(`,`].map(`);
rep(`className:"sticky-search-shell p-2 flex items-center gap-2 overflow-x-auto",children:[{id:"shop"`,`className:"grid grid-cols-2 gap-2.5 p-2.5 rounded-3xl bg-[var(--bg-card)] border border-[var(--border-subtle)]",children:[{id:"shop"`);
rep("className:`min-h-[46px] px-4 py-2 rounded-2xl text-xs font-bold flex items-center gap-2 whitespace-nowrap shrink-0 transition-all ${vt?","className:`min-h-[68px] px-3 py-3 rounded-2xl text-xs font-extrabold flex flex-col items-center justify-center gap-1.5 text-center transition-all ${vt?");
rep(`(0,K.jsxs)("div",{className:"glass-card p-5 flex items-start gap-3",children:[(0,K.jsx)("div",{className:"w-10 h-10 rounded-2xl bg-emerald-500/15 flex items-center justify-center shrink-0"`,`!1&&(0,K.jsxs)("div",{className:"glass-card p-5 flex items-start gap-3",children:[(0,K.jsx)("div",{className:"w-10 h-10 rounded-2xl bg-emerald-500/15 flex items-center justify-center shrink-0"`);
const BT=`[{id:"mobile",label:"Mobile shop"},{id:"footwear",label:"Footwear / \\u0B9A\\u0BAA\\u0BCD\\u0BAA\\u0BB2\\u0BCD"},{id:"bag",label:"Bag shop"},{id:"clothing",label:"Clothing"},{id:"electronics",label:"Electronics"},{id:"general",label:"General store"},{id:"repair",label:"Repair / service only"},{id:"other",label:"Other"}]`;
rep(`E==="shop"&&(0,K.jsxs)("div",{className:"space-y-4",children:[`,`E==="shop"&&(0,K.jsxs)("div",{className:"space-y-4",children:[(0,K.jsxs)("div",{className:"glass-card p-5 space-y-3",children:[(0,K.jsx)("h3",{className:"text-sm font-bold text-[var(--text-primary)]",children:"Business Type"}),(0,K.jsx)("p",{className:"text-xs text-[var(--text-muted)] leading-relaxed",children:"Select all that apply. Features adapt to your business: Service & Repairs shows for Mobile shops (and Repair / service). Footwear and bag shops do not get it."}),(0,K.jsx)("div",{className:"flex flex-wrap gap-2",children:${BT}.map(__b=>{const __cur=Array.isArray(t.settings.businessTypes)&&t.settings.businessTypes.length?t.settings.businessTypes:["mobile"],__on=__cur.includes(__b.id);return(0,K.jsx)("button",{type:"button",onClick:()=>{const __n=__on?__cur.filter(z=>z!==__b.id):[...__cur,__b.id];ot({businessTypes:__n.length?__n:["other"]})},className:"min-h-[44px] px-4 rounded-2xl border text-xs font-bold transition-all "+(__on?"accent-btn text-white border-transparent":"bg-[var(--bg-input)] border-[var(--border-subtle)] text-[var(--text-primary)]"),children:(__on?"\\u2713 ":"")+__b.label},__b.id)})})]}),`);
rep("<head><script>window.__anNo=","<head><script>window.__anSvc=function(){return window.__anSvcOn!==false};window.__anNo=");

/* ================= multi-shop (email login) ================= */
// shop context at boot: which shop (owner email) this device is working in. '' = the original shop.
rep("<head><script>window.__anSvc=","<head><script>(function(){var E=[];function add(m){E.push(String(m).slice(0,400));if(E.length>6)E.shift()}window.addEventListener('error',function(e){add((e.message||'error')+' @'+(e.filename||'').split('/').pop()+':'+e.lineno+':'+e.colno)});window.addEventListener('unhandledrejection',function(e){add('promise: '+(e.reason&&(e.reason.message||e.reason)))});function show(why){if(document.getElementById('an-diag'))return;var d=document.createElement('div');d.id='an-diag';d.style.cssText='position:fixed;inset:0;z-index:2147483647;background:#000;color:#fff;font:13px/1.4 monospace;padding:16px;overflow:auto';d.innerHTML='<div style=\\\"color:#FF1F8E;font-weight:800;font-size:16px\\\">Noor Software could not start</div><div style=\\\"margin:8px 0\\\">'+why+'</div><pre style=\\\"white-space:pre-wrap;color:#fbbf24\\\">'+(E.join('\\\\n')||'no script errors captured').replace(/</g,'&lt;')+'</pre><div style=\\\"margin-top:12px;color:#aaa\\\">Send a screenshot of this screen. Tap Reload to retry.</div><button onclick=\\\"location.reload()\\\" style=\\\"margin-top:10px;padding:12px 20px;background:#FF1F8E;color:#fff;border:0;border-radius:12px;font-weight:800\\\">Reload</button>';(document.body||document.documentElement).appendChild(d)}window.__anDiagShow=show;setTimeout(function(){var r=document.getElementById('root');var ok=r&&r.children.length>0&&(document.body.innerText||'').trim().length>20;if(!ok)show('Screen stayed empty for 12 seconds.')},12000);window.addEventListener('error',function(){setTimeout(function(){var r=document.getElementById('root');if(!(r&&r.children.length>0))show('A script error stopped startup.')},800)})})();(function(){var S='';try{S=(localStorage.getItem('noor_shop_v10')||'').toLowerCase()}catch(e){}function sk(e){return String(e||'').toLowerCase().split('@').join('-at-').split('.').join('-')}window.__ANSHOP=S;window.__anSK=sk;window.__ANSP=S?'__s-'+sk(S):'';window.__anSwitch=function(o){try{localStorage.setItem('noor_shop_v10',o||'')}catch(e){}setTimeout(function(){location.reload()},60);return new Promise(function(){})}})();window.__anSvc=");
// per-shop local registry / database suffix
rep("(function(){var R=null;try{R=JSON.parse(localStorage.getItem('annoor_companies_v6'))}catch(e){}","(function(){var SP=window.__ANSP||'',RK='annoor_companies_v6'+SP,R=null;try{R=JSON.parse(localStorage.getItem(RK))}catch(e){}");
rep("try{var s=JSON.parse(localStorage.getItem('annoor_msp_backup_v1')||'{}');nm=","try{var s=JSON.parse(localStorage.getItem('annoor_msp_backup_v1'+SP)||'{}');nm=");
rep("R={active:'c0',list:[{id:'c0',name:nm,sfx:''}]};try{localStorage.setItem('annoor_companies_v6',JSON.stringify(R))}catch(e){}}","R={active:'c0',list:[{id:'c0',name:nm,sfx:SP}]};try{localStorage.setItem(RK,JSON.stringify(R))}catch(e){}}");
rep("REG='annoor_companies_v6',","REG='annoor_companies_v6'+(window.__ANSP||''),");
rep("sfx='__'+id;seedDb(","sfx=(window.__ANSP||'')+'__'+id;seedDb(");
// bundle: owner email is per shop
rep('var f4=1,jr="tokiyotokiyo143@gmail.com",','var f4=1,LEG="tokiyotokiyo143@gmail.com",jr=window.__ANSHOP||LEG,__ns=c=>c==="members"||jr===LEG?c:c+"__"+window.__anSK(jr),');
rep('PN={shopName:"Noor Software",','PN={shopName:(()=>{try{return localStorage.getItem("noor_newshop_name_"+jr)||"Noor Software"}catch(e){return"Noor Software"}})(),');
// namespaced Firestore collections
rep('jv(s,y)','jv(s,__ns(y))',3);
rep('ah(s,"shopConfig","main")','ah(s,__ns("shopConfig"),"main")');
rep('ah(s,"ownerCosts","main")','ah(s,__ns("ownerCosts"),"main")');
rep('ah(n,g.col,g.docId)','ah(n,__ns(g.col),g.docId)');
rep('ah(s,"users",an(o))','ah(s,__ns("users"),an(o))');
rep('await Zee(ah(e,"users",r))','await Zee(ah(e,__ns("users"),r)),await Zee(ah(e,"members",window.__anSK(jr)+"__"+r)).catch(()=>{})');
// members directory (lets staff / co-owners find their shop at login)
rep('c("users",A,{id:A,authUid:E,email:C,name:S,role:I,enabled:w,permissions:B,ownerEmail:jr,updatedByEmail:i,createdAt:T,updatedAt:G})}',
    'c("users",A,{id:A,authUid:E,email:C,name:S,role:I,enabled:w,permissions:B,ownerEmail:jr,updatedByEmail:i,createdAt:T,updatedAt:G}),C!==jr&&E&&c("members",window.__anSK(jr)+"__"+A,{id:window.__anSK(jr)+"__"+A,authUid:E,email:C,role:I,enabled:w,ownerEmail:jr,updatedByEmail:i})}');
rep('for(let b of ih("users"))y.has(an(b))||u.push({kind:"del",path:`users/${b}`,col:"users",docId:b})}','for(let b of ih("users"))y.has(an(b))||(u.push({kind:"del",path:`users/${b}`,col:"users",docId:b}),u.push({kind:"del",path:`members/${window.__anSK(jr)}__${an(b)}`,col:"members",docId:`${window.__anSK(jr)}__${an(b)}`}))}');
// login: resolve which shop this account belongs to
rep('if(!r)return{authorized:!1,isOwner:!1,uid:a,email:"",emailVerified:t.emailVerified,shopUser:null,offlineFallback:!1,reason:"Signed-in account has no email address."};if(r===jr)',
    'if(!r)return{authorized:!1,isOwner:!1,uid:a,email:"",emailVerified:t.emailVerified,shopUser:null,offlineFallback:!1,reason:"Signed-in account has no email address."};if(r===LEG&&jr!==LEG)return await window.__anSwitch("");let __mem=null;if(r!==jr&&navigator.onLine){try{let __q=await i6(dB(jv(gI(),"members"),fB("authUid","==",a)));if(__q.empty&&t.emailVerified)__q=await i6(dB(jv(gI(),"members"),fB("email","==",r)));if(!__q.empty){__mem=__q.docs[0].data();if(__mem.enabled!==!1&&__mem.ownerEmail&&Mr(__mem.ownerEmail)!==jr)return await window.__anSwitch(Mr(__mem.ownerEmail)===LEG?"":Mr(__mem.ownerEmail))}}catch(__e){}}if(r===jr)');
rep('offlineFallback:!0}}return{authorized:!1,isOwner:!1,uid:a,email:r,emailVerified:t.emailVerified,shopUser:null,offlineFallback:!1,reason:`Account "${r}" is not added in Shop Settings','offlineFallback:!0}}if(!__mem&&navigator.onLine)return await window.__anSwitch(r);return{authorized:!1,isOwner:!1,uid:a,email:r,emailVerified:t.emailVerified,shopUser:null,offlineFallback:!1,reason:`Account "${r}" is not added in Shop Settings');
// expose sign-up for the login screen
rep('function nte(t){let e=ate();return EF(e,t)}','function nte(t){let e=ate();return EF(e,t)}window.__anAuth={signUp:async(e,p)=>{let{auth:a}=oh(),m=Mr(e);await bF(a,m,p);try{await CF(a.currentUser)}catch(x){}return m}};');

/* signup UI script */
rep('</body>','<script>'+fs.readFileSync(__dirname+'/signup-ui.js','utf8')+'</script></body>');
fs.writeFileSync(out,h);console.log('ok',h.length);
