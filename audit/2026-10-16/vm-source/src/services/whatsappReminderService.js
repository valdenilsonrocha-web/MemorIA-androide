/**
 * WhatsApp Reminder Service
 * Verifica medicamentos com horário próximo e envia lembretes via WhatsApp.
 * Roda em background via setInterval, iniciado pelo server.js após o banco conectar.
 */

import { Op } from 'sequelize';
import { User, Medication, MedicationHistory } from '../models/index.js';
import { userIsPremium } from '../middleware/auth.js';

// Evita enviar o mesmo lembrete duas vezes na mesma janela de execução.
// Chave: `${medicationId}_${YYYY-MM-DD}_${HH:MM}`
const sentReminders = new Set();

// Limpa o cache de lembretes à meia-noite para não crescer indefinidamente.
const scheduleDailyClear = () => {
    const now = new Date();
    const msTilMidnight =
        new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 1, 0).getTime() - now.getTime();
    setTimeout(() => {
        sentReminders.clear();
        console.log('[whatsapp-reminders] Cache de lembretes limpo à meia-noite.');
        scheduleDailyClear();
    }, msTilMidnight);
};

const getMetaConfig = () => ({
    baseUrl: (process.env.WHATSAPP_META_BASE_URL || 'https://graph.facebook.com').replace(/\/$/, ''),
    version: process.env.WHATSAPP_META_API_VERSION || 'v21.0',
    accessToken: process.env.WHATSAPP_ACCESS_TOKEN || '',
    phoneNumberId: process.env.WHATSAPP_PHONE_NUMBER_ID || '',
});

const isMetaConfigured = () => {
    const { accessToken, phoneNumberId } = getMetaConfig();
    return Boolean(accessToken && phoneNumberId);
};

const getEvolutionConfig = () => ({
    url: (process.env.EVOLUTION_API_URL || 'http://localhost:8081').replace(/\/$/, ''),
    apiKey: process.env.EVOLUTION_API_KEY || '',
    instance: process.env.EVOLUTION_INSTANCE_NAME || 'memoria',
});

const isEvolutionConfigured = () => {
    const { url, apiKey } = getEvolutionConfig();
    return Boolean(url && apiKey);
};

const getProvider = () => (process.env.WHATSAPP_PROVIDER || 'evolution').toLowerCase();

const isConfigured = () => {
    const p = getProvider();
    return p === 'meta' ? isMetaConfigured() : isEvolutionConfigured();
};

const sendViaMetaAPI = async (to, text) => {
    const { baseUrl, version, accessToken, phoneNumberId } = getMetaConfig();
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 12000);
    try {
        const res = await fetch(`${baseUrl}/${version}/${phoneNumberId}/messages`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${accessToken}`,
            },
            body: JSON.stringify({
                messaging_product: 'whatsapp',
                to,
                type: 'text',
                text: { body: text },
            }),
            signal: controller.signal,
        });
        clearTimeout(timeout);
        const data = await res.json().catch(() => ({}));
        return { ok: res.ok, status: res.status, data };
    } catch (err) {
        clearTimeout(timeout);
        return { ok: false, status: 0, data: { error: { message: err.message } } };
    }
};

const sendViaEvolution = async (to, text) => {
    const { url, apiKey, instance } = getEvolutionConfig();
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 12000);
    try {
        const res = await fetch(`${url}/message/sendText/${instance}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                apikey: apiKey,
            },
            body: JSON.stringify({ number: to, text }),
            signal: controller.signal,
        });
        clearTimeout(timeout);
        const data = await res.json().catch(() => ({}));
        return { ok: res.ok, status: res.status, data };
    } catch (err) {
        clearTimeout(timeout);
        return { ok: false, status: 0, data: { error: { message: err.message } } };
    }
};

const sendWhatsApp = (to, text) =>
    getProvider() === 'meta' ? sendViaMetaAPI(to, text) : sendViaEvolution(to, text);

