# Multi-Container Photon-Controller and Lightwave cluster
These scripts use docker to create a network on which 3+3 Photon-Controller and Lighwtave containers
are created and form a 3 node clusters of Lightwave and Photon-Controller. Photon-Controller joins
the Lightwave nodes and creates a auth-enabled deployment.

## Requirements
1. You need following tools installed
  * [docker](https://docs.docker.com/engine/installation/)
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

## Running the scripts
Following steps are for Linux with Docker. For Macbook you need to first prepare using `docker-machine` as mentioned at the end of this section.

1. Load docker images by running `./load-images.sh`
2. Create LW cluster by running `./make-lw-cluster.sh`
3. Create PC cluster by running `./make-pc-cluster.sh`
4. Create PC deployment by running `./make-deployment.sh`
5. Create demo users and groups by running `./make-users.sh`
6. Run basic sanity test `./basic-test.sh`
7. Destroy the environment using `./delete-pc-cluster.ssh` and then `./delete-lw-cluster.sh`

For running above scripts in *MacBook* you would need to have `docker-machine` and `virtualbox` installed.
Use following steps to prepare your MacBook to run the scripts listed above.
* `docker-machine create -d virtualbox --virtualbox-memory 4096 default`
* `eval $(docker-machine eval default)`
* `docker-machine scp ./prepare-docker-machine.sh default:/tmp`
* `docker-machien ssh default:/tmp/prepare-docker-machine.sh`
