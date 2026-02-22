CREATE TABLE distributors (
    id   SERIAL PRIMARY KEY,
    name VARCHAR NOT NULL UNIQUE
);

CREATE TABLE product_urls (
    id             SERIAL PRIMARY KEY,
    sku            VARCHAR NOT NULL,
    distributor_id INT     NOT NULL REFERENCES distributors(id),
    url            VARCHAR NOT NULL,
    UNIQUE (sku, distributor_id)
);

-- Distribuidoras iniciales
INSERT INTO distributors (name) VALUES ('vaperalia'), ('eciglogistica');
