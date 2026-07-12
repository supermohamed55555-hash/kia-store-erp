-- ============= SUPPLIERS =============
INSERT IGNORE INTO suppliers(id, name, phone, address, notes) VALUES
  (1, 'Bosch Egypt', '+20-2-23456789', 'Cairo, Nasr City', 'Primary supplier for Bosch spark plugs and filters'),
  (2, 'Denso Distributors', '+20-2-34567890', 'Giza, 6th of October', 'Supplier for electrical components and filters'),
  (3, 'NGK Spark Plugs Egypt', '+20-2-45678901', 'Alexandria, Smouha', 'Primary spark plugs supplier'),
  (4, 'Brembo Brakes Egypt', '+20-2-67890123', 'Cairo, Maadi', 'Brake pads and discs supplier'),
  (5, 'Valeo Egypt', '+20-2-11223344', 'Giza, Sheikh Zayed', 'Clutch kits and lighting supplier');

-- ============= PARTS =============
INSERT IGNORE INTO parts(id, part_type, location, car_name, car_model, manufacturer, full_name, part_number, part_number_normalized, internal_code, barcode, sale_price, min_stock, current_stock, description, compatible_cars, alternatives, created_by) VALUES
  (1, 'Spark Plug', 'A-1-2', 'Toyota Corolla', '2015', 'NGK', 'Spark Plug NGK BKR6E', 'BKR6E', 'BKR6E', 'NGK-BKR6E', '4964336000010', 75.00, 20, 100, 'NGK Standard Spark Plug', '["Toyota Corolla 2013-2018", "Hyundai Elantra 2012-2016"]', '["DENSO-K16R-U"]', 1),
  (2, 'Oil Filter', 'B-3-1', 'Hyundai Elantra', '2018', 'Bosch', 'Oil Filter Bosch P7', 'P7', 'P7', 'BOS-P7', '3165141234567', 95.00, 15, 80, 'Bosch Premium Oil Filter', '["Hyundai Elantra 2016-2020", "Kia Cerato 2016-2020"]', '["MAHLE-OC205"]', 1),
  (3, 'Brake Pads', 'C-2-4', 'Kia Cerato', '2016', 'Brembo', 'Brake Pads Front Brembo', 'P30012', 'P30012', 'BRE-P30012', '8020582012345', 360.00, 10, 40, 'Brembo Front Brake Pads Kit', '["Kia Cerato 2014-2018", "Hyundai Accent 2015-2019"]', '["VALEO-301012"]', 1),
  (4, 'Clutch Kit', 'D-1-1', 'Chevrolet Optra', '2012', 'Valeo', 'Clutch Kit Valeo Optra', '826345', '826345', 'VAL-826345', '3276428263456', 1250.00, 5, 10, 'Valeo OEM Clutch Kit', '["Chevrolet Optra 2008-2015"]', '["SACHS-3000951"]', 1);

-- ============= BATCHES =============
INSERT IGNORE INTO batches(id, part_id, supplier_id, quantity, purchase_price, purchase_invoice_number, notes, received_by) VALUES
  (1, 1, 3, 100, 45.00, 'INV-NGK-2026-001', 'Initial stock ingestion', 1),
  (2, 2, 1, 80, 55.00, 'INV-BOS-2026-042', 'Spring batch', 1),
  (3, 3, 4, 40, 220.00, 'INV-BRE-9912', 'Brake parts delivery', 1),
  (4, 4, 5, 10, 850.00, 'INV-VAL-0051', 'Heavy transmission parts', 1);

-- ============= INVOICES =============
INSERT IGNORE INTO invoices(id, invoice_number, customer_name, customer_phone, total_amount, discount, final_amount, payment_method, status, notes, created_by) VALUES
  (1, 'INV-2026-0001', 'Ahmed Ali', '+20-10-12345678', 245.00, 15.00, 230.00, 'cash', 'active', 'Regular walk-in customer', 2),
  (2, 'INV-2026-0002', 'Mohamed Hassan', '+20-12-87654321', 360.00, 0.00, 360.00, 'card', 'active', NULL, 2);

-- ============= INVOICE ITEMS =============
INSERT IGNORE INTO invoice_items(id, invoice_id, part_id, quantity, unit_price, total_price) VALUES
  (1, 1, 1, 2, 75.00, 150.00),
  (2, 1, 2, 1, 95.00, 95.00),
  (3, 2, 3, 1, 360.00, 360.00);

-- ============= AUDIT LOGS =============
INSERT IGNORE INTO audit_logs(id, user_id, user_name, action, table_name, record_id, old_data, new_data) VALUES
  (1, 1, 'admin', 'CREATE', 'parts', 1, NULL, '{"full_name": "Spark Plug NGK BKR6E"}'),
  (2, 1, 'admin', 'CREATE', 'parts', 2, NULL, '{"full_name": "Oil Filter Bosch P7"}'),
  (3, 1, 'admin', 'CREATE', 'parts', 3, NULL, '{"full_name": "Brake Pads Front Brembo"}'),
  (4, 1, 'admin', 'CREATE', 'parts', 4, NULL, '{"full_name": "Clutch Kit Valeo Optra"}');
