# Dev Box "Photon Controller"

## Introduction

Dev Box uses [Vagrant], [Docker] and [Photon OS] to create a local VM and install the Photon Controller components in docker containers. These components are installed from source located in the local working copy. This allows you to make a change locally without committing it and testing it end to end.

All of the components are compiled, packaged and started in their own containers inside the VM. The process does not make any changes to your working copy.

The logs from the Photon Controller services are synced to the host and are located in `log` directory.

## Requirements

* vagrant (http://www.vagrantup.com/downloads.html)

Steps to install: https://docs.vagrantup.com/v2/installation/index.html

* virtualbox (https://www.virtualbox.org/wiki/Downloads)

Steps to install: https://www.virtualbox.org/manual/ch01.html#intro-installing

* vagrant-guests-photon 1.0.2 for Photon OS

Available as a Ruby Gem: https://rubygems.org/gems/vagrant-guests-photon
Source code is at: https://github.com/vmware/vagrant-guests-photon

The plugin can be installed by the install_photon_plugin.sh script listed below.

## Instructions

### Starting Devbox

#### Photon OS plugin for Vagrant

In order to control the Dev Box VM, Vagrant needs a plugin for Photon OS. The plugin is installed as a gem directly into Vagrant. Before starting Dev Box, run the following script to install the plugin:

    install_photon_plugin.sh

The script can be invoked repeatedly if you want to make sure the plugin is installed or if you want to reinstall the plugin.

#### Start and provision Dev Box

Dev Box configuration and startup code is located in the Dev Box directory (`<code>/photon-devbox`). The following command starts Dev Box:

    vagrant up

#### Setting Vagrant box name and location

Dev Box Vagrant box file is located at `https://bintray.com/artifact/download/photon-controller/public/<build #>/resource/photon-devbox.box`. It is downloaded on-demand and cached locally. It is also associated with a name. Both name and URL can be redirected by using environment variables (`DEVBOX_NAME` and `DEVBOX_URL`).

### Checking Dev Box status

Dev Box status can be checked with the following command:

    vagrant status

The API Frontend has been forwarded locally to port 9080. Status can be checked with the following commands:

    curl http://localhost:9080/status/
    curl http://<devbox ip>:9000/status/

By default Dev Box starts with a private IP address (172.31.253.66). As described below, a public or a specific private IP address can be configured using environment variables.

### Accessing Dev Box

You can go into the Dev Box VM using the vagrant ssh command.

    vagrant ssh [photon]

### Stopping and destroying Dev Box

    vagrant destroy [-f] [photon]

Destroying Dev Box is the recommended way of cleaning up.

### Recreate without destroying

Vagrant supports reprovisioning and reloading of Dev Box VMs, but in order to perform complete cleanup, it is not recommended to use the Vagrant reload feature (`vagrant reload [photon]`). It is recommended to destroy and create the Dev Box VM as described above.

### Emergency VM cleanup

If for some reason Dev Box VM cannot be destroyed via Vagrang command (`vagrant destroy [-f] [photon]`), follow the next steps to manually power off and delete the VM:

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

## Controlling Dev Box behavior with environment variables

### Vagrant box file
To override the default values of the vagrant box name and URL, define the following self-explanatory environment variables before starting Dev Box:

* `DEVBOX_NAME`
* `DEVBOX_URL`

### IP Address
By default Dev Box runs with a private IP address as part of the default configuration of VirtualBox (172.31.253.66). To specify your own private IP address define the following environment variable before starting Dev Box:

* `PRIVATE_NETWORK_IP`

To use a public IP address for the Dev Box, define the following environment variables before starting Dev Box:

* `PUBLIC_NETWORK_IP`
* `PUBLIC_NETWORK_NETMASK`

The following self-explanatory variables are optional:

* `BRIDGE_NETWORK`
* `PUBLIC_NETWORK_GATEWAY`

### Using Dev Box with Lightwave for authentication
To enable authentication in Photon Controller services, a Lightwave STS must be installed, configured and available. In most cases a public IP address needs to be set on the Dev Box in order to access the Lightwave server. To enable authentication define the following environment variables before starting Dev Box:

* `ENABLE_AUTH` - set to `true` to enable authentication, do not set or set to `false` to disable it
* `PHOTON_AUTH_LS_ENDPOINT` - address of the Lightwave server
* `PHOTON_AUTH_SERVER_PORT` - port of Lightwave server, currently fixed to 443
* `PHOTON_AUTH_SERVER_TENANT` - tenant (domain) of the Lightwave server
* `PHOTON_SWAGGER_LOGIN_URL` - registered login URL for the client at the Lightwave server
* `PHOTON_SWAGGER_LOGOUT_URL` - registered logout URL for the client at the Lightwave server

### Using Dev Box with a real ESX host
By default Dev Box uses an internal Photon Controller Agent, which mimics the ESX operations. To use with an actual ESX host, set the following environment variables before starting Dev Box:

* `REAL_AGENT` set to `true` to use real ESX host. Do not set or set to false to use internal Agent
* `ESX_IP` IP address of the ESX host
* `ESX_DATASTORE` name of the data store on the ESX host to be used by Dev Box

NOTE: When using real ESX host, Dev Box installs Photon Controller Agent's VIB on it. This setting is used frequently with a public IP address of the Dev Box.

### Enable syslog log entries remoting
To enable sending log entries to a remote syslog endpoint (such as VMware LogInsight), use the following environment variables before starting Dev Box:

* `ENABLE_SYSLOG` - set to `true` to use a remote syslog endpoint
* `SYSLOG_ENDPOINT` - address of the remote syslog endpoint

### Other settings

* `NO_PORT_FORWARDING` - define if you want to disable the Vagrant/VirtualBox port forwarding of the services from the VM to the host machine. For details on the forwarded ports, refer to the `Vagrantfile`. In most cases forwarding is not necessary as the ports are accessible directly on the VM via its IP address.
* `DEVBOX_PHOTON_MEMORY` - override the memory setting for the Dev Box VM. Default is 3072MB.
* `DEVBOX_PHOTON_CPUS` - override the CPUs assigned to the Dev Box VM. Default is 4.
* `PROXY_PROFILE` - Allows configuring proxy inside the Dev Box VM. Requires a proxy setting file `/etc/profile.d/proxy.sh` which is used inside the VM for setting up proxy.
* `DEVBOX_NO_CLEAN_LOGS` - set if you don't want the logs directory cleaned up when bringin up Dev Box.
* `DEVBOX_FORCE_PHOTON_PLUGIN_REINSTALL` - force reinstall of the Vagrant plugin for Photon OS even if it's already installed.

## Remote debugging
Remote debugging is enabled by default for the Java services in Dev Box. Debugger endpoints are available at the following ports:

* API Frontend: 39000
* Root Scheduler: 23010
* Housekeeper: 26000
* Cloud Store: 29000
* Deployer: 28000

To connect the remote debugger, add a new remote debug configuration with the hostname set to Dev Box's IP address (default for private configuration is `172.31.253.66`) and the corresponding service port.

## Deploy Photon Controller from Dev Box
To deploy photon controller on to one or multiple hosts, follow the set of steps given below.

The following enviroment variables need to be set in addition to the ones mentioned above.

* `OVFTOOL_FULL_PATH` to specify the path of the ovftool installation on your machine
* `RUN_ON_MAC=true` if you are running Dev Box on Mac
 
Then run the script `./prepare-devbox-deployment.sh`. On successful completion of the script you will be able to see 
that `Dev Box is ready for deployment`.

Then you can point the CLI against the endpoint `http://PUBLIC_NETWORK_IP:9000` and do a deployment. To find out how 
to use CLI, please refer to the Readme under ruby/cli.

[Vagrant]: http://vagrantup.com
[Docker]: http://www.docker.com
[Photon OS]: https://vmware.github.io/photon/
