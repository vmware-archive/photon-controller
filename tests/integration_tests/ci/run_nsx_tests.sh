#!/bin/bash -ex

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

# Define any custom config process
if [ -n "$CUSTOM_TEST_CONFIG" ]; then
    echo Using custom settings in "$CUSTOM_TEST_CONFIG"
    # Note: Do NOT put quotes around this. $CUSTOM_TEST_CONFIG has been improperly
    # used on our build machines, and isn't just a path to a file, but includes
    # arguments that must be passed to that file. Using quotes will break that.
    source $CUSTOM_TEST_CONFIG
else
    echo No CUSTOM_TEST_CONFIG to override default test behavior
fi

env

cd "$TESTS"

bundle exec rake esxcloud:virtual_network
# bundle exec rake esxcloud:vm_on_virtual_network
