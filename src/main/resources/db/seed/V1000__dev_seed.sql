-- Dữ liệu mẫu 7 ngày (chỉ nạp ở profile dev; mật khẩu 123456 chỉ dành cho dữ liệu mẫu).
-- Version 1000+ để không đụng số version của migration schema. Đừng sửa file đã chạy; thêm file mới.
INSERT INTO employees (id, email, password_hash, full_name, role, enabled, created_at, updated_at)
SELECT md5('restosphere:demo:' || account)::uuid, account || '@restosphere.test',
       '$2b$10$LTsVxyCfFG2CRk86pJ6HKO9vBqu9FVik7RgWPNgT0jYZPLvPxU54W',
       full_name, role, true, now(), now()
FROM (VALUES
('admin', 'Quản trị demo', 'ADMIN'),
('manager', 'Quản lý demo', 'MANAGER'),
('cashier', 'Thu ngân demo', 'CASHIER'),
('kitchen', 'Đầu bếp demo', 'KITCHEN'),
('receptionist', 'Lễ tân demo', 'RECEPTIONIST'),
('waiter', 'Phục vụ demo', 'WAITER'),
('waiter2', 'Phục vụ demo 2', 'WAITER')
) AS accounts(account, full_name, role)
ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash
WHERE employees.email = EXCLUDED.email;

