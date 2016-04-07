#!/bin/bash -e
set -x

if [ -z "$DEVBOX" ]; then
  export DEVBOX=$WORKSPACE/devbox-photon
  echo Assume DEVBOX at $DEVBOX
fi
if [ ! -d "$DEVBOX" ]; then fail "$DEVBOX is not accessible"; fi

checklist="PUBLIC_NETWORK_IP\
  PUBLIC_NETWORK_NETMASK\
  PUBLIC_NETWORK_GATEWAY\
  BRIDGE_NETWORK"


for var in $checklist
do
  if [ -z "$(printenv $var)" ]
    then
      echo Cannot start devbox. $var is not defined.
      echo This list of properties must be defined. $checklist
      exit 1
  fi
done

# Now installing devbox
cd $DEVBOX
(
  # Using custom HOME so 'puppet install' processes don't step on each other
  export HOME=/tmp/devbox$WORKSPACE_INDEX
  mkdir -p $HOME
  ./update_dependencies.sh
)

vagrant destroy -f
rm -rf $DEVBOX/log/*

# Exporting deployment id generated randomly used to create deployment document in cloudstore:seed
if [ "$(uname)" == "Darwin" ]; then
  export RANDOM_GENERATED_DEPLOYMENT_ID=fixed-test-deployemnt-id
else
  export RANDOM_GENERATED_DEPLOYMENT_ID=$(shuf -i 1000000000-10000000000 -n 1)
fi

if [ "$DEPLOYER_TEST" ]
then
  ./prepare-devbox-deployment.sh
else
  vagrant up
  # seed the database
  (
    cd $TESTS
    bundle exec rake cloudstore:seed
  )
fi

# Setup auth-token tool
if [ -n "$ENABLE_AUTH" ]
then
    echo "Copy auth-tool from within vagrant box"
    vagrant ssh -c "mkdir -p /devbox_data/ruby/cli/assets/ && cd /devbox_data/ruby/cli/assets/ && sudo rm -f ./auth*.jar && sudo cp /esxcloud/java/auth-tool/build/libs/auth*.jar ." -- -T
fi

# Register real agent to devbox
if [ -n "$REAL_AGENT" ]; then
  echo vagrant ssh -c "sudo WORKSPACE=/esxcloud DEVBOX_PHOTON=1 /esxcloud/python/misc/register_agent $ESX_IP $PUBLIC_NETWORK_IP:13000 $ESX_DATASTORE" -- -T

  if [ -z "$DEPLOYER_TEST" ]; then
    (
      cd $TESTS
      bundle exec rake seed:host

      # Wait for the host monitoring service to detect the newly added host
      bundle exec rake monitor:host
    )
  fi
fi
