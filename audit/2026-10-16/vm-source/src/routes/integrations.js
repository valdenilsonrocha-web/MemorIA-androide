import express from 'express';
import { body, query } from 'express-validator';
import { protect, adminOnly } from '../middleware/auth.js';
import { handleValidationErrors } from '../middleware/validation.js';
import {
    sendWhatsAppMessage,
    getWhatsAppQRCode,
    getWhatsAppStatus,
    verifyWhatsAppWebhook,
    handleWhatsAppWebhook,
    getRecentWhatsAppEvents,
    getVoiceAssistantConfig,
    updateVoiceAssistantConfig,
    dispatchVoiceAssistantReminder
} from '../controllers/integrationController.js';

const router = express.Router();

// Webhook da Meta precisa permanecer público para verificação/entrega de eventos.
router.get('/whatsapp/webhook', verifyWhatsAppWebhook);
router.post('/whatsapp/webhook', handleWhatsAppWebhook);

// Endpoints operacionais do WhatsApp exigem autenticação.
router.use(protect);

router.post(
    '/whatsapp/send',
    adminOnly,
    [
        body('to')
            .isString()
            .trim()
            .isLength({ min: 8, max: 25 })
            .withMessage('Telefone de destino inválido.'),
        body('message')
            .isString()
            .trim()
            .isLength({ min: 1, max: 1000 })
            .withMessage('Mensagem inválida.')
    ],
    handleValidationErrors,
    sendWhatsAppMessage
);
router.get('/whatsapp/qrcode', adminOnly, getWhatsAppQRCode);
router.get('/whatsapp/status', adminOnly, getWhatsAppStatus);
router.get(
    '/whatsapp/events',
    adminOnly,
    [
        query('limit')
            .optional()
            .isInt({ min: 1, max: 100 })
            .withMessage('Parâmetro limit inválido.')
    ],
    handleValidationErrors,
    getRecentWhatsAppEvents
);

router.route('/voice-assistant/config')
    .get(getVoiceAssistantConfig)
    .put(
        [
            body('enabled')
                .optional()
                .isBoolean()
                .withMessage('Campo enabled inválido.'),
            body('provider')
                .optional()
                .isIn(['alexa', 'google', 'webhook', 'browser-tts'])
                .withMessage('Provedor de assistente inválido.'),
            body('deviceName')
                .optional({ checkFalsy: true })
                .isString()
                .trim()
                .isLength({ max: 120 })
                .withMessage('Nome do dispositivo inválido.'),
            body('webhookUrl')
                .optional({ checkFalsy: true })
                .isURL({ protocols: ['http', 'https'], require_protocol: true })
                .withMessage('URL de webhook inválida.')
        ],
        handleValidationErrors,
        updateVoiceAssistantConfig
    );

router.post(
    '/voice-assistant/dispatch',
    [
        body('message')
            .optional({ checkFalsy: true })
            .isString()
            .trim()
            .isLength({ max: 500 })
            .withMessage('Mensagem inválida.'),
        body('medicationName')
            .optional({ checkFalsy: true })
            .isString()
            .trim()
            .isLength({ max: 255 })
            .withMessage('Nome do medicamento inválido.'),
        body('scheduleTime')
            .optional({ checkFalsy: true })
            .isString()
            .trim()
            .isLength({ max: 20 })
            .withMessage('Horário inválido.')
    ],
    handleValidationErrors,
    dispatchVoiceAssistantReminder
);

export default router;
