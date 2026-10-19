import jwt from 'jsonwebtoken';
import { hasGooglePlayAccess, refreshGooglePlay } from '../services/googlePlay.js';
import { User } from '../models/index.js';

const getJwtSecret = () => {
    const secret = String(process.env.JWT_SECRET || '').trim();
    return secret || null;
};

export const protect = async (req, res, next) => {
    let token;
    const jwtSecret = getJwtSecret();

    if (!jwtSecret) {
        return res.status(500).json({
            success: false,
            message: 'Configuração de autenticação indisponível.'
        });
    }

    // Check for token in Authorization header
    if (req.headers.authorization && req.headers.authorization.startsWith('Bearer')) {
        token = req.headers.authorization.split(' ')[1];
    }

    if (!token) {
        return res.status(401).json({
            success: false,
            message: 'Não autorizado. Token não fornecido.'
        });
    }

    try {
        // Verify token
        const decoded = jwt.verify(token, jwtSecret);

        // Get user from token
        req.user = await User.findByPk(decoded.id);

        if (!req.user) {
            return res.status(401).json({
                success: false,
                message: 'Usuário não encontrado.'
            });
        }

        if (!req.user.isActive) {
            return res.status(401).json({
                success: false,
                message: 'Conta desativada.'
            });
        }

        // Recheck renewals, cancellations and refunds; a failed lookup never extends expiry.
        try { await refreshGooglePlay(req.user); } catch { /* keep last verified access until expiry */ }
        next();
    } catch (error) {
        return res.status(401).json({
            success: false,
            message: 'Token inválido ou expirado.'
        });
    }
};

export const adminOnly = (req, res, next) => {
    if (!req.user?.isAdmin) {
        return res.status(403).json({
            success: false,
            message: 'Acesso restrito a administradores.'
        });
    }
    next();
};

export const TRIAL_DAYS = 7;

/**
 * Calcula os dados de período de teste (7 dias de acesso total) ou assinatura paga.
 * Ao logar no sistema, o usuário tem 7 dias de acesso total; após 7 dias, precisa assinar para ter acesso a tudo.
 */
export const getUserTrialInfo = (user) => {
    if (!user) {
        return {
            isPremium: false,
            isTrialActive: false,
            trialDaysRemaining: 0,
            trialExpired: true,
            isPaid: false,
            trialEndsAt: null,
            firstLoginAt: null
        };
    }

    const isPaid = user.isAdmin === true || user.subscriptionStatus === 'authorized' || hasGooglePlayAccess(user.googlePlaySubscription);
    if (isPaid) {
        return {
            isPremium: true,
            isTrialActive: false,
            trialDaysRemaining: null,
            trialExpired: false,
            isPaid: true,
            trialEndsAt: null,
            firstLoginAt: user.firstLoginAt ? new Date(user.firstLoginAt).toISOString() : null
        };
    }

    // O período de teste começa na data do primeiro login (ou da criação da conta)
    const trialStart = user.firstLoginAt
        ? new Date(user.firstLoginAt)
        : (user.createdAt ? new Date(user.createdAt) : new Date());

    const trialEnd = new Date(trialStart.getTime() + TRIAL_DAYS * 24 * 60 * 60 * 1000);
    const now = new Date();
    const isTrialActive = now < trialEnd;
    const diffMs = trialEnd.getTime() - now.getTime();
    const trialDaysRemaining = Math.max(0, Math.ceil(diffMs / (1000 * 60 * 60 * 24)));

    return {
        isPremium: isTrialActive,
        isTrialActive,
        trialDaysRemaining: isTrialActive ? trialDaysRemaining : 0,
        trialExpired: !isTrialActive,
        isPaid: false,
        trialEndsAt: trialEnd.toISOString(),
        firstLoginAt: trialStart.toISOString()
    };
};

// Temporário: conta gratuita mantém todos os recursos gated no servidor
// (cuidadores, estoque crítico, Alexa). Voltar a false restaura o plano.
export const FREE_HAS_ALL_FEATURES = true;

// Premium = assinatura autorizada, admin OU usuário dentro dos 7 dias de acesso total gratuito.
export const userIsPremium = (user) => FREE_HAS_ALL_FEATURES || getUserTrialInfo(user).isPremium;

export const requirePremium = (req, res, next) => {
    if (!userIsPremium(req.user)) {
        return res.status(402).json({
            success: false,
            message: 'Período de teste de 7 dias finalizado. Assine o plano para continuar com acesso total a todos os recursos.'
        });
    }
    next();
};

// Generate JWT token
export const generateToken = (userId) => {
    const jwtSecret = getJwtSecret();
    if (!jwtSecret) {
        throw new Error('JWT secret not configured');
    }

    return jwt.sign(
        { id: userId },
        jwtSecret,
        { expiresIn: process.env.JWT_EXPIRE || '7d' }
    );
};
