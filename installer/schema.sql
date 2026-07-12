-- USERS
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('admin', 'cashier', 'warehouse') DEFAULT 'cashier',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- SUPPLIERS
CREATE TABLE IF NOT EXISTS suppliers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(20),
    address TEXT,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- PARTS
CREATE TABLE IF NOT EXISTS parts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    part_type VARCHAR(100),
    location VARCHAR(50),
    car_name VARCHAR(100),
    car_model VARCHAR(20),
    manufacturer VARCHAR(100),
    full_name VARCHAR(255),
    part_number VARCHAR(100),
    part_number_normalized VARCHAR(100),
    internal_code VARCHAR(100) UNIQUE,
    barcode VARCHAR(100),
    sale_price DECIMAL(10,2),
    purchase_price DECIMAL(10,2) DEFAULT 0.00,
    min_stock INT DEFAULT 5,
    current_stock INT DEFAULT 0,
    images JSON,
    description TEXT,
    compatible_cars JSON,
    alternatives JSON,
    is_active BOOLEAN DEFAULT TRUE,
    created_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

-- BATCHES
CREATE TABLE IF NOT EXISTS batches (
    id INT AUTO_INCREMENT PRIMARY KEY,
    part_id INT NOT NULL,
    supplier_id INT NOT NULL,
    quantity INT NOT NULL,
    purchase_price DECIMAL(10,2) NOT NULL,
    purchase_invoice_number VARCHAR(100),
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    received_by INT,
    notes TEXT,
    FOREIGN KEY (part_id) REFERENCES parts(id),
    FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    FOREIGN KEY (received_by) REFERENCES users(id)
);

-- INVOICES
CREATE TABLE IF NOT EXISTS invoices (
    id INT AUTO_INCREMENT PRIMARY KEY,
    invoice_number VARCHAR(50) UNIQUE NOT NULL,
    customer_name VARCHAR(150),
    customer_phone VARCHAR(20),
    total_amount DECIMAL(10,2) NOT NULL,
    discount DECIMAL(10,2) DEFAULT 0,
    final_amount DECIMAL(10,2) NOT NULL,
    payment_method ENUM('cash', 'card', 'other', 'credit') DEFAULT 'cash',
    amount_paid DECIMAL(10,2) DEFAULT 0.00,
    amount_due DECIMAL(10,2) DEFAULT 0.00,
    status ENUM('active', 'cancelled', 'returned') DEFAULT 'active',
    notes TEXT,
    created_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

-- INVOICE ITEMS
CREATE TABLE IF NOT EXISTS invoice_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    invoice_id INT NOT NULL,
    part_id INT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    total_price DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    FOREIGN KEY (part_id) REFERENCES parts(id)
);

-- RETURNS
CREATE TABLE IF NOT EXISTS returns (
    id INT AUTO_INCREMENT PRIMARY KEY,
    invoice_id INT NOT NULL,
    part_id INT NOT NULL,
    quantity INT NOT NULL,
    reason TEXT,
    returned_by INT,
    returned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    FOREIGN KEY (part_id) REFERENCES parts(id),
    FOREIGN KEY (returned_by) REFERENCES users(id)
);

-- AUDIT LOG
CREATE TABLE IF NOT EXISTS audit_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    user_name VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    table_name VARCHAR(100),
    record_id INT,
    old_data JSON,
    new_data JSON,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- SEARCH INDEX
CREATE TABLE IF NOT EXISTS search_index (
    id INT AUTO_INCREMENT PRIMARY KEY,
    part_id INT NOT NULL,
    keyword VARCHAR(255) NOT NULL,
    weight INT DEFAULT 1,
    FOREIGN KEY (part_id) REFERENCES parts(id)
);

-- CUSTOMER PAYMENTS
CREATE TABLE IF NOT EXISTS customer_payments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(150) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    amount_paid DECIMAL(10,2) NOT NULL,
    payment_method ENUM('cash', 'card', 'other') DEFAULT 'cash',
    notes TEXT,
    created_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

-- AUDIT LOG ARCHIVE (for rows older than 6 months)
CREATE TABLE IF NOT EXISTS audit_logs_archive LIKE audit_logs;

-- ─── PERFORMANCE INDEXES ───────────────────────────────────────────────────
CREATE INDEX idx_search_keyword    ON search_index(keyword);
CREATE INDEX idx_audit_created     ON audit_logs(created_at);
CREATE INDEX idx_parts_stock       ON parts(current_stock);
CREATE INDEX idx_parts_active      ON parts(is_active);
CREATE INDEX idx_invoices_date     ON invoices(created_at);
CREATE INDEX idx_invoice_items_part ON invoice_items(part_id);
CREATE INDEX idx_batches_part      ON batches(part_id);