const formatPhone = (raw = '') => String(raw).replace(/\D/g, '');

const buildReminderMessage = (userName, medName, dosage, scheduleTime) => {
    const [h, m] = scheduleTime.split(':');
    const timeFormatted = `${h}:${m || '00'}`;
    const firstName = (userName || 'você').split(' ')[0];
    return (
        `🔔 *MemorIA — Lembrete de Medicamento*\n\n` +
        `Olá, ${firstName}! Está na hora de tomar:\n\n` +
        `💊 *${medName}*` +
        (dosage ? ` — ${dosage}` : '') +
        `\n⏰ Horário: ${timeFormatted}\n\n` +
        `Abra o app MemorIA para confirmar a dose. 💙`
    );
};

const buildLowStockMessage = (userName, medName, stock) => {
    const firstName = (userName || 'você').split(' ')[0];
    return (
        `⚠️ *MemorIA — Estoque Baixo*\n\n` +
        `Atenção, ${firstName}!\n\n` +
        `O medicamento *${medName}* está com apenas *${stock} unidade(s)* no estoque.\n\n` +
        `Providencie a reposição para não interromper o tratamento. 🏥`
    );
};

const buildCriticalCaregiverStockMessage = (caregiverName, patientName, medName, stock) => {
    return (
        `🚨 *MemorIA — Estoque Crítico*\n\n` +
        `Olá, o(a) Sr(a). *${patientName}* está ficando sem o medicamento *${medName}* ` +
        `(${stock} unidades restantes).\n\n` +
        `Por favor, verifique se a reposição já foi providenciada.`
    );
};

const buildCriticalSupplierMessage = (supplierName, patientName, patientPhone, medName) => {
    const formattedPhone = patientPhone ? patientPhone.replace(/(\d{2})(\d{4,5})(\d{4})/, '($1) $2-$3') : 'não informado';
    return (
        `🏥 *MemorIA — Pedido de Reposição*\n\n` +
        `Olá *${supplierName || 'Farmácia'}*, o paciente *${patientName}* (telefone: ${formattedPhone}) ` +
        `precisa de reposição do medicamento *${medName}*.\n\n` +
        `Favor entrar em contato com o paciente para providenciar.`
    );
};

const getCaregivers = (user) => {
    const list = Array.isArray(user?.caregivers) ? user.caregivers : [];
    return list
        .map((c) => ({ name: c?.name || '', phone: formatPhone(c?.phone || ''), relation: c?.relation || '' }))
        .filter((c) => c.phone.length >= 10);
};

/**
 * Janela de antecedência (em minutos) para disparar o lembrete.
 * Valor padrão: 5 minutos antes do horário cadastrado.
 */
const REMINDER_LEAD_MINUTES = Number(process.env.WHATSAPP_REMINDER_LEAD_MINUTES) || 5;

/**
 * Limiar de estoque baixo (unidades).
 */
const LOW_STOCK_THRESHOLD = Number(process.env.WHATSAPP_LOW_STOCK_THRESHOLD) || 5;

/**
 * Limiar de estoque crítico (unidades) - para usuários Premium alertarem cuidadores e farmácia.
 */
const CRITICAL_STOCK_THRESHOLD = Number(process.env.WHATSAPP_CRITICAL_STOCK_THRESHOLD) || 2;

const padTime = (n) => String(n).padStart(2, '0');

// Fuso da aplicação — os horários das doses são "HH:MM" no fuso do paciente.
// Sem isto, num servidor em UTC os lembretes/alertas saem com horas trocadas.
const APP_TZ = process.env.APP_TIMEZONE || 'America/Sao_Paulo';
const zonedNow = () => {
    const parts = new Intl.DateTimeFormat('en-CA', {
        timeZone: APP_TZ, year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', hour12: false
    }).formatToParts(new Date()).reduce((a, p) => { a[p.type] = p.value; return a; }, {});
    const h = Number(parts.hour) % 24;
    const m = Number(parts.minute);
    return { date: `${parts.year}-${parts.month}-${parts.day}`, h, m, mod: h * 60 + m };
};

