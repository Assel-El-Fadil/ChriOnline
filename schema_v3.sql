CREATE DATABASE chriOnline;
USE chriOnline;

CREATE TABLE users (
    id            INT          NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(64)  NOT NULL,
    email         VARCHAR(150) NOT NULL,
    role          ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email    (email),
    INDEX idx_users_username     (username),
    INDEX idx_users_role         (role)
);

-- ============================================================
--  TABLE: products
-- ============================================================
CREATE TABLE products (
    id          INT           NOT NULL AUTO_INCREMENT,
    category    ENUM(
                    'ELECTRONIQUES',
                    'VETEMENTS',
                    'ELECTROMENAGER',
                    'BEAUTE_ET_COSMETIQUES',
                    'JEUX_VIDEO',
                    'SANTE',
                    'FITNESS'
                ) NOT NULL,
    name        VARCHAR(150)  NOT NULL,
    description TEXT,
    price       DECIMAL(10,2) NOT NULL,
    stock       INT           NOT NULL DEFAULT 0,
    active      TINYINT(1)    NOT NULL DEFAULT 1,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_products_category_active (category, active),
    INDEX idx_products_name            (name),
    INDEX idx_products_stock           (stock)
);

-- ============================================================
--  TABLE: carts
--
--  One row per user.  A cart is created the first time a user
--  adds an item (or eagerly at registration — your choice).
--  The UNIQUE constraint on user_id is what enforces the
--  "one cart per user" rule at the database level, so even if
--  two concurrent requests both try to INSERT a cart for the
--  same user, only one succeeds.
--
--  Creation strategy used in CartDAO:
--    INSERT IGNORE INTO carts (user_id) VALUES (?)
--    followed by
--    SELECT id FROM carts WHERE user_id = ?
--  This is atomic and safe under concurrent access.
-- ============================================================
CREATE TABLE carts (
    id         INT       NOT NULL AUTO_INCREMENT,
    user_id    INT       NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_carts_user (user_id),       -- one cart per user, enforced at DB level

    CONSTRAINT fk_carts_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE    -- deleting a user wipes their cart automatically
        ON UPDATE CASCADE
);

-- ============================================================
--  TABLE: cart_items
--
--  Each row is one product line inside a cart.
--  cart_id FK has ON DELETE CASCADE, meaning:
--    - Removing a cart item = DELETE FROM cart_items WHERE id = ?
--      The row is physically gone.  Nothing soft-deleted.
--    - Deleting the parent cart cascades and removes all its
--      items automatically (happens at checkout or account deletion).
--
--  The UNIQUE KEY on (cart_id, product_id) prevents duplicate
--  product rows inside the same cart — adding an item that
--  already exists must UPDATE quantity, not INSERT a new row.
-- ============================================================
CREATE TABLE cart_items (
    id          INT       NOT NULL AUTO_INCREMENT,
    cart_id     INT       NOT NULL,             -- FK → carts.id  (replaces direct user_id)
    product_id  INT       NOT NULL,
    quantity    INT       NOT NULL DEFAULT 1
                          CHECK (quantity > 0), -- quantity 0 is meaningless — delete the row
    added_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_cart_items_cart_product (cart_id, product_id),  -- one row per product per cart

    CONSTRAINT fk_cart_items_cart
        FOREIGN KEY (cart_id) REFERENCES carts(id)
        ON DELETE CASCADE    -- deleting the cart deletes all its items
        ON UPDATE CASCADE,

    CONSTRAINT fk_cart_items_product
        FOREIGN KEY (product_id) REFERENCES products(id)
        ON DELETE RESTRICT   -- cannot delete a product that is in someone's active cart
        ON UPDATE CASCADE,

    INDEX idx_cart_items_cart    (cart_id),
    INDEX idx_cart_items_product (product_id)
);

