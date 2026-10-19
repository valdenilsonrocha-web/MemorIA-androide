import sequelize, { testConnection } from '../config/database.js';
import User from './User.js';
import Medication from './Medication.js';
import MedicationHistory from './MedicationHistory.js';
import Prescription from './Prescription.js';
import WhatsAppEvent from './WhatsAppEvent.js';

// Define relationships
User.hasMany(Medication, {
    foreignKey: 'userId',
    as: 'medications'
});

Medication.belongsTo(User, {
    foreignKey: 'userId',
    as: 'user'
});

User.hasMany(MedicationHistory, {
    foreignKey: 'userId',
    as: 'history'
});

MedicationHistory.belongsTo(User, {
    foreignKey: 'userId',
    as: 'user'
});

Medication.hasMany(MedicationHistory, {
    foreignKey: 'medicationId',
    as: 'history'
});

MedicationHistory.belongsTo(Medication, {
    foreignKey: 'medicationId',
    as: 'medication'
});

User.hasMany(Prescription, {
    foreignKey: 'userId',
    as: 'prescriptions'
});

Prescription.belongsTo(User, {
    foreignKey: 'userId',
    as: 'user'
});

// Sync function to create tables
export const syncDatabase = async (options = {}) => {
    try {
        await sequelize.sync(options);
        console.log('✓ Database synchronized successfully');
        return true;
    } catch (error) {
        if (options?.alter && error?.original?.code === 'ER_TOO_MANY_KEYS') {
            console.warn('⚠ Schema alter skipped because the MySQL table already reached the index limit.');
            console.warn('⚠ Continuing with a safe sync without alter. Use migrations to evolve the schema.');

            try {
                await sequelize.sync();
                console.log('✓ Database synchronized successfully (safe mode)');
                return true;
            } catch (fallbackError) {
                console.error('✗ Error synchronizing database in safe mode:', fallbackError);
                return false;
            }
        }

        console.error('✗ Error synchronizing database:', error);
        return false;
    }
};

export {
    User,
    Medication,
    MedicationHistory,
    Prescription,
    WhatsAppEvent,
    testConnection
};

export default sequelize;