const getWindowTimes = (leadMinutes) => {
    const times = new Set();
    const base = zonedNow().mod;
    for (let offset = 0; offset <= leadMinutes; offset++) {
        const t = (base + offset) % 1440;
        times.add(`${padTime(Math.floor(t / 60))}:${padTime(t % 60)}`);
    }
    return times;
};

const todayStr = () => zonedNow().date;

export const runReminderCheck = async () => {
    if (!isConfigured()) return;

    try {
        const windowTimes = getWindowTimes(REMINDER_LEAD_MINUTES);
        const today = todayStr();

        // Busca todos os medicamentos ativos de usuários com telefone
        const medications = await Medication.findAll({
            where: { active: true },
            include: [
                {
                    model: User,
                    as: 'user',
                    attributes: ['id', 'name', 'phone', 'subscriptionStatus', 'caregivers'],
                    where: {
                        phone: { [Op.ne]: null },
                        isActive: true,
                    },
                    required: true,
                },
            ],
            raw: false,
        });

        let sent = 0;
        let lowStockSent = 0;

        for (const med of medications) {
            const user = med.user;
            if (!user?.phone) continue;

            const phone = formatPhone(user.phone);
            if (phone.length < 10) continue;

            const times = Array.isArray(med.times) ? med.times : [];

            // — Lembrete de horário —
            for (const t of times) {
                if (!windowTimes.has(t)) continue;

                const key = `${med.id}_${today}_${t}`;
                if (sentReminders.has(key)) continue;

                const msg = buildReminderMessage(user.name, med.name, med.dosage, t);
                const result = await sendWhatsApp(phone, msg);

                if (result.ok) {
                    sentReminders.add(key);
                    sent++;
                    console.log(`[whatsapp-reminders] ✅ Lembrete enviado → ${user.name} (${phone}) — ${med.name} ${t}`);
                } else {
                    const errMsg = result.data?.error?.message || result.status;
                    console.warn(`[whatsapp-reminders] ❌ Falha ao enviar → ${user.name} (${phone}): ${errMsg}`);
                }

                // Pequena pausa para não sobrecarregar a API
                await new Promise((r) => setTimeout(r, 300));
            }

            // — Alerta de estoque baixo (uma vez por dia) —
            if (
                typeof med.stock === 'number' &&
                med.stock > 0 &&
                med.stock <= LOW_STOCK_THRESHOLD
            ) {
                const stockKey = `lowstock_${med.id}_${today}`;
                if (!sentReminders.has(stockKey)) {
                    const msg = buildLowStockMessage(user.name, med.name, med.stock);
                    const result = await sendWhatsApp(phone, msg);
                    if (result.ok) {
                        sentReminders.add(stockKey);
                        lowStockSent++;
                        console.log(`[whatsapp-reminders] ⚠️ Alerta estoque baixo → ${user.name} — ${med.name} (${med.stock} un)`);
                    }
                    await new Promise((r) => setTimeout(r, 300));
                }

                // DIFERENCIAL PREMIUM: Alerta crítico para cuidador e farmácia (ou período de teste de 7 dias)
                if (userIsPremium(user) && med.stock <= CRITICAL_STOCK_THRESHOLD) {
                    const criticalKey = `criticalstock_${med.id}_${today}`;
                    if (!sentReminders.has(criticalKey)) {
                        let sentToCaregiverOrPharmacy = false;
                        
                        // Alert Caregivers
                        const caregivers = getCaregivers(user);
                        for (const cg of caregivers) {
                            const cgMsg = buildCriticalCaregiverStockMessage(cg.name, user.name, med.name, med.stock);
                            const res = await sendWhatsApp(cg.phone, cgMsg);
                            if (res.ok) sentToCaregiverOrPharmacy = true;
                            await new Promise((r) => setTimeout(r, 300));
                        }
                        
                        // Alert Pharmacy (Supplier)
                        if (med.supplier && med.supplier.phone) {
                            const supplierPhone = formatPhone(med.supplier.phone);
                            if (supplierPhone.length >= 10) {
                                const supMsg = buildCriticalSupplierMessage(med.supplier.name, user.name, user.phone, med.name);
                                const res = await sendWhatsApp(supplierPhone, supMsg);
                                if (res.ok) sentToCaregiverOrPharmacy = true;
                                await new Promise((r) => setTimeout(r, 300));
                            }
                        }
                        
                        if (sentToCaregiverOrPharmacy) {
                            sentReminders.add(criticalKey);
                            console.log(`[whatsapp-reminders] 🚨 Alerta crítico de estoque para ${user.name} enviado para cuidador/farmácia.`);
                        }
                    }
                }
            }
        }

        if (sent > 0 || lowStockSent > 0) {
            console.log(`[whatsapp-reminders] Ciclo concluído: ${sent} lembrete(s) de horário, ${lowStockSent} alerta(s) de estoque.`);
        }
    } catch (err) {
        console.error('[whatsapp-reminders] Erro no ciclo de verificação:', err.message);
    }
};