-- ============================================================
--  TABLE: orders
-- ============================================================
CREATE TABLE orders (
    id             INT           NOT NULL AUTO_INCREMENT,
    user_id        INT           NOT NULL,
    total_amount   DECIMAL(10,2) NOT NULL,
    status         ENUM(
                       'PENDING',
                       'VALIDATED',
                       'SHIPPED',
                       'DELIVERED',
                       'CANCELLED'
                   ) NOT NULL DEFAULT 'PENDING',
    payment_method ENUM('CARD','CASH_ON_DELIVERY') NOT NULL DEFAULT 'CARD',
    payment_ref    VARCHAR(20),
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                 ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT   -- cannot delete a user who has order history
        ON UPDATE CASCADE,

    INDEX idx_orders_user_created (user_id, created_at DESC),
    INDEX idx_orders_status       (status),
    INDEX idx_orders_payment_ref  (payment_ref)
);

-- ============================================================
--  TABLE: order_items
-- ============================================================
CREATE TABLE order_items (
    id          INT           NOT NULL AUTO_INCREMENT,
    order_id    INT           NOT NULL,
    product_id  INT           NOT NULL,
    quantity    INT           NOT NULL,
    unit_price  DECIMAL(10,2) NOT NULL,
    subtotal    DECIMAL(10,2) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id) REFERENCES products(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    INDEX idx_order_items_order   (order_id),
    INDEX idx_order_items_product (product_id)
);

-- ============================================================
--  SEED DATA
-- ============================================================

-- SHA-256("admin123")
INSERT INTO users (username, password_hash, email, role) VALUES
('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9',
 'admin@chrionline.ma', 'ADMIN');

-- SHA-256("pass123")
INSERT INTO users (username, password_hash, email, role) VALUES
('alice', '9f8dfa5c7c3d898fa3cbfa75a82e3dfc1be32700fce9e0f8b0e46c8e7ae1c2e4',
 'alice@test.ma', 'USER'),
('bob',   '9f8dfa5c7c3d898fa3cbfa75a82e3dfc1be32700fce9e0f8b0e46c8e7ae1c2e4',
 'bob@test.ma', 'USER');

INSERT INTO products (category, name, description, price, stock) VALUES
('ELECTRONIQUES',       'Smartphone Galaxy X12',  'Android 14, 6.7" AMOLED, 256GB',          3299.00, 15),
('ELECTRONIQUES',       'Laptop ProBook 450',      'Intel i5, 16GB RAM, 512GB SSD, 15.6" FHD', 8750.00,  8),
('ELECTRONIQUES',       'Wireless Earbuds TWS',    'Bluetooth 5.3, ANC, 30h battery',           449.00, 40),
('ELECTRONIQUES',       'USB-C Hub 7-in-1',        'HDMI 4K, 3x USB-A, SD card, 100W PD',       299.00, 25),
('VETEMENTS',           'Casual Cotton T-Shirt',   '100% cotton, S/M/L/XL, 5 colours',          129.00, 60),
('VETEMENTS',           'Slim Fit Jeans',           'Stretch denim, dark blue, sizes 28-36',      349.00, 30),
('VETEMENTS',           'Running Sneakers V3',      'Lightweight mesh upper, cushioned sole',      599.00, 20),
('SANTE',               'Green Tea Premium 200g',  'Organic Japanese sencha, loose leaf',          89.00, 50),
('FITNESS',             'Protein Bar Box x12',     'Chocolate, 20g protein per bar',              210.00, 35),
('ELECTROMENAGER',      'Mineral Water 1.5L x6',   'Still natural mineral water, pack of 6',       42.00,  0);
-- ^ out of stock — use to test ERR|Not enough stock

-- Create alice's cart (user_id = 2) and populate it with two items
INSERT INTO carts (user_id) VALUES (2);
-- alice's cart gets id = 1 (first row in carts table)
INSERT INTO cart_items (cart_id, product_id, quantity) VALUES
(1, 1, 1),   -- 1x Smartphone Galaxy X12
(1, 3, 2);   -- 2x Wireless Earbuds TWS