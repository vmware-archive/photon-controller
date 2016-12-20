# ClusterManager

ClusterManager in Photon Controller provides the ability to provision and manage clusters of different container
orchestration frameworks such as Mesos, Kubernetes and Docker Swarm. The features provided by ClusterManager today
include:
- Provisioning/ De-provisioning Clusters
- Resizing Clusters
- Background Cluster maintenance
- Network Affinity

Going forward, the ClusterManager will be extended to support the following features:
- Provide a dynamic model to support newer versions of frameworks such as Mesos, Kubernetes and Docker Swarm.
- Support new frameworks such as Hadoop and Jenkins.
- Improve health monitoring of Clusters
- Improve scalability and performance of different cluster operations.

The ClusterManager is implemented as a set of Xenon services, with individual tasks implemented
as collections of micro-services.

## Build

ClusterManager is build using Gradle. The gradle wrapper script in the root directory will take care of installing the
appropriate Gradle binaries for you.

To build, run ClusterManager unit tests, and assemble the release JAR, run this command from the 'cluster-manager'
directory:

```
../gradlew clean build
```
The resulting JAR files can be found under:
- service-documents/build/libs
- backend/build/libs

## Runtime Configuration

ClusterManager is currently hosted in Deployer's Xenon host and runs in the same container as Deployer.

## Debugging

See the "Debugging" Section in deployer/README.md
