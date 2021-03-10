#!/usr/bin/env zsh

export DB_URI=jdbc:postgresql://localhost:5432/postgres
export DB_USER=postgres
export DB_PWD=example
export HTTP_PORT=9001

SCRIPT_PATH=$(cd "$(dirname "${BASH_SOURCE[0]}")" || exit; pwd -P)
cd "$SCRIPT_PATH"/.. || exit

sbt "run" -jvm-debug 5005