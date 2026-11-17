-- Run with psql --single-transaction -v ON_ERROR_STOP=1 after Hibernate schema update.
-- Replace only enum checks; validate existing rows without rewriting business data.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('CUSTOMER','ADMIN','MANAGER','RECEPTIONIST','WAITER','KITCHEN','CASHIER'));
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_audience_role_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_audience_role_check CHECK (audience_role IN ('CUSTOMER','ADMIN','MANAGER','RECEPTIONIST','WAITER','KITCHEN','CASHIER'));
ALTER TABLE reservations DROP CONSTRAINT IF EXISTS reservations_status_check;
ALTER TABLE reservations ADD CONSTRAINT reservations_status_check CHECK (status IN ('PENDING','CONFIRMED','REJECTED','CANCELLED','ARRIVED','SEATED','NO_SHOW','COMPLETED'));
ALTER TABLE reservation_histories DROP CONSTRAINT IF EXISTS reservation_histories_previous_status_check;
ALTER TABLE reservation_histories ADD CONSTRAINT reservation_histories_previous_status_check CHECK (previous_status IN ('PENDING','CONFIRMED','REJECTED','CANCELLED','ARRIVED','SEATED','NO_SHOW','COMPLETED'));
ALTER TABLE reservation_histories DROP CONSTRAINT IF EXISTS reservation_histories_new_status_check;
ALTER TABLE reservation_histories ADD CONSTRAINT reservation_histories_new_status_check CHECK (new_status IN ('PENDING','CONFIRMED','REJECTED','CANCELLED','ARRIVED','SEATED','NO_SHOW','COMPLETED'));
ALTER TABLE order_items DROP CONSTRAINT IF EXISTS order_items_status_check;
ALTER TABLE order_items ADD CONSTRAINT order_items_status_check CHECK (status IN ('SUBMITTED','PREPARING','READY','SERVED','REJECTED','CANCELLED','VOIDED'));
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check CHECK (status IN ('PENDING','COMPLETED','FAILED','VOIDED','CANCELLED'));
