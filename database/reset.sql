-- Wipes transactional data but keeps core config/users/parts
DELETE FROM returns;
DELETE FROM invoice_items;
DELETE FROM invoices;
DELETE FROM batches;
DELETE FROM audit_logs;

ALTER TABLE invoices AUTO_INCREMENT = 1;
ALTER TABLE invoice_items AUTO_INCREMENT = 1;
ALTER TABLE returns AUTO_INCREMENT = 1;
ALTER TABLE batches AUTO_INCREMENT = 1;
ALTER TABLE audit_logs AUTO_INCREMENT = 1;
