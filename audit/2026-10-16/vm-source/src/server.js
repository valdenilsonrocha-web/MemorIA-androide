import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import dotenv from 'dotenv';
import rateLimit from 'express-rate-limit';
import { fileURLToPath, pathToFileURL } from 'url';
import { testConnection } from './config/database.js';
import { syncDatabase } from './models/index.js';
import { startWhatsAppReminderService } from './services/whatsappReminderService.js';
import { startWeeklyReportService } from './services/emailService.js';

// Load environment variables
dotenv.config();

// Import routes

import authRoutes from './routes/auth.js';
import medicationRoutes from './routes/medications.js';
import historyRoutes from './routes/history.js';
import adminRoutes from './routes/admin.js';
import paymentRoutes from './routes/payments.js';
import integrationRoutes from './routes/integrations.js';
import prescriptionRoutes from './routes/prescriptions.js';

// Initialize Express app
const app = express();
let databaseStatus = 'unknown';

const allowedOrigins = new Set(
    [
        process.env.CORS_ORIGIN,
        process.env.FRONTEND_URL,
        'http://localhost:8080',
        'http://127.0.0.1:8080',
        'http://localhost',
        'capacitor://localhost',
        'ionic://localhost'
    ]
        .flatMap((value) => (value || '').split(','))
        .map((value) => value.trim())
        .filter(Boolean)
);

const noStoreSensitive = (req, res, next) => {
    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, private');
    res.setHeader('Pragma', 'no-cache');
    res.setHeader('Expires', '0');
    next();
};

// Security middleware
app.use(helmet({
    referrerPolicy: { policy: 'no-referrer' },
    crossOriginResourcePolicy: { policy: 'cross-origin' }
}));

// CORS configuration
app.use(cors({
    origin(origin, callback) {
        if (!origin || allowedOrigins.has(origin)) {
            callback(null, true);
            return;
        }

        callback(new Error('Origin not allowed by CORS'));
    },
    credentials: true
}));

// Rate limiting
const limiter = rateLimit({
    windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS) || 15 * 60 * 1000, // 15 minutes
    max: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS) || 100
});
app.use('/api/', limiter);
app.use('/api/auth', noStoreSensitive);
app.use('/api/payments', noStoreSensitive);

// Body parser middleware
// Webhook Stripe precisa do raw body antes do express.json()
app.use('/api/payments/webhook', express.raw({ type: 'application/json' }));
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({
        status: 'ok',
        database: databaseStatus,
        timestamp: new Date().toISOString(),
        uptime: process.uptime()
    });
});

app.get('/api/health', (req, res) => {
    res.json({
        status: 'ok',
        database: databaseStatus,
        timestamp: new Date().toISOString(),
        uptime: process.uptime()
    });
});

// API routes

app.use('/api/auth', authRoutes);
app.use('/api/medications', medicationRoutes);
app.use('/api/history', historyRoutes);
app.use('/api/admin', adminRoutes);
app.use('/api/payments', paymentRoutes);
app.use('/api/integrations', integrationRoutes);
// Sem esta linha o router, o controller e o modelo existiam — e /api/prescriptions
// respondia 404 a todos os clientes, deixando "Minhas Receitas" morta na web e
// no Android. Qualquer rota nova tem de ser montada aqui, ou e codigo inalcancavel.
app.use('/api/prescriptions', prescriptionRoutes);

// Root endpoint
app.get('/', (req, res) => {
    res.json({
        message: 'MemorIA API - Sistema de Controle de Medicamentos para Saúde Pública',
        version: '1.0.0',
        endpoints: {
            health: '/health',
            auth: '/api/auth',
            medications: '/api/medications',
            history: '/api/history',
            admin: '/api/admin',
            payments: '/api/payments',
            integrations: '/api/integrations',
            prescriptions: '/api/prescriptions'
        }
    });
});

// 404 handler
app.use((req, res) => {
    res.status(404).json({
        success: false,
        message: 'Endpoint não encontrado'
    });
});

// Error handler
app.use((err, req, res, next) => {
    console.error('Error:', err);
    res.status(err.status || 500).json({
        success: false,
        message: err.message || 'Erro interno do servidor',
        ...(process.env.NODE_ENV === 'development' && { stack: err.stack })
    });
});

// Start server
const PORT = parseInt(process.env.PORT || '3001', 10);

const listenAsync = (port) => new Promise((resolve, reject) => {
    const server = app.listen(port, () => resolve(server));
    server.on('error', reject);
});

const startServer = async () => {
    const isProduction = process.env.NODE_ENV === 'production';
    const allowStartWithoutDb = !isProduction && process.env.ALLOW_START_WITHOUT_DB === 'true';

    try {
        // Test database connection
        const dbConnected = await testConnection();

        if (!dbConnected) {
            databaseStatus = 'disconnected';

            if (!allowStartWithoutDb) {
                console.error('Failed to connect to database. Exiting...');
                process.exit(1);
            }

            console.warn('⚠ Banco indisponível. Iniciando backend em modo degradado (ALLOW_START_WITHOUT_DB=true).');
        } else {
            databaseStatus = 'connected';

            // Sync database without destructive alter by default.
            const shouldAlterSchema = !isProduction && process.env.DB_SYNC_ALTER === 'true';
            await syncDatabase(shouldAlterSchema ? { alter: true } : {});
        }

        // Start listening
        try {
            await listenAsync(PORT);
            console.log(`\n✓ Server running on port ${PORT}`);
            console.log(`✓ Environment: ${process.env.NODE_ENV || 'development'}`);
            console.log(`✓ Database status: ${databaseStatus}`);
            console.log(`✓ Health check: http://localhost:${PORT}/health`);
            console.log(`✓ API documentation: http://localhost:${PORT}\n`);

            // Keep-alive: evita que o Render (plano gratuito) hiberne o serviço
            if (process.env.NODE_ENV === 'production') {
                const selfUrl = process.env.RENDER_EXTERNAL_URL
                    ? `${process.env.RENDER_EXTERNAL_URL}/health`
                    : `http://localhost:${PORT}/health`;

                setInterval(async () => {
                    try {
                        await fetch(selfUrl);
                        console.log('[keep-alive] ping ok →', selfUrl);
                    } catch (e) {
                        console.warn('[keep-alive] ping falhou:', e.message);
                    }
                }, 14 * 60 * 1000); // 14 minutos
            }

            // WhatsApp medication reminders (roda em todos os ambientes se configurado)
            startWhatsAppReminderService(5);

            // Relatórios semanais por email aos cuidadores/filhos (Premium)
            startWeeklyReportService();
        } catch (listenError) {
            if (listenError?.code === 'EADDRINUSE') {
                console.warn(`\n⚠ Port ${PORT} is already in use. Another backend instance is likely running.`);
                console.warn('⚠ Keeping current instance untouched and exiting this start command successfully.\n');
                process.exit(0);
            }

            throw listenError;
        }
    } catch (error) {
        console.error('Failed to start server:', error);
        process.exit(1);
    }
};

const entryFilePath = process.argv[1] ? pathToFileURL(process.argv[1]).href : '';
const currentFilePath = import.meta.url;

if (entryFilePath === currentFilePath) {
    startServer();
}

export { app, startServer };
export default app;
