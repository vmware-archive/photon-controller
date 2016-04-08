# Debugging the Zookeeper cluster

## Remote commands
The following commands can be run from a remote machine to asses the health of a zookeeper cluster. To execute them
you just need a OS X or Linus box with the **nc** utilty available.

```bash
    # retrieve runtime information on the Zookeeper node. Highly relevant info:
    # - the mode (standalone|clusered)
    echo srvr | nc %vm_ip% 2181

    # retrieve the configuration of the Zookeeper node. Highly relevant info:
    # - dataDir
    # - serverId (in clustered mode this should be != 0)
    echo conf | nc %vm_ip% 2181

    #retrieve the environment that the Zookeeper node was started from.
    echo envi | nc 10.146.34.101 2181
```

## Local commands
These commands are meant to be run from within one of the Zookeeper containers. To execute them log into one of the
management VMs.

```bash
    # start bash inside the container
    docker exec -it Zookeeper bash

    # retrieve the status of the Zookeeper service
    /usr/bin/zkServer.sh status
```
