#!/usr/bin/env zsh
kubectl create secret generic nanter-db-secret --from-file=password="$HOME"/code/.password-database-nanter.txt
