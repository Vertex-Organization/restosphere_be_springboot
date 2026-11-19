-- Demo data for the WAITER flow: one table per step of the service lifecycle (all in Khu A).
-- Runs after dataset.sql on every startup. Stable md5 IDs + ON CONFLICT DO NOTHING keep reruns
-- from duplicating rows or undoing what you did in the app. "created_at = now()" is used to
-- detect rows created in this run (now() is fixed for the whole transaction).
--
--   A03  Chờ tiếp nhận         lễ tân vừa bàn giao, khách có món đặt trước (accept → vào giỏ)
--   A04  Có giỏ món chưa gửi   waiter đã nhận bàn, 3 món trong giỏ (sửa / xóa / gửi bếp)
--   A05  Đang phục vụ          2 lượt gọi: SERVED, REJECTED, READY, PREPARING (chờ duyệt hủy), SUBMITTED; lượt 2 ưu tiên
--   A06  Đủ điều kiện TT       mọi món đã phục vụ → có thể yêu cầu thanh toán
--   A07  Đã thanh toán         khách còn ngồi → xác nhận khách rời
--   A08  Đang dọn              khách đã rời → xác nhận dọn xong
--   A09  Đã hoàn tất           lượt đã đóng, hiện trong Lịch sử
--   A10  Bàn của waiter2       waiter không thao tác được (kiểm tra quyền phụ trách)
BEGIN;

INSERT INTO reservations (id, code, customer_name, phone, guest_count, actual_guests, area_id,
                          starts_at, ends_at, arrived_at, handed_over_at, source, status, note,
                          created_at, updated_at)
SELECT md5('restosphere:waiter-demo:reservation:' || v.code)::uuid, 'RS-DEMO-' || v.code,
       v.customer, v.phone, v.guests, v.guests, t.area_id,
       now() - v.ago, now() - v.ago + interval '2 hours', now() - v.ago, now() - v.ago,
       v.source, v.status, v.note, now() - v.ago, now()
FROM (VALUES
('A03', 'Nguyễn Văn An',  '0901000003', 4, interval '5 minutes',  'PHONE',   'SEATED',    'Đặt trước lẩu Thái'),
('A04', 'Trần Thị Bình',  '0901000004', 2, interval '10 minutes', 'WALK_IN', 'SEATED',    ''),
('A05', 'Lê Hoàng Cường', '0901000005', 4, interval '45 minutes', 'PHONE',   'SEATED',    'Sinh nhật'),
('A06', 'Phạm Minh Đức',  '0901000006', 3, interval '60 minutes', 'WALK_IN', 'SEATED',    ''),
('A07', 'Võ Thu Hà',      '0901000007', 4, interval '70 minutes', 'PHONE',   'SEATED',    ''),
('A08', 'Đặng Quốc Huy',  '0901000008', 2, interval '90 minutes', 'WALK_IN', 'SEATED',    ''),
('A09', 'Bùi Lan Khuê',   '0901000009', 2, interval '3 hours',    'PHONE',   'COMPLETED', ''),
('A10', 'Hồ Gia Long',    '0901000010', 2, interval '30 minutes', 'WALK_IN', 'SEATED',    '')
) AS v(code, customer, phone, guests, ago, source, status, note)
JOIN tables t ON t.code = v.code
ON CONFLICT DO NOTHING;

INSERT INTO reservation_tables (id, reservation_id, table_id, created_at, updated_at)
SELECT md5('restosphere:waiter-demo:assignment:' || t.code)::uuid, r.id, t.id, r.created_at, now()
FROM reservations r
JOIN tables t ON r.code = 'RS-DEMO-' || t.code
ON CONFLICT DO NOTHING;

-- Món đặt trước của A03: được chép vào giỏ khi waiter tiếp nhận bàn.
INSERT INTO reservation_items (id, reservation_id, menu_item_id, quantity, unit_price, note, created_at, updated_at)
SELECT md5('restosphere:waiter-demo:preorder:A03:' || v.dish)::uuid,
       md5('restosphere:waiter-demo:reservation:A03')::uuid, m.id, v.qty, m.price, v.note, now(), now()
FROM (VALUES ('thai-pot', 1, 'Ít cay'), ('peach-tea', 4, 'Ít đá')) AS v(dish, qty, note)
JOIN menu_items m ON m.id = md5('restosphere:menu:' || v.dish)::uuid
WHERE EXISTS (SELECT 1 FROM reservations WHERE id = md5('restosphere:waiter-demo:reservation:A03')::uuid)
ON CONFLICT DO NOTHING;

