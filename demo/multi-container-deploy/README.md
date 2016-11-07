# Multi-Container Photon-Controller and Lightwave cluster
These scripts use docker to create a network on which 3+3 Photon-Controller and Lighwtave containers
are created and form a 3 node clusters of Lightwave and Photon-Controller. Photon-Controller joins
the Lightwave nodes and creates a auth-enabled deployment.


## Requirements
1. You need following tools installed
  * [docker](https://docs.docker.com/engine/installation/) (Version >= 1.12.1)
  * For running these scripts on *MacBook* you need
    * [docker-machine](https://docs.docker.com/machine/install-machine/)
    * VMware Fusion (or VirtualBox)

2. Following docker image files present in current directory
  * photon-controller-docker-*.tar
  * vmware-lightwave-sts.tar

## IP address distribution in docker overlay network.

* Subnet: 192.168.114/27
* LW Container Node IPs: 192.168.114.2 - 192.168.114.4
* PC Container Node IPs: 192.168.114.11 - 192.168.114.13
* Docker network name: lightwave

## Running the scripts on Mac OS
Following script will start the VM and create one Lightwave container, one Photon container, and one HAProxy container.

```bash
./up.sh
```

or to demo multi-host, multi-container scenario run the script with 'multi' parameter.

```bash
./up.sh multi
```

If above script was successfull, then use following command to get the IP address of load balancer.

```bash
LOAD_BALANCER_IP=$(docker inspect --format '{{ .Node.IP }}' haproxy)
```

Now connect `photon` CLI to the load balancer and verify the deployment.

```bash
photon target set https://$LOAD_BALANCER_IP:28080 -c
photon target login --username photon@photon.local --password Photon123$
photon deployment show default
```
