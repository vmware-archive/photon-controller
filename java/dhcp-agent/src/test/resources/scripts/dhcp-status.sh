#!/usr/bin/env bash

DIR="$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
FILENAME="dhcpServerTestConfig"

while IFS='' read -r line || [[ -n "$line" ]]; do
    if [[ $line == *"error"* ]]
    then
      exit 1
    fi
done < "$DIR/$FILENAME"

exit 0