#!/bin/bash -ex

if [ -n "$REAL_AGENT" ]; then
    trap 'cd $TESTS && bundle exec rake download_esx_logs || true' EXIT
fi

function checkenv ()
{
  for var in $@
  do
    if [ -z "$(printenv $var)" ]
      then
        echo Cannot run test. $var is not defined.
        echo This list of properties must be defined. $@
        exit 1
    fi
  done
}

source $(dirname $BASH_SOURCE)/common.sh

# Define any custom config process
if [ -n "$CUSTOM_TEST_CONFIG" ]; then
    echo Using custom settings in $CUSTOM_TEST_CONFIG
    source $CUSTOM_TEST_CONFIG
else
    echo No CUSTOM_TEST_CONFIG to override default test behavior
fi

env

# environment checks
if [ -z "NO_TESTENVCHECK" ]; then
  # Auths
  if [ -n "$ENABLE_AUTH" ]; then
    echo "auth enabled"
    envlist="\
    PHOTON_ADMIN_GROUP\
    PHOTON_USERNAME_ADMIN\
    PHOTON_PASSWORD_ADMIN\
    PHOTON_ADMIN2_GROUP\
    PHOTON_USERNAME_ADMIN2\
    PHOTON_PASSWORD_ADMIN2\
    PHOTON_TENANT_ADMIN_GROUP\
    PHOTON_USERNAME_TENANT_ADMIN\
    PHOTON_PASSWORD_TENANT_ADMIN\
    PHOTON_PROJECT_USER_GROUP\
    PHOTON_USERNAME_PROJECT_USER\
    PHOTON_PASSWORD_PROJECT_USER\
    PHOTON_USERNAME_NON_ADMIN\
    PHOTON_PASSWORD_NON_ADMIN\
    PHOTON_AUTH_LS_ENDPOINT\
    PHOTON_AUTH_SERVER_PORT\
    PHOTON_AUTH_SERVER_TENANT\
    PHOTON_SWAGGER_LOGIN_URL\
    PHOTON_SWAGGER_LOGOUT_URL"
    checkenv $envlist
  fi

  # Cluster mgr
  if [ -z "$DISABLE_CLUSTER_INTEGRATION" ]; then
    envlist="\
    MESOS_ZK_DNS\
    MESOS_ZK_GATEWAY\
    MESOS_ZK_NETMASK\
    MESOS_ZK_1_IP\
    KUBERNETES_ETCD_1_IP\
    KUBERNETES_MASTER_IP\
    SWARM_ETCD_1_IP\
    KUBERNETES_IMAGE\
    MESOS_IMAGE\
    SWARM_IMAGE"
    checkenv $envlist
  fi

  # get the image/iso file
  if [ -z "$NO_IMAGE" ]; then
    envlist="\
    ESXCLOUD_DISK_IMAGE\
    ESXCLOUD_BAD_DISK_IMAGE\
    ESXCLOUD_DISK_OVA_IMAGE\
    ESXCLOUD_DISK_BOOTABLE_OVA_IMAGE\
    ESXCLOUD_ISO_FILE\
    MGMT_IMAGE"
    checkenv $envlist
  fi

  # general must have
  envlist="\
  API_ADDRESS\
  API_FE_PORT\
  ESX_IP\
  ESX_DATASTORE\
  ESX_DATASTORE_ID\
  ESX_MGMT_PORT_GROUP\
  ESX_VM_PORT_GROUP\
  export ESX_USERNAME\
  export ESX_PASSWORD\
  MGMT_VM_DNS_SERVER\
  MGMT_VM_GATEWAY\
  MGMT_VM_IP\
  MGMT_VM_NETMASK\
  DEPLOYER_ADDRESS\
  ZOOKEEPER_ADDRESS\
  ZOOKEEPER_PORT"
  checkenv $envlist
fi

cd $TESTS

# verify that no objects were left over at the beginning of the run
if [ -n "$DEVBOX" ]
then
  bundle exec rake clean_vms_on_real_host
fi

bundle exec rake zookeeper

# API tests
bundle exec rake esxcloud:authorization

# run tests using API & CLI drivers in subshells
if [ -n "$NO_PARALLEL" ]
then
  export DRIVER=api
  bundle exec rake esxcloud:api

  export DRIVER=cli
  bundle exec rake esxcloud:cli

  export DRIVER=gocli
  bundle exec rake esxcloud:gocli
else
  pids=[]
  (
      export DRIVER=api
      bundle exec rake esxcloud:api
  ) &
  pids[0]=$!

  (
      export DRIVER=cli
      bundle exec rake esxcloud:cli
  ) &
  pids[1]=$!

  for pid in ${pids[*]}; do wait $pid; done;

  # Don't run gocli in parrllel now due to the agent capacity
  export DRIVER=gocli
  bundle exec rake esxcloud:gocli
fi

# verify that no objects were left over at the end of the run
export DRIVER=api
bundle exec rake esxcloud:deployment
bundle exec rake esxcloud:validate
bundle exec rake esxcloud:life_cycle

# run the housekeeper integration test
if [ -n "$DISABLE_HOUSEKEEPER" ]; then
  bundle exec rake housekeeper
fi

if [ -z "$DISABLE_CLUSTER_INTEGRATION" ]; then
  env
  bundle exec parallel_rspec -o '--tag cluster --format RspecJunitFormatter --out reports/rspec-cluster.xml --tag ~slow' -- spec/api/cluster/*_spec.rb
fi

if [ -z "$DISABLE_DEPLOYER" ]; then
  bundle exec rake deployer
fi
