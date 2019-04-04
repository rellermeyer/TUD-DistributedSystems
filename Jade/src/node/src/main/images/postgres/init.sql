DROP DATABASE IF EXISTS jade;

CREATE DATABASE jade;
\connect jade

CREATE TABLE jade(name VARCHAR(45));

INSERT INTO jade (name)
VALUES
('1'),
('2'),
('3'),
('4'),
('5');
