import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const source=fs.readFileSync(new URL('./vm-source/src/services/whatsappReminderService.js', import.meta.url),'utf8').replace(/^import .*;\r?\n/gm,'').replace(/^export /gm,'');
const fixed='2026-10-16T12:00:00Z';
const fixture={id:'synthetic-med',name:'SYNTHETIC',dosage:'test',frequency:'weekly',weekDays:['monday'],times:['09:00'],stock:20,user:{id:'synthetic-user',name:'SYNTHETIC',phone:'11912345678',caregivers:[]}};
function setup(now=fixed, ok=true) {
 const calls=[];let historyWhere;
 class FixedDate extends Date {constructor(...args){super(...(args.length?args:[now]));}static now(){return new Date(now).getTime();}}
 const ctx=vm.createContext({process:{env:{WHATSAPP_PROVIDER:'evolution',EVOLUTION_API_KEY:'synthetic-key',APP_TIMEZONE:'America/Sao_Paulo'}},console:{log(){},warn(){},error(e){throw new Error(String(e));}},Intl,Date:FixedDate,Set,Number,String,Boolean,Array,AbortController,Op:{ne:Symbol.for('ne'),in:Symbol.for('in'),between:Symbol.for('between')},User:{},Medication:{findAll:async()=>[fixture]},MedicationHistory:{count:async(q)=>{historyWhere=q.where;return 0;}},userIsPremium:()=>true,setTimeout:(fn,ms)=>{if(ms<=300)queueMicrotask(fn);return 1;},clearTimeout(){},setInterval(){},fetch:async(url,options)=>{calls.push({url,body:JSON.parse(options.body)});return {ok,status:ok?201:500,json:async()=>ok?{key:{id:'mock-id'},status:'PENDING'}:{error:'Internal Server Error'}};}});
 vm.runInContext(source+'\nglobalThis.audit={formatPhone,isScheduledToday,dayBounds,doseJaResolvida,runReminderCheck};',ctx);
 return {api:ctx.audit,calls,getHistory:()=>historyWhere};
}
const checks=[];
let c=setup();assert.equal(c.api.formatPhone('11912345678'),'11912345678');assert.equal(c.api.formatPhone('011912345678'),'011912345678');checks.push('Production formatter preserves missing DDI and leading zero.');
assert.equal(c.api.isScheduledToday(fixture),false);await c.api.runReminderCheck();assert.equal(c.calls.length,1);checks.push('Patient reminder is sent on Friday for a Monday-only medication (mock transport).');
assert.equal(c.calls[0].body.number,'11912345678');checks.push('Production sender passes the unnormalized number to Evolution.');
await c.api.runReminderCheck();assert.equal(c.calls.length,1);let reboot=setup();await reboot.api.runReminderCheck();assert.equal(reboot.calls.length,1);checks.push('Deduplication is lost after process restart; same synthetic dose is submitted again.');
const bounds=c.api.dayBounds().map(d=>d.toISOString());assert.equal(bounds[0],'2026-10-16T00:00:00.000Z');assert.notEqual(bounds[0],'2026-10-16T03:00:00.000Z');checks.push('With host TZ=UTC the history day begins at 00:00Z instead of Sao Paulo 03:00Z.');
let late=setup('2026-10-16T12:01:00Z',false);await late.api.runReminderCheck();assert.equal(late.calls.length,0);checks.push('A 09:00 dose is no longer attempted at 09:01; there is no catch-up after its minute.');
const output={hostTZ:process.env.TZ,fixedNow:fixed,transport:'mock only; no network',checksPassed:checks.length,checks};
console.log(JSON.stringify(output,null,2));
