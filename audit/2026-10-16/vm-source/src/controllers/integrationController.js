const DEFAULT_VOICE_ASSISTANT_CONFIG = {
    enabled: false,
    provider: 'alexa',
    deviceName: '',
    webhookUrl: ''
};

import { WhatsAppEvent } from '../models/index.js';

const MAX_RECENT_WHATSAPP_EVENTS = 200;
const recentWhatsAppEvents = [];

const pushRecentWhatsAppEvent = (event) => {
    recentWhatsAppEvents.unshift(event);
    if (recentWhatsAppEvents.length > MAX_RECENT_WHATSAPP_EVENTS) {
        recentWhatsAppEvents.length = MAX_RECENT_WHATSAPP_EVENTS;
    }
};

const parseEventAt = (value) => {
    if (!value) {
        return new Date();
    }

    if (typeof value === 'string' && /^\d+$/.test(value)) {
        return new Date(Number(value) * 1000);
    }

    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? new Date() : parsed;
};

const persistWhatsAppEvent = async (event) => {
    try {
        await WhatsAppEvent.create({
            provider: event.provider || 'meta-cloud-api',
            eventType: event.eventType || 'unknown',
            messageId: event.messageId || null,
            status: event.status || null,
            recipient: event.recipient || event.from || null,
            error: event.error || null,
            payload: event,
            eventAt: parseEventAt(event.timestamp || event.at)
        });
    } catch (error) {
        console.error('Persist WhatsApp event error:', error.message);
    }
};

const normalizePhoneNumber = (value = '') => String(value).replace(/\D/g, '');
const sanitizeString = (value, max = 255) => String(value || '').trim().slice(0, max);
const clampInt = (value, min, max, fallback) => {
    const parsed = Number.parseInt(value, 10);
    if (!Number.isFinite(parsed)) return fallback;
    return Math.min(max, Math.max(min, parsed));
};

const getWhatsAppProvider = () => (process.env.WHATSAPP_PROVIDER || 'evolution').toLowerCase();

// Evolution API helpers
const getEvolutionConfig = () => ({
    url: (process.env.EVOLUTION_API_URL || 'http://localhost:8081').replace(/\/$/, ''),
    apiKey: process.env.EVOLUTION_API_KEY || '',
    instance: process.env.EVOLUTION_INSTANCE_NAME || 'memoria'
});

const getMetaConfig = () => ({
    baseUrl: (process.env.WHATSAPP_META_BASE_URL || 'https://graph.facebook.com').replace(/\/$/, ''),
    version: process.env.WHATSAPP_META_API_VERSION || 'v21.0',
    accessToken: process.env.WHATSAPP_ACCESS_TOKEN || '',
    phoneNumberId: process.env.WHATSAPP_PHONE_NUMBER_ID || ''
});

const isWhatsAppConfigured = () => {
    const provider = getWhatsAppProvider();

    if (provider === 'meta') {
        const { accessToken, phoneNumberId } = getMetaConfig();
        return Boolean(accessToken && phoneNumberId);
    }

    const { url, apiKey } = getEvolutionConfig();
    return Boolean(url && apiKey);
};

const evolutionRequest = async (path, method = 'GET', body = null) => {
    const { url, apiKey } = getEvolutionConfig();
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
        const res = await fetch(`${url}${path}`, {
            method,
            headers: {
                'Content-Type': 'application/json',
                apikey: apiKey
            },
            ...(body ? { body: JSON.stringify(body) } : {}),
            signal: controller.signal
        });
        clearTimeout(timeout);
        const text = await res.text();
        let data = null;
        try { data = JSON.parse(text); } catch { data = { raw: text }; }
        return { ok: res.ok, status: res.status, data };
    } finally {
        clearTimeout(timeout);
    }
};

