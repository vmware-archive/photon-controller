#!/bin/bash -x +e

# unset PYTHONPATH to prevent polluting the pip installer
unset PYTHONPATH

ESX_VERSION=${ESX_VERSION:-5.5.0}
TOPLEVEL=$(git rev-parse --show-toplevel)
REVISION=$(git rev-parse HEAD)
DIRTY=$([[ $(git diff-files $TOPLEVEL/python $TOPLEVEL/thrift) != "" ]] && echo "-dirty")


# Make sure we're in the right location
cd "$(dirname "$0")"
VIB_DIR=$PWD

# Create tmp work directory inside current directory so that vibauthor container can access it
TMPDIR=`pwd`
TMP_VIB_DIR=`mktemp -d -t temp_create_vib.XXXXX`
trap "rm -rf $TMP_VIB_DIR" EXIT

# Copy vib layout to work directory
SRC_VIB_LAYOUT=../vib/agent
DEST_VIB_LAYOUT=$TMP_VIB_DIR/vib
DEST_VIB_ROOT=$DEST_VIB_LAYOUT/payloads/agent/opt/vmware/photon/controller
LOG_DIR=$DEST_VIB_LAYOUT/payloads/agent/var/log

# git loses the sticky bit on rebase ops let's add it back
chmod a+w $SRC_VIB_LAYOUT/payloads/agent/etc/opt/vmware/photon/controller/{config,state}.json
chmod +t $SRC_VIB_LAYOUT/payloads/agent/etc/opt/vmware/photon/controller/{config,state}.json

# tar -p is needed here to preserve the attributes and permissions of the
# layout files
mkdir -p $DEST_VIB_LAYOUT
(cd $SRC_VIB_LAYOUT; tar pcf - . | (cd $DEST_VIB_LAYOUT && tar pxf -))

# Add optional agent params used during startup
echo "PHOTON_CONTROLLER_AGENT_PARAMS=\"${PHOTON_CONTROLLER_AGENT_PARAMS}\"" > $DEST_VIB_ROOT/sh/photon_controller_params.sh

# Create symlinks to log files in /var/log/.
mkdir -p $LOG_DIR
ln -sf /scratch/log/photon-controller-agent.log $LOG_DIR/photon-controller-agent.log
ln -sf /scratch/log/photon-controller-agent-hypervisor.log $LOG_DIR/photon-controller-agent-hypervisor.log

build_for_py_ver() {
   esx_version=$1
   if [ $esx_version == "5.5.0" ]; then
      PYTHON_VERSION=2.6
      PYTHON=python2.6
   elif [ $esx_version == "6.0.0" ]; then
      PYTHON_VERSION=2.7
      PYTHON=python2.7
   fi

   # Install virtualenv in the working directory
   virtualenv --python=python$PYTHON_VERSION $TMP_VIB_DIR/virtualenv

   . $TMP_VIB_DIR/virtualenv/bin/activate

   # Install pip 1.3.1
   pip install pip==1.3.1

   DIST_DIR=$(readlink -nf ../dist)

   # Install the package in work directory given dist
   PIP_MAJOR_VER=$(pip --version | awk '{print $2}' | cut -d. -f1)
   PIP_NO_CACHE_OPT=
   if [[ $PIP_MAJOR_VER -ge 6 ]]; then
      PIP_NO_CACHE_OPT="--no-cache-dir"
   fi

   pip install $PIP_NO_CACHE_OPT -q -f file://$DIST_DIR photon.controller.agent

   # Copy the site packages to the vib layout
   SRC_SITE_PACKAGES=`../misc/get_site_packages_path`
   DEST_SITE_PACKAGES=$DEST_VIB_ROOT/$PYTHON_VERSION/site-packages
   (cd $SRC_SITE_PACKAGES; tar cf - . | (cd $DEST_SITE_PACKAGES && tar xf -))

   # Fill revision
   sed s/#REVISION#/$REVISION$DIRTY/g -i $DEST_SITE_PACKAGES/agent/version.py

   # Generate pyc files
   find $DEST_SITE_PACKAGES -name '*.py' -exec $PYTHON -m py_compile {} +

   # Delete py files and tests dir
   if [ -z "$DEBUG" ]; then
       find $DEST_SITE_PACKAGES \( -name '*.py' -or  -type d -and -name tests -or  -type d -and -name esx_tests \) -exec rm -rf {} +
       find $DEST_SITE_PACKAGES \( -type d -and -name fake \) -exec rm -rf {} +
   fi

   # Delete stress_test
   find $DEST_SITE_PACKAGES -maxdepth 1 \( -type d -and -name chairman \) -exec rm -rf {} +

   # Delete files we don't want in the vib
   find $DEST_VIB_LAYOUT -name '.gitkeep' -exec rm -rf {} \;

   # Fill in templates (primarily for integration test)
   FIREWALL_CONF=payloads/agent/etc/vmware/firewall/photon-controller-agent.xml
   CHAIRMAN_PORT=${CHAIRMAN_PORT:-13000}
   ../misc/fill_template $SRC_VIB_LAYOUT/$FIREWALL_CONF \
     CHAIRMAN_PORT $CHAIRMAN_PORT \
     > $DEST_VIB_LAYOUT/$FIREWALL_CONF

   AGENT_VERSION=$(cat ../src/agent/VERSION)
   if [ -n "$PROMOTE_NUMBER" ]; then
     AGENT_VERSION="$AGENT_VERSION.$PROMOTE_NUMBER"
   else
     AGENT_VERSION="$AGENT_VERSION.dev"
   fi
   ../misc/fill_template $SRC_VIB_LAYOUT/descriptor.xml \
     ESX_VERSION $esx_version \
     AGENT_VERSION $AGENT_VERSION \
     > $DEST_VIB_LAYOUT/descriptor.xml

   # Need to deactivate virtualenv before running vibauthor(python app)
   deactivate
}

# XXX Do we need a new vib naming scheme for the unified vib?
#
# For now we take the smaller of the supported esx version as the value
# used for $ESX_VERSION, which in this new format now represents the minimum #
# version of ESX this vib supports.
for esxver in 6.0.0 5.5.0; do
   ESX_VERSION=$esxver
   build_for_py_ver $esxver
done
vibauthor -C -t $DEST_VIB_LAYOUT -v $DIST_DIR/photon-controller-agent-$AGENT_VERSION-$ESX_VERSION.vib -f