-- Trạng thái bàn chỉ đặt lần đầu (trước khi session demo tồn tại).
UPDATE tables t SET status = v.status, updated_at = now()
FROM (VALUES ('A03', 'OCCUPIED'), ('A04', 'OCCUPIED'), ('A05', 'OCCUPIED'), ('A06', 'OCCUPIED'),
             ('A07', 'OCCUPIED'), ('A08', 'CLEANING'), ('A10', 'OCCUPIED')) AS v(code, status)
WHERE t.code = v.code
  AND NOT EXISTS (SELECT 1 FROM table_sessions s WHERE s.id = md5('restosphere:waiter-demo:session:' || v.code)::uuid);

INSERT INTO table_sessions (id, table_id, reservation_id, waiter_id, guest_count, started_at,
                            accepted_at, guest_left_at, ended_at, status, created_at, updated_at)
SELECT md5('restosphere:waiter-demo:session:' || v.code)::uuid, t.id, r.id,
       CASE WHEN v.waiter IS NOT NULL THEN md5('restosphere:demo:' || v.waiter)::uuid END,
       r.guest_count, r.handed_over_at,
       CASE WHEN v.waiter IS NOT NULL THEN r.handed_over_at + interval '2 minutes' END,
       now() - v.left_ago, now() - v.ended_ago, v.status, now(), now()
FROM (VALUES
('A03', NULL,      'OPEN',              NULL::interval,      NULL::interval),
('A04', 'waiter',  'OPEN',              NULL,                NULL),
('A05', 'waiter',  'OPEN',              NULL,                NULL),
('A06', 'waiter',  'OPEN',              NULL,                NULL),
('A07', 'waiter',  'PAYMENT_REQUESTED', NULL,                NULL),
('A08', 'waiter',  'PAYMENT_REQUESTED', interval '5 minutes', NULL),
('A09', 'waiter',  'CLOSED',            interval '70 minutes', interval '60 minutes'),
('A10', 'waiter2', 'OPEN',              NULL,                NULL)
) AS v(code, waiter, status, left_ago, ended_ago)
JOIN tables t ON t.code = v.code
JOIN reservations r ON r.id = md5('restosphere:waiter-demo:reservation:' || v.code)::uuid
ON CONFLICT DO NOTHING;

-- Mỗi lượt phục vụ có một hóa đơn; tổng tiền được tính ở bước cuối từ món đã gửi.
INSERT INTO bills (id, code, session_id, status, subtotal, discount, service_charge, tax_amount, total,
                   requested_at, paid_at, version, created_at, updated_at)
SELECT md5('restosphere:waiter-demo:bill:' || v.code)::uuid, 'B-DEMO-' || v.code, s.id, v.status,
       0, 0, 0, 0, 0, now() - v.requested_ago, now() - v.paid_ago, 0, now(), now()
FROM (VALUES
('A03', 'OPEN', NULL::interval,        NULL::interval),
('A04', 'OPEN', NULL,                  NULL),
('A05', 'OPEN', NULL,                  NULL),
('A06', 'OPEN', NULL,                  NULL),
('A07', 'PAID', interval '15 minutes', interval '10 minutes'),
('A08', 'PAID', interval '25 minutes', interval '20 minutes'),
('A09', 'PAID', interval '80 minutes', interval '75 minutes'),
('A10', 'OPEN', NULL,                  NULL)
) AS v(code, status, requested_ago, paid_ago)
JOIN table_sessions s ON s.id = md5('restosphere:waiter-demo:session:' || v.code)::uuid
ON CONFLICT DO NOTHING;

INSERT INTO orders (id, session_id, waiter_id, round_number, priority_at, priority_by_id, priority_reason,
                    created_at, updated_at)
SELECT md5('restosphere:waiter-demo:order:' || v.code || ':' || v.round)::uuid, s.id, s.waiter_id, v.round,
       CASE WHEN v.priority IS NOT NULL THEN now() - interval '5 minutes' END,
       CASE WHEN v.priority IS NOT NULL THEN s.waiter_id END,
       v.priority, now() - v.ago, now()
