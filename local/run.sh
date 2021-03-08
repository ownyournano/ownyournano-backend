#!/usr/bin/env zsh

#export DATABASE_DRIVER=org.postgresql.Driver;
#export DATABASE_URL=jdbc:postgresql://localhost:5432/postgres;
#export DATABASE_USERNAME=postgres;
#export DATABASE_PASSWORD=example;

SCRIPT_PATH=$(cd "$(dirname "${BASH_SOURCE[0]}")" || exit; pwd -P)
cd "$SCRIPT_PATH"/.. || exit

sbt "run" -jvm-debug 5005