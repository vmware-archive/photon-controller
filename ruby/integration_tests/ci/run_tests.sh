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

if [ "$DEPLOYER_TEST" ]
then
  bundle exec rake deployer
  bundle exec rake clean_vms_on_real_host
else
  bundle exec rake zookeeper

  # API tests
  bundle exec rake esxcloud:authorization

  # run tests using API & CLI drivers in subshells
  if [ -n "$NO_PARALLEL" ]
  then
    if [ -z "$DISABLE_API_TESTS" ]
    then
      export DRIVER=api
      bundle exec rake esxcloud:api
    fi

    if [ -z "$DISABLE_CLI_TESTS" ]
    then

      if [ -z "$DISABLE_RUBY_CLI_TESTS" ]
      then
        export DRIVER=cli
        bundle exec rake esxcloud:cli
      fi

      export DRIVER=gocli
      bundle exec rake esxcloud:gocli
    fi
  else
    pids=[]
    pids_idx=0

    if [ -z "$DISABLE_API_TESTS" ]
    then
      (
          export DRIVER=api
          bundle exec rake esxcloud:api
      ) &
      pids[$pids_idx]=$!
      pids_idx=$((pids_idx + 1))
    fi

    if [ -z "$DISABLE_CLI_TESTS" ]
    then
      (
          if [ -z "$DISABLE_RUBY_CLI_TESTS" ]
          then
            export DRIVER=cli
            bundle exec rake esxcloud:cli
          fi

          # Don't run gocli in parallel now due to the agent capacity
          export DRIVER=gocli
          bundle exec rake esxcloud:gocli
      ) &
      pids[$pids_idx]=$!
      pids_idx=$((pids_idx + 1))
    fi

    for pid in ${pids[*]}; do wait $pid; done;
  fi

  # re-set the driver to API
  export DRIVER=api

  # run life_cycle tests
  bundle exec rake esxcloud:life_cycle

  # run the housekeeper integration test
  if [ -n "$DISABLE_HOUSEKEEPER" ]; then
    bundle exec rake housekeeper
  fi

  if [ -z "$DISABLE_CLUSTER_INTEGRATION" ]; then
    env
    # The report being created by following line is not sane XML, so we are naming it such that Jenkins JUnit reporting will ignore it.
    bundle exec parallel_rspec -o '--tag cluster --format RspecJunitFormatter --out reports/rspec-cluster.Xxml --tag ~slow' -- spec/api/cluster/*_spec.rb
  fi

  # run the availability zone integration test
  if [ "$PROMOTE" = true ] && [ -z "$UPTIME" ]; then
    bundle exec rake availabilityzone
  fi

  # verify that no objects were left over at the end of the run
  bundle exec rake esxcloud:validate
fi
