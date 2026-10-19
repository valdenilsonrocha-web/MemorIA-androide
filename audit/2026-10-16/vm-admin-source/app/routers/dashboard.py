from datetime import datetime, timedelta, date
from fastapi import APIRouter, Request, Depends, Query
from sqlalchemy.orm import Session
from sqlalchemy import func, desc, or_
from app.config import url_path
from app.database import get_db
from app.models import User, Medication, MedicationHistory, Prescription, WhatsAppEvent
from app.auth import require_admin

router = APIRouter(tags=["dashboard"])

def get_age_group(birth_date):
    if not birth_date:
        return "Não informado"
    today = date.today()
    age = today.year - birth_date.year - ((today.month, today.day) < (birth_date.month, birth_date.day))
    if age < 18:
        return "0-17"
    elif age < 30:
        return "18-29"
    elif age < 45:
        return "30-44"
    elif age < 60:
        return "45-59"
    elif age < 75:
        return "60-74"
    else:
        return "75+"

@router.get("/")
def dashboard_page(
    request: Request,
    admin: dict = Depends(require_admin),
    db: Session = Depends(get_db)
):
    from app.main import templates

    now = datetime.utcnow()
    today_start = datetime(now.year, now.month, now.day)
    week_start = today_start - timedelta(days=now.weekday())
    month_start = datetime(now.year, now.month, 1)
    thirty_days_ago = now - timedelta(days=30)

    # Contagens gerais
    total_users = db.query(func.count(User.id)).scalar() or 0
    active_users = db.query(func.count(User.id)).filter(User.is_active == True).scalar() or 0
    new_today = db.query(func.count(User.id)).filter(User.created_at >= today_start).scalar() or 0
    new_this_week = db.query(func.count(User.id)).filter(User.created_at >= week_start).scalar() or 0
    new_this_month = db.query(func.count(User.id)).filter(User.created_at >= month_start).scalar() or 0

    total_medications = db.query(func.count(Medication.id)).scalar() or 0
    low_stock_meds = db.query(func.count(Medication.id)).filter(Medication.stock <= 5, Medication.active == True).scalar() or 0

    total_prescriptions = db.query(func.count(Prescription.id)).scalar() or 0
    total_whatsapp = db.query(func.count(WhatsAppEvent.id)).scalar() or 0

    # Doses e taxa de adesão
    total_taken = db.query(func.count(MedicationHistory.id)).filter(MedicationHistory.status.in_(["taken", "snoozed"])).scalar() or 0
    total_missed = db.query(func.count(MedicationHistory.id)).filter(MedicationHistory.status == "missed").scalar() or 0
    total_doses = total_taken + total_missed
    adherence_rate = round((total_taken / total_doses * 100), 1) if total_doses > 0 else 0.0

    # Assinaturas / Financeiro
    active_subscriptions = db.query(func.count(User.id)).filter(User.subscription_status == "active").scalar() or 0
    trial_users = db.query(func.count(User.id)).filter(User.subscription_status == "trial").scalar() or 0

    # Usuários recentes
    recent_users = db.query(User).order_by(desc(User.created_at)).limit(5).all()

    # Remédios com estoque baixo (alerta imediato)
    critical_stock = db.query(Medication).filter(Medication.stock <= 5, Medication.active == True).order_by(Medication.stock.asc()).limit(5).all()

    return templates.TemplateResponse(request=request, name="dashboard.html", context={
        "request": request,
        "admin": admin,
        "stats": {
            "total_users": total_users,
            "active_users": active_users,
            "new_today": new_today,
            "new_this_week": new_this_week,
            "new_this_month": new_this_month,
            "total_medications": total_medications,
            "low_stock_meds": low_stock_meds,
            "total_prescriptions": total_prescriptions,
            "total_whatsapp": total_whatsapp,
            "total_taken": total_taken,
            "total_missed": total_missed,
            "adherence_rate": adherence_rate,
            "active_subscriptions": active_subscriptions,
            "trial_users": trial_users
        },
        "recent_users": recent_users,
        "critical_stock": critical_stock
    })

