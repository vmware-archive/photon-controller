#!/bin/bash +xe

print_help()
{
  echo "Usage: start-pc.sh [OPTIONS]

Script to run Photon Controller and Lightwave containers for demo purpose.

Options:

  -a <datastore>  Image datastore name (default: datastore1)
  -b <ip>         Syslog endpint (default: None)
  -c <ip>         NTP endpoint (default: None)
  -e <ip>         Load balancer IP address
  -u <username>   Photon Controller username (default: photon)
  -p <passwrod>   Photon Controller password (default: <random>)
  -l <password>   Lightwave Administrator password (default: <random>)
  -d <domain>     Lightwave domain name (default: photon.local)
  -i <path>       Photon Controller Docker image tar file
                  (If not specified then Docker Hub image will be used.)
  -x <path>       UI Docker image tar file
  -m              Create three containers for Photon Controller for HA
  -o              Create three containers for Lightwave for HA
  -S              Skip deleting old containers
  -D              Debug mode prints more output
  -h              Print usage

Usage Examples:
  ./start-pc.sh
  ./start-pc.sh -i ./photon-controller-docker-develop.tar
"
}

if [ $(hash docker; echo $?) -ne 0 ]; then
  echo "ERROR: docker is not installed. Please install docker and docker-machine first before running this script."
  exit 1
fi

if [ $(uname) == "Darwin" ]; then
  if [ $(hash docker-machine; echo $?) -ne 0 ]; then
    echo "ERROR: docker-machine is not installed. Please install docker-machine before running this script."
    exit 1
  fi
fi

DESIRED_DOCKER_VERSION=1.12
DOCKER_VERSION=$(docker version --format '{{.Client.Version}}' | cut -f1 -d '-' | cut -f1-2 -d '.')
if [ $(echo $DOCKER_VERSION'>='$DESIRED_DOCKER_VERSION | bc -l) -eq 0 ]; then
  echo "ERROR: Docker version should be >= $DESIRED_DOCKER_VERSION. Exiting!"
  exit 1
fi

OPTIND=1         # Reset in case getopts has been used previously in the shell.
MULTI_CONTAINER_PC=0
MULTI_CONTAINER_LW=0
MULTI_HOST=0
USERNAME="photon"
PASSWORD=$(openssl rand -base64 8 | tr "+\!/=" "\$")A\$i0
LIGHTWAVE_PASSWORD=$(openssl rand -base64 8 | tr "+\!/=" "\$")A\$i0
LIGHTWAVE_DOMAIN="photon.local"
PC_DOCKER_IMAGE_FILE=""
UI_DOCKER_IMAGE_FILE=""
VM_DRIVER=vmwarefusion
ENABLE_FQDN=0
UI_CONTAINER_VERSION=develop
PC_CONTAINER_VERSION=develop
LW_CONTAINER_VERSION=1.0.2
IMAGE_DATASTORE_NAMES="datastore1"
SYSLOG_ENDPOINT=""
NTP_ENDPOINT=""
LOAD_BALANCER_IP=""

while getopts "h?mDonsu:p:l:d:i:x:v:a:b:c:e:" opt; do
    case "$opt" in
    h|\?)
        print_help
        exit 0
        ;;
    m)  MULTI_CONTAINER_PC=1
        ;;
    o)  MULTI_CONTAINER_LW=1
        ;;
    n)  MULTI_HOST=1
        ;;
    u)  USERNAME=$OPTARG
        ;;
    p)  PASSWORD=$OPTARG
        ;;
    l)  LIGHTWAVE_PASSWORD=$OPTARG
        ;;
    d)  LIGHTWAVE_DOMAIN=$OPTARG
        ;;
    i)  PC_DOCKER_IMAGE_FILE=$OPTARG
        ;;
    x)  UI_DOCKER_IMAGE_FILE=$OPTARG
        ;;
    v)  VM_DRIVER=$OPTARG
        ;;
    s)  ENABLE_FQDN=1
        ;;
    a)  IMAGE_DATASTORE_NAMES=$OPTARG
        ;;
    b)  SYSLOG_ENDPOINT=$OPTARG
        ;;
    c)  NTP_ENDPOINT=$OPTARG
        ;;
    e)  LOAD_BALANCER_IP=$OPTARG
        ;;
    D)  DEBUG=1
    esac
