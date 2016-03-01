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
  # This closes the control channel
  ssh_base -O exit $target_host || true

  rm -rf $tmpdir || true
}

trap cleanup EXIT

ssh_base -o ControlMaster=auto -f -N $target_host 2>/dev/null 1>&2
if [ $? -gt 0 ]; then
    echo "ERROR: error connecting to ssh server"
    exit 1
fi

scp -o $control_path $vibfile root@$target_host:/tmp/$vibfilename

# Assume if vib remove fails, its because the vib isn't installed
ssh_base -t $target_host "esxcli software vib remove --vibname=${vibname} -f || true"
ssh_base -t $target_host "esxcli software vib install -v /tmp/${vibfilename} -f"

exit 0
