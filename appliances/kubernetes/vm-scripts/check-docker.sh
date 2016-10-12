#!/bin/bash

restart_retry_max="5"
restart_retry="0"
retry_max="20"
while [ "$restart_retry" -lt "$restart_retry_max" ]
do
  retry="0"
  while [ "$retry" -lt "$retry_max" ]
  do
    docker version
    if [ "$?" == "0" ]; then
      echo "Docker daemon is up and running!"
      break 2
    fi
    sleep 1
    retry=`expr $retry + 1`
  done
    echo "Docker unreachable after $retry_max retries, restarting docker ($restart_retry/$restart_retry_max)"
    systemctl restart docker
    sleep 1
    restart_retry=`expr $restart_retry + 1`
done

if [ "$retry" -eq "$retry_max" ] && [ "$restart_retry" -eq "$restart_retry_max" ]
then
  echo "Docker daemon is not up yet!"
fi
