# Chairman

Chairman is a Photon Controller component that is responsible for two things:

1. Recieve registrations from host agents and persist them to ZooKeeper.
2. Build the distributed scheduler tree, and configure the root scheduler
   and hosts with the scheduler tree information.

Chairman uses ZooKeeper to elect a leader instance, and the leader instance
takes the responsibility of building the scheduler tree and configuring the
root scheduler and hosts. Any chairman instance can handle host registration
requests since they simply get forwarded to ZooKeeper.

See `java/root-scheduler/README.md` for more details on the scheduler tree.

## Build

To build chairman and run unit test, run the following command:

    ../gradlew build


## Runtime Configuration

The chairman configuration file is in `/etc/esxcloud/chairman.yml`, and the
container entrypoint is `/etc/esxcloud/run.sh`.

## Debugging

The chairman log file is in `/var/log/esxcloud/chairman.log`. You can set the
log level from the configuration file. The log directory is mapped to `/var/log/esxcloud`
in the underlying management VM.

## How to run chairman locally for testing

### Download ZooKeeper

Download the latest release of ZooKeeper from
[here](http://www.apache.org/dyn/closer.cgi/zookeeper/).

### Start ZooKeeper

    tar xfvz zookeeper-3.4.6.tar.gz
    cd zookeeper-3.4.6
    cp conf/zoo_sample.cfg conf/zoo.cfg
    ./bin/zkServer.sh start

### Generate and modify configuration file

Install [mustache](https://github.com/mustache/mustache) and generate a
configuration file:

    mustache src/dist/configuration/chairman_test.json src/dist/configuration/chairman.yml > chairman.yml

Then open chairman.yml, and set `logging.console.enabled` to `true` and
`logging.file.enabled` to `false`.

### Start Chairman

Run the following command:

    ../gradlew installDist
    JVM_OPTS="-Dcurator-dont-log-connection-problems=true" ./build/install/chairman/bin/chairman chairman.yml

This command starts chairman with the logger configured to write logs to stdout.
