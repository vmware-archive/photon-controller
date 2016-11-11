# Photon Controller Deployment 2.0
These scripts use docker to launch Photon Controller and Lighwtave containers.
The purpose of these scripts is to have easiest way to deploy Photon Controller with all
battries included. No need to have OVAs, heavy installers and separate host. Just run one
docker command that will launch everything Photon Controller with necessary things included.
After launching in your Laptop, you can start adding cloud hosts and managing them.

## Requirements
1. You need following tools pre-installed
  * [docker](https://docs.docker.com/engine/installation/) (Version >= 1.12.1)
  * [Photon CLI](https://github.com/vmware/photon-controller-cli)
  * One ESXi cloud host to manage. Host is not required for deployment of Photon Controller in this method.
  * For running these scripts on *macOS* you need
    * [docker-machine](https://docs.docker.com/machine/install-machine/)
    * VMware Fusion (or VirtualBox)

## Starting Photon Controller

### On MacOS you need to prepare the environment to launch the Photon Controller containers.
These two lines of bash create VM and set docker client to talk with docker daemon in that VM.

```bash
# Not required on Linux
docker-machine create -d virtualbox --virtualbox-memory 4096 vm-0
eval $(docker-machine env vm-0)
```

### Launch command

Following docker command start a seed container that will create one container each for Lightwave, Photon Controller and HAProxy.
Yes! battaries are included.

```bash
docker run --rm --net=host -it \
       -v /var/run/docker.sock:/var/run/docker.sock \
       vmware/photon-controller-seed:1.0.3 start-pc.sh
```

If above script was successful, then it will display IP address of load balancer and commands to try out.

You can see the different paramters to start-pc.sh script by passing `-h` parameter to it.

## Building

The scripts are packaged into a docker image and that image is available in docker hub.
You only need to build this docker image if want to do modifications in the scripts.

```bash
docker build --no-cache -t vmware/photon-controller-seed .
```

# Upgrading Scripts

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
