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

2. You need following docker image files present in current directory
  * photon-controller-docker-*.tar
  * esxcloud-management-ui.tar (optional if Photon Controller UI is not desired)
  * vmware-lightwave-sts.tar (optional because if not available latest lightwave container from hub.docker.com will be pulled.)

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
photon target set https://$LOAD_BALANCER_IP:9000 -c
photon target login --username photon@photon.local --password Photon123$
photon deployment show default
```

If Photon Controller UI container tar file was present then UI container will be started and UI can be viewed at following URL.

https://$LOAD_BALANCER_IP:4343

# Upgrading

To upgrade Photon Controller from OLD version to NEW version we support Blue-Green Upgrades.

First user needs to start a new Photon Controller deployment. Following is the script you can
use to start new deployment. Currently it will start new deployment with same container image as was
used for old deployment.

```
./make-new-pc-cluster.sh
```

Next step is to migrate all the data from OLD deployment to this NEW deployment.

```
./migrate.sh
```

Above script does following things
 1. Pause NEW deployment
 2. Migrate data from OLD to NEW deployment
 3. Pause OLD deployment
 4. Resume NEW deployment

After above script finish the NEW deployment is ready to be used. But there is one
additional thing you need to do to make it seamless for users.
All of our containers are running on same overlay network and are exposed through
haproxy load balancer. Currently the two (NEW and OLD) Photon Controller deployments
are exposed through different ports. You would need to swap the exposed ports of two deployments
in haproxy config and restart the load balcancer. Following is the script that swaps the ports
for OLD and NEW deployments and restarts the haproxy.
After running following command you can keep using same API port before.

```
./swap-old-new-deployments.sh
```