@router.get("/api/dashboard/stats")
def api_dashboard_stats(
    admin: dict = Depends(require_admin),
    db: Session = Depends(get_db)
):
    now = datetime.utcnow()
    thirty_days_ago = now - timedelta(days=30)

    # 1. Gênero
    gender_counts = db.query(User.gender, func.count(User.id)).group_by(User.gender).all()
    gender_map = {"female": "Feminino", "male": "Masculino", "other": "Outro", "not_informed": "N/I"}
    by_gender = [{"label": gender_map.get(g, g or "N/I"), "count": c} for g, c in gender_counts]

    # 2. Faixa Etária
    users_birth = db.query(User.birth_date).filter(User.birth_date != None).all()
    age_groups = {"0-17": 0, "18-29": 0, "30-44": 0, "45-59": 0, "60-74": 0, "75+": 0}
    for (bd,) in users_birth:
        grp = get_age_group(bd)
        if grp in age_groups:
            age_groups[grp] += 1
    by_age = [{"label": k, "count": v} for k, v in age_groups.items()]

    # 3. Top Medicamentos
    top_meds_query = db.query(Medication.name, func.count(Medication.id)).group_by(Medication.name).order_by(desc(func.count(Medication.id))).limit(6).all()
    top_medications = [{"name": name, "count": count} for name, count in top_meds_query]

    # 4. Tendência de Novos Usuários (últimos 15 dias)
    daily_signups = []
    for i in range(14, -1, -1):
        day_date = (now - timedelta(days=i)).date()
        day_start = datetime(day_date.year, day_date.month, day_date.day)
        day_end = day_start + timedelta(days=1)
        cnt = db.query(func.count(User.id)).filter(User.created_at >= day_start, User.created_at < day_end).scalar() or 0
        daily_signups.append({
            "date": day_date.strftime("%d/%m"),
            "count": cnt
        })

    # 5. Doses por status
    doses_by_status = db.query(MedicationHistory.status, func.count(MedicationHistory.id)).group_by(MedicationHistory.status).all()
    status_map = {"taken": "Tomado no horário", "snoozed": "Atrasado", "missed": "Perdido", "snoozed": "Adiado"}
    doses_chart = [{"label": status_map.get(s, s), "count": c} for s, c in doses_by_status]

    return {
        "success": True,
        "by_gender": by_gender,
        "by_age": by_age,
        "top_medications": top_medications,
        "daily_signups": daily_signups,
        "doses_chart": doses_chart
    }

@router.get("/api/search")
def api_global_search(
    q: str = Query("", description="Termo de pesquisa"),
    admin: dict = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """Busca global rápida (Cmd+K) por Nome, CPF, Telefone ou Cidade."""
    term = (q or "").strip()
    if not term or len(term) < 2:
        return []

    clean_digits = "".join(filter(str.isdigit, term))
    filters = [
        User.name.ilike(f"%{term}%"),
        User.email.ilike(f"%{term}%"),
        User.city.ilike(f"%{term}%")
    ]
    if clean_digits:
        filters.append(User.cpf.like(f"%{clean_digits}%"))
        filters.append(User.phone.like(f"%{clean_digits}%"))

    users = db.query(User).filter(or_(*filters)).limit(7).all()
    results = []
    for u in users:
        results.append({
            "id": u.id,
            "name": u.name,
            "cpf": u.formatted_cpf,
            "phone": u.phone or "",
            "city": f"{u.city or ''}/{u.state or ''}",
            "is_active": u.is_active,
            "subscription": u.subscription_status or "trial",
            "url": url_path(f"/users/{u.id}")
        })
    return results

@router.get("/api/alerts")
def api_critical_alerts(
    admin: dict = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """Centro de triagem de alertas críticos para o sino de notificações."""
    alerts = []

    # 1. Pacientes com 3 ou mais doses perdidas
    missed_doses = db.query(
        MedicationHistory.user_id,
        func.count(MedicationHistory.id).label("missed_cnt")
    ).filter(
        MedicationHistory.status == "missed"
    ).group_by(
        MedicationHistory.user_id
    ).having(
        func.count(MedicationHistory.id) >= 3
    ).limit(5).all()

    for uid, cnt in missed_doses:
        u = db.query(User).filter(User.id == uid).first()
        if u:
            alerts.append({
                "type": "missed_doses",
                "severity": "high",
                "title": f"Adesão Crítica: {u.name}",
                "description": f"{cnt} doses perdidas consecutivas",
                "url": url_path(f"/users/{u.id}?tab=history"),
                "time": "Ação necessária"
            })

    # 2. Medicamentos com estoque esgotando (<= 3)
    low_stock = db.query(Medication).filter(
        Medication.stock <= 3,
        Medication.active == True
    ).limit(5).all()

    for med in low_stock:
        u = db.query(User).filter(User.id == med.user_id).first()
        alerts.append({
            "type": "low_stock",
            "severity": "medium",
            "title": f"Estoque Crítico: {med.name}",
            "description": f"Restam apenas {med.stock} unidades para {u.name if u else 'paciente'}",
            "url": url_path(f"/users/{med.user_id}?tab=resources") if u else url_path("/medications"),
            "time": "Reposição urgente"
        })

    # 3. Falhas recentes de entrega no WhatsApp
    failed_wa = db.query(WhatsAppEvent).filter(
        WhatsAppEvent.status == "failed"
    ).order_by(desc(WhatsAppEvent.created_at)).limit(4).all()

    for wa in failed_wa:
        alerts.append({
            "type": "whatsapp_failure",
            "severity": "warning",
            "title": "Falha de Envio WhatsApp",
            "description": f"Destinatário: {wa.recipient_phone or 'Não informado'} ({wa.event_type})",
            "url": url_path("/whatsapp"),
            "time": wa.created_at.strftime("%d/%m %H:%M") if wa.created_at else "Recente"
        })

    return {
        "count": len(alerts),
        "alerts": alerts
    }
