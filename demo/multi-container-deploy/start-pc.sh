#!/bin/bash +xe

print_help()
{
  echo "Usage: start-pc.sh [OPTIONS]

Script to run Photon Controller and Lightwave containers for demo purpose.

Options:

  -m                              Create three containers for Photon Controller for HA
  -o                              Create three containers for Lightwave for HA
  -n                              Creates three VMs using docker-machine to run
                                  all containers in multi-host environment connected with
                                  the help of overlay network.
  -u                              Photon Controller username
  -p                              Photon Controller password
  -l                              Lightwave Administrator password
  -d                              Lightwave domain name
  -i                              Photon Controller Docker image tar file
                                  (If not specified then Docker Hub image will be used.)
  -x                              UI Docker image tar file
  -S                              Skip deleting old containers
  -h                              Print usage

Usage Examples:
  ./start-pc.sh
  ./start-pc.sh -m
  ./start-pc.sh -i ./photon-controller-docker-develop.tar
  ./start-pc.sh -m -n -u scott -p tiger -l Admin123! -d photon.com
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

if [ $(hash photon; echo $?) -ne 0 ]; then
  echo "ERROR: photon CLI is not installed. Please install Photon Controller (photon) CLI first before running this script."
  exit 1
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

while getopts "h?monsu:p:l:d:i:x:v:" opt; do
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
    esac
done

shift $((OPTIND-1))
[ "$1" = "--" ] && shift

if [ $(uname) == "Darwin" ]; then
  TOTAL_VMS=1
else
  TOTAL_VMS=0
fi

if [ "$MULTI_HOST" == "1" ]; then
  TOTAL_VMS=3
fi

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
  else
    echo "Error: File not found: ${PC_DOCKER_IMAGE_FILE}"
    exit 1
  fi
fi

if [ "${UI_DOCKER_IMAGE_FILE}TEST" != "TEST" ]; then
	if [ -f ${UI_DOCKER_IMAGE_FILE}  ]; then
    docker load -i ${UI_DOCKER_IMAGE_FILE}
  else
    echo "Error: File not found: ${UI_DOCKER_IMAGE_FILE}"
    exit 1
  fi
fi

./helpers/make-lw-cluster.sh $MULTI_CONTAINER_LW "$LIGHTWAVE_PASSWORD"
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

./helpers/run-haproxy-container.sh "$LIGHTWAVE_PASSWORD" $LIGHTWAVE_DOMAIN
if [ $? -ne 0 ]; then
  echo "Could not run haproxy container, please fix the errors and try again."
  exit 1
fi

./helpers/make-pc-cluster.sh $MULTI_CONTAINER_PC "$LIGHTWAVE_PASSWORD" $LIGHTWAVE_DOMAIN $ENABLE_FQDN
if [ $? -ne 0 ]; then
  echo "Could not make Photon Controller , please fix the errors and try again."
  exit 1
fi

./helpers/make-users.sh $USERNAME $PASSWORD $LIGHTWAVE_PASSWORD
if [ $? -ne 0 ]; then
  echo "Could not make Lightwave users , please fix the errors and try again."
  exit 1
fi

UI_AVAILABLE=0

if [ $(docker images -qa  esxcloud/management_ui | wc -l) -eq 1 ]; then
  ./helpers/make-ui-cluster.sh
  UI_AVAILABLE=1
  if [ $? -ne 0 ]; then
    echo "Error: Could not make UI cluster. Fix the problems and try again."
    UI_AVAILABLE=0
  fi
fi

if [ $(uname) == "Darwin" ]; then
  # Get Load balancer IP from docker-machine if this is one VM setup.
  LOAD_BALANCER_IP=$(docker-machine ip vm-0) || true
else
  # User localhost as load balancer if this is a local setup running all containers locally without a VM.
  LOAD_BALANCER_IP=$(ip route get 8.8.8.8 | awk 'NR==1 {print $NF}')
fi

# Get Load balancer IP from swarm multi-host setup if available.
docker inspect --format '{{ .Node.IP }}' haproxy > /dev/null 2>&1
if [ $? == 0 ]; then
  LOAD_BALANCER_IP=$(docker inspect --format '{{ .Node.IP }}' haproxy)
fi

echo "Deployment completed!

NOTE: Only for preview purpose -- not to be used in production.

IP address of the gateway: $LOAD_BALANCER_IP

Connect 'photon' CLI to above IP and use following credentials.
Do not forget to change the password after you login.

Username: ${USERNAME}@${LIGHTWAVE_DOMAIN}
Password: ${PASSWORD}

Following are the first CLI commands you would need for interacting with
current Photon Controller deployment.

photon target set https://$LOAD_BALANCER_IP:9000 -c
photon target login --username ${USERNAME}@${LIGHTWAVE_DOMAIN} --password '${PASSWORD}'
photon deployment show"

if [ $UI_AVAILABLE -eq 1 ]; then
  echo "URL for UI is: https://$LOAD_BALANCER_IP:4343"
fi
