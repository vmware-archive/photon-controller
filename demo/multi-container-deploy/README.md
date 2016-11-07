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

## Running the scripts
Following steps are for Linux with Docker. For Macbook you need to first prepare using `docker-machine` as mentioned at the end of this section.

1. Create three docker host VMs using `./make-vms.sh` or use `docker-machine create -d vmwarefusion default` to create one VM that will host all the containers being created.
2. Load docker images of Photon-Contorller and Lightwave by running `./load-images.sh`
3. Create LW cluster by running `./make-lw-cluster.sh`
4. Start Load Balancer by runnig `./run-haproxy-container.sh`
5. Create PC cluster by running `./make-pc-cluster.sh`
6. Create demo users and groups by running `./make-users.sh`
7. Deploy UI container by running `./make-ui-container.sh`
8. Go to https://load-balancer-ip:4343 to login to Photon-Controlelr UI.

For running above scripts in *MacBook* you would need to have `docker-machine` and `virtualbox` installed.
Use following steps to prepare your MacBook to run the scripts listed above.
* `docker-machine create -d virtualbox --virtualbox-memory 4096 default`
* `eval $(docker-machine eval default)`
* `docker-machine scp ./prepare-docker-machine.sh default:/tmp`
* `docker-machien ssh default:/tmp/prepare-docker-machine.sh`

# Multi-Host Multi-Container Photon-Controller and Lightwave cluster.
To create all containers in different VMs, we can leverage docker-machine and its swarm feature.
Make sure your have latest `docker-machine` installed. Then run following script to create three
swarm nodes (VMs).
```
./make-vms.sh
```
After VMs are created, you can run following command to set your docker client to point to swarm cluster
just created.
```
eval $(docker-machine env --swarm mhs-demo0)
```
Now you are ready to run all the scripts mentioned in section "Runngin the scripts" above.