// ─────────────────────────────────────────────────────────────────────────
// DIFERENCIAL: alerta ao CUIDADOR quando a dose NÃO é confirmada (server-side).
// Funciona mesmo com o telemóvel do idoso desligado/app fechada.
// ─────────────────────────────────────────────────────────────────────────

/** Tolerância (min) após o horário antes de considerar a dose perdida. */
const MISSED_GRACE_MINUTES = Number(process.env.WHATSAPP_MISSED_GRACE_MINUTES) || 30;
/** Janela (min) após a tolerância em que o alerta ainda dispara (evita avisar
 *  sobre doses muito antigas, ex.: ao reiniciar o servidor à noite). */
const MISSED_ALERT_WINDOW_MIN = Number(process.env.WHATSAPP_MISSED_ALERT_WINDOW_MIN) || 120;

const WEEKDAY_KEYS = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday'];

const getSaudacao = () => {
    const h = zonedNow().h;
    if (h >= 5 && h < 12) return 'Bom dia';
    if (h >= 12 && h < 18) return 'Boa tarde';
    return 'Boa noite';
};

const buildMissedCaregiverMessage = (caregiverName, patientName, medName, dosage, scheduleTime) => {
    const saudacao = getSaudacao();
    return (
        `${saudacao}! Até o momento, o(a) Sr(a). *${patientName}* não confirmou a medicação das *${scheduleTime}*.\n` +
        `Se possível, entre em contato para verificar se está tudo bem.\n\n` +
        `Medicamento: *${medName}*${dosage ? ` — ${dosage}` : ''}\n\n` +
        `Sistema MemorIA. Obrigado!`
    );
};


const isScheduledToday = (med) => {
    const freq = med.frequency || 'daily';
    // 'alternate' não tem data-âncora no modelo → não arrisca falso-positivo de dose perdida.
    if (freq === 'alternate') return false;
    if (freq !== 'weekly') return true; // daily
    const wd = Array.isArray(med.weekDays) ? med.weekDays.map(String) : [];
    if (!wd.length) return true;
    const idx = new Date().getDay();
    return wd.includes(String(idx)) || wd.includes(WEEKDAY_KEYS[idx]);
};

const minutesSince = (hhmm) => {
    const [h, m] = String(hhmm).split(':').map(Number);
    if (Number.isNaN(h)) return -1;
    return zonedNow().mod - (h * 60 + (m || 0));
};

const dayBounds = () => {
    const a = new Date(); a.setHours(0, 0, 0, 0);
    const b = new Date(); b.setHours(23, 59, 59, 999);
    return [a, b];
};

