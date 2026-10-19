import subprocess,json,re,pathlib,urllib.request
logs=subprocess.check_output(['sudo','journalctl','-u','memoria-backend','--since','2026-10-13 20:18:23 UTC','--no-pager','-o','short-iso'],text=True)
rows=[l for l in logs.splitlines() if '[whatsapp-reminders]' in l or '[missed-dose]' in l]
fail=[l for l in rows if 'Falha' in l]
print(json.dumps({'check':'logs','since':'2026-10-13T20:18:23Z','patient_failed_attempts':sum('[whatsapp-reminders]' in l for l in fail),'caregiver_failed_attempts':sum('[missed-dose]' in l for l in fail),'patient_success_logs':sum('Lembrete enviado' in l for l in rows),'caregiver_success_logs':sum('Alerta sequencial' in l for l in rows),'fail_status_counts':{s:sum(bool(re.search(r': '+s+r'(?:$| )',l)) for l in fail) for s in ['500','401','403','404','0']},'first_failure_at':fail[0].split()[0] if fail else None,'last_failure_at':fail[-1].split()[0] if fail else None,'startup':[l for l in rows if 'Serviço iniciado' in l]},ensure_ascii=False))
for p in ['/api/health','/health']:
 try:
  with urllib.request.urlopen('http://127.0.0.1:3001'+p,timeout=10) as res: print(json.dumps({'check':'backend_health','path':p,'status':res.status,'body':json.load(res)}))
 except Exception as e: print(json.dumps({'check':'backend_health','path':p,'error':str(e)}))
