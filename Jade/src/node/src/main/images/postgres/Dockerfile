# vim:set ft=dockerfile:

FROM postgres:latest

EXPOSE 5432/tcp
ENV POSTGRES_PASSWORD pass

COPY init.sql ./docker-entrypoint-initdb.d/
