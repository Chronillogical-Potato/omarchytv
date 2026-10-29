const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
function boot(apps, helperRunning = false, options = {}) {
  const handlers = {}, events = [], keys = [];
  let contextQueries=0;
  const contexts = helperRunning ? [{appId:'OmKeyCtrl0.Keys'}] : [], launches = [];
  const ctx = {document:{title:'OmarchyTV (webOS)',hidden:!!options.hidden,addEventListener:(k,f)=>handlers[k]=f},
    addEventListener:(k,f)=>handlers[k]=f, dispatchEvent:e=>events.push(e),
    CustomEvent:function(type,init){this.type=type;this.detail=init.detail;},
    tizen:{application:{getAppsInfo:ok=>ok(apps),getCurrentApplication:()=>({appInfo:{id:options.selfId || "OmarchyTV0.OmarchyTV"}}),getAppsContext:(ok,fail)=>{contextQueries++;if(options.contextHook)options.contextHook(ok,fail);else options.contextError?fail(options.contextError):ok(contexts);},launch:(id,ok)=>{launches.push(id);contexts.push({appId:id});if(ok)ok();}},tvinputdevice:{registerKey:k=>keys.push(k)}}};
  ctx.window=ctx;vm.runInNewContext(fs.readFileSync(__dirname+'/tizen-shim.js','utf8'),ctx);
  return {ctx,handlers,events,keys,launches,contextQueries:()=>contextQueries};
}
test('discover installed native IDs, preserve favorite brands and exclude system helpers',()=>{
  const b=boot([{id:'org.tizen.netflix-app',name:'Netflix',show:true},
    {id:'AbCdEfGhIj.Future',name:'Future streaming',show:true},
    {id:'AbCdEfGhIk.Service',name:'Background service',show:true},
    {id:'Arn0Lnchr0.Launcher',name:'CleanYourTV',show:true},
    {id:'org.tizen.netflix-app',name:'Netflix',show:true}]);
  assert.deepEqual(Array.from(b.ctx.OMARCHY_TIZEN_APPS,a=>a.tizenId),['org.tizen.netflix-app','AbCdEfGhIj.Future']);
  assert.equal(b.ctx.OMARCHY_TIZEN_APPS[0].id,'netflix');
  assert.equal(b.ctx.OMARCHY_TIZEN_IDS.netflix,'org.tizen.netflix-app');
  assert.ok(!b.keys.includes('Home')&&!b.keys.includes('Netflix'));
  assert.ok(b.keys.includes('MediaPlayPause'));
});
test('refresh discovery after returning to Home, including future installs',()=>{
  const apps=[{id:'org.tizen.netflix-app',name:'Netflix',show:true}],b=boot(apps);
  apps.push({id:'AbCdEfGhIj.Future',name:'Future streaming',show:true});
  b.handlers.visibilitychange();
  assert.equal(b.events.at(-1).detail.length,2);
});
test('manifest grants installed app discovery and remote keys',()=>{
  const xml=fs.readFileSync(__dirname+'/config.xml','utf8');
  for(const p of ['application.info','tv.inputdevice'])assert.ok(xml.includes('http://tizen.org/privilege/'+p));
});
test('start the button helper once and preserve the foreground on Home returns',()=>{
  const b=boot([{id:'org.tizen.netflix-app',name:'Netflix',show:true},{id:'OmKeyCtrl0.Keys',name:'OmarchyTV Buttons',show:false}]);
  assert.deepEqual(b.launches,['OmKeyCtrl0.Keys']);
  b.handlers.visibilitychange();
  assert.equal(b.launches.length,1,'an existing helper must not be relaunched on every visibility change');
  assert.deepEqual(boot([{id:'OmKeyCtrl0.Keys',name:'OmarchyTV Buttons',show:false}],true).launches,[],'leave an already running installed helper alone');
});

test('a widget-only installation does not query or launch a missing optional helper',()=>{
  const b=boot([{id:'org.tizen.netflix-app',name:'Netflix',show:true}]);b.handlers.visibilitychange();
  assert.deepEqual(b.launches,[]);assert.equal(b.contextQueries(),0);
});
test('discover an independently installed helper on a later Home return',()=>{
  const apps=[{id:'org.tizen.netflix-app',name:'Netflix',show:true}],b=boot(apps);
  assert.deepEqual(b.launches,[]);
  apps.push({id:'OmKeyCtrl0.Keys',name:'OmarchyTV Buttons',show:false});b.handlers.visibilitychange();
  assert.deepEqual(b.launches,['OmKeyCtrl0.Keys']);
  assert.ok(!b.ctx.OMARCHY_TIZEN_APPS.some(a=>a.tizenId==='OmKeyCtrl0.Keys'));
});
test('an installed helper is not relaunched if its running contexts cannot be read',()=>{
  const b=boot([{id:'OmKeyCtrl0.Keys',name:'OmarchyTV Buttons',show:false}],false,{contextError:{name:'SecurityError'}});
  assert.equal(b.contextQueries(),1);assert.deepEqual(b.launches,[]);
});
test('exclude the running widget identity when a distributor uses a different app ID',()=>{
  const b=boot([{id:'AbCdEfGhIx.Custom',name:'OmarchyTV',show:true},{id:'org.tizen.netflix-app',name:'Netflix',show:true}],false,{selfId:'AbCdEfGhIx.Custom'});
  assert.deepEqual(Array.from(b.ctx.OMARCHY_TIZEN_APPS,a=>a.tizenId),['org.tizen.netflix-app']);
});

test('a prelaunched hidden widget does not start the optional helper until visible',()=>{
  const b=boot([{id:'OmKeyCtrl0.Keys',name:'OmarchyTV Buttons',show:false}],false,{hidden:true});
  assert.deepEqual(b.launches,[]);assert.equal(b.contextQueries(),0);
  b.ctx.document.hidden=false;b.handlers.visibilitychange();
  assert.deepEqual(b.launches,['OmKeyCtrl0.Keys']);
});
test('a delayed helper-context response cannot launch it after the widget is hidden',()=>{
  let respond;
  const b=boot([{id:'OmKeyCtrl0.Keys',name:'OmarchyTV Buttons',show:false}],false,{contextHook:ok=>{respond=ok;}});
  b.ctx.document.hidden=true;respond([]);
  assert.deepEqual(b.launches,[]);
});
