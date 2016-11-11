# Multi-Container Photon-Controller and Lightwave cluster
These scripts use docker to create a network on which Photon Controller containers and Lighwtave containers
are created. Photon Controller joins the Lightwave nodes and creates a auth-enabled deployment.
A laod balancer container is also created that let users access this deployment.

## Requirements
1. You need following tools installed
  * [docker](https://docs.docker.com/engine/installation/) (Version >= 1.12.1)
  * For running these scripts on *Mac* you need
    * [docker-machine](https://docs.docker.com/machine/install-machine/)
    * VMware Fusion (or VirtualBox)
  * [Photon CLI](https://github.com/vmware/photon-controller-cli)
  * At-least one ESXi cloud host to manage.

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

If above script was successful, then it will display IP address of load balancer.
You can also use following command later if ever want to get the IP address of the load balancer.

```bash
LOAD_BALANCER_IP=$(docker inspect --format '{{ .Node.IP }}' haproxy)
```

Now connect `photon` CLI to the load balancer and have fun with it.

```bash
photon target set https://$LOAD_BALANCER_IP:9000 -c
photon target login --username photon@photon.local --password <password>
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

# Turn off all CLOUD only hosts managed by Photon Controller

To suspend Photon Controller to allow maintenance on the managed CLOUD only hosts requiring those
hosts to be powered off can be done in the following script:

This assumes that Photon Controller management plane and hosts with MGMT tag will continue to be on and
the CLOUD only hosts will only have transparent changes when they are powered back up.

```
./photon-system.sh pause username password
```

The script does the following things:
 1. Stop all VMs running on all CLOUD only hosts.
 2. Pause Photon Controller, setting the plane into a read only state.
 3. Wait for started tasks issued to Photon Controller before the pause to finish.

On successful completion, all the CLOUD only hosts are ready for maintenance operations.

When the system should be resumed, with all the CLOUD only hosts, run the following script:
```
./photon-system.sh resume username password
```

The script resumes Photon Controller to allow changes to its state.
Note that for some commands to succeed Photon Controller it is recommended to wait one to two minutes after it
has resumed. This is for the periodic host health check to contact the rebooted host.

# Turn off Photon Controller without losing state

To turn off Photon Controller to allow maintenance on all hosts without losing state of the management plane
add specific parameters inside the following script and run:
```
./photon-full-system.sh shutdown
```

The script must be run in a location that has access to:
 - Photon Controller CLI
 - Photon Controller containers
 - Lightwave containers

The script does the following things:
 1. Stops all the VMs on all Photon Controller hosts (MGMT or CLOUD)
 2. Pause Photon Controller, setting the plane into a read only state.
 3. Wait for started tasks issued to Photon Controller before the pause to finish.
 4. Updates Xenon to a full quorum membership to prevent synchronization issues on shutdown.
 5. Update each Photon Controller configuration that on startup will start Xenon with a full quorum membership.
 6. Stop all Photon Controller containers.
 7. Stop all Lightwave containers.

When the system should be restored, using same parameters in the script above run the following script:
```
./photon-full-system.sh turnon
```

The script does the following things:
 1. Start all Lightwave containers.
 2. Start all Photon Controller containers.
 3. Wait for Xenon to synchronize with a full quorum membership.
 4. Revert the Xenon quorum membership to be the majority of nodes (n/2+1).
 5. Resume Photon Controller.
