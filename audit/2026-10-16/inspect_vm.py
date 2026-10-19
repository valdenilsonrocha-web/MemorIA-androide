import json, pathlib, subprocess, urllib.request, urllib.error, re, hashlib
root=pathlib.Path('/home/web2a/MemorIA/backend')
env={}
for line in (root/'.env').read_text().splitlines():
 if '=' in line and not line.lstrip().startswith('#'):
  k,v=line.split('=',1); env[k.strip()]=v.strip().strip(chr(34)).strip(chr(39))
pid=subprocess.check_output(['systemctl','show','memoria-backend','-p','MainPID','--value'],text=True).strip()
try:
 for x in pathlib.Path('/proc/'+pid+'/environ').read_bytes().split(b'\0'):
  if b'=' in x:
   k,v=x.decode().split('=',1); env[k]=v
except Exception as e: print('proc-env:',type(e).__name__)
public=['WHATSAPP_PROVIDER','EVOLUTION_API_URL','EVOLUTION_INSTANCE_NAME','APP_TIMEZONE','TZ','WHATSAPP_REMINDER_LEAD_MINUTES','DB_DIALECT','NODE_ENV']
print('backend_config',json.dumps({k:env.get(k,'<unset>') for k in public}))
print('backend_secret_presence',json.dumps({k:bool(env.get(k)) for k in ['EVOLUTION_API_KEY','WHATSAPP_ACCESS_TOKEN','WHATSAPP_PHONE_NUMBER_ID','RESEND_API_KEY']}))
cont=json.loads(subprocess.check_output(['docker','inspect','memoria-evolution-api'],text=True))[0]
ce=dict(x.split('=',1) for x in cont['Config']['Env'] if '=' in x)
print('container_config',json.dumps({k:ce.get(k,'<unset>') for k in ['LOG_LEVEL','CONFIG_SESSION_PHONE_VERSION','CONFIG_SESSION_PHONE_CLIENT','CONFIG_SESSION_PHONE_NAME','CACHE_REDIS_ENABLED','CACHE_LOCAL_ENABLED','DATABASE_ENABLED','DATABASE_PROVIDER','WEBHOOK_GLOBAL_ENABLED','AUTHENTICATION_TYPE','SERVER_PORT']}))
print('api_keys_equal',env.get('EVOLUTION_API_KEY')==ce.get('AUTHENTICATION_API_KEY'))
print('container_log',json.dumps({'driver':cont['HostConfig']['LogConfig'],'path':cont.get('LogPath'),'started':cont['State']['StartedAt'],'restartCount':cont['RestartCount']}))
url=env.get('EVOLUTION_API_URL','http://localhost:8081').rstrip('/')
instance=env.get('EVOLUTION_INSTANCE_NAME','memoria')
def clean(x):
 if isinstance(x,dict):
  return {k:('<redacted>' if re.search('key|token|secret|password|base64|qrcode|owner|number|name|profile|jid',k,re.I) and k not in ['name','instanceName'] else clean(v)) for k,v in x.items()}
 if isinstance(x,list): return [clean(v) for v in x]
 if isinstance(x,str):
  for k in ['EVOLUTION_API_KEY','AUTHENTICATION_API_KEY']:
   val=env.get(k) or ce.get(k)
   if val: x=x.replace(val,'<redacted>')
  return re.sub(r'\d{10,}', '<phone-redacted>',x)
 return x
for p in ['/', '/instance/connectionState/'+instance, '/instance/fetchInstances', '/webhook/find/'+instance]:
 try:
  req=urllib.request.Request(url+p,headers={'apikey':env.get('EVOLUTION_API_KEY','')})
  with urllib.request.urlopen(req,timeout=15) as res:
   data=json.load(res); print('GET',p,res.status,json.dumps(clean(data),ensure_ascii=False))
 except urllib.error.HTTPError as e:
  print('GET',p,e.code,clean(e.read().decode())[:1000])
 except Exception as e: print('GET',p,type(e).__name__,str(e))
for f in ['src/services/whatsappReminderService.js','src/controllers/integrationController.js','src/server.js']:
 p=root/f; print('file',f,'sha256',hashlib.sha256(p.read_bytes()).hexdigest(),'mtime',p.stat().st_mtime)
 if f.endswith('whatsappReminderService.js'):
  lines=p.read_text().splitlines()
  for i,l in enumerate(lines,1):
   if any(s in l for s in ['formatPhone','setInterval','intervalMinutes','startWhatsAppReminderService','const APP_TZ','const todayStr','getWindowTimes','isScheduledToday','subscriptionStatus']): print(str(i)+':'+l)
