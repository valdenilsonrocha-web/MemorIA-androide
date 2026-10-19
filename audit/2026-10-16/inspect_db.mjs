import sequelize from './src/config/database.js';
const queries={
 patients:`SELECT COUNT(*)::int AS total, COUNT(*) FILTER (WHERE is_active)::int AS active, COUNT(*) FILTER (WHERE is_active AND phone IS NOT NULL AND phone <> '')::int AS active_with_phone FROM users`,
 phone_shapes:`SELECT length(regexp_replace(phone,'[^0-9]','','g')) AS digits, (phone ~ '^0') AS leading_zero, (phone ~ '^55') AS starts_55, COUNT(*)::int AS count FROM users WHERE is_active AND phone IS NOT NULL AND phone <> '' GROUP BY 1,2,3 ORDER BY 1,2,3`,
 medications:`SELECT active, frequency, COUNT(*)::int AS count FROM medications GROUP BY 1,2 ORDER BY 1,2`,
 eligible:`SELECT COUNT(*)::int AS active_meds, COUNT(DISTINCT m.user_id)::int AS patients FROM medications m JOIN users u ON u.id=m.user_id WHERE m.active AND u.is_active AND u.phone IS NOT NULL AND u.phone<>''`,
 events:`SELECT provider,event_type,status,COUNT(*)::int AS count,MIN(created_at) AS first_at,MAX(created_at) AS last_at FROM whatsapp_events GROUP BY 1,2,3 ORDER BY 4 DESC`,
 events_recent:`SELECT provider,event_type,status,COUNT(*)::int AS count FROM whatsapp_events WHERE created_at >= now()-interval '72 hours' GROUP BY 1,2,3 ORDER BY 4 DESC`
};
try {
 await sequelize.query('BEGIN READ ONLY');
 await sequelize.query("SET LOCAL statement_timeout = '10s'");
 for(const [k,q] of Object.entries(queries)) {const [rows]=await sequelize.query(q); console.log(JSON.stringify({check:k,rows}));}
 await sequelize.query('ROLLBACK');
} catch(e) {console.log(JSON.stringify({error:e.message})); try{await sequelize.query('ROLLBACK');}catch{}}
finally {await sequelize.close();}