FROM (VALUES
('A05', 1, interval '40 minutes', NULL),
('A05', 2, interval '15 minutes', 'Khách cần về sớm'),
('A06', 1, interval '55 minutes', NULL),
('A07', 1, interval '65 minutes', NULL),
('A08', 1, interval '85 minutes', NULL),
('A09', 1, interval '170 minutes', NULL),
('A10', 1, interval '25 minutes', NULL)
) AS v(code, round, ago, priority)
JOIN table_sessions s ON s.id = md5('restosphere:waiter-demo:session:' || v.code)::uuid
ON CONFLICT DO NOTHING;

INSERT INTO order_items (id, order_id, menu_item_id, item_name, unit, unit_price, quantity, note, status,
                         chef_id, started_at, ready_at, served_at, rejection_reason, version,
                         created_at, updated_at)
SELECT md5('restosphere:waiter-demo:item:' || v.code || ':' || v.round || ':' || v.dish)::uuid,
       o.id, m.id, m.name, coalesce(m.price_unit, 'portion'), m.price, v.qty, v.note, v.status,
       CASE WHEN v.status <> 'SUBMITTED' THEN md5('restosphere:demo:kitchen')::uuid END,
       CASE WHEN v.status <> 'SUBMITTED' THEN o.created_at + interval '3 minutes' END,
       CASE WHEN v.status IN ('READY', 'SERVED') THEN o.created_at + interval '12 minutes' END,
       CASE WHEN v.status = 'SERVED' THEN o.created_at + interval '14 minutes' END,
       v.reason, 0, o.created_at, now()
FROM (VALUES
('A05', 1, 'garlic-prawns',  1.0, 'SERVED',    '',            NULL),
('A05', 1, 'beer',           3.0, 'SERVED',    '',            NULL),
('A05', 1, 'king-crab',      1.2, 'REJECTED',  '',            'Hết cua hoàng đế'),
('A05', 2, 'grilled-fish',   1.0, 'READY',     '',            NULL),
('A05', 2, 'beef',           1.0, 'PREPARING', 'Chín vừa',    NULL),
('A05', 2, 'lemonade',       2.0, 'SUBMITTED', 'Ít đường',    NULL),
('A06', 1, 'chicken',        1.0, 'SERVED',    '',            NULL),
('A06', 1, 'seafood-rice',   2.0, 'SERVED',    '',            NULL),
('A06', 1, 'orange-juice',   2.0, 'SERVED',    '',            NULL),
('A07', 1, 'thai-pot',       1.0, 'SERVED',    'Ít cay',      NULL),
('A07', 1, 'peach-tea',      4.0, 'SERVED',    'Ít đá',       NULL),
('A08', 1, 'grilled-shrimp', 2.0, 'SERVED',    '',            NULL),
('A08', 1, 'water',          2.0, 'SERVED',    '',            NULL),
('A09', 1, 'steamed-crab',   1.5, 'SERVED',    '',            NULL),
('A09', 1, 'noodles',        1.0, 'SERVED',    '',            NULL),
('A09', 1, 'iced-coffee',    2.0, 'SERVED',    '',            NULL),
('A10', 1, 'grilled-squid',  1.0, 'SERVED',    '',            NULL),
('A10', 1, 'beer',           2.0, 'READY',     '',            NULL)
) AS v(code, round, dish, qty, status, note, reason)
JOIN orders o ON o.id = md5('restosphere:waiter-demo:order:' || v.code || ':' || v.round)::uuid
JOIN menu_items m ON m.id = md5('restosphere:menu:' || v.dish)::uuid
ON CONFLICT DO NOTHING;

-- Giỏ món của A04: chỉ tạo khi session vừa được tạo, để món đã gửi/xóa không quay lại sau khi khởi động lại.
INSERT INTO order_draft (id, session_id, waiter_id, menu_item_id, quantity, note, created_at, updated_at)
SELECT md5('restosphere:waiter-demo:draft:A04:' || v.dish)::uuid, s.id, s.waiter_id,
       md5('restosphere:menu:' || v.dish)::uuid, v.qty, v.note, now(), now()
FROM (VALUES ('grilled-squid', 2, 'Ít cay'), ('seafood-rice', 1, ''), ('iced-coffee', 2, 'Ít đường'))
     AS v(dish, qty, note)
JOIN table_sessions s ON s.id = md5('restosphere:waiter-demo:session:A04')::uuid AND s.created_at = now()
ON CONFLICT DO NOTHING;

