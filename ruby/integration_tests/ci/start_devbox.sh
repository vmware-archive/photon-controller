#!/bin/bash -ex

export WORKSPACE=${WORKSPACE:=$(git rev-parse --show-toplevel)}
export DEVBOX=${DEVBOX:="$WORKSPACE/devbox-photon"}
export TESTS=${TESTS:="$WORKSPACE/ruby/integration_tests"}
if [ ! -d "$DEVBOX" ]; then fail "$DEVBOX is not accessible"; fi

checklist=(PUBLIC_NETWORK_IP PUBLIC_NETWORK_NETMASK PUBLIC_NETWORK_GATEWAY BRIDGE_NETWORK)

for var in "${checklist[@]}"; do
  if [ -z "$(printenv "$var")" ]; then
    echo Cannot start devbox. "$var" is not defined.
    echo This list of env vars must be defined: "${checklist[@]}"
    exit 1
  fi
done

cd "$DEVBOX"
vagrant destroy -f
rm -rf "$DEVBOX/log/"

./update_dependencies.sh

# Exporting deployment id generated randomly used to create deployment document in cloudstore:seed
if [ "$(uname)" == "Darwin" ]; then
  export RANDOM_GENERATED_DEPLOYMENT_ID=fixed-test-deployemnt-id
else
  export RANDOM_GENERATED_DEPLOYMENT_ID=$(shuf -i 1000000000-10000000000 -n 1)
fi

if [ -n "$DEPLOYER_TEST" ]; then
  ./prepare-devbox-deployment.sh
else
  (cd ../java && ./gradlew :devbox:buildAll :devbox:startAll)
  # seed the database
  #(cd "$TESTS" && bundle exec rake cloudstore:seed)
fi

# Setup auth-token tool
if [ -n "$ENABLE_AUTH" ]; then
    echo "Copy auth-tool from within vagrant box"
    vagrant ssh -c "mkdir -p /devbox_data/ruby/cli/assets/ && cd /devbox_data/ruby/cli/assets/ && sudo rm -f ./auth*.jar && sudo cp /devbox_data/java/auth-tool/build/libs/auth*.jar ." -- -T
fi

if [ -n "$REAL_AGENT" ] && [ -z "$DEPLOYER_TEST" ]; then
  #(cd "$TESTS" && bundle exec rake api:seed:host)
  echo
fi
