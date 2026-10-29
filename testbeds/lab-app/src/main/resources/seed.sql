-- lab-app: фиксированные учётные данные стенда. Пароли читаются прямо из таблицы (сравнение строк) —
-- это намеренно упрощённый стенд для измерения качества сканера, а не образец безопасной реализации.

INSERT INTO users (username, password, email, role) VALUES
    ('alice', 'alice-pass-1', 'alice@lab.test', 'USER'),
    ('bob', 'bob-pass-1', 'bob@lab.test', 'USER'),
    ('admin', 'admin-pass-1', 'admin@lab.test', 'ADMIN');

INSERT INTO orders (id, owner_username, content) VALUES
    (1001, 'alice', 'Заказ Alice #1001: ноутбук, 1 шт.'),
    (1002, 'bob', 'Заказ Bob #1002: клавиатура, 2 шт.'),
    (1003, 'admin', 'Заказ Admin #1003: серверное оборудование.');

INSERT INTO documents (id, owner_username, content) VALUES
    (2001, 'alice', 'Документ Alice #2001: договор на оказание услуг.'),
    (2002, 'bob', 'Документ Bob #2002: внутренняя заметка.'),
    (2003, 'admin', 'Документ Admin #2003: конфиденциальная служебная записка.');

-- Документ 2001 явно расшарен Alice для Bob — это разрешённый доступ, а не IDOR.
INSERT INTO document_shares (document_id, shared_with_username) VALUES
    (2001, 'bob');

INSERT INTO posts (id, author_username, content) VALUES
    (3001, 'alice', 'Alice: добро пожаловать на тестовый стенд!');

INSERT INTO products (id, name, code, price, description) VALUES
    (1, 'Keyboard Lab 100', 'KB-100', 19.90, 'Механическая клавиатура, учебный товар.'),
    (2, 'Mouse Lab 200', 'MS-200', 9.90, 'Беспроводная мышь, учебный товар.'),
    (3, 'Monitor Lab 300', 'MN-300', 149.00, 'Монитор 24", учебный товар.');