-- A05: món bò đang làm có yêu cầu hủy chờ quản lý duyệt.
INSERT INTO approval_requests (id, type, status, target_type, target_id, amount, reason, requested_by,
                               created_at, updated_at)
SELECT md5('restosphere:waiter-demo:approval:A05:beef')::uuid, 'CANCEL_ITEM', 'PENDING', 'ORDER_ITEM',
       i.id, round(i.unit_price * i.quantity), 'Khách đổi ý, không dùng bò nữa',
       md5('restosphere:demo:waiter')::uuid, now() - interval '3 minutes', now()
FROM order_items i
WHERE i.id = md5('restosphere:waiter-demo:item:A05:2:beef')::uuid
ON CONFLICT DO NOTHING;

-- Thông báo cho phục vụ (path = id lượt phục vụ, giống backend tạo ra).
INSERT INTO notifications (id, audience_role, kind, title, body, path, created_at, updated_at)
SELECT md5('restosphere:waiter-demo:notification:' || v.code || ':' || v.kind)::uuid, 'WAITER',
       v.kind, v.title, v.body, s.id::text, now() - v.ago, now()
FROM (VALUES
('A05', 'ITEM_REJECTED', 'Dish rejected',    'Hết cua hoàng đế', interval '30 minutes'),
('A05', 'READY',         'Dish ready',       'Cá nướng',         interval '3 minutes'),
('A07', 'PAYMENT',       'Payment received', 'B-DEMO-A07',       interval '10 minutes'),
('A10', 'READY',         'Dish ready',       'Bia tươi',         interval '13 minutes')
) AS v(code, kind, title, body, ago)
JOIN table_sessions s ON s.id = md5('restosphere:waiter-demo:session:' || v.code)::uuid
ON CONFLICT DO NOTHING;

-- Tổng hóa đơn = tổng món chưa bị từ chối/hủy (phí dịch vụ và VAT để 0 cho dễ đối chiếu).
UPDATE bills b SET subtotal = x.amount, total = x.amount, updated_at = now()
FROM (SELECT o.session_id, sum(round(i.unit_price * i.quantity)) AS amount
      FROM order_items i JOIN orders o ON o.id = i.order_id
      WHERE i.status NOT IN ('REJECTED', 'CANCELLED', 'VOIDED')
      GROUP BY o.session_id) AS x
WHERE b.session_id = x.session_id AND b.code LIKE 'B-DEMO-%' AND b.created_at = now();

-- Hóa đơn đã thanh toán cần một ca thu ngân (đã đóng) và khoản thu tiền mặt.
INSERT INTO cashier_shifts (id, cashier_id, opened_at, opening_cash, closed_at, note, created_at, updated_at)
SELECT md5('restosphere:waiter-demo:cashier-shift')::uuid, md5('restosphere:demo:cashier')::uuid,
       now() - interval '4 hours', 1000000, now() - interval '5 minutes', 'Ca demo', now(), now()
WHERE EXISTS (SELECT 1 FROM users WHERE id = md5('restosphere:demo:cashier')::uuid)
ON CONFLICT DO NOTHING;

INSERT INTO payments (id, bill_id, cashier_shift_id, method, status, amount, tendered, created_by, paid_at,
                      created_at, updated_at)
SELECT md5('restosphere:waiter-demo:payment:' || b.code)::uuid, b.id,
       md5('restosphere:waiter-demo:cashier-shift')::uuid, 'CASH', 'COMPLETED', b.total, b.total,
       md5('restosphere:demo:cashier')::uuid::text, b.paid_at, b.paid_at, now()
FROM bills b
WHERE b.code LIKE 'B-DEMO-%' AND b.status = 'PAID' AND b.total > 0 AND b.created_at = now()
  AND EXISTS (SELECT 1 FROM cashier_shifts WHERE id = md5('restosphere:waiter-demo:cashier-shift')::uuid)
ON CONFLICT DO NOTHING;

UPDATE cashier_shifts c
SET expected_cash = c.opening_cash + p.amount, counted_cash = c.opening_cash + p.amount, updated_at = now()
FROM (SELECT cashier_shift_id, sum(amount) AS amount FROM payments GROUP BY cashier_shift_id) AS p
WHERE c.id = md5('restosphere:waiter-demo:cashier-shift')::uuid AND p.cashier_shift_id = c.id
  AND c.expected_cash IS NULL;

COMMIT;
