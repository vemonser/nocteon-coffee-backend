-- =========================================================
-- Users
-- Password for all users:
-- (Argon2 Hash)
-- =========================================================

INSERT INTO users
(username, email, password, enabled, provider, role, is_active)
VALUES

('admin',
'admin@aurum.local',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'ADMIN',
TRUE),

('ahmed',
'ahmed@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('mohamed',
'mohamed@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('sara',
'sara@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('nour',
'nour@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('omar',
'omar@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('youssef',
'youssef@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('salma',
'salma@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('mahmoud',
'mahmoud@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('mariam',
'mariam@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE);

-- =========================================================
-- Profiles
-- =========================================================

INSERT INTO user_profiles
(user_id, first_name, last_name, phone, bio)
VALUES

(1,'Admin','Aurum','01000000000','System Administrator'),

(2,'Ahmed','Ali','01011111111','Coffee Lover'),
(3,'Mohamed','Hassan','01022222222','Specialty Coffee Fan'),
(4,'Sara','Khaled','01033333333','Barista'),
(5,'Nour','Adel','01044444444','Coffee Explorer'),
(6,'Omar','Samy','01055555555','Espresso Addict'),
(7,'Youssef','Mostafa','01066666666','V60 Enthusiast'),
(8,'Salma','Maher','01077777777','Latte Artist'),
(9,'Mahmoud','Fathy','01088888888','Home Brewer'),
(10,'Mariam','Tarek','01099999999','Coffee Blogger');

-- =========================================================
-- Addresses
-- =========================================================

INSERT INTO addresses
(user_id, full_name, phone, street, city, state, country, postal_code, is_default)
VALUES

(1,'Admin Aurum','01000000000','Smart Village','Giza','Giza','Egypt','12577',TRUE),

(2,'Ahmed Ali','01011111111','Street 1','Cairo','Cairo','Egypt','11511',TRUE),
(3,'Mohamed Hassan','01022222222','Street 2','Alexandria','Alex','Egypt','21500',TRUE),
(4,'Sara Khaled','01033333333','Street 3','Cairo','Cairo','Egypt','11511',TRUE),
(5,'Nour Adel','01044444444','Street 4','Giza','Giza','Egypt','12511',TRUE),
(6,'Omar Samy','01055555555','Street 5','Mansoura','Dakahlia','Egypt','35511',TRUE),
(7,'Youssef Mostafa','01066666666','Street 6','Tanta','Gharbia','Egypt','31511',TRUE),
(8,'Salma Maher','01077777777','Street 7','Zagazig','Sharqia','Egypt','44511',TRUE),
(9,'Mahmoud Fathy','01088888888','Street 8','Aswan','Aswan','Egypt','81511',TRUE),
(10,'Mariam Tarek','01099999999','Street 9','Luxor','Luxor','Egypt','85511',TRUE);

-- =========================================================
-- Carts
-- =========================================================

INSERT INTO carts(user_id)
VALUES
(1),(2),(3),(4),(5),(6),(7),(8),(9),(10);

-- =========================================================
-- Wishlists
-- =========================================================

INSERT INTO wishlists(user_id)
VALUES
(1),(2),(3),(4),(5),(6),(7),(8),(9),(10);