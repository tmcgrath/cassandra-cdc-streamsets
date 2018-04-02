create keyspace cdc WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

use cdc;

CREATE TABLE users (
    id uuid,
    firstname text,
    lastname text,
    email text,
    created_date timestamp,
    PRIMARY KEY (id)
);

ALTER TABLE users WITH cdc = true;

