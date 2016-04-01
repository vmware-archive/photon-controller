# Photon Controller

Photon Controller is an open-source system for managing hardware, containers, and clusters at scale.

Photon Controller is designed to support:
* **High Scale**: Manage tens of thousands of compute nodes in one deployment.
* **High Churn**: Handle thousands of concurrent API requests.
* **Multiple Tenants**: Allocate and manage resources for many users and groups in a single deployment.
* **Container Frameworks**: Easily spin up instances of [Kubernetes](http://kubernetes.io), [Mesos](http://mesos.apache.org), and [Docker Swarm](http://docs.docker.com/swarm/) in seconds.

## Participation

If you'd like to become a part of the Photon Controller community, here are some ways to connect:

* Visit us on [GitHub](http://vmware.github.io/photon-controller)
* Join the [Photon Controller group](http://groups.google.com/group/photon-controller) on Google Groups
* Ask questions using the "photon-controller" tag on [Stack Overflow](http://stackoverflow.com/)

If you're looking to play with the code, keep reading.

## Repository

The product is arranged in a single repository, with subdirectories arranged by language. See the individual READMEs for instructions on building the code.

* [Devbox](devbox-photon/README.md): Devbox uses [Vagrant](http://vagrantup.com) to create a small standalone deployment of Photon Controller for test purposes.
* [Java](java/README.md): Most of the Photon Controller management plane is written in Java, with many individual services implemented on top of the [Xenon framework](http://vmware.github.io/xenon).
* [Python](python/README.md): The ESX agent and its test and analysis collateral are implemented in Python.
* [Ruby](ruby/README.md): The Photon Controller CLI is implemented in Ruby, as are the integration tests for the product.
  * **Note**: The Ruby CLI will soon be replaced with a Golang version.
* [Thrift](thrift/README.md): Photon Controller uses [Apache Thrift](http://thrift.apache.org) for RPC communication between the management plane and the agent.

## Development

If you'd like to make changes to Photon Controller, you can submit pull requests on [GitHub](http://github.com/vmware/photon-controller).

If you have not signed our contributor license agreement (CLA), our bot will update the issue when you open a [Pull Request](https://help.github.com/articles/creating-a-pull-request). For any questions about the CLA process, please refer to our [FAQ](https://cla.vmware.com/faq).

All pull requests satisfy the following criteria:

1. Pass **unit tests** according to the instructions in the appropriate language-specific README file.
2. Pass **integration tests** according to the instructions for [Devbox](devbox-photon/README.md).

We will run any changes through our own validation process which ensures that both conditions are met, but it's in everyone's interest if you take care of this on your own first.

Thanks, and enjoy!
