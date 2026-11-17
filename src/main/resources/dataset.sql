-- PostgreSQL: run manually after Hibernate has created the schema.
-- Demo prices/names from FE web src/api/menu/mockData.ts; not a confirmed menu.
-- Stable IDs and DO NOTHING preserve existing records when rerun.
-- Shared Cloudinary collection: web photos, with mobile mushroom hotpot fallback.
BEGIN;

-- Local demo accounts only. Password: 123456 (BCrypt). Existing users are preserved.
-- Refresh the legacy role constraint; NOT VALID preserves legacy rows until migrated.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
CHECK (role IN ('ADMIN', 'MANAGER', 'RECEPTIONIST', 'WAITER', 'KITCHEN', 'CASHIER')) NOT VALID;
INSERT INTO users (id, email, password_hash, full_name, role, enabled, created_at, updated_at)
SELECT md5('restosphere:demo:' || account)::uuid, account || '@restosphere.test',
       '$2b$10$LTsVxyCfFG2CRk86pJ6HKO9vBqu9FVik7RgWPNgT0jYZPLvPxU54W',
       full_name, role, true, now(), now()
FROM (VALUES
('admin', 'Quản trị demo', 'ADMIN'),
('manager', 'Quản lý demo', 'MANAGER'),
('cashier', 'Thu ngân demo', 'CASHIER'),
('kitchen', 'Đầu bếp demo', 'KITCHEN'),
('receptionist', 'Lễ tân demo', 'RECEPTIONIST'),
('waiter', 'Phục vụ demo', 'WAITER')
) AS accounts(account, full_name, role)
ON CONFLICT DO NOTHING;

INSERT INTO areas (id, name, created_at, updated_at) VALUES
('10000000-0000-0000-0000-000000000001', 'Khu A · Tầng trệt', now(), now()),
('10000000-0000-0000-0000-000000000002', 'Khu B · Tầng 1', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO tables (id, code, area_id, capacity, status, created_at, updated_at)
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

COMMIT;