done

shift $((OPTIND-1))
[ "$1" = "--" ] && shift

export DEBUG=$DEBUG
if [ "$DEBUG" == "1" ]; then
  set -xe
fi

if [ $(uname) == "Darwin" ]; then
  TOTAL_VMS=1
else
  TOTAL_VMS=0
fi

if [ "$MULTI_HOST" == "1" ]; then
  TOTAL_VMS=3
fi

echo "---------------"
echo "Step 1/7: Setup"
echo "---------------"


if [ $(uname) == "Darwin" ]; then
  if [ $(docker-machine ls -q --filter=name=vm- | wc -l) -ne $TOTAL_VMS ]; then
    ./helpers/delete-vms.sh
    ./helpers/make-vms.sh $MULTI_HOST $MULTI_CONTAINER_PC $VM_DRIVER
    ./helpers/prepare-docker-machine.sh
  fi
fi

if [ "$MULTI_HOST" == "1" ]; then
  eval $(docker-machine env --swarm vm-0)
else
  if [ "$(uname)" == "Darwin" ]; then
    eval $(docker-machine env vm-0)
  fi
fi

# Cleanup old deployment
./helpers/delete-all-containers.sh

if [ "${PC_DOCKER_IMAGE_FILE}TEST" != "TEST" ]; then
	if [ -f ${PC_DOCKER_IMAGE_FILE}  ]; then
    docker load -i ${PC_DOCKER_IMAGE_FILE}
    PC_CONTAINER_VERSION=local
    docker tag vmware/photon-controller vmware/photon-controller:$PC_CONTAINER_VERSION
  else
    echo "Error: File not found: ${PC_DOCKER_IMAGE_FILE}"
    exit 1
  fi
fi

if [ "${UI_DOCKER_IMAGE_FILE}TEST" != "TEST" ]; then
	if [ -f ${UI_DOCKER_IMAGE_FILE}  ]; then
    docker load -i ${UI_DOCKER_IMAGE_FILE}
    UI_CONTAINER_VERSION=local
    docker tag esxcloud/management_ui vmware/photon-controller-ui:$UI_CONTAINER_VERSION
  else
    echo "Error: File not found: ${UI_DOCKER_IMAGE_FILE}"
    exit 1
  fi
fi

./helpers/pre-setup.sh $MULTI_CONTAINER_LW "$LIGHTWAVE_PASSWORD" $LW_CONTAINER_VERSION $PC_CONTAINER_VERSION
if [ $? -ne 0 ]; then
  echo "Could not setup, please fix the errors and try again."
  exit 1
fi

echo "-------------------------"
echo "Step 2/7: Start Lightwave"
echo "-------------------------"

./helpers/make-lw-cluster.sh $MULTI_CONTAINER_LW "$LIGHTWAVE_PASSWORD" $LW_CONTAINER_VERSION $PC_CONTAINER_VERSION
if [ $? -ne 0 ]; then
  echo "Could not make Lightwave cluster, please fix the errors and try again."
  exit 1
fi

if [ $ENABLE_FQDN -eq 1 ]; then
  ./helpers/make-dns-entries.sh "$LIGHTWAVE_PASSWORD" $LIGHTWAVE_DOMAIN
  if [ $? -ne 0 ]; then
    echo "Could not add DNS entries in the lightwave"
    exit 1
  fi
fi

echo "---------------------"
echo "Step 3/7: Start Proxy"
echo "---------------------"

./helpers/run-haproxy-container.sh "$LIGHTWAVE_PASSWORD" $LIGHTWAVE_DOMAIN $PC_CONTAINER_VERSION
if [ $? -ne 0 ]; then
  echo "Could not run haproxy container, please fix the errors and try again."
  exit 1
fi

