#!/bin/bash -xe

usage() {
  echo "usage: deploy_vib.sh -t <target host> -n <vibname> -f <vibfile>"
  exit 1
}

ssh_base() {
  ssh -l root -o $control_path $@
}

while getopts "t:n:f:h" opt; do
  case "$opt" in
    t)
      target_host=$OPTARG
      ;;
    n)
      vibname=$OPTARG
      ;;
    f)
      vibfile=$OPTARG
      ;;
    h|*)
      usage
      ;;
    esac
done

if [ -z "$vibname" ] || [ -z "$target_host" ] || [ -z "$vibfile" ]; then
  usage
fi

if [ ! -f "$vibfile" ]; then
  echo "No such file: $vibfile"
  exit 1
fi

tmpdir=`mktemp -d -t deploy_vib.XXXXX`
control_path="ControlPath=${tmpdir}/master-$$"
vibfilename=`basename $vibfile`

cleanup() {
  rm -rf $tmpdir
}

trap cleanup EXIT

sshpass -p "$ESX_PWD" scp -o $control_path $vibfile root@$target_host:/tmp/$vibfilename

# Assume if vib remove fails, its because the vib isn't installed
sshpass -p "$ESX_PWD" ssh $target_host "esxcli software vib remove --vibname=${vibname} -f || true"
sshpass -p "$ESX_PWD" ssh $target_host "esxcli software vib install -v /tmp/${vibfilename} -f"

exit 0
