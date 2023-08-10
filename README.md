# Bring Your Own Schema (BYOS)

**Bring Your Own Schema (BYOS)** is an implementation based on the concepts presented in my thesis, focusing on transpiling GraphQL queries to SQL.
This repository provides a practical example of how to bring your own GraphQL schema and use it to generate efficient SQL queries.

**This is very much a prototype for now!**

## Features
- One SQL query for every root level query.
- GraphQL schema and SQL schema are not generated and do not contain BYOS specific code. 
- jOOQ is used to ensure correctness of the SQL configuration. 
- Supports filter arguments, pagination and sorting.

## If you want to try it out...

... you will have to have a local psql version of the [sakila](https://github.com/jOOQ/sakila/tree/main/postgres-sakila-db) database with this small addition:
```SQL
ALTER TABLE category
ADD COLUMN parent_category_id INTEGER;
```

You could also set up your own database by:
- changing the connection of jOOQ (and the language if you do not want to use psql).
- using a GraphQL schema matching the SQL schema.
- defining relationships and how to resolve them.

---

For more details about the approach and implementation, refer to the associated thesis.

Happy querying and transpiling!
