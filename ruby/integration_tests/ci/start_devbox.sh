#!/bin/bash -ex

export WORKSPACE=${WORKSPACE:=$(git rev-parse --show-toplevel)}
export DEVBOX=${DEVBOX:="$WORKSPACE/devbox-photon"}
export TESTS=${TESTS:="$WORKSPACE/ruby/integration_tests"}
if [ ! -d "$DEVBOX" ]; then fail "$DEVBOX is not accessible"; fi

# Only continue if all these environment variables are defined
checklist=(PUBLIC_NETWORK_IP PUBLIC_NETWORK_NETMASK PUBLIC_NETWORK_GATEWAY BRIDGE_NETWORK)
for var in "${checklist[@]}"; do
  if [ -z "$(printenv "$var")" ]; then
    echo Cannot start devbox. "$var" is not defined.
    echo This list of env vars must be defined: "${checklist[@]}"
    exit 1
  fi
done

cd "$DEVBOX"

# Installs vagrant-guests-photon
./update_dependencies.sh

# Exporting deployment id generated randomly used to create deployment document in cloudstore:seed
if [ "$(uname)" == "Darwin" ]; then
  export RANDOM_GENERATED_DEPLOYMENT_ID=fixed-test-deployemnt-id
else
  export RANDOM_GENERATED_DEPLOYMENT_ID=$(shuf -i 1000000000-10000000000 -n 1)
fi

if [ -n "$DEPLOYER_TEST" ]; then
  ./prepare-devbox-deployment.sh
  return
fi

# Start fresh devbox and build services
rm -rf "$DEVBOX/log/"
./gradlew :devbox:renewPhoton


# Seed cloudstore with deployment
#(cd "$TESTS" && bundle exec rake cloudstore:seed)
#
## Register real agent to devbox
#if [ -n "$REAL_AGENT" ]; then
#  (
#    cd "$TESTS"
#    bundle exec rake seed:host
#
#    # Wait for the host monitoring service to detect the newly added host
#    bundle exec rake monitor:host
#  )
#fi
