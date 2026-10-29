-- lab-app: заведомо упрощённая схема учебного стенда. Намеренно без FK-каскадов там, где это усложнило бы
-- сценарии (например document_shares может указывать на несуществующего пользователя — это не проверяется).

DROP TABLE IF EXISTS document_shares;
DROP TABLE IF EXISTS documents;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS posts;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    username TEXT PRIMARY KEY,
    password TEXT NOT NULL,
    email TEXT NOT NULL,
    role TEXT NOT NULL
);

CREATE TABLE orders (
    id INT PRIMARY KEY,
    owner_username TEXT NOT NULL,
    content TEXT NOT NULL
);

CREATE TABLE documents (
    id INT PRIMARY KEY,
    owner_username TEXT NOT NULL,
    content TEXT NOT NULL
);

CREATE TABLE document_shares (
    document_id INT NOT NULL,
    shared_with_username TEXT NOT NULL,
    PRIMARY KEY (document_id, shared_with_username)
);

CREATE TABLE posts (
    id INT PRIMARY KEY,
    author_username TEXT NOT NULL,
    content TEXT NOT NULL
);

CREATE TABLE products (
    id INT PRIMARY KEY,
    name TEXT NOT NULL,
    code TEXT NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    description TEXT NOT NULL
);
