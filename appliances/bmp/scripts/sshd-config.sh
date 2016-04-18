#!/bin/bash
shopt -s nocasematch
if [[ $SSHD_ENABLE_ROOT_LOGIN == "true" ]]; then
  val="yes"
else
  val="no"
fi

# Replace existing PermitRootLogin line, or append one if none is found
if [[ ! -z "$(cat /etc/ssh/sshd_config | grep PermitRootLogin)" ]]; then
  sed -i "s/^\s*PermitRootLogin.*/PermitRootLogin $val/g" /etc/ssh/sshd_config
else
  echo "PermitRootLogin $val" >> /etc/ssh/sshd_config
fi
