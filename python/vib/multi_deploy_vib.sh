#!/bin/bash -xe

usage() {
  echo "usage: multi_deploy_vib.sh -t <target hosts> -n <vibname> -f <vibfile>"
  exit 1
}

while getopts "t:n:f:h" opt; do
  case "$opt" in
    t)
      target_hosts=$OPTARG
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

if [ -z "$vibname" ] || [ -z "$target_hosts" ] || [ -z "$vibfile" ]; then
  usage
fi

for target_host in ${target_hosts//,/ }; do
  vib_dir=`dirname $0`
  $vib_dir/deploy_vib.sh -t $target_host -n $vibname -f $vibfile
done

exit 0