INSERT INTO areas (id, name, created_at, updated_at) VALUES
('10000000-0000-0000-0000-000000000001', 'Khu A · Tầng trệt', now(), now()),
('10000000-0000-0000-0000-000000000002', 'Khu B · Tầng 1', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO restaurant_tables (id, code, area_id, capacity, status, created_at, updated_at)
SELECT md5('restosphere:table:' || zone || n)::uuid,
       zone || lpad(n::text, 2, '0'), a.id,
       CASE WHEN (n - 1) % 3 = 0 THEN 6 ELSE 4 END,
       'AVAILABLE', now(), now()
FROM (VALUES ('A', 'Khu A · Tầng trệt'), ('B', 'Khu B · Tầng 1')) AS zones(zone, area_name)
JOIN areas a ON a.name = zones.area_name
CROSS JOIN generate_series(1, 12) AS n
ON CONFLICT DO NOTHING;

INSERT INTO menu_categories (id, name, sort_order, visible, created_at, updated_at)
SELECT md5('restosphere:category:' || slug)::uuid, name, sort_order, true, now(), now()
FROM (VALUES
('signature', 'Đặc sản', 1), ('crab-prawn', 'Cua & tôm', 2),
('squid-fish', 'Mực & cá', 3), ('sharing', 'Món dùng chung', 4),
('rice', 'Cơm & mì', 5), ('hotpot', 'Lẩu', 6), ('main', 'Món khác', 7),
('coffee-tea', 'Cà phê & trà', 8), ('juice', 'Nước ép', 9), ('soft', 'Bia & nước suối', 10)
) AS categories(slug, name, sort_order)
ON CONFLICT DO NOTHING;

INSERT INTO menu_items (id, name, category_id, price, description, image_url, status, created_at, updated_at)
SELECT md5('restosphere:menu:' || slug)::uuid, dishes.name, c.id, price,
       description, NULL, status, now(), now()
FROM (VALUES
('king-crab', 'Cua hoàng đế', 'Đặc sản', 2490000, 'Giá/kg. Thịt chân cua chắc, ngọt đậm; phù hợp 3–5 người.', 'AVAILABLE'),
('lobster', 'Tôm hùm bông', 'Đặc sản', 1890000, 'Giá/kg. Tôm hùm tươi sống, thịt dai ngọt.', 'AVAILABLE'),
('garlic-prawns', 'Tôm sốt tỏi', 'Đặc sản', 320000, 'Giá/phần. Tôm sú áp chảo với bơ tỏi, ăn cùng bánh mì.', 'AVAILABLE'),
('steamed-crab', 'Cua hấp', 'Cua & tôm', 890000, 'Giá/kg. Cua thịt Cà Mau hấp bia.', 'AVAILABLE'),
('grilled-shrimp', 'Tôm nướng', 'Cua & tôm', 280000, 'Giá/phần. Tôm nướng than hoa, phết sa tế nhẹ.', 'AVAILABLE'),
('grilled-squid', 'Mực nướng', 'Mực & cá', 260000, 'Giá/phần. Mực ống một nắng nướng thơm.', 'AVAILABLE'),
('steamed-squid', 'Mực hấp gừng', 'Mực & cá', 240000, 'Giá/phần. Mực tươi hấp gừng sả.', 'AVAILABLE'),
('grilled-fish', 'Cá nướng', 'Mực & cá', 350000, 'Giá/phần. Cá chẽm nguyên con nướng giấy bạc.', 'AVAILABLE'),
('seafood-platter', 'Mâm hải sản', 'Món dùng chung', 1590000, 'Giá/phần. Tôm, cua, mực, nghêu và sò điệp cho 4–6 người.', 'AVAILABLE'),
('seafood-ceviche', 'Gỏi hải sản chanh', 'Món dùng chung', 210000, 'Giá/phần. Tôm, mực tái chanh với hành tím, ớt và rau thơm.', 'AVAILABLE'),
('seafood-rice', 'Cơm chiên hải sản', 'Cơm & mì', 150000, 'Giá/phần. Cơm chiên với tôm, mực và trứng.', 'AVAILABLE'),
('noodles', 'Mì xào hải sản', 'Cơm & mì', 140000, 'Giá/phần. Mì trứng xào với hải sản và rau cải.', 'AVAILABLE'),
('thai-pot', 'Lẩu Thái hải sản', 'Lẩu', 450000, 'Giá/phần. Lẩu chua cay sả, lá chanh với tôm, mực, nghêu.', 'AVAILABLE'),
('old-pot', 'Lẩu nấm', 'Lẩu', 380000, 'Giá/phần. Lẩu nấm thanh ngọt.', 'DISCONTINUED'),
('beef', 'Bò lúc lắc', 'Món khác', 220000, 'Giá/phần. Thăn bò xào với ớt chuông và hành tây.', 'AVAILABLE'),
('chicken', 'Gà nướng muối ớt', 'Món khác', 260000, 'Giá/phần. Gà ta nướng muối ớt.', 'AVAILABLE'),
('peach-tea', 'Trà đào', 'Cà phê & trà', 55000, 'Giá/ly. Trà đen ủ lạnh với đào miếng và sả.', 'AVAILABLE'),
('lemonade', 'Nước chanh', 'Nước ép', 45000, 'Giá/ly. Chanh tươi vắt, ít đường theo yêu cầu.', 'AVAILABLE'),
('orange-juice', 'Nước cam', 'Nước ép', 60000, 'Giá/ly. Cam sành ép tươi mỗi ngày.', 'AVAILABLE'),
('iced-coffee', 'Cà phê sữa đá', 'Cà phê & trà', 45000, 'Giá/ly. Cà phê phin pha với sữa đặc.', 'AVAILABLE'),
('ginger-tea', 'Trà gừng', 'Cà phê & trà', 40000, 'Giá/ly. Gừng tươi với mật ong.', 'AVAILABLE'),
('water', 'Nước suối', 'Bia & nước suối', 20000, 'Giá/chai 500 ml.', 'AVAILABLE'),
('beer', 'Bia tươi', 'Bia & nước suối', 35000, 'Giá/ly. Chỉ phục vụ khách từ 18 tuổi.', 'AVAILABLE')
) AS dishes(slug, name, category_name, price, description, status)
JOIN menu_categories c ON c.name = dishes.category_name
ON CONFLICT DO NOTHING;

UPDATE menu_categories SET menu_group = 'drink'
WHERE id IN (md5('restosphere:category:coffee-tea')::uuid, md5('restosphere:category:juice')::uuid, md5('restosphere:category:soft')::uuid);

UPDATE menu_items SET price_unit = CASE
WHEN description LIKE 'Giá/kg.%' THEN 'kg'
WHEN description LIKE 'Giá/ly.%' THEN 'glass'
WHEN description LIKE 'Giá/chai%' THEN 'bottle'
ELSE 'portion' END
WHERE id IN (SELECT md5('restosphere:menu:' || slug)::uuid FROM unnest(ARRAY[
'king-crab','lobster','garlic-prawns','steamed-crab','grilled-shrimp','grilled-squid','steamed-squid','grilled-fish',
'seafood-platter','seafood-ceviche','seafood-rice','noodles','thai-pot','old-pot','beef','chicken',
'peach-tea','lemonade','orange-juice','iced-coffee','ginger-tea','water','beer']) AS slug);

UPDATE menu_items m
SET image_url = 'https://res.cloudinary.com/dkm4rdx48/image/upload/v' || photos.version || '/restosphere/menu/' || photos.slug || '.jpg',
    updated_at = now()
FROM (VALUES
('king-crab', '1794883926'), ('lobster', '1794883929'),
('garlic-prawns', '1794883932'), ('steamed-crab', '1794883934'),
('grilled-shrimp', '1794883937'), ('grilled-squid', '1794883940'),
('steamed-squid', '1794883942'), ('grilled-fish', '1794883945'),
('seafood-platter', '1794883948'), ('seafood-ceviche', '1794883951'),
('seafood-rice', '1794883954'), ('noodles', '1794883957'),
('thai-pot', '1794883959'), ('beef', '1794883962'),
('chicken', '1794883964'), ('peach-tea', '1794883966'),
('lemonade', '1794883968'), ('orange-juice', '1794883970'),
('iced-coffee', '1794883972'), ('ginger-tea', '1794883974'),
('water', '1794883976'), ('beer', '1794883978'), ('old-pot', '1794883980')
) AS photos(slug, version)
WHERE m.id = md5('restosphere:menu:' || photos.slug)::uuid AND m.image_url IS NULL;

INSERT INTO audit_logs (id, actor_id, action, target_type, target_id, description, created_at)
VALUES (md5('restosphere:seed:anchor')::uuid, md5('restosphere:demo:admin')::uuid,
        'DEMO_SEED', 'USER', md5('restosphere:demo:admin')::uuid,
        'Seven-day demo anchor; retain this row when reapplying the dataset.', now())
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE demo_cases ON COMMIT DROP AS
SELECT n, CASE WHEN n <= 18 THEN (n - 1) / 3 - 6 ELSE 0 END AS day,
       CASE WHEN n <= 18 THEN 'A' || lpad(((n - 1) % 3 + 1)::text, 2, '0')
            ELSE 'B' || lpad((n - 18)::text, 2, '0') END AS table_code,
       a.created_at + CASE WHEN n <= 18 THEN ((n - 1) / 3 - 6) * interval '1 day'
                          + ((n - 1) % 3 - 4) * interval '1 hour'
                          ELSE interval '-90 minutes' END AS starts_at,
       a.created_at AS anchor
FROM audit_logs a CROSS JOIN generate_series(1, 26) n
WHERE a.id = md5('restosphere:seed:anchor')::uuid;

INSERT INTO restaurant_settings
(id, singleton_key, name, address, phone, hold_minutes, no_show_minutes, vat_rate, service_charge_rate,
 online_reservations, buffer_minutes, timezone, currency, email_notifications, revision,
 cashier_discount_percent, manager_approval_limit, opening_hours, created_at, updated_at)
SELECT md5('restosphere:seed:settings')::uuid, 1, 'RestoSphere Demo', '12 Nguyễn Huệ, TP. Hồ Chí Minh',
       '02800000000', 15, 30, 8, 5, true, 15, 'Asia/Ho_Chi_Minh', 'VND', true, 0, 10, 10000000,
       '10:00–23:00', anchor, anchor FROM demo_cases WHERE n = 1
ON CONFLICT DO NOTHING;

INSERT INTO shifts (id, name, starts_at, ends_at, minimum_staff, created_at, updated_at)
SELECT md5('restosphere:seed:shift:' || d)::uuid, 'DEMO · Ca phục vụ ' || (d + 7),
       anchor + d * interval '1 day' - interval '5 hours',
       anchor + d * interval '1 day' + interval '6 hours', 5,
       anchor - interval '7 days', anchor - interval '7 days'
FROM demo_cases CROSS JOIN generate_series(-6, 0) d WHERE n = 1
ON CONFLICT DO NOTHING;

INSERT INTO shift_assignments (id, shift_id, employee_id, created_at)
SELECT md5('restosphere:seed:staff:' || d || ':' || account)::uuid,
       md5('restosphere:seed:shift:' || d)::uuid, md5('restosphere:demo:' || account)::uuid,
       anchor - interval '7 days'
FROM demo_cases CROSS JOIN generate_series(-6, 0) d
CROSS JOIN unnest(ARRAY['manager','cashier','kitchen','receptionist','waiter','waiter2']) account WHERE n = 1
ON CONFLICT DO NOTHING;

INSERT INTO cashier_shifts
(id, scheduled_shift_id, cashier_id, opened_at, closed_at, opening_cash, expected_cash, counted_cash, note, created_at, updated_at)
SELECT md5('restosphere:seed:cashier:' || d)::uuid, md5('restosphere:seed:shift:' || d)::uuid,
       md5('restosphere:demo:cashier')::uuid, anchor + d * interval '1 day' - interval '5 hours',
       CASE WHEN d < 0 THEN anchor + d * interval '1 day' + interval '6 hours' END,
       1000000, NULL, NULL, 'DEMO · Quỹ đầu ca',
       anchor + d * interval '1 day' - interval '5 hours', anchor + d * interval '1 day' - interval '5 hours'
FROM demo_cases CROSS JOIN generate_series(-6, 0) d WHERE n = 1
ON CONFLICT DO NOTHING;

INSERT INTO reservations
(id, code, customer_name, phone, email, area_id, guest_count, actual_guests, starts_at, ends_at,
 status, source, arrived_at, handed_over_at, occasion, special_requests, created_at, updated_at)
SELECT md5('restosphere:seed:reservation:' || n)::uuid, 'DEMO-R-' || lpad(n::text, 3, '0'),
       (ARRAY['Nguyễn Minh Anh','Trần Hoàng Nam','Lê Thanh Hà','Phạm Gia Linh'])[(n - 1) % 4 + 1],
       '090000' || lpad(n::text, 4, '0'), 'guest' || n || '@restosphere.test',
       t.area_id, 4, 4, c.starts_at, c.starts_at + interval '2 hours',
       CASE WHEN n <= 18 OR n = 26 THEN 'COMPLETED' ELSE 'SEATED' END,
       CASE WHEN n % 2 = 0 THEN 'PHONE' ELSE 'WEB' END,
       c.starts_at, c.starts_at + interval '2 minutes',
       CASE WHEN n % 3 = 0 THEN 'Sinh nhật' ELSE 'Bữa ăn gia đình' END,
       CASE WHEN n % 3 = 0 THEN 'Không dùng đậu phộng' ELSE 'Ít cay' END,
       c.starts_at - interval '1 day', c.starts_at
FROM demo_cases c JOIN restaurant_tables t ON t.id = md5('restosphere:table:' || left(table_code, 1) || substring(table_code from 2)::int)::uuid
ON CONFLICT DO NOTHING;

INSERT INTO reservations
(id, code, customer_name, phone, area_id, guest_count, starts_at, ends_at, status, source, note, created_at, updated_at)
SELECT md5('restosphere:seed:reservation:' || k)::uuid, 'DEMO-R-' || k,
       'Khách đặt bàn demo ' || k, '090000' || lpad(k::text, 4, '0'),
       '10000000-0000-0000-0000-000000000001'::uuid, 4,
       anchor + hours * interval '1 hour', anchor + (hours + 2) * interval '1 hour',
       status, 'WEB', reason, anchor - interval '2 days', anchor - interval '10 minutes'
FROM demo_cases CROSS JOIN (VALUES
(27, 'PENDING', 3, 'Chờ lễ tân xác nhận'), (28, 'CONFIRMED', 2, 'Đã xác nhận bàn A05'),
(29, 'ARRIVED', 0, 'Khách đang chờ nhận bàn A06'), (30, 'NO_SHOW', -24, 'Không đến; đã gọi hai lần'),
(31, 'CANCELLED', -48, 'Khách đổi lịch'), (32, 'REJECTED', -72, 'Không còn bàn theo yêu cầu')
) extra(k, status, hours, reason) WHERE n = 1
ON CONFLICT DO NOTHING;

INSERT INTO reservation_tables (id, reservation_id, table_id, created_at)
SELECT md5('restosphere:seed:assignment:' || n)::uuid, md5('restosphere:seed:reservation:' || n)::uuid,
       md5('restosphere:table:' || left(table_code, 1) || substring(table_code from 2)::int)::uuid, starts_at - interval '1 day'
FROM demo_cases
UNION ALL
SELECT md5('restosphere:seed:assignment:' || k)::uuid, md5('restosphere:seed:reservation:' || k)::uuid,
       md5('restosphere:table:A' || (k - 23))::uuid, anchor - interval '1 hour'
FROM demo_cases CROSS JOIN generate_series(28, 29) k WHERE n = 1
ON CONFLICT DO NOTHING;

INSERT INTO reservation_items (id, reservation_id, menu_item_id, quantity, unit_price, note, created_at, updated_at)
SELECT md5('restosphere:seed:preorder:' || k)::uuid, r.id, m.id, 1, m.price, 'Ít cay', r.created_at, r.created_at
FROM generate_series(1, 29) k JOIN reservations r ON r.id = md5('restosphere:seed:reservation:' || k)::uuid
CROSS JOIN menu_items m WHERE m.id = md5('restosphere:menu:seafood-rice')::uuid
ON CONFLICT DO NOTHING;

INSERT INTO audit_logs
(id, target_type, target_id, actor_id, action, description, created_at)
SELECT md5('restosphere:seed:history:' || k || ':' || step)::uuid, 'RESERVATION', r.id,
       md5('restosphere:demo:receptionist')::uuid,
       CASE WHEN step = 0 THEN 'CREATE' ELSE r.status END,
       CASE WHEN step = 0 THEN 'Tiếp nhận đặt bàn demo' ELSE 'Cập nhật đặt bàn: ' || r.status END,
       CASE WHEN step = 0 THEN r.created_at ELSE r.updated_at END
FROM generate_series(1, 32) k JOIN reservations r ON r.id = md5('restosphere:seed:reservation:' || k)::uuid
CROSS JOIN generate_series(0, 1) step WHERE step = 0 OR r.status <> 'PENDING'
ON CONFLICT DO NOTHING;

INSERT INTO bills
(id, version, code, subtotal, discount, service_charge, tax_amount, total, status,
 discount_mode, discount_value, discount_reason, discount_applied_by_id, discount_applied_at, requested_at, paid_at, created_at, updated_at)
SELECT md5('restosphere:seed:bill:' || n)::uuid, 0, 'DEMO-B-' || lpad(n::text, 3, '0'), 0, 0, 0, 0, 0,
       CASE WHEN n <= 18 THEN 'PAID' WHEN n = 26 THEN 'VOIDED' ELSE 'OPEN' END,
       CASE WHEN n % 3 = 0 THEN 'PERCENT' END, CASE WHEN n % 3 = 0 THEN 5 END,
       CASE WHEN n % 3 = 0 THEN 'Ưu đãi khách thân thiết' END,
       CASE WHEN n % 3 = 0 THEN md5('restosphere:demo:cashier')::uuid END,
       CASE WHEN n % 3 = 0 THEN starts_at + interval '40 minutes' END,
       CASE WHEN n <= 18 OR n >= 23 THEN starts_at + interval '45 minutes' END,
       CASE WHEN n <= 18 THEN starts_at + interval '60 minutes' END,
       starts_at, starts_at + interval '45 minutes'
FROM demo_cases
ON CONFLICT DO NOTHING;

WITH inserted AS (
INSERT INTO table_sessions
(id, bill_id, table_id, reservation_id, waiter_id, guest_count, started_at, accepted_at, ended_at, guest_left_at, status, created_at, updated_at)
SELECT md5('restosphere:seed:session:' || n)::uuid, md5('restosphere:seed:bill:' || n)::uuid, md5('restosphere:table:' || left(table_code, 1) || substring(table_code from 2)::int)::uuid,
       md5('restosphere:seed:reservation:' || n)::uuid,
       md5('restosphere:demo:' || CASE WHEN n % 2 = 0 THEN 'waiter2' ELSE 'waiter' END)::uuid,
       4, starts_at, starts_at + interval '3 minutes',
       CASE WHEN n <= 18 OR n = 26 THEN starts_at + interval '75 minutes' END,
       CASE WHEN n <= 18 OR n = 26 THEN starts_at + interval '80 minutes' END,
       CASE WHEN n <= 18 OR n = 26 THEN 'CLOSED' WHEN n IN (23,24,25) THEN 'PAYMENT_REQUESTED' ELSE 'OPEN' END,
       starts_at, starts_at FROM demo_cases
ON CONFLICT DO NOTHING RETURNING table_id, status
)
UPDATE restaurant_tables t SET status = 'OCCUPIED'
FROM inserted i WHERE t.id = i.table_id AND i.status <> 'CLOSED' AND t.status = 'AVAILABLE';

INSERT INTO orders (id, session_id, waiter_id, status, round_number, submitted_at, priority_at, priority_by_id, priority_reason, created_at, updated_at)
SELECT md5('restosphere:seed:order:' || n || ':' || round)::uuid, s.id, s.waiter_id, 'SUBMITTED', round,
       c.starts_at + round * interval '5 minutes',
       CASE WHEN n = 20 AND round = 1 THEN starts_at + interval '20 minutes' END,
       CASE WHEN n = 20 AND round = 1 THEN md5('restosphere:demo:waiter2')::uuid END,
       CASE WHEN n = 20 AND round = 1 THEN 'Khách có trẻ nhỏ, ưu tiên món cơm' END,
       c.starts_at + round * interval '5 minutes', c.starts_at + round * interval '5 minutes'
FROM demo_cases c JOIN table_sessions s ON s.id = md5('restosphere:seed:session:' || n)::uuid
CROSS JOIN generate_series(1, 2) round
ON CONFLICT DO NOTHING;

INSERT INTO order_items
(id, version, order_id, menu_item_id, item_name, unit, quantity, unit_price, status,
 chef_id, started_at, ready_at, served_at, rejection_reason, note, created_at, updated_at)
SELECT md5('restosphere:seed:item:' || n || ':' || slot)::uuid, 0,
       md5('restosphere:seed:order:' || n || ':' || CASE WHEN slot <= 2 THEN 1 ELSE 2 END)::uuid,
       m.id, m.name, m.price_unit, CASE WHEN slot = 2 THEN 4 WHEN slot = 3 THEN 0.375 ELSE 1 END,
       m.price, state,
       CASE WHEN state IN ('PREPARING','READY','SERVED','VOIDED') THEN md5('restosphere:demo:kitchen')::uuid END,
       CASE WHEN state IN ('PREPARING','READY','SERVED','VOIDED') THEN starts_at + interval '15 minutes' END,
       CASE WHEN state IN ('READY','SERVED','VOIDED') THEN starts_at + interval '25 minutes' END,
       CASE WHEN state IN ('SERVED','VOIDED') THEN starts_at + interval '30 minutes' END,
       CASE WHEN state = 'REJECTED' THEN 'Không đủ nguyên liệu' END,
       CASE WHEN state = 'CANCELLED' THEN 'Khách bỏ món trước khi chế biến'
            WHEN state = 'VOIDED' THEN 'Món không đạt chất lượng, quản lý duyệt bỏ tính tiền' ELSE 'Ít cay' END,
       starts_at + CASE WHEN slot <= 2 THEN 5 ELSE 10 END * interval '1 minute',
       starts_at + interval '30 minutes'
FROM demo_cases CROSS JOIN (VALUES (1,'seafood-rice'),(2,'water'),(3,'grilled-shrimp'),(4,'peach-tea')) dishes(slot, slug)
JOIN menu_items m ON m.id = md5('restosphere:menu:' || slug)::uuid
CROSS JOIN LATERAL (SELECT CASE
 WHEN n = 26 THEN 'CANCELLED'
 WHEN n = 19 AND slot = 1 THEN 'SUBMITTED'
 WHEN n = 20 AND slot = 1 THEN 'PREPARING'
 WHEN n = 21 AND slot = 1 THEN 'READY'
 WHEN n = 22 AND slot = 4 THEN 'REJECTED'
 WHEN n % 5 = 0 AND slot = 4 THEN 'VOIDED'
 WHEN n % 4 = 0 AND slot = 4 THEN 'CANCELLED'
 ELSE 'SERVED' END AS state) states
ON CONFLICT DO NOTHING;

INSERT INTO orders (id, session_id, waiter_id, status, round_number, created_at, updated_at)
SELECT md5('restosphere:seed:cart')::uuid, s.id, s.waiter_id, 'DRAFT', NULL,
       anchor - interval '5 minutes', anchor - interval '5 minutes'
FROM demo_cases c JOIN table_sessions s ON s.id = md5('restosphere:seed:session:19')::uuid WHERE n = 19
ON CONFLICT DO NOTHING;

INSERT INTO order_items (id, version, order_id, menu_item_id, item_name, unit, quantity, unit_price, status, note, created_at, updated_at)
SELECT md5('restosphere:seed:draft')::uuid, 0, md5('restosphere:seed:cart')::uuid, m.id, m.name, m.price_unit, 2, m.price, 'DRAFT',
       'Không đường, chưa gửi bếp', anchor - interval '5 minutes', anchor - interval '5 minutes'
FROM demo_cases c JOIN menu_items m ON m.id = md5('restosphere:menu:lemonade')::uuid WHERE n = 19
ON CONFLICT DO NOTHING;

UPDATE bills b SET subtotal = x.subtotal, discount = x.discount, service_charge = x.service,
       tax_amount = x.tax, total = x.subtotal - x.discount + x.service + x.tax
FROM (SELECT n, subtotal, discount, service, tax
 FROM demo_cases c CROSS JOIN restaurant_settings settings
 CROSS JOIN LATERAL (SELECT coalesce(sum(round(i.quantity * i.unit_price)),0) AS subtotal
  FROM order_items i JOIN orders o ON o.id = i.order_id
  WHERE o.session_id = md5('restosphere:seed:session:' || n)::uuid
  AND i.status NOT IN ('DRAFT','CANCELLED','REJECTED','VOIDED')) sums
 CROSS JOIN LATERAL (SELECT CASE WHEN n % 3 = 0 THEN round(subtotal * 0.05) ELSE 0 END AS discount) discounts
 CROSS JOIN LATERAL (SELECT round((subtotal - discount) * settings.service_charge_rate / 100) AS service) charges
 CROSS JOIN LATERAL (SELECT round((subtotal - discount + service) * settings.vat_rate / 100) AS tax) taxes
 WHERE settings.singleton_key = 1) x
WHERE b.id = md5('restosphere:seed:bill:' || x.n)::uuid;
INSERT INTO payments
(id, bill_id, cashier_shift_id, amount, method, status, tendered, created_by_id, verified_by_id,
 transaction_reference, idempotency_key, paid_at, note, created_at, updated_at)
SELECT md5('restosphere:seed:payment:' || n || ':' || attempt)::uuid, b.id,
       md5('restosphere:seed:cashier:' || day)::uuid,
       CASE WHEN n IN (23,24) THEN CASE WHEN attempt = 1 THEN floor(b.total / 2) ELSE b.total - floor(b.total / 2) END ELSE b.total END,
       CASE WHEN n % 2 = 0 OR attempt > 1 THEN 'MANUAL_TRANSFER' ELSE 'CASH' END, state,
       CASE WHEN n % 2 <> 0 AND attempt = 1 THEN ceil(b.total / 100000) * 100000 END,
       md5('restosphere:demo:cashier')::uuid,
       CASE WHEN state = 'COMPLETED' AND (n % 2 = 0 OR attempt > 1) THEN md5('restosphere:demo:cashier')::uuid END,
       CASE WHEN n % 2 = 0 OR attempt > 1 THEN 'DEMO-TX-' || n || '-' || attempt END,
       'DEMO-PAY-' || n || '-' || attempt,
       CASE WHEN state IN ('COMPLETED','VOIDED') THEN starts_at + interval '60 minutes' END,
       CASE WHEN state = 'VOIDED' THEN 'Ghi nhận nhầm; quản lý đã duyệt hủy'
            WHEN state = 'CANCELLED' THEN 'Khách đổi phương thức thanh toán'
            WHEN state = 'FAILED' THEN 'Ngân hàng từ chối giao dịch' ELSE 'Thanh toán demo' END,
       starts_at + (50 + attempt * 5) * interval '1 minute', starts_at + interval '65 minutes'
FROM demo_cases c JOIN bills b ON b.id = md5('restosphere:seed:bill:' || n)::uuid
CROSS JOIN generate_series(1, 3) attempt
CROSS JOIN LATERAL (SELECT CASE WHEN n <= 18 THEN 'COMPLETED'
 WHEN n IN (23,24) AND attempt = 1 THEN 'COMPLETED'
 WHEN n = 24 AND attempt = 2 THEN 'PENDING'
 WHEN n = 25 AND attempt = 1 THEN 'VOIDED'
 WHEN n = 25 AND attempt = 2 THEN 'CANCELLED' ELSE 'FAILED' END AS state) states
WHERE (n <= 18 AND attempt = 1) OR (n = 23 AND attempt = 1) OR (n = 24 AND attempt <= 2) OR n = 25
ON CONFLICT DO NOTHING;

UPDATE cashier_shifts cs SET expected_cash = x.expected,
 counted_cash = CASE WHEN cs.closed_at IS NOT NULL THEN x.expected END
FROM (SELECT md5('restosphere:seed:cashier:' || d)::uuid AS id,
 1000000 + coalesce(sum(p.amount) FILTER (WHERE p.method = 'CASH' AND p.status = 'COMPLETED'),0) AS expected
 FROM generate_series(-6,0) d LEFT JOIN payments p ON p.cashier_shift_id = md5('restosphere:seed:cashier:' || d)::uuid
 GROUP BY d) x WHERE cs.id = x.id AND cs.expected_cash IS NULL;

INSERT INTO approval_requests
(id, type, status, requested_by, decided_by, target_type, target_id, amount, reason,
 discount_mode, discount_value, decision_note, decided_at, created_at, updated_at)
SELECT md5('restosphere:seed:approval:' || k)::uuid, type, status, md5('restosphere:demo:cashier')::uuid,
       CASE WHEN status IN ('APPROVED','REJECTED') THEN md5('restosphere:demo:manager')::uuid END,
       target_type, md5(target_key)::uuid,
       CASE WHEN type = 'PAYMENT_VOID' THEN (SELECT p.amount FROM payments p WHERE p.id = md5(target_key)::uuid)
            ELSE amount END, reason,
       CASE WHEN type = 'DISCOUNT' THEN 'PERCENT' END, CASE WHEN type = 'DISCOUNT' THEN 15 END,
       CASE WHEN status = 'APPROVED' THEN 'Đã kiểm tra và duyệt'
            WHEN status = 'REJECTED' THEN 'Không đủ điều kiện giảm giá' END,
       CASE WHEN status IN ('APPROVED','REJECTED') THEN anchor - interval '20 minutes' END,
       anchor - interval '30 minutes', anchor - interval '20 minutes'
FROM demo_cases CROSS JOIN (VALUES
(1,'DISCOUNT','PENDING','BILL','restosphere:seed:bill:22',50000,'Khách chờ món lâu, xin giảm 15%'),
(2,'PAYMENT_VOID','APPROVED','PAYMENT','restosphere:seed:payment:25:1',335000,'Ghi nhận thanh toán nhầm'),
(3,'DISCOUNT','REJECTED','BILL','restosphere:seed:bill:21',50000,'Yêu cầu ưu đãi hết hạn'),
(4,'DISCOUNT','WITHDRAWN','BILL','restosphere:seed:bill:20',50000,'Khách không yêu cầu giảm nữa')
) requests(k,type,status,target_type,target_key,amount,reason) WHERE n = 1
ON CONFLICT DO NOTHING;

INSERT INTO incidents
(id, type, title, description, status, severity, reporter_id, assignee_id, order_item_id, resolution, resolved_at, created_at, updated_at)
SELECT md5('restosphere:seed:incident:' || k)::uuid, 'FOOD_QUALITY', title, description, status, severity,
       md5('restosphere:demo:kitchen')::uuid, md5('restosphere:demo:manager')::uuid,
       md5('restosphere:seed:item:' || case_no || ':' || slot)::uuid,
       CASE WHEN status IN ('RESOLVED','CLOSED') THEN 'Đã thay món và kiểm tra lại nguyên liệu' END,
       CASE WHEN status IN ('RESOLVED','CLOSED') THEN c.starts_at + interval '40 minutes' END,
       c.starts_at + interval '20 minutes', c.starts_at + interval '40 minutes'
FROM (VALUES
(1,22,4,'OPEN','MEDIUM','Thiếu nguyên liệu trà đào','Bếp đã từ chối món, cần phục vụ tư vấn món thay thế'),
(2,20,1,'ACKNOWLEDGED','LOW','Chậm món cơm','Quản lý đã nhận, bếp đang ưu tiên xử lý'),
(3,15,4,'RESOLVED','MEDIUM','Món không đạt chất lượng','Đã bỏ tính tiền món và xử lý phản hồi'),
(4,10,4,'CLOSED','LOW','Đồ uống cần làm lại','Đã xác nhận khách hài lòng')
) incidents(k,case_no,slot,status,severity,title,description)
JOIN demo_cases c ON c.n = case_no
ON CONFLICT DO NOTHING;

INSERT INTO notifications (id, title, body, kind, path, audience_role, recipient_id, created_at, updated_at)
SELECT md5('restosphere:seed:notification:' || k)::uuid, title, body, kind, path, role,
       CASE WHEN k = 4 THEN md5('restosphere:demo:waiter')::uuid END,
       anchor - k * interval '5 minutes', anchor - k * interval '5 minutes'
FROM demo_cases CROSS JOIN (VALUES
(1,'Cần duyệt giảm giá','Bàn B04 chờ duyệt giảm giá 15%','APPROVAL','/manager/approvals','MANAGER'),
(2,'Món sẵn sàng','Bàn B03 có món cơm sẵn sàng phục vụ','ORDER_READY','/waiter/orders','WAITER'),
(3,'Chờ xác minh chuyển khoản','Bàn B06 đã thanh toán một phần','PAYMENT','/cashier/bills','CASHIER'),
(4,'Khách đặt thêm nước','Bàn B01 cần xác nhận món nháp','ORDER','/waiter/orders','WAITER'),
(5,'Đặt bàn mới','Có yêu cầu đặt bàn web đang chờ xác nhận','RESERVATION','/receptionist/reservations','RECEPTIONIST'),
(6,'Ưu tiên món cơm','Bàn B02 có trẻ nhỏ','ORDER_PRIORITY','/kitchen/orders','KITCHEN')
) messages(k,title,body,kind,path,role) WHERE n = 1
ON CONFLICT DO NOTHING;

INSERT INTO notification_reads (id, notification_id, employee_id, read_at, created_at)
SELECT md5('restosphere:seed:read:' || k)::uuid, md5('restosphere:seed:notification:' || k)::uuid,
       md5('restosphere:demo:' || account)::uuid, anchor - interval '1 minute', anchor - interval '1 minute'
FROM demo_cases CROSS JOIN (VALUES (2,'waiter2'),(4,'waiter'),(6,'kitchen')) receipts(k,account) WHERE n = 1
ON CONFLICT DO NOTHING;

INSERT INTO audit_logs (id, actor_id, action, target_type, target_id, description, created_at)
SELECT md5('restosphere:seed:audit:bill:' || n)::uuid, md5('restosphere:demo:cashier')::uuid,
       CASE WHEN n <= 18 THEN 'PAYMENT_COMPLETED' WHEN n = 26 THEN 'BILL_VOIDED' ELSE 'BILL_CREATED' END,
       'BILL', md5('restosphere:seed:bill:' || n)::uuid,
       'DEMO · ' || CASE WHEN n <= 18 THEN 'Đã thu đủ và đóng phiên'
                        WHEN n = 26 THEN 'Khách rời đi trước khi chế biến, toàn bộ món đã hủy'
                        ELSE 'Phiên phục vụ đang hoạt động' END,
       starts_at + interval '65 minutes'
FROM demo_cases
ON CONFLICT DO NOTHING;
