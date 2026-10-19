-- ============================================================
-- V108: Seed login activities
-- ============================================================

-- 50 login activities for the 5 sample users
-- Table structure: id, user_id, device_type, created_at, updated_at

-- Login activity 1: User 1, Windows (30 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', NOW() - INTERVAL '30 days';

-- Login activity 2: User 2, Mac (28 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'MAC', NOW() - INTERVAL '28 days';

-- Login activity 3: User 3, Linux (26 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'LINUX', NOW() - INTERVAL '26 days';

-- Login activity 4: User 4, Windows (24 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 4, 'WINDOWS', NOW() - INTERVAL '24 days';

-- Login activity 5: User 5, Mac (22 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 5, 'MAC', NOW() - INTERVAL '22 days';

-- Login activity 6: User 1, iPhone (20 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'IOS', NOW() - INTERVAL '20 days';

-- Login activity 7: User 2, Linux (18 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'LINUX', NOW() - INTERVAL '18 days';

-- Login activity 8: User 3, Windows (16 days ago, suspicious)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'WINDOWS', NOW() - INTERVAL '16 days';

-- Login activity 9: User 4, office Windows (14 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 4, 'WINDOWS', NOW() - INTERVAL '14 days';

-- Login activity 10: User 5, home Mac (12 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 5, 'MAC', NOW() - INTERVAL '12 days';

-- Login activity 11: User 1, Mac (10 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'MAC', NOW() - INTERVAL '10 days';

-- Login activity 12: User 2, Linux (8 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'LINUX', NOW() - INTERVAL '8 days';

-- Login activity 13: User 3, Windows (6 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'WINDOWS', NOW() - INTERVAL '6 days';

-- Login activity 14: User 4, failed login (4 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 4, 'WINDOWS', NOW() - INTERVAL '4 days';

-- Login activity 15: User 5, iPhone (2 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 5, 'IOS', NOW() - INTERVAL '2 days';

-- Login activity 16: User 1, Windows (1 day ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', NOW() - INTERVAL '1 day';

-- Login activity 17: User 2, Mac (recent, 2 hours ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'MAC', NOW() - INTERVAL '2 hours';

-- Login activity 18: User 3, Linux (recent, 1 hour ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'LINUX', NOW() - INTERVAL '1 hour';

-- Login activity 19: User 4, Windows (recent, 30 min ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 4, 'WINDOWS', NOW() - INTERVAL '30 minute';

-- Login activity 20: User 5, Mac (recent, 10 min ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 5, 'MAC', NOW() - INTERVAL '10 minute';

-- Login activity 21: Admin login (user 1, 5 min ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', NOW() - INTERVAL '5 minute';

-- Login activity 22: Admin login (user 1, 3 min ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', NOW() - INTERVAL '3 minute';

-- Login activity 23: Admin login (user 1, 1 min ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', NOW() - INTERVAL '1 minute';

-- Login activity 24: Admin login (user 1, just now)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', NOW();

-- Login activities 25-50: Additional login activities for remaining users
-- User 1 patterns
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', NOW() - INTERVAL '15 minute';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'MAC', NOW() - INTERVAL '30 minute';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'IOS', NOW() - INTERVAL '45 minute';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'LINUX', NOW() - INTERVAL '1 hour';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', NOW() - INTERVAL '30 minute';

-- User 2 patterns
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'MAC', NOW() - INTERVAL '1 hour';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'LINUX', NOW() - INTERVAL '2 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'IOS', NOW() - INTERVAL '3 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'WINDOWS', NOW() - INTERVAL '4 hours';

-- User 3 patterns
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'WINDOWS', NOW() - INTERVAL '3 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'LINUX', NOW() - INTERVAL '4 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'IOS', NOW() - INTERVAL '5 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'WINDOWS', NOW() - INTERVAL '6 hours';

-- User 4 patterns
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 4, 'WINDOWS', NOW() - INTERVAL '7 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 4, 'MAC', NOW() - INTERVAL '8 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 4, 'WINDOWS', NOW() - INTERVAL '9 hours';

-- User 5 patterns
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 5, 'MAC', NOW() - INTERVAL '10 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 5, 'IOS', NOW() - INTERVAL '12 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 5, 'LINUX', NOW() - INTERVAL '15 hours';

-- ----------------------------------------------------------
-- End of V108 seed data migration
-- ============================================================