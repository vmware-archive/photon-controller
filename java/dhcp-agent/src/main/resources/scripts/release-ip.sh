#!/usr/bin/env bash

MACADDRESS=$1
DHCPLEASEFILE=$2
DHCPRELEASEIP=$3
NETWORK=$4

while IFS='' read -r line || [[ -n "$line" ]]; do
    FOUND=0
    for field in $line
    do
        if [[ $FOUND -eq 1 ]]
        then
          $DHCPRELEASEIP $NETWORK $field $MACADDRESS
          exit 0
        fi

        if [[ "$field" == "$MACADDRESS" ]]
        then
          let "FOUND += 1"
        fi
    done

done < "$DHCPLEASEFILE"

exit 1