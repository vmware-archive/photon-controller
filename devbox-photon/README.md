# Devbox Environment

## Introduction

Devbox uses [Vagrant], [Docker] and [Photon OS] to create a local VM and install the Photon Controller components in docker containers. These components are installed from source located in the local working copy. This allows you to make a change locally without committing it and testing it end to end.

All of the components are compiled, packaged and started in their own containers inside the VM. The process does not make any changes to your working copy.

The logs from the Photon Controller services are synced to the host and are located in `log` directory.

## Requirements

* [Vagrant](http://www.vagrantup.com/)
* [VirtualBox](https://www.virtualbox.org/)
* [Vagrant plugin for Photon OS](https://github.com/vmware/vagrant-guests-photon-controller)

## Instructions

### Starting Devbox

#### Photon OS plugin for Vagrant

In order to control the devbox VM, Vagrant needs a plugin for Photon OS. The plugin is installed as a gem directly into Vagrant. Run the following to install the plugin:

    vagrant plugin install vagrant-guests-photon

#### Start and provision devbox

We use Gradle for building and provisioning the devbox. The entire lifecycle can be controlled from Gradle. From `/java` or `/devbox`, run:

	./gradlew :devbox:up

This will bring up Vagrant, build all service containers, then start them. Your devbox is now ready.

#### Setting Vagrant box name and location

Devbox Vagrant box file is located at `https://s3.amazonaws.com/photon-platform/artifacts/devbox/<build #>/photon-devbox.box`. It is downloaded on-demand and cached locally. It is also associated with a name. Both name and URL can be redirected by using environment variables (`DEVBOX_NAME` and `DEVBOX_URL`).

### Checking devbox status

Devbox status can be checked with the following command:

    vagrant status

The API Frontend has been forwarded locally to port 9080. Status can be checked with the following commands:

    curl http://localhost:9080/status/
    curl http://<devbox ip>:9000/status/

By default devbox starts with a private IP address (172.31.253.66). As described below, a public or a specific private IP address can be configured using environment variables.

### Accessing devbox

You can go into the devbox VM using the vagrant ssh command.

    vagrant ssh [photon]

### Stopping and destroying devbox

    vagrant destroy [-f] [photon]

Destroying devbox is the recommended way of cleaning up.

### Recreate without destroying

Vagrant supports reprovisioning and reloading of devbox VMs, but in order to perform complete cleanup, it is not recommended to use the Vagrant reload feature (`vagrant reload [photon]`). It is recommended to destroy and create the devbox VM as described above.

### Emergency VM cleanup

If for some reason devbox VM cannot be destroyed via Vagrant command (`vagrant destroy [-f] [photon]`), follow the next steps to manually power off and delete the VM:

* Identify the running VMs

    `vboxmanage list runningvms`

Sample output may look like this:

    "devbox-photon_photon_1446051688357_62198" {4b963230-c8b4-434d-b70d-39270373fa65}

Notice the machine ID ("devbox-photon_photon_1446051688357_62198").

* If the VM is not running, identify it with the following command:

    `vboxmanage list vms`

* Power off the VM:

    `vboxmanage controlvm <machine ID> poweroff`

for example:

    vboxmanage controlvm devbox-photon_photon_1446051688357_62198 poweroff

* Unregister the VM (with optional deletion of its artifacts)

    `vboxmanage unregistervm <machine ID> [--delete]`

for example:

    vboxmanage unregistervm devbox-photon_photon_1446051688357_62198 --delete

## Controlling devbox behavior with environment variables

### Vagrant box file
To override the default values of the vagrant box name and URL, define the following self-explanatory environment variables before starting devbox:

* `DEVBOX_NAME`
* `DEVBOX_URL`

### IP Address
By default devbox runs with a private IP address as part of the default configuration of VirtualBox (172.31.253.66). To specify your own private IP address define the following environment variable before starting devbox:

* `PRIVATE_NETWORK_IP`

To use a public IP address for the devbox, define the following environment variables before starting devbox:

* `PUBLIC_NETWORK_IP`
* `PUBLIC_NETWORK_NETMASK`

The following self-explanatory variables are optional:

* `BRIDGE_NETWORK`
* `PUBLIC_NETWORK_GATEWAY`

### Using devbox with Lightwave for authentication
To enable authentication in Photon Controller services, a Lightwave STS must be installed, configured and available. In most cases a public IP address needs to be set on the devbox in order to access the Lightwave server. To enable authentication define the following environment variables before starting devbox:

* `ENABLE_AUTH` - set to `true` to enable authentication, do not set or set to `false` to disable it
* `PHOTON_AUTH_LS_ENDPOINT` - address of the Lightwave server
* `PHOTON_AUTH_SERVER_PORT` - port of Lightwave server, currently fixed to 443
* `PHOTON_AUTH_SERVER_TENANT` - tenant (domain) of the Lightwave server
* `PHOTON_SWAGGER_LOGIN_URL` - registered login URL for the client at the Lightwave server
* `PHOTON_SWAGGER_LOGOUT_URL` - registered logout URL for the client at the Lightwave server

### Using devbox with a real ESX host
By default devbox uses an internal Photon Controller Agent, which mimics the ESX operations. To use with an actual ESX host, set the following environment variables before starting devbox:

* `REAL_AGENT` set to `true` to use real ESX host. Do not set or set to false to use internal Agent
* `ESX_IP` IP address of the ESX host
* `ESX_DATASTORE` name of the data store on the ESX host to be used by devbox

NOTE: When using real ESX host, devbox installs Photon Controller Agent's VIB on it. This setting is used frequently with a public IP address of the devbox.

### Enable syslog log entries remoting
To enable sending log entries to a remote syslog endpoint (such as VMware LogInsight), use the following environment variables before starting devbox:

* `ENABLE_SYSLOG` - set to `true` to use a remote syslog endpoint
* `SYSLOG_ENDPOINT` - address of the remote syslog endpoint

### Build into remote Docker endpoint
The Gradle container build tasks can target any Docker API endpoint. By default, the local devbox is assumed. You can override this by setting `DOCKER_URL`.

### Other settings

* `NO_PORT_FORWARDING` - define if you want to disable the Vagrant/VirtualBox port forwarding of the services from the VM to the host machine. For details on the forwarded ports, refer to the `Vagrantfile`. In most cases forwarding is not necessary as the ports are accessible directly on the VM via its IP address.
* `DEVBOX_PHOTON_MEMORY` - override the memory setting for the devbox VM. Default is 3072MB.
* `DEVBOX_PHOTON_CPUS` - override the CPUs assigned to the devbox VM. Default is 4.
* `PROXY_PROFILE` - Allows configuring proxy inside the devbox VM. Requires a proxy setting file `/etc/profile.d/proxy.sh` which is used inside the VM for setting up proxy.
* `DEVBOX_CLEAN_LOGS` - set if you want the logs directory cleaned up when running 'vagrant up' or 'vagrant provision'.
* `DEVBOX_FORCE_PHOTON_PLUGIN_REINSTALL` - force reinstall of the Vagrant plugin for Photon OS even if it's already installed.

## Remote debugging
Remote debugging is enabled by default for the Java services in devbox. Debugger endpoints are available at the following ports:

* API Frontend: 39000
* Root Scheduler: 23010
* Housekeeper: 26000
* Cloud Store: 29000
* Deployer: 28000

To connect the remote debugger, add a new remote debug configuration with the hostname set to devbox's IP address (default for private configuration is `172.31.253.66`) and the corresponding service port.

## Deploy Photon Controller from devbox
To deploy photon controller on to one or multiple hosts, follow the set of steps given below.

The following enviroment variables need to be set in addition to the ones mentioned above.

* `OVFTOOL_FULL_PATH` to specify the path of the ovftool installation on your machine
* `RUN_ON_MAC=true` if you are running devbox on Mac

Then run the script `./prepare-devbox-deployment.sh`. On successful completion of the script you will be able to see
that `devbox is ready for deployment`.

Then you can point the CLI against the endpoint `http://PUBLIC_NETWORK_IP:9000` and do a deployment. To find out how
to use CLI, please refer to the Readme under ruby/cli.


## Controlling devbox from Gradle
The Gradle build system under `/java` provides tasks for managing the devbox.

	# Equivalent to vagrant up
	./gradlew :devbox:vagrantUp

	# Equivalent to vagrant destroy
	./gradlew :devbox:vagrantDestroy

	# Equivalent to vagrant halt
	./gradlew :devbox:vagrantHalt

	# Equivalent to vagrant reload
	./gradlew :devbox:vagrantReload

	# Equivalent to vagrant status
	./gradlew :devbox:vagrantStatus

	# Build agent VIB
	./gradlew :devbox:agent:buildVib

	# Start/stop/restart fake agent
	./gradlew :devbox:agent:[start|stop|restart]

	# Complete destroy and rebuild of devbox and containers
	./gradlew :devbox:renew

	# Builds everything
	./gradlew :devbox:buildAll

	# Cleans containers
	./gradlew :devbox:cleanAll

	# Does a :devbox:cleanAll followed by :devbox:buildAll
	./gradlew :devbox:rebuild

	# Starts everything
	./gradlew :devbox:startAll

	# Builds all service containers (not including agent)
	./gradlew :containers:all

	# Builds container for serviceName, e.g. deployer, housekeeper, etc
	./gradlew :serviceName:container

	# Starts/restarts container for serviceName, e.g. deployer, housekeeper, etc
	./gradlew :serviceName:start

Task definitions can be found in their respective Gradle build files. The usual Gradle project layout is used. E.g., devbox tasks are under `/java/devbox/build.gradle`, deployer tasks are under `/java/deployer/build.gradle`, etc.

Note that management-api tasks are under `api-frontend:management`, e.g.:

	./gradlew :api-frontend:management:container

### Incremental builds
You can rebuild and restart individual containers at a time. If you make a change in one service, e.g. deployer, you do not need to rebuild the VM, reprovision the VM, or rebuild everything. Just build and start the service you want:

	./gradlew :deployer:container :deployer:start

The first task, `container`, builds the container into the devbox, while `start` brings it online (stopping/removing any previous container first). This pattern follows for the other services as well. Also, the exact ordering on the command line does not matter, the Gradle tasks express proper dependency.

## Service build and start scripts
The Vagrant provision process creates a handful of helper shell scripts under $HOME/bin, which are used to build and start the service containers. These can be invoked via Gradle tasks or by hand from within the VM. Invoking them via Gradle provides a nice way to manage a complex build workflow, but running them by hand is quickest and often best when debugging.

These scripts are avaiable on the vagrant user's PATH, so they can be invoked from anywhere after connecting via SSH with `vagrant ssh`.

To build the agent:

	build-agent

To start a service, use `start-serviceName`, e.g.:

	start-agent

To start the deployer:

	start-deployer

And so forth for each service. Just take a look at $HOME/bin for all scripts.

To start everything:

	start-all

These start scripts will stop, remove, and restart their respective service containers.

## Gradle wrapper
The root of the Gradle project is under `/java`, so a wrapper is provided under `/devbox-photon` that forwards calls to `/java/gradlew`. This is just for the convenience of invoking `./gradlew` from `/devbox-photon` without having to change directories or open another shell.
