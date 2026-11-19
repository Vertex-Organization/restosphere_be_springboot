-- Xóa toàn bộ dữ liệu demo của waiter (bàn A03–A10), kể cả những gì đã tạo khi bấm thử trên app,
-- rồi trả các bàn về AVAILABLE. Khởi động lại backend để waiter-demo.sql nạp lại từ đầu.
-- Chạy tay:
--   docker exec -i restosphere-postgres-1 psql -U restosphere -d restosphere < src/main/resources/waiter-demo-reset.sql
BEGIN;

CREATE TEMP TABLE demo_sessions ON COMMIT DROP AS
SELECT s.id, s.reservation_id FROM table_sessions s
JOIN reservations r ON r.id = s.reservation_id
WHERE r.code LIKE 'RS-DEMO-%';

CREATE TEMP TABLE demo_targets ON COMMIT DROP AS
SELECT id FROM demo_sessions
UNION SELECT o.id FROM orders o WHERE o.session_id IN (SELECT id FROM demo_sessions)
UNION SELECT i.id FROM order_items i JOIN orders o ON o.id = i.order_id WHERE o.session_id IN (SELECT id FROM demo_sessions)
UNION SELECT b.id FROM bills b WHERE b.session_id IN (SELECT id FROM demo_sessions);

DELETE FROM notification_reads WHERE notification_id IN (SELECT id FROM notifications WHERE path IN (SELECT id::text FROM demo_targets));
DELETE FROM notifications WHERE path IN (SELECT id::text FROM demo_targets);
DELETE FROM approval_requests WHERE target_id IN (SELECT id FROM demo_targets);
DELETE FROM payments WHERE bill_id IN (SELECT id FROM demo_targets);
DELETE FROM order_items WHERE order_id IN (SELECT o.id FROM orders o WHERE o.session_id IN (SELECT id FROM demo_sessions));
DELETE FROM orders WHERE session_id IN (SELECT id FROM demo_sessions);
DELETE FROM order_draft WHERE session_id IN (SELECT id FROM demo_sessions);
DELETE FROM bills WHERE session_id IN (SELECT id FROM demo_sessions);
DELETE FROM table_sessions WHERE id IN (SELECT id FROM demo_sessions);
DELETE FROM reservation_items WHERE reservation_id IN (SELECT id FROM reservations WHERE code LIKE 'RS-DEMO-%');
DELETE FROM reservation_histories WHERE reservation_id IN (SELECT id FROM reservations WHERE code LIKE 'RS-DEMO-%');
DELETE FROM reservation_tables WHERE reservation_id IN (SELECT id FROM reservations WHERE code LIKE 'RS-DEMO-%');
DELETE FROM reservations WHERE code LIKE 'RS-DEMO-%';
DELETE FROM cashier_shifts WHERE id = md5('restosphere:waiter-demo:cashier-shift')::uuid
  AND NOT EXISTS (SELECT 1 FROM payments WHERE cashier_shift_id = md5('restosphere:waiter-demo:cashier-shift')::uuid);

UPDATE tables SET status = 'AVAILABLE', updated_at = now()
WHERE code IN ('A03', 'A04', 'A05', 'A06', 'A07', 'A08', 'A09', 'A10')
  AND NOT EXISTS (SELECT 1 FROM table_sessions s WHERE s.table_id = tables.id AND s.status <> 'CLOSED');

COMMIT;
