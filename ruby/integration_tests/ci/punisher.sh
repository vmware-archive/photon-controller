#!/bin/bash -x

# Assumes that IP_RANGE is set to the 3 octets of the mgmt IP range i.e.
# 10.146.36 for inf3 and MGMT_PASSWD is set to the root password for the mgmt
# VMs.

source $(dirname $BASH_SOURCE)/common.sh

APIFE_HOST=${IP_RANGE}.100
SYSLOG_HOST=${IP_RANGE}.102
export API_ADDRESS=http://$APIFE_HOST:9000
export no_proxy=$APIFE_HOST,$no_proxy

cd $WORKSPACE/ruby
./bin/load-flavors $API_ADDRESS common/test_files/flavors

cd $TESTS

export DRIVER=gocli
bundle exec rake esxcloud:gocli
error=$?
unset DRIVER

# Collect support info
LOGDIR=$WORKSPACE/punisher-logs

# scp wrapper to avoid key checks
function SCP() {
  sshpass \
    -p $MGMT_PASSWD \
    scp \
    -o UserKnownHostsFile=/dev/null \
    -o StrictHostKeyChecking=no \
    $@
}

rm -rf $LOGDIR

mkdir -p $LOGDIR/apife
SCP -r root@$APIFE_HOST:/var/log/esxcloud $LOGDIR/apife/

mkdir -p $LOGDIR/agents
SCP -r root@$SYSLOG_HOST:/srv/log/* $LOGDIR/agents/

exit $error