HOSTNAME=$(docker run --rm --net=host -t -v /etc:/host-etc vmware/photon-controller-seed:$PC_CONTAINER_VERSION /bin/bash -c "cat /host-etc/hostname")
OS_NAME=$(docker run --rm --net=host -t -v /etc:/host-etc vmware/photon-controller-seed:$PC_CONTAINER_VERSION /bin/bash -c "cat /host-etc/os-release | grep '^NAME=' | sed -e 's/NAME=//g' | tr -d '[:space:]'")
HYPERVISOR=$(docker run --rm --net=host -t -v /etc:/host-etc vmware/photon-controller-seed:$PC_CONTAINER_VERSION /bin/bash -c "dmidecode -s system-product-name | tr -d '[:space:]'")

if [ "${LOAD_BALANCER_IP}TEST" == "TEST" ]; then
  if [ "$OS_NAME" == "Boot2Docker" ]; then
    if [ "$HYPERVISOR" == "VirtualBox" ]; then
      LOAD_BALANCER_IP=$(ip route get 8.8.8.8 dev eth1 | awk 'NR==1 {print $NF}')
    else
      LOAD_BALANCER_IP=$(ip route get 8.8.8.8 dev eth0 | awk 'NR==1 {print $NF}')
    fi
  else
    if [ "$HOSTNAME" == "moby" ]; then
      # Get Load balancer IP from docker-machine if this is one VM setup.
      LOAD_BALANCER_IP=127.0.0.1
    else
      # User localhost as load balancer if this is a local setup running all containers locally without a VM.
      LOAD_BALANCER_IP=$(ip route get 8.8.8.8 | awk 'NR==1 {print $NF}')
    fi
  fi
fi

echo "---------------------------------"
echo "Step 4/7: Start Photon Controller"
echo "---------------------------------"

./helpers/make-pc-cluster.sh $MULTI_CONTAINER_PC "$LIGHTWAVE_PASSWORD" $LIGHTWAVE_DOMAIN $ENABLE_FQDN $PC_CONTAINER_VERSION $LOAD_BALANCER_IP $IMAGE_DATASTORE_NAMES $SYSLOG_ENDPOINT $NTP_ENDPOINT
if [ $? -ne 0 ]; then
  echo "Could not make Photon Controller , please fix the errors and try again."
  exit 1
fi

echo "---------------------------------"
echo "Step 5/7: Create Users and Groups"
echo "---------------------------------"

./helpers/make-users.sh $USERNAME $PASSWORD $LIGHTWAVE_PASSWORD
if [ $? -ne 0 ]; then
  echo "Could not make Lightwave users , please fix the errors and try again."
  exit 1
fi

echo "------------------------------------"
echo "Step 6/7: Start Photon Controller UI"
echo "------------------------------------"

UI_AVAILABLE=0
./helpers/make-ui-cluster.sh $UI_CONTAINER_VERSION $LOAD_BALANCER_IP
UI_AVAILABLE=1
if [ $? -ne 0 ]; then
  echo "Error: Could not make UI cluster. Fix the problems and try again."
  UI_AVAILABLE=0
fi

echo "----------------------------------------------------"
echo "Step 7/7: Set initial defaults for Photon Controller"
echo "----------------------------------------------------"

./helpers/post-install-setup.sh $LOAD_BALANCER_IP $USERNAME $PASSWORD $LIGHTWAVE_DOMAIN

echo "
Deployment completed!
---------------------

NOTE: Only for preview purpose -- not to be used in production.

IP address of the gateway: $LOAD_BALANCER_IP

Connect 'photon' CLI to above IP and use following credentials.
Do not forget to change the password after you login.

Username: ${USERNAME}@${LIGHTWAVE_DOMAIN}
Password: ${PASSWORD}

Start with following CLI commands to interact with this new deployment.

photon target set https://$LOAD_BALANCER_IP:9000 -c
photon target login --username ${USERNAME}@${LIGHTWAVE_DOMAIN} --password '${PASSWORD}'
photon deployment show

"

if [ $UI_AVAILABLE -eq 1 ]; then
  echo "UI URL: https://$LOAD_BALANCER_IP:4343"
fi
