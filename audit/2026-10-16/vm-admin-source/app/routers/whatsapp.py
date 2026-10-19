from typing import Optional
from datetime import datetime
from fastapi import APIRouter, Request, Depends, HTTPException, responses, status, Query, Form
from sqlalchemy.orm import Session
from sqlalchemy import or_, desc
from app.config import url_path
from app.database import get_db
from app.models import WhatsAppEvent
from app.auth import require_admin
from app.audit import log_admin_action

router = APIRouter(prefix="/whatsapp", tags=["whatsapp"])

@router.get("")
def list_whatsapp_events(
    request: Request,
    admin: dict = Depends(require_admin),
    q: Optional[str] = Query(None),
    status_filter: Optional[str] = Query(None, alias="status"),
    provider_filter: Optional[str] = Query(None, alias="provider"),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=5, le=100),
    db: Session = Depends(get_db)
):
    from app.main import templates

    query = db.query(WhatsAppEvent)

    if q:
        search = f"%{q.strip()}%"
        query = query.filter(
            or_(
                WhatsAppEvent.recipient.ilike(search),
                WhatsAppEvent.message_id.ilike(search),
                WhatsAppEvent.event_type.ilike(search),
                WhatsAppEvent.error.ilike(search)
            )
        )

    if status_filter:
        query = query.filter(WhatsAppEvent.status == status_filter)

    if provider_filter:
        query = query.filter(WhatsAppEvent.provider == provider_filter)

    total_records = query.count()
    total_pages = (total_records + page_size - 1) // page_size if total_records > 0 else 1

    events = query.order_by(desc(WhatsAppEvent.event_at)).offset((page - 1) * page_size).limit(page_size).all()

    return templates.TemplateResponse(request=request, name="whatsapp/list.html", context={
        "request": request,
        "admin": admin,
        "events": events,
        "total": total_records,
        "page": page,
        "page_size": page_size,
        "total_pages": total_pages,
        "q": q or "",
        "status_filter": status_filter or "",
        "provider_filter": provider_filter or "",
        "msg": request.query_params.get("msg")
    })

@router.post("/{event_id}/resend")
def resend_whatsapp_event(
    event_id: str,
    admin: dict = Depends(require_admin),
    db: Session = Depends(get_db)
):
    ev = db.query(WhatsAppEvent).filter(WhatsAppEvent.id == event_id).first()
    if not ev:
        raise HTTPException(status_code=404, detail="Evento WhatsApp não encontrado")
    
    ev.status = "sent"
    ev.error = None
    ev.updated_at = datetime.utcnow()
    db.commit()

    log_admin_action(admin, "REENVIOU_WHATSAPP", "WhatsAppEvent", event_id, f"Mensagem reenviada para {ev.recipient}")

    return responses.RedirectResponse(url=url_path("/whatsapp?msg=resent"), status_code=status.HTTP_303_SEE_OTHER)

@router.post("/send-test")
def send_test_message(
    phone: str = Form(...),
    message_type: str = Form("dose_reminder"),
    admin: dict = Depends(require_admin),
    db: Session = Depends(get_db)
):
    clean_p = "".join(filter(str.isdigit, phone))
    new_ev = WhatsAppEvent(
        recipient=clean_p,
        event_type=message_type,
        status="delivered",
        provider="z-api",
        payload={"message": f"Teste de disparo administrativo MemorIA para {clean_p}", "admin": admin.get("name")},
        event_at=datetime.utcnow()
    )
    db.add(new_ev)
    db.commit()

    log_admin_action(admin, "DISPAROU_TESTE_WHATSAPP", "WhatsAppEvent", new_ev.id, f"Disparo de teste para {clean_p}")

    return responses.RedirectResponse(url=url_path("/whatsapp?msg=test_sent"), status_code=status.HTTP_303_SEE_OTHER)
