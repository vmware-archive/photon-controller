# Kubernetes Stem Cell

This builds the Kubernetes stem cell for use by the Cluster Manager. It is
capable of being used to install three nodes types: etcd, master, and worker node.
The stem cell has Kubernetes hyperkube pre-installed, and we bring up
Kubernetes using docker-multinode (from kube-deploy). We've modified
kube-deploy to allow etcd to be on one or more nodes, all separate from the master.

To bring up each node, the appropriate script needs to be run. The scripts are
in /root/docker-multinode

To run etcd:

```
./etcd.sh
```

To run the master, create an environment variable named ETCD_IPS with a comma-separated
list of IP addresses of the etcd nodes, then something like the following (with
appropriate IP addresses).

```
ETCD_IPS=1.2.3.4,2.3.4.5 ./master.sh
```

To run the master, create two environment variables. One is ETCD_IPS (as above),
while the other is MASTER_IP for the master's IP address. Then run something like
the following (with the appropriate IP addresses):

```
ETCD_IPS=1.2.3.4,2.3.4.5 MASTER_IP=4.4.4.4 ./worker.sh
```
