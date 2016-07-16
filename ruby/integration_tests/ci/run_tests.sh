#!/bin/bash -ex


sleep 3600

#if [ -n "$REAL_AGENT" ]; then
#    trap 'cd $TESTS && bundle exec rake download_esx_logs || true' EXIT
#fi
#
#source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
#
## Define any custom config process
#if [ -n "$CUSTOM_TEST_CONFIG" ]; then
#    echo Using custom settings in "$CUSTOM_TEST_CONFIG"
#    # Note: Do NOT put quotes around this. $CUSTOM_TEST_CONFIG has been improperly
#    # used on our build machines, and isn't just a path to a file, but includes
#    # arguments that must be passed to that file. Using quotes will break that.
#    source $CUSTOM_TEST_CONFIG
#else
#    echo No CUSTOM_TEST_CONFIG to override default test behavior
#fi
#
#env
#
#cd "$TESTS"
#
## verify that no objects were left over at the beginning of the run
#if [ -n "$DEVBOX" ]; then
#  bundle exec rake clean_vms_on_real_host
#fi
#
#if [ "$DEPLOYER_TEST" ]; then
#  bundle exec rake deployer
#  bundle exec rake clean_vms_on_real_host
#  exit $?
#fi
#
## Ensure there is a default network
#if [ -z "$DISABLE_DEFAULT_NETWORK_CREATE" ]; then
#  bundle exec rake seed:ensure_default_network
#fi
#
## API tests
#if [ -z "$DISABLE_AUTHORIZATION_TESTS" ]; then
#  bundle exec rake esxcloud:authorization
#fi
#
#drivers=()
#if [ -z "$DISABLE_API_TESTS" ]; then
#  drivers+=(api)
#fi
#
#if [ -z "$DISABLE_CLI_TESTS" ]; then
#  drivers+=(gocli)
#fi
#
#pids=()
#for driver in "${drivers[@]}"; do
#  DRIVER="${driver}" bundle exec rake "esxcloud:${driver}" & pids+=($!)
#  if [ -n "$NO_PARALLEL" ]; then
#    # The last value we just put into $pids
#    wait "${pids[-1]}"
#  fi
#done
#
## Wait for parallel tests, will do nothing if not parallel
#for pid in "${pids[@]}"; do wait "$pid"; done
#
## Make sure driver is set to API for remaining tests
#export DRIVER=api
#
## run life_cycle tests
#if [ -z "$DISABLE_LIFECYCLE_TESTS" ]; then
#  bundle exec rake esxcloud:life_cycle
#fi
#
## run the housekeeper integration test
#if [ -z "$DISABLE_HOUSEKEEPER" ]; then
#  bundle exec rake housekeeper
#fi
#
#if [ -z "$DISABLE_CLUSTER_INTEGRATION" ]; then
#  bundle exec rake cluster
#fi
#
## run the availability zone integration test
#if [ "$PROMOTE" = "true" ] && [ -z "$UPTIME" ]; then
#  bundle exec rake availabilityzone
#fi
#
## Disable in promote until graphite story is figured out for promote
## Only run when REAL_AGENT is defined
#
#if [ -z "$DISABLE_STATS_TESTS" ]; then
#  if [ "$PROMOTE" != "true" ] && [ ! -z "$REAL_AGENT" ]; then
#    bundle exec rake agent:stats
#  fi
#fi
## verify that no objects were left over at the end of the run
#bundle exec rake esxcloud:validate
#
#if [ -z "$PROMOTE" ]; then
#  # Examines the service logs to verify no instances of usernames or passwords
#  # Run after logging from tests and clean up have completed.
#  # Disable in promote due to multiple hosts each with their own logs that would
#  # need to be downloaded and examined.
#  bundle exec rake verify_logs
#fi
