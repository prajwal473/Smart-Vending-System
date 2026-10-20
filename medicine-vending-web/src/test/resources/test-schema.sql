-- TEST-ONLY schema for the in-memory H2 database. Mirrors the columns the Swing app and Stage 2-4 use.
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS medicines;

CREATE TABLE medicines (
  medicine_id INT AUTO_INCREMENT PRIMARY KEY,
  medicine_name VARCHAR(100) NOT NULL,
  category VARCHAR(50),
  price DECIMAL(10,2) NOT NULL,
  stock INT NOT NULL,
  slot_number INT,
  expiry_date DATE,
  prescription_required BOOLEAN DEFAULT FALSE
);

CREATE TABLE transactions (
  transaction_id INT AUTO_INCREMENT PRIMARY KEY,
  medicine_id INT,
  medicine_name VARCHAR(100),
  quantity INT,
  total_amount DECIMAL(10,2),
  transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
