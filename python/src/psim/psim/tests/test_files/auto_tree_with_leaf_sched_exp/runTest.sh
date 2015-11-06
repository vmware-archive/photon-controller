#!/bin/bash

# Copyright (c) 2015 VMware, Inc. All Rights Reserved.

# A simple wrapper to run arbitrary sized simulations.
# This script calls the tests/test_config.py to setup
# the test. The test_config.py takes the test type 
# from the command line. The type argument is of the 
# form: NUMHOSTS_ROOTFANOUT_FANOUTRATIO. Once test_config.py 
# sets up the right values for the test, runTest.sh symlinks 
# to those config files and runs the test.
#
# Since these tests can be long running, these are not enabled
# to be run during CI. These scripts are present to make the job 
# of running future simulations easy. For any CI related simulator
# tests, look at test_simulator.py.

if [ ! -e ../../../../bin/psim ]; then
   echo "Running script from the wrong directory....exiting."
   exit -1
fi

# Directions: Add the test type string to the 
# dir_names array below. The format is: NUMHOSTS_ROOTFANOUT

dir_names=(
   512_32_0.50
   )

test_config="test_config_multi_vm.py"

O_PWD=$(pwd)
test_base_dir=${O_PWD}/tests

for d in ${dir_names[@]}
do
   echo "Running Test $d"
   cd ${test_base_dir}
   python $test_config $d
   cd ${O_PWD}
   rm -f requests.yml tree.yml
   ln -s "${test_base_dir}/${d}/requests.yml"
   if [ $? -ne 0 ];then
      echo "Error while symlinking $d/requests.yml...exiting."
      exit -1
   fi
   ln -s "${test_base_dir}/${d}/tree.yml"
   if [ $? -ne 0 ];then
      echo "Error while symlinking $d/tree.yml...exiting."
      exit -1
   fi
   cd ${O_PWD}/..
   ../../../bin/psim -r auto_tree_with_leaf_sched_exp/run.cmd > "${test_base_dir}/${d}/results.txt"
   if [ $? -ne 0 ];then
      echo "Error while running test $d."
      exit -1
   fi
   cd ${O_PWD}
done
