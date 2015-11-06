#!/bin/sh
export LOG_FILE=$1

if [ `uname -r | grep '^5\.'` ]; then
    PYVER="2.6"
else
    PYVER="2.7"
fi

# src root in a deployed vib.
PHOTON_CONTROLLER_ROOT="/opt/vmware/photon/controller/${PYVER}/site-packages"

# Set python path to the src root
export PYTHONPATH=$PYTHONPATH:$PHOTON_CONTROLLER_ROOT

/bin/python $PHOTON_CONTROLLER_ROOT/common/esx_tests/test_ref_count_multithreaded.py 2>&1 | tee $LOG_FILE
/bin/python $PHOTON_CONTROLLER_ROOT/common/esx_tests/test_thin_copy.py 2>&1 | tee $LOG_FILE
/bin/python $PHOTON_CONTROLLER_ROOT/common/esx_tests/test_vsi_value.py 2>&1 | tee $LOG_FILE
