#!/bin/bash -xe

# unset PYTHONPATH to prevent polluting the pip installer
unset PYTHONPATH

TOPLEVEL=${GIT_ROOT:-$(git rev-parse --show-toplevel)}
REVISION=${GIT_REVISION:-$(git rev-parse HEAD)}

DIRTY=""
set +x
if [ "$(git diff-files $TOPLEVEL/python $TOPLEVEL/thrift)" != "" ]; then
	DIRTY="-dirty"
fi
set -x

# Create temp work directory in current directory to support OS X docker container for vibauthor,
# because directory vibautor container can mount to current directory and need access to
# vib temp directory to create the vib.
TMP_VIB_DIR=$(mktemp -d "$PWD/create_vib.XXXXX")
trap "rm -rf $TMP_VIB_DIR" EXIT

# Make sure we're in the right location
cd "$(dirname "$0")"
VIB_DIR=$PWD

# Copy vib layout to work directory
SRC_VIB_LAYOUT=../vib/agent
DEST_VIB_LAYOUT=$TMP_VIB_DIR/vib
DEST_VIB_ROOT=$DEST_VIB_LAYOUT/payloads/agent/opt/vmware/photon/controller
LOG_DIR=$DEST_VIB_LAYOUT/payloads/agent/var/log

if [ "$(uname)" == "Darwin" ]; then
        # On OSX default BSD version of sed and readlink do not behave same as GNU versions.
        # brew install coreutils gnu-sed
        SED=gsed
        READLINK=greadlink
else
        SED=sed
        READLINK=readlink
fi

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

   DIST_DIR=$($READLINK -nf ../dist)

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

   # Generate pyc files
   find $DEST_SITE_PACKAGES -name '*.py' -exec $PYTHON -m py_compile {} +

   # Delete py files and tests dir
   if [ -z "$DEBUG" ]; then
       find $DEST_SITE_PACKAGES \( -name '*.py' -or  -type d -and -name tests -or  -type d -and -name esx_tests \) -exec rm -rf {} +
       find $DEST_SITE_PACKAGES \( -type d -and -name fake \) -exec rm -rf {} +
   fi

   # Delete files we don't want in the vib
   find $DEST_VIB_LAYOUT -name '.gitkeep' -exec rm -rf {} \;

   # Copy firewall configuration to destination directory (firewall is needed mainly for integration tests)
   FIREWALL_CONF=payloads/agent/etc/vmware/firewall/photon-controller-agent.xml
   cp $SRC_VIB_LAYOUT/$FIREWALL_CONF $DEST_VIB_LAYOUT/$FIREWALL_CONF

   # Fill in template for stats publisher's port rule
   # TODO: Update this port based on user's provided state store port number at deployment time.
   FIREWALL_STATS_CONF=payloads/agent/etc/vmware/firewall/photon-controller-agent-stats.xml
   STATS_STORE_PORT=${STATS_STORE_PORT:-2004}
   ../misc/fill_template $SRC_VIB_LAYOUT/$FIREWALL_STATS_CONF \
     STATS_STORE_PORT  $STATS_STORE_PORT \
     > $DEST_VIB_LAYOUT/$FIREWALL_STATS_CONF

   # agent version needs to be of the format [a-Z0-9]+([.][a-Z0-9]+){0-2}-[a-Z0-9]+([.][a-Z0-9]+){0-2}
	 # to make local development easier with where branch names often contain [_-] we'll replace them with [.]
   AGENT_VERSION="`echo $GERRIT_BRANCH | tr '_-' .`-$COMMIT_HASH"

   ../misc/fill_template $SRC_VIB_LAYOUT/descriptor.xml \
		 AGENT_VERSION  $AGENT_VERSION\
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
./vibauthor.sh -C -t $DEST_VIB_LAYOUT -v $DIST_DIR/photon-controller-agent-$AGENT_VERSION.vib -f
