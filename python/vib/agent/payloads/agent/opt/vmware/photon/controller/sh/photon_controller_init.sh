#!/bin/sh
#
# Copyright 2013 VMware, Inc.  All rights reserved.
#

PHOTON_CONTROLLER_RPCMD="localcli --plugin-dir=/usr/lib/vmware/esxcli/int sched group"
PHOTON_CONTROLLER_RPROOT="host/vim/vimuser"

init_destroy_rp() {
  # Delete the given resource pool from the system
  #
  # Args:
  #   1: Name of the resource pool (not full path)

  path="${PHOTON_CONTROLLER_RPROOT}/${1}"

  ${PHOTON_CONTROLLER_RPCMD} list --group-path=$path &> /dev/null
  if [ $? -ne 0 ]; then
    # resource group doesn't exist. nothing to delete.
    return 0;
  fi

  ${PHOTON_CONTROLLER_RPCMD} delete --group-path=$path
  if [ $? -ne 0 ]; then
    echo "Resource pool $1 destruction failed. Host may require rebooting."
    return 1;
  fi
  return 0
}


init_config_rp() {
  # Create the given resource pool if it doesn't exist and set the
  # given min/max limit.
  #
  # Args:
  #   1: Name of the resource pool (not full path)
  #   2: Minimum memory reservation for the resource pool
  #   3: Maximum memory reservation for the resource pool
  #   4: Minimum cpu reservation for the resource pool
  #   5: Maximum cpu reservation for the resource pool

  rp=${1}
  memmin=${2}
  memmax=${3}
  cpumin=${4}
  cpumax=${5}
  path="${PHOTON_CONTROLLER_RPROOT}/${rp}"
  echo "Called with $rp $memmin $memmax $cpumin $cpumax $path"
  group=$(${PHOTON_CONTROLLER_RPCMD} list | grep ${path})
  if [ -z "$group" ]; then
    ${PHOTON_CONTROLLER_RPCMD} add --group-name=${rp} --parent-path=${PHOTON_CONTROLLER_RPROOT}
    if [ $? -ne 0 ]; then
      echo "Failed to add resource pool ${rp} to ${PHOTON_CONTROLLER_RPROOT}"
      return 1
    fi
  fi
  # Set RP config to memmin, memmax and  minlimit=unlimited
  ${PHOTON_CONTROLLER_RPCMD} setmemconfig -g ${path} --min=${memmin} --max=${memmax} --minlimit=-1 -u mb
  if [ $? -ne 0 ]; then
      echo "Failed to setup memory config of photon controller resource pool ${path}"
      destroy_rp
      return 1
  fi
  # Set RP config to cpumin, cpumax and minlimit=unlimited
  ${PHOTON_CONTROLLER_RPCMD} setcpuconfig -g ${path} --min=${cpumin} --max=${cpumax}  --minlimit=-1 -u pct
  if [ $? -ne 0 ]; then
      echo "Failed to setup cpu config of resource pool ${path}"
      destroy_rp
      return 1
  fi
  return 0
}


init_start() {
  # Start the given command through the watchdog if it wasn't started already.
  #
  # Args:
  #   1: Watchdog tag
  #   2: Resource pool to run the command in
  #   3: Executable
  #   4..: Params

  tag=${1}; shift;
  sched_param="++group=${PHOTON_CONTROLLER_RPROOT}/${1}"; shift;
  binary=${1}; shift;
  args=$*

  echo "tag $tag"
  echo "sched_param $sched_param"
  echo "binary $binary"
  echo "args $args"

  WATCHDOG_RUNNING="$(/sbin/watchdog.sh -r ${tag})"
  if [ $WATCHDOG_RUNNING -gt 0 2> /dev/null ]; then
    echo "watchdog for ${tag} is already running"
  else
    configure_rp
    if [ $? -ne 0 ]; then
      echo "Failed to create resource pool"
      exit 1
    fi
    setsid /sbin/watchdog.sh -s ${tag} -q 1000000 "${binary} ${sched_param} ${args}" 2>&1 | logger -t "${tag}" &
    echo "started ${tag}"
  fi
}


init_stop() {
  # Stop the daemon given by its watchdog tag.
  #
  # Stopping the daemon involves
  # - Disabling the watchdog
  # - Sending the kill signal to the daemon
  # - Deleting the resource pool for the daemon
  #
  # Args:
  #   1: Watchdog tag
  #   2: pid of daemon the watchdog is attached to

  tag=${1}
  daemon_pid="${2}"
  watchdog_running="$(/sbin/watchdog.sh -r ${tag})"
  if [ -n "${watchdog_running}" ]; then
    echo "Stopping watchdog for ${tag}"
    /sbin/watchdog.sh -k "${tag}"
  fi
  if [ -n "${daemon_pid}" ]; then
    echo "Killing ${tag} daemon (${daemon_pid})"
    kill -9 ${daemon_pid}
  fi
  destroy_rp
}


usage() {
  echo "Usage: $0 {start|stop|restart|status}"
}


init_main() {
  # Main entry point for a photon controller init script. Parse the command line
  # and execute the given command.
  #
  # Scripts using this library must provide four routine stubs start, stop,
  # destroy_rp and configure_rp that call the init_* equivalents with specific
  #  parameters.
  #
  # Args:
  #   1: Watchdog tag
  #   2: Command

  tag=${1}
  cmd=${2}
  if [ -z "${tag}" ]; then
    usage
    exit 1
  fi

  case $cmd in
    "start")
      start
      ;;
    "stop")
      stop
      ;;
    "remove")
      stop
      ;;
    "restart")
      stop
      start
      ;;
    "status")
      watchdog.sh -r ${tag}
      if [ $? -eq 0 ]; then
        echo "${tag} running"
        exit 0
      else
        echo "${tag} not-running"
        exit 1
      fi
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}
