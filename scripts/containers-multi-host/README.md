# Test multi-host Photon-Controller and Lightwave cluster
These scripts use `docker-machine` to build a cluster of three VMs. Then it creates a docker swarm cluster
in these VMs, and create a docker overlay network. Then we create three LW containers in these
VMs and put them on the overlay network we created. After LW cluster is up and ready, we create three
PC containers joined together and pointing to LW master node.

## Requirements
You need `docker` and `docker-machine` installed on your machine to run these scripts.
Also Lightwave container tar file and Photon-Controller docker tar file should be present in current directory.

### IP address distribution in docker overlay network.

* Subnet: 192.168.114/27
* LW Nodes: 192.168.114.2 - 192.168.114.4
* PC Nodes: 192.168.114.11 - 192.168.114.13

1. Create VMs
```
. ./make-vms
```

2. Create LW cluster
```
./make-lw-cluster
```

3. Create PC Cluster
```
./make-pc-cluster
```

4. View docker containers
Use following command to connect your docker client to docker swarm being created by above scripts,
and view the running containers.
```
eval $(docker-machine env --swarm mhs-demo0)
docker ps
```

4. Destroy the environment
```
./delete-vms
```
