/* v10: "Create shop account" on the login screen. Each shop owner signs up with their own email; their shop data is separate. */
(function(){
function esc(x){return String(x==null?'':x).replace(/[&<>"]/g,function(c){return{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]})}
var css='#nsu-link{display:block;width:100%;margin:12px 0 0;padding:12px;border-radius:14px;border:1px solid #FF1F8E66;background:transparent;color:#FF1F8E;font-weight:800;font-size:13px}'
+'#nsu{position:fixed;inset:0;z-index:2147483000;background:rgba(0,0,0,.78);display:flex;align-items:center;justify-content:center;padding:16px}'
+'#nsu .p{width:100%;max-width:26rem;background:var(--bg-card-solid,#14141a);border:1px solid #FF1F8E55;border-radius:20px;padding:20px;color:var(--text-primary,#fff);font-family:inherit}'
+'#nsu h3{margin:0 0 4px;font-size:18px;font-weight:900}#nsu .s{font-size:12px;color:var(--text-muted,#999);margin-bottom:12px;line-height:1.4}'
+'#nsu label{display:block;font-size:11px;font-weight:800;color:var(--text-secondary,#aaa);margin:10px 0 4px;text-transform:uppercase}'
+'#nsu input{width:100%;box-sizing:border-box;height:46px;border-radius:12px;border:1px solid var(--border-subtle,#333);background:var(--bg-input,#1a1a1f);color:var(--text-primary,#fff);font-size:16px;padding:0 12px}'
+'#nsu .e{color:#f87171;font-size:12px;margin-top:10px;min-height:16px}'
+'#nsu button{width:100%;margin-top:12px;padding:13px;border-radius:14px;border:0;font-weight:800;font-size:14px}#nsu .go{background:#FF1F8E;color:#fff}#nsu .no{background:transparent;color:var(--text-muted,#999);border:1px solid var(--border-subtle,#333)}';
var st=document.createElement('style');st.textContent=css;document.head.appendChild(st);
function loginForm(){var pw=document.querySelector('input[type=password]');if(!pw)return null;var f=pw.closest('form');if(!f)return null;
 var t=(f.innerText||'');return /Sign In/i.test(t)&&!/Reset|reset link/i.test(t)?f:null}
function ensure(){var f=loginForm(),l=document.getElementById('nsu-link');
 if(!f){if(l)l.remove();return}
 if(!l||l.parentNode!==f.parentNode){if(l)l.remove();l=document.createElement('button');l.type='button';l.id='nsu-link';l.textContent='New shop? Create your shop account';l.onclick=open;f.insertAdjacentElement('afterend',l)}}
function open(){var o=document.getElementById('nsu');if(o)o.remove();o=document.createElement('div');o.id='nsu';
 o.innerHTML='<div class="p"><h3>Create shop account</h3><div class="s">Use your own email. Your shop gets its own stock, bills and data. Nobody else can see it. You will get a verification email once.</div>'
 +'<label>Shop name</label><input id="nsu-n" type="text" autocomplete="organization" maxlength="60">'
 +'<label>Email</label><input id="nsu-e" type="email" autocomplete="email" autocapitalize="none">'
 +'<label>Password (min 6)</label><input id="nsu-p" type="password" autocomplete="new-password">'
 +'<div class="e" id="nsu-x"></div><button class="go" id="nsu-go">Create account</button><button class="no" id="nsu-no">Cancel</button></div>';
 document.body.appendChild(o);
 var q=function(i){return o.querySelector('#'+i)};
 q('nsu-no').onclick=function(){o.remove()};
 q('nsu-go').onclick=function(){var n=q('nsu-n').value.trim(),e=q('nsu-e').value.trim().toLowerCase(),p=q('nsu-p').value,x=q('nsu-x');x.textContent='';
  if(!n){x.textContent='Enter the shop name.';return}
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e)){x.textContent='Enter a valid email address.';return}
  if(p.length<6){x.textContent='Password must be at least 6 characters.';return}
  if(!window.__anAuth){x.textContent='Not ready yet. Try again.';return}
  var b=q('nsu-go');b.disabled=true;b.textContent='Creating...';
  try{localStorage.setItem('noor_newshop_name_'+e,n)}catch(_){}
  window.__anAuth.signUp(e,p).then(function(){o.remove()}).catch(function(err){b.disabled=false;b.textContent='Create account';var m=String(err&&err.code||err&&err.message||err);
   x.textContent=/email-already-in-use/.test(m)?'This email already has an account. Close this and use Sign In.':/weak-password/.test(m)?'Password is too weak.':/network/.test(m)?'No internet. Connect and try again.':/invalid-email/.test(m)?'Invalid email.':'Could not create the account. '+m})}}
var pend=false;function tick(){if(pend)return;pend=true;setTimeout(function(){pend=false;try{ensure()}catch(e){}},200)}
new MutationObserver(tick).observe(document.documentElement,{childList:true,subtree:true});tick();
})();