const metaRequest = async (path, method = 'GET', body = null) => {
    const { baseUrl, version, accessToken } = getMetaConfig();
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
        const res = await fetch(`${baseUrl}/${version}${path}`, {
            method,
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${accessToken}`
            },
            ...(body ? { body: JSON.stringify(body) } : {}),
            signal: controller.signal
        });
        clearTimeout(timeout);
        const text = await res.text();
        let data = null;
        try { data = JSON.parse(text); } catch { data = { raw: text }; }
        return { ok: res.ok, status: res.status, data };
    } finally {
        clearTimeout(timeout);
    }
};

const extractProviderError = (data, status) => {
    return (
        data?.error?.message ||
        data?.response?.error?.message ||
        data?.message ||
        data?.raw ||
        `status_${status}`
    );
};

const flattenMetaWebhookEvents = (payload = {}) => {
    const events = [];
    const entries = Array.isArray(payload.entry) ? payload.entry : [];

    for (const entry of entries) {
        const changes = Array.isArray(entry.changes) ? entry.changes : [];
        for (const change of changes) {
            const value = change?.value || {};
            const statuses = Array.isArray(value.statuses) ? value.statuses : [];
            const messages = Array.isArray(value.messages) ? value.messages : [];

            for (const status of statuses) {
                events.push({
                    eventType: 'status',
                    provider: 'meta-cloud-api',
                    at: new Date().toISOString(),
                    messageId: status?.id || null,
                    status: status?.status || 'unknown',
                    recipient: status?.recipient_id || null,
                    timestamp: status?.timestamp || null,
                    conversationId: status?.conversation?.id || null,
                    error: status?.errors?.[0]?.message || null
                });
            }

            for (const message of messages) {
                events.push({
                    eventType: 'incoming-message',
                    provider: 'meta-cloud-api',
                    at: new Date().toISOString(),
                    messageId: message?.id || null,
                    from: message?.from || null,
                    messageType: message?.type || 'unknown',
                    timestamp: message?.timestamp || null
                });
            }
        }
    }

    return events;
};

export const verifyWhatsAppWebhook = async (req, res) => {
    try {
        const mode = sanitizeString(req.query['hub.mode'], 40);
        const token = sanitizeString(req.query['hub.verify_token'], 200);
        const challenge = sanitizeString(req.query['hub.challenge'], 300);
        const expectedToken = process.env.WHATSAPP_WEBHOOK_VERIFY_TOKEN || '';

        if (!expectedToken) {
            return res.status(500).send('WEBHOOK_VERIFY_TOKEN_NOT_CONFIGURED');
        }

        if (mode === 'subscribe' && token === expectedToken) {
            return res.status(200).send(challenge);
        }

        return res.status(403).send('FORBIDDEN');
    } catch (error) {
        console.error('Verify WhatsApp webhook error:', error);
        return res.status(500).send('INTERNAL_ERROR');
    }
};

export const handleWhatsAppWebhook = async (req, res) => {
    try {
        const provider = getWhatsAppProvider();

        if (provider !== 'meta') {
            // Acknowledge requests to avoid retries even when provider is not Meta.
            return res.status(200).send('EVENT_RECEIVED');
        }

        const events = flattenMetaWebhookEvents(req.body || {});

        if (events.length === 0) {
            const fallbackEvent = {
                eventType: 'webhook-payload',
                provider: 'meta-cloud-api',
                at: new Date().toISOString(),
                note: 'Payload recebido sem statuses/messages parseáveis.'
            };
            pushRecentWhatsAppEvent(fallbackEvent);
            await persistWhatsAppEvent(fallbackEvent);
        } else {
            for (const event of events) {
                pushRecentWhatsAppEvent(event);
                console.log('[meta-webhook]', event);
                await persistWhatsAppEvent(event);
            }
        }

        return res.status(200).send('EVENT_RECEIVED');
    } catch (error) {
        console.error('Handle WhatsApp webhook error:', error);
        return res.status(500).send('INTERNAL_ERROR');
    }
};

export const getRecentWhatsAppEvents = async (req, res) => {
    try {
        const limit = clampInt(req.query.limit, 1, 100, 20);

        let persistedEvents = [];
        try {
            const rows = await WhatsAppEvent.findAll({
                order: [['createdAt', 'DESC']],
                limit
            });
            persistedEvents = rows.map((row) => row.toJSON());
        } catch (error) {
            console.error('Load persisted WhatsApp events error:', error.message);
        }

        return res.json({
            success: true,
            data: persistedEvents.length > 0 ? persistedEvents : recentWhatsAppEvents.slice(0, limit),
            totalBuffered: recentWhatsAppEvents.length,
            persistedCount: persistedEvents.length
        });
    } catch (error) {
        console.error('Get recent WhatsApp events error:', error);
        return res.status(500).json({
            success: false,
            message: 'Erro ao buscar eventos recentes do WhatsApp.',
            error: error.message
        });
    }
};

const mergeConfig = (config = {}) => ({
    ...DEFAULT_VOICE_ASSISTANT_CONFIG,
    ...(config || {})
});

const isValidProvider = (provider = '') => {
    return ['alexa', 'google', 'webhook', 'browser-tts'].includes(provider);
};

const isHttpUrl = (value = '') => {
    try {
        const parsed = new URL(value);
        return parsed.protocol === 'http:' || parsed.protocol === 'https:';
    } catch {
        return false;
    }
};

export const getVoiceAssistantConfig = async (req, res) => {
    try {
        const config = mergeConfig(req.user.voiceAssistantConfig || {});

        return res.json({
            success: true,
            data: { config }
        });
    } catch (error) {
        console.error('Get voice assistant config error:', error);
        return res.status(500).json({
            success: false,
            message: 'Erro ao buscar configuração do assistente de voz.',
            error: error.message
        });
    }
};

export const updateVoiceAssistantConfig = async (req, res) => {
    try {
        const payload = req.body || {};
        const config = mergeConfig({
            enabled: payload.enabled === true,
            provider: sanitizeString(payload.provider, 30).toLowerCase(),
            deviceName: sanitizeString(payload.deviceName, 120),
            webhookUrl: sanitizeString(payload.webhookUrl, 500)
        });

        if (!isValidProvider(config.provider)) {
            return res.status(400).json({
                success: false,
                message: 'Provedor de assistente inválido.'
            });
        }

        const webhookRequired = config.enabled && config.provider !== 'browser-tts';
        if (webhookRequired && !isHttpUrl(config.webhookUrl)) {
            return res.status(400).json({
                success: false,
                message: 'Informe uma URL de webhook válida para Alexa/outros assistentes.'
            });
        }

        await req.user.update({ voiceAssistantConfig: config });

        return res.json({
            success: true,
            message: 'Configuração de assistente de voz salva com sucesso.',
            data: { config }
        });
    } catch (error) {
        console.error('Update voice assistant config error:', error);
        return res.status(500).json({
            success: false,
            message: 'Erro ao salvar configuração do assistente de voz.',
            error: error.message
        });
    }
};

export const dispatchVoiceAssistantReminder = async (req, res) => {
    try {
        const { message = '', medicationName = '', scheduleTime = '' } = req.body || {};
        const config = mergeConfig(req.user.voiceAssistantConfig || {});

        const safeMessage = sanitizeString(message, 500);
        const safeMedicationName = sanitizeString(medicationName, 255);
        const safeScheduleTime = sanitizeString(scheduleTime, 20);

        if (!config.enabled) {
            return res.status(400).json({
                success: false,
                message: 'Integração com assistente de voz está desativada.'
            });
        }

        if (config.provider === 'browser-tts') {
            return res.json({
                success: true,
                message: 'Lembrete preparado para voz no navegador.',
                data: { dispatched: false, mode: 'browser-tts' }
            });
        }

        if (!isHttpUrl(config.webhookUrl)) {
            return res.status(400).json({
                success: false,
                message: 'Webhook do assistente não configurado.'
            });
        }

        const payload = {
            provider: config.provider,
            deviceName: sanitizeString(config.deviceName || '', 120),
            userName: sanitizeString(req.user.name, 120),
            medicationName: safeMedicationName,
            scheduleTime: safeScheduleTime,
            message: safeMessage,
            timestamp: new Date().toISOString()
        };

        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 8000);

        try {
            const response = await fetch(config.webhookUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload),
                signal: controller.signal
            });

            clearTimeout(timeout);

            if (!response.ok) {
                return res.status(502).json({
                    success: false,
                    message: 'Falha ao entregar lembrete para o assistente de voz.'
                });
            }

            return res.json({
                success: true,
                message: 'Lembrete enviado para o assistente de voz.',
                data: { dispatched: true }
            });
        } catch (error) {
            clearTimeout(timeout);
            throw error;
        }
    } catch (error) {
        console.error('Dispatch voice assistant reminder error:', error);
        return res.status(500).json({
            success: false,
            message: 'Erro ao enviar lembrete para o assistente de voz.',
            error: error.message
        });
    }
};

export const sendWhatsAppMessage = async (req, res) => {
    try {
        const { to = '', message = '' } = req.body || {};
        const provider = getWhatsAppProvider();

        if (!isWhatsAppConfigured()) {
            const messageByProvider = provider === 'meta'
                ? 'Meta Cloud API não configurada no servidor. Verifique WHATSAPP_ACCESS_TOKEN e WHATSAPP_PHONE_NUMBER_ID no .env.'
                : 'Evolution API não configurada no servidor. Verifique EVOLUTION_API_URL e EVOLUTION_API_KEY no .env.';
            return res.status(400).json({
                success: false,
                message: messageByProvider
            });
        }

        const normalizedPhone = normalizePhoneNumber(to);
        if (!normalizedPhone || normalizedPhone.length < 10 || normalizedPhone.length > 15) {
            return res.status(400).json({
                success: false,
                message: 'Telefone de destino inválido.'
            });
        }

        if (!message || !String(message).trim()) {
            return res.status(400).json({
                success: false,
                message: 'Mensagem obrigatória.'
            });
        }

        const textMessage = sanitizeString(message, 1000);

        let result;
        if (provider === 'meta') {
            const { phoneNumberId } = getMetaConfig();
            result = await metaRequest(`/${phoneNumberId}/messages`, 'POST', {
                messaging_product: 'whatsapp',
                to: normalizedPhone,
                type: 'text',
                text: { body: textMessage }
            });
        } else {
            const { instance } = getEvolutionConfig();
            result = await evolutionRequest(`/message/sendText/${instance}`, 'POST', {
                number: normalizedPhone,
                text: textMessage
            });
        }

        if (!result.ok) {
            const failureEvent = {
                eventType: 'send-failed',
                provider: provider === 'meta' ? 'meta-cloud-api' : 'evolution-api',
                at: new Date().toISOString(),
                status: 'failed',
                recipient: normalizedPhone,
                error: extractProviderError(result.data, result.status),
                response: result.data
            };
            pushRecentWhatsAppEvent(failureEvent);
            await persistWhatsAppEvent(failureEvent);

            return res.status(502).json({
                success: false,
                message: provider === 'meta'
                    ? 'Falha ao enviar mensagem via Meta Cloud API.'
                    : 'Falha ao enviar mensagem via Evolution API.',
                error: extractProviderError(result.data, result.status)
            });
        }

        const successEvent = {
            eventType: 'send-accepted',
            provider: provider === 'meta' ? 'meta-cloud-api' : 'evolution-api',
            at: new Date().toISOString(),
            messageId: result.data?.messages?.[0]?.id || null,
            status: result.data?.messages?.[0]?.message_status || 'accepted',
            recipient: normalizedPhone,
            response: result.data
        };
        pushRecentWhatsAppEvent(successEvent);
        await persistWhatsAppEvent(successEvent);

        return res.json({
            success: true,
            message: provider === 'meta'
                ? 'Mensagem enviada com sucesso via WhatsApp (Meta Cloud API).'
                : 'Mensagem enviada com sucesso via WhatsApp (Evolution API).',
            data: {
                provider: provider === 'meta' ? 'meta-cloud-api' : 'evolution-api',
                to: normalizedPhone,
                response: result.data
            }
        });
    } catch (error) {
        console.error('Send WhatsApp message error:', error);
        return res.status(500).json({
            success: false,
            message: 'Erro ao enviar mensagem via WhatsApp.',
            error: error.message
        });
    }
};

export const getWhatsAppQRCode = async (req, res) => {
    try {
        const provider = getWhatsAppProvider();

        if (provider === 'meta') {
            return res.status(400).json({
                success: false,
                message: 'QR Code não se aplica ao Meta Cloud API. Esse provedor não usa conexão por QR.'
            });
        }

        if (!isWhatsAppConfigured()) {
            return res.status(400).json({
                success: false,
                message: 'Evolution API não configurada no servidor.'
            });
        }

        const { instance } = getEvolutionConfig();

        // Tenta criar a instância (ignora erro se já existir)
        await evolutionRequest('/instance/create', 'POST', {
            instanceName: instance,
            qrcode: true,
            integration: 'WHATSAPP-BAILEYS'
        });

        // Busca o QR code
        const result = await evolutionRequest(`/instance/connect/${instance}`);

        if (!result.ok) {
            return res.status(502).json({
                success: false,
                message: 'Não foi possível obter o QR code.',
                error: result.data?.message || `status_${result.status}`
            });
        }

        return res.json({
            success: true,
            data: result.data
        });
    } catch (error) {
        console.error('Get WhatsApp QR code error:', error);
        return res.status(500).json({
            success: false,
            message: 'Erro ao obter QR code do WhatsApp.',
            error: error.message
        });
    }
};

export const getWhatsAppStatus = async (req, res) => {
    try {
        const provider = getWhatsAppProvider();

        if (provider === 'meta') {
            const configured = isWhatsAppConfigured();
            return res.json({
                success: true,
                data: {
                    state: configured ? 'configured' : 'not_configured',
                    provider: 'meta-cloud-api'
                }
            });
        }

        if (!isWhatsAppConfigured()) {
            return res.status(400).json({
                success: false,
                message: 'Evolution API não configurada no servidor.'
            });
        }

        const { instance } = getEvolutionConfig();
        const result = await evolutionRequest(`/instance/connectionState/${instance}`);

        return res.json({
            success: true,
            data: result.ok ? result.data : { state: 'unavailable' }
        });
    } catch (error) {
        console.error('Get WhatsApp status error:', error);
        return res.status(500).json({
            success: false,
            message: 'Erro ao verificar status do WhatsApp.',
            error: error.message
        });
    }
};