const doseJaResolvida = async (med, time) => {
    const [a, b] = dayBounds();
    const count = await MedicationHistory.count({
        where: {
            medicationId: med.id,
            scheduleTime: time,
            status: { [Op.in]: ['taken', 'snoozed'] },
            createdAt: { [Op.between]: [a, b] },
        },
    });
    return count > 0;
};

export const runMissedDoseCheck = async () => {
    if (!isConfigured()) return;

    try {
        const today = todayStr();
        const medications = await Medication.findAll({
            where: { active: true },
            include: [{
                model: User,
                as: 'user',
                attributes: ['id', 'name', 'phone', 'caregivers', 'isActive'],
                where: { isActive: true },
                required: true,
            }],
            raw: false,
        });

        let sent = 0;
        for (const med of medications) {
            const caregivers = getCaregivers(med.user);
            if (!caregivers.length || !isScheduledToday(med)) continue;

            const times = Array.isArray(med.times) ? med.times : [];
            for (const t of times) {
                const overdue = minutesSince(t);
                if (overdue < MISSED_GRACE_MINUTES) continue;                       // ainda dentro da tolerância
                if (overdue > MISSED_GRACE_MINUTES + MISSED_ALERT_WINDOW_MIN) continue; // antiga demais

                const key = `missed_${med.id}_${today}_${t}`;
                if (sentReminders.has(key)) continue;
                if (await doseJaResolvida(med, t)) { sentReminders.add(key); continue; }

                // Envio SEQUENCIAL: tenta contato 1 → se falhar, tenta contato 2 → se falhar, tenta contato 3.
                // Regra de negócio: o próximo contato só é acionado se o anterior falhar.
                let alertSent = false;
                for (const cg of caregivers) {
                    const msg = buildMissedCaregiverMessage(cg.name, med.user.name, med.name, med.dosage, t);
                    const result = await sendWhatsApp(cg.phone, msg);
                    await new Promise((r) => setTimeout(r, 300));
                    if (result.ok) {
                        alertSent = true;
                        sent++;
                        console.log(`[missed-dose] 🚨 Alerta sequencial → cuidador ${cg.name || cg.phone} (${cg.relation || 'contato'}) sobre ${med.user.name} — ${med.name} ${t}`);
                        break; // ✅ Entregue: não aciona o próximo contato
                    } else {
                        console.warn(`[missed-dose] ❌ Falha ao contatar ${cg.name || cg.phone} (${cg.relation || 'contato'}): ${result.data?.error?.message || result.status} → tentando próximo contato...`);
                    }
                }
                if (alertSent) sentReminders.add(key);
            }
        }

        if (sent > 0) console.log(`[missed-dose] Ciclo concluído: ${sent} alerta(s) a cuidador(es).`);
    } catch (err) {
        console.error('[missed-dose] Erro no ciclo de verificação:', err.message);
    }
};

/**
 * Inicia o serviço de lembretes automáticos.
 * @param {number} intervalMinutes - Intervalo de verificação (padrão: 5 minutos)
 */
export const startWhatsAppReminderService = (intervalMinutes = 5) => {
    const provider = getProvider();

    if (!isConfigured()) {
        console.log(`[whatsapp-reminders] ⚠️ WhatsApp não configurado (provider: ${provider}). Lembretes automáticos desativados.`);
        return;
    }

    console.log(`[whatsapp-reminders] ✅ Serviço iniciado (provider: ${provider}, intervalo: ${intervalMinutes}min, antecedência: ${REMINDER_LEAD_MINUTES}min)`);

    // Ciclo: lembrete ao paciente + alerta ao cuidador (dose perdida).
    const cycle = () => { runReminderCheck(); runMissedDoseCheck(); };

    // Executa imediatamente no boot para pegar horários que coincidem com o startup
    cycle();

    // Agenda verificação periódica
    setInterval(cycle, intervalMinutes * 60 * 1000);

    // Limpa cache à meia-noite
    scheduleDailyClear();
};
