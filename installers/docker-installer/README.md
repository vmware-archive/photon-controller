# Photon Controller Deployment 3.0
These scripts use docker to launch Photon Controller and Lighwtave containers.
The purpose of these scripts is to have easiest way to deploy Photon Controller with all
battries included. No need to have OVAs, heavy installers and separate host. Just run one
docker command that will launch Photon Controller with necessary things included.
After launching in your Laptop, you can start adding cloud hosts and managing them.

## Requirements
Following are the requirements to launch Photon Controller.
  * [Docker](https://docs.docker.com/engine/installation/) (Version >= 1.12.1)
    * Tested with Docker on Linux and Docker for Mac.
  * [Photon CLI](https://github.com/vmware/photon-controller-cli)
  * ESXi host is not requied for installation. After installation you will need some ESXi to manage.

## Starting Photon Controller

### Launch command

Following docker command starts a seed container that will create one container each for Lightwave, Photon Controller and HAProxy.

```bash
docker run --rm --net=host -it \
       -v /var/run/docker.sock:/var/run/docker.sock \
       vmware/photon-controller-seed:latest start-pc.sh
```

If above command was successful, then it will display IP address of load balancer and commands to try out.

You can see the detailed usage by passing `-h` to the end of above docker run command.

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
