-- V1: schema đầy đủ của RestoSphere (24 bảng).
-- Không sửa file này sau khi đã chạy trên máy ai đó; thay đổi mới viết thành V2, V3...

create table approval_requests (
    id uuid not null,
    amount numeric(14,0),
    discount_value numeric(14,2),
    decided_at timestamptz,
    decided_by uuid,
    requested_by uuid not null,
    target_id uuid not null,
    discount_mode varchar(20),
    menu_status varchar(20),
    status varchar(20) not null,
    type varchar(30) not null,
    target_type varchar(50) not null,
    decision_note varchar(2000),
    reason varchar(2000) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table areas (
    id uuid not null,
    active boolean default true not null,
    sort_order integer default 0 not null,
    name varchar(100) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table audit_logs (
    id uuid not null,
    actor_id uuid,
    target_id uuid,
    ip_address varchar(45),
    target_type varchar(50) not null,
    action varchar(100) not null,
    description varchar(4000) not null,
    created_at timestamptz not null
);

create table bills (
    id uuid not null,
    discount numeric(14,0) not null,
    discount_value numeric(14,2),
    service_charge numeric(14,0) not null,
    subtotal numeric(14,0) not null,
    tax_amount numeric(14,0) not null,
    total numeric(14,0) not null,
    discount_applied_at timestamptz,
    paid_at timestamptz,
    requested_at timestamptz,
    version bigint not null,
    approval_request_id uuid,
    discount_applied_by_id uuid,
    discount_mode varchar(20),
    status varchar(20) not null,
    code varchar(50) not null,
    discount_reason varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table cashier_shifts (
    id uuid not null,
    counted_cash numeric(14,0),
    expected_cash numeric(14,0),
    opening_cash numeric(14,0) not null,
    closed_at timestamptz,
    opened_at timestamptz not null,
    cashier_id uuid not null,
    scheduled_shift_id uuid,
    note varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table customers (
    id uuid not null,
    birthday date,
    enabled boolean not null,
    marketing_consent boolean not null,
    phone varchar(30) not null,
    full_name varchar(150) not null,
    email varchar(254),
    note varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table employees (
    id uuid not null,
    enabled boolean not null,
    assigned_area_id uuid,
    role varchar(20) not null,
    full_name varchar(150) not null,
    email varchar(254) not null,
    password_hash varchar(255) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table incidents (
    id uuid not null,
    resolved_at timestamptz,
    assignee_id uuid,
    order_item_id uuid,
    reporter_id uuid not null,
    severity varchar(20) not null,
    status varchar(20) not null,
    type varchar(50) not null,
    title varchar(200) not null,
    description varchar(2000) not null,
    resolution varchar(2000),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table menu_categories (
    id uuid not null,
    sort_order integer not null,
    visible boolean not null,
    menu_group varchar(20) default 'food' not null,
    name varchar(100) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table menu_items (
    id uuid not null,
    price numeric(12,0) not null,
    category_id uuid not null,
    price_unit varchar(20) default 'portion' not null,
    status varchar(20) not null,
    name varchar(150) not null,
    image_url varchar(500),
    description varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table notification_reads (
    id uuid not null,
    read_at timestamptz not null,
    employee_id uuid not null,
    notification_id uuid not null,
    created_at timestamptz not null
);

create table notifications (
    id uuid not null,
    recipient_id uuid,
    audience_role varchar(20),
    kind varchar(50) not null,
    title varchar(200) not null,
    path varchar(500),
    body varchar(2000) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table order_items (
    id uuid not null,
    quantity numeric(10,3) not null,
    unit_price numeric(12,0) not null,
    ready_at timestamptz,
    served_at timestamptz,
    started_at timestamptz,
    version bigint not null,
    chef_id uuid,
    menu_item_id uuid not null,
    order_id uuid not null,
    status varchar(20) not null,
    unit varchar(20) not null,
    item_name varchar(150) not null,
    note varchar(1000),
    rejection_reason varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table orders (
    id uuid not null,
    round_number integer,
    priority_at timestamptz,
    submitted_at timestamptz,
    priority_by_id uuid,
    session_id uuid not null,
    waiter_id uuid not null,
    status varchar(20) not null,
    priority_reason varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table payments (
    id uuid not null,
    amount numeric(14,0) not null,
    tendered numeric(14,0),
    paid_at timestamptz,
    payos_order_code bigint,
    bill_id uuid,
    cashier_shift_id uuid,
    created_by_id uuid,
    reservation_id uuid,
    verified_by_id uuid,
    method varchar(20) not null,
    status varchar(20) not null,
    idempotency_key varchar(150),
    transaction_reference varchar(150),
    checkout_url varchar(500),
    qr_code varchar(1000),
    proof_image_url varchar(500),
    note varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table refresh_tokens (
    id uuid not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    employee_id uuid not null,
    family_id uuid not null,
    token_hash varchar(64) not null,
    created_at timestamptz not null
);

create table reservation_items (
    id uuid not null,
    quantity numeric(10,3) not null,
    unit_price numeric(12,0) not null,
    menu_item_id uuid not null,
    reservation_id uuid not null,
    note varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table reservation_tables (
    id uuid not null,
    reservation_id uuid not null,
    table_id uuid not null,
    created_at timestamptz not null
);

create table reservations (
    id uuid not null,
    actual_guests integer,
    deposit_amount numeric(14,0),
    guest_count integer not null,
    arrived_at timestamptz,
    deposit_due_at timestamptz,
    deposit_cash_requested_at timestamptz,
    ends_at timestamptz not null,
    handed_over_at timestamptz,
    starts_at timestamptz not null,
    area_id uuid,
    source varchar(20) not null,
    status varchar(20) not null,
    code varchar(30) not null,
    phone varchar(30) not null,
    customer_name varchar(150) not null,
    occasion varchar(150),
    email varchar(254),
    note varchar(1000),
    special_requests varchar(255),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table restaurant_settings (
    id uuid not null,
    buffer_minutes integer not null,
    cashier_discount_percent integer default 10 not null,
    email_notifications boolean not null,
    hold_minutes integer not null,
    no_show_minutes integer not null,
    online_reservations boolean not null,
    service_charge_rate numeric(5,2) not null,
    singleton_key integer not null,
    vat_rate numeric(5,2) not null,
    bank_bin varchar(6),
    manager_approval_limit bigint default 10000000 not null,
    revision bigint not null,
    phone varchar(30),
    name varchar(150) not null,
    address varchar(255),
    bank_account_name varchar(255),
    bank_account_number varchar(255),
    bank_name varchar(255),
    cover_url varchar(255),
    currency varchar(255),
    description varchar(255),
    email varchar(255),
    facebook varchar(255),
    instagram varchar(255),
    logo_url varchar(255),
    opening_hours varchar(255),
    tiktok varchar(255),
    timezone varchar(255),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table restaurant_tables (
    id uuid not null,
    capacity integer not null,
    area_id uuid not null,
    status varchar(20) not null,
    code varchar(30) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table shift_assignments (
    id uuid not null,
    employee_id uuid not null,
    shift_id uuid not null,
    created_at timestamptz not null
);

create table shifts (
    id uuid not null,
    minimum_staff integer not null,
    ends_at timestamptz not null,
    starts_at timestamptz not null,
    name varchar(100) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table table_sessions (
    id uuid not null,
    guest_count integer not null,
    accepted_at timestamptz,
    ended_at timestamptz,
    guest_left_at timestamptz,
    started_at timestamptz not null,
    bill_id uuid not null,
    reservation_id uuid,
    table_id uuid not null,
    waiter_id uuid,
    status varchar(30) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

-- Khóa chính
alter table approval_requests add constraint pk_approval_requests primary key (id);
alter table areas add constraint pk_areas primary key (id);
alter table audit_logs add constraint pk_audit_logs primary key (id);
alter table bills add constraint pk_bills primary key (id);
alter table cashier_shifts add constraint pk_cashier_shifts primary key (id);
alter table customers add constraint pk_customers primary key (id);
alter table employees add constraint pk_employees primary key (id);
alter table incidents add constraint pk_incidents primary key (id);
alter table menu_categories add constraint pk_menu_categories primary key (id);
alter table menu_items add constraint pk_menu_items primary key (id);
alter table notification_reads add constraint pk_notification_reads primary key (id);
alter table notifications add constraint pk_notifications primary key (id);
alter table order_items add constraint pk_order_items primary key (id);
alter table orders add constraint pk_orders primary key (id);
alter table payments add constraint pk_payments primary key (id);
alter table refresh_tokens add constraint pk_refresh_tokens primary key (id);
alter table reservation_items add constraint pk_reservation_items primary key (id);
alter table reservation_tables add constraint pk_reservation_tables primary key (id);
alter table reservations add constraint pk_reservations primary key (id);
alter table restaurant_settings add constraint pk_restaurant_settings primary key (id);
alter table restaurant_tables add constraint pk_restaurant_tables primary key (id);
alter table shift_assignments add constraint pk_shift_assignments primary key (id);
alter table shifts add constraint pk_shifts primary key (id);
alter table table_sessions add constraint pk_table_sessions primary key (id);

-- Ràng buộc duy nhất
alter table areas add constraint uq_areas_name unique (name);
alter table bills add constraint uq_bills_code unique (code);
alter table customers add constraint uq_customers_phone unique (phone);
alter table employees add constraint uq_employees_email unique (email);
alter table menu_categories add constraint uq_menu_categories_name unique (name);
alter table notification_reads add constraint uq_notification_reads_notification_id_employee_id unique (notification_id, employee_id);
alter table orders add constraint uq_orders_session_id_round_number unique (session_id, round_number);
alter table payments add constraint uq_payments_idempotency_key unique (idempotency_key);
alter table payments add constraint uq_payments_payos_order_code unique (payos_order_code);
alter table payments add constraint uq_payments_transaction_reference unique (transaction_reference);
alter table refresh_tokens add constraint uq_refresh_tokens_token_hash unique (token_hash);
alter table reservation_tables add constraint uq_reservation_tables_reservation_id_table_id unique (reservation_id, table_id);
alter table reservations add constraint uq_reservations_code unique (code);
alter table restaurant_settings add constraint uq_restaurant_settings_singleton_key unique (singleton_key);
alter table restaurant_tables add constraint uq_restaurant_tables_code unique (code);
alter table shift_assignments add constraint uq_shift_assignments_shift_id_employee_id unique (shift_id, employee_id);

-- Khóa ngoại
alter table approval_requests add constraint fk_approval_requests_decided_by foreign key (decided_by) references employees (id);
alter table approval_requests add constraint fk_approval_requests_requested_by foreign key (requested_by) references employees (id);
alter table audit_logs add constraint fk_audit_logs_actor foreign key (actor_id) references employees (id);
alter table bills add constraint fk_bills_approval_request foreign key (approval_request_id) references approval_requests (id);
alter table bills add constraint fk_bills_discount_applied_by foreign key (discount_applied_by_id) references employees (id);
alter table cashier_shifts add constraint fk_cashier_shifts_cashier foreign key (cashier_id) references employees (id);
alter table cashier_shifts add constraint fk_cashier_shifts_scheduled_shift foreign key (scheduled_shift_id) references shifts (id);
alter table employees add constraint fk_employees_assigned_area foreign key (assigned_area_id) references areas (id);
alter table incidents add constraint fk_incidents_order_item foreign key (order_item_id) references order_items (id);
alter table incidents add constraint fk_incidents_reporter foreign key (reporter_id) references employees (id);
alter table incidents add constraint fk_incidents_assignee foreign key (assignee_id) references employees (id);
alter table menu_items add constraint fk_menu_items_category foreign key (category_id) references menu_categories (id);
alter table notification_reads add constraint fk_notification_reads_employee foreign key (employee_id) references employees (id);
alter table notification_reads add constraint fk_notification_reads_notification foreign key (notification_id) references notifications (id);
alter table notifications add constraint fk_notifications_recipient foreign key (recipient_id) references employees (id);
alter table order_items add constraint fk_order_items_order foreign key (order_id) references orders (id);
alter table order_items add constraint fk_order_items_menu_item foreign key (menu_item_id) references menu_items (id);
alter table order_items add constraint fk_order_items_chef foreign key (chef_id) references employees (id);
alter table orders add constraint fk_orders_session foreign key (session_id) references table_sessions (id);
alter table orders add constraint fk_orders_priority_by foreign key (priority_by_id) references employees (id);
alter table orders add constraint fk_orders_waiter foreign key (waiter_id) references employees (id);
alter table payments add constraint fk_payments_cashier_shift foreign key (cashier_shift_id) references cashier_shifts (id);
alter table payments add constraint fk_payments_bill foreign key (bill_id) references bills (id);
alter table payments add constraint fk_payments_verified_by foreign key (verified_by_id) references employees (id);
alter table payments add constraint fk_payments_created_by foreign key (created_by_id) references employees (id);
alter table payments add constraint fk_payments_reservation foreign key (reservation_id) references reservations (id);
alter table refresh_tokens add constraint fk_refresh_tokens_employee foreign key (employee_id) references employees (id);
alter table reservation_items add constraint fk_reservation_items_reservation foreign key (reservation_id) references reservations (id);
alter table reservation_items add constraint fk_reservation_items_menu_item foreign key (menu_item_id) references menu_items (id);
alter table reservation_tables add constraint fk_reservation_tables_reservation foreign key (reservation_id) references reservations (id);
alter table reservation_tables add constraint fk_reservation_tables_table foreign key (table_id) references restaurant_tables (id);
alter table reservations add constraint fk_reservations_area foreign key (area_id) references areas (id);
alter table restaurant_tables add constraint fk_restaurant_tables_area foreign key (area_id) references areas (id);
alter table shift_assignments add constraint fk_shift_assignments_shift foreign key (shift_id) references shifts (id);
alter table shift_assignments add constraint fk_shift_assignments_employee foreign key (employee_id) references employees (id);
alter table table_sessions add constraint fk_table_sessions_reservation foreign key (reservation_id) references reservations (id);
alter table table_sessions add constraint fk_table_sessions_table foreign key (table_id) references restaurant_tables (id);
alter table table_sessions add constraint fk_table_sessions_waiter foreign key (waiter_id) references employees (id);
alter table table_sessions add constraint fk_table_sessions_bill foreign key (bill_id) references bills (id);

-- Ràng buộc kiểm tra
alter table approval_requests add constraint ck_approval_requests_discount_mode check (discount_mode in ('PERCENT', 'AMOUNT'));
alter table approval_requests add constraint ck_approval_requests_menu_status check (menu_status in ('AVAILABLE', 'SOLD_OUT', 'DISCONTINUED'));
alter table approval_requests add constraint ck_approval_requests_status check (status in ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'));
alter table approval_requests add constraint ck_approval_requests_type check (type in ('DISCOUNT', 'CANCEL_ITEM', 'RESERVATION', 'BILL_ADJUSTMENT', 'PAYMENT_VOID', 'MENU_STATUS'));
alter table bills add constraint ck_bills_rules check (((subtotal >= 0) AND (discount >= 0) AND (tax_amount >= 0) AND (service_charge >= 0) AND (total >= 0) AND (total = (((subtotal - discount) + tax_amount) + service_charge))));
alter table bills add constraint ck_bills_discount_mode check (discount_mode in ('PERCENT', 'AMOUNT'));
alter table bills add constraint ck_bills_status check (status in ('OPEN', 'PAID', 'VOIDED'));
alter table cashier_shifts add constraint ck_cashier_shifts_rules check (((opening_cash >= 0) AND ((counted_cash IS NULL) OR (counted_cash >= 0)) AND ((closed_at IS NULL) OR (closed_at >= opened_at))));
alter table employees add constraint ck_employees_role check (role in ('ADMIN', 'MANAGER', 'RECEPTIONIST', 'WAITER', 'KITCHEN', 'CASHIER'));
alter table incidents add constraint ck_incidents_severity check (severity in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));
alter table incidents add constraint ck_incidents_status check (status in ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'CLOSED'));
alter table menu_categories add constraint ck_menu_categories_menu_group check (menu_group in ('food', 'drink'));
alter table menu_items add constraint ck_menu_items_price_unit check (price_unit in ('glass', 'portion', 'bottle', 'kg'));
alter table menu_items add constraint ck_menu_items_status check (status in ('AVAILABLE', 'SOLD_OUT', 'DISCONTINUED'));
alter table notifications add constraint ck_notifications_audience_role check (audience_role in ('ADMIN', 'MANAGER', 'RECEPTIONIST', 'WAITER', 'KITCHEN', 'CASHIER'));
alter table order_items add constraint ck_order_items_rules check (((quantity > 0) AND (unit_price >= 0)));
alter table order_items add constraint ck_order_items_status check (status in ('DRAFT', 'SUBMITTED', 'PREPARING', 'READY', 'SERVED', 'REJECTED', 'CANCELLED', 'VOIDED'));
alter table orders add constraint ck_orders_rules check (((status = 'DRAFT' AND (round_number IS NULL)) OR (status = 'SUBMITTED' AND (round_number > 0))));
alter table orders add constraint ck_orders_status check (status in ('DRAFT', 'SUBMITTED'));
alter table payments add constraint ck_payments_rules check (((amount > 0) AND ((tendered IS NULL) OR (tendered >= 0)) AND ((bill_id IS NOT NULL) OR (reservation_id IS NOT NULL)) AND (method = 'PAYOS' OR (cashier_shift_id IS NOT NULL))));
alter table payments add constraint ck_payments_method check (method in ('CASH', 'PAYOS', 'MANUAL_TRANSFER'));
alter table payments add constraint ck_payments_status check (status in ('PENDING', 'COMPLETED', 'FAILED', 'VOIDED', 'CANCELLED'));
alter table reservation_items add constraint ck_reservation_items_rules check (((quantity > 0) AND (unit_price >= 0)));
alter table reservations add constraint ck_reservations_rules check (((guest_count > 0) AND (ends_at > starts_at)));
alter table reservations add constraint ck_reservations_source check (source in ('WEB', 'PHONE', 'WALK_IN'));
alter table reservations add constraint ck_reservations_status check (status in ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'ARRIVED', 'SEATED', 'NO_SHOW', 'COMPLETED'));
alter table restaurant_settings add constraint ck_restaurant_settings_rules check (((singleton_key = 1) AND (hold_minutes >= 0) AND (no_show_minutes >= 0) AND ((vat_rate >= 0) AND (vat_rate <= 100)) AND ((service_charge_rate >= 0) AND (service_charge_rate <= 100))));
alter table restaurant_tables add constraint ck_restaurant_tables_status check (status in ('AVAILABLE', 'OCCUPIED', 'CLEANING', 'OUT_OF_SERVICE'));
alter table shifts add constraint ck_shifts_rules check (((ends_at > starts_at) AND (minimum_staff >= 0)));
alter table table_sessions add constraint ck_table_sessions_rules check (((guest_count > 0) AND ((ended_at IS NULL) OR (ended_at >= started_at))));
alter table table_sessions add constraint ck_table_sessions_status check (status in ('OPEN', 'PAYMENT_REQUESTED', 'CLOSED'));

-- Index
create index idx_approval_requests_requested_by on approval_requests (requested_by);
create index idx_approval_requests_target_id on approval_requests (target_id);
create index idx_approval_requests_type_status on approval_requests (type, status);
create index idx_audit_logs_actor_id on audit_logs (actor_id);
create index idx_audit_logs_created_at on audit_logs (created_at);
create index idx_audit_logs_target_id on audit_logs (target_id);
create index idx_bills_created_at on bills (created_at);
create index idx_bills_status on bills (status);
create index idx_cashier_shifts_cashier_closed on cashier_shifts (cashier_id, closed_at);
create index idx_employees_role_enabled on employees (role, enabled);
create index idx_incidents_created_at on incidents (created_at);
create index idx_incidents_status on incidents (status);
create index idx_menu_items_category_id on menu_items (category_id);
create index idx_notification_reads_employee_id on notification_reads (employee_id);
create index idx_notifications_audience_role on notifications (audience_role);
create index idx_notifications_created_at on notifications (created_at);
create index idx_notifications_recipient_id on notifications (recipient_id);
create index idx_order_items_menu_item_id on order_items (menu_item_id);
create index idx_order_items_order_id on order_items (order_id);
create index idx_order_items_status on order_items (status);
create index idx_payments_bill_status on payments (bill_id, status);
create index idx_payments_paid_at on payments (paid_at);
create index idx_payments_shift_status on payments (cashier_shift_id, status);
create index idx_refresh_tokens_employee_id on refresh_tokens (employee_id);
create index idx_refresh_tokens_family_id on refresh_tokens (family_id);
create index idx_reservation_items_menu_item_id on reservation_items (menu_item_id);
create index idx_reservation_items_reservation_id on reservation_items (reservation_id);
create index idx_reservation_tables_table_id on reservation_tables (table_id);
create index idx_reservations_area_status on reservations (area_id, status);
create index idx_reservations_starts_at on reservations (starts_at);
create index idx_reservations_status on reservations (status);
create index idx_restaurant_tables_area_status on restaurant_tables (area_id, status);
create index idx_shift_assignments_employee_id on shift_assignments (employee_id);
create index idx_table_sessions_table_status on table_sessions (table_id, status);
create index idx_table_sessions_waiter_status on table_sessions (waiter_id, status);

-- Chỉ JPA không khai báo được các index một phần; đặt ở đây để DB tự bảo vệ luật nghiệp vụ.
create unique index uq_cashier_shifts_open_per_cashier on cashier_shifts (cashier_id) where closed_at is null;
create unique index uq_table_sessions_open_per_table on table_sessions (table_id) where status <> 'CLOSED';
create unique index uq_orders_draft_per_session_waiter on orders (session_id, waiter_id) where status = 'DRAFT';
