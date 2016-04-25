# Housekeeper

The housekeeper service is the component for triggering and coordinating image replication,
as well as also periodically deleting stale images on datastores.

Housekeeper is implemented as a Xenon service, with individual tasks implemented as collections of microservices.

Note that images can end up on two kinds of datastores:
* Image datastores. Normally image datastores are shared between hosts and there is at least one image datastore per host
* Local datastores: Before a VM is created, the image needs to be on a local datastore

When an image is created, the user will specify how it will be replicated. There are two kinds of replication:
1. _Eager replication_ will copy the image to all image and local datastores.
2. _On demand replication_ will copy the image to all image datastores. When a VM is created,
it will be copied as needed (on demand) to the local datastore)

## Build

Housekeeper is build through Gradle. The gradle wrapper script in the root directory will take care of installing the
appropriate Gradle binaries for you. The release JAR is built under '/housekeeper' directory, 
run this command from the '/housekeeper' directory to run unit tests and build release JAR:

```
../../gradlew clean build distTar
```

The resulting JAR file can be found in `build/distributions`.

To build, without running Housekeeper unit tests or assembling the release JAR, run this command from the
'/housekeeper' directory:

```
../../gradlew clean build -x test
```

## Runtime Configuration

When the housekeeper container is started, the container entry point is `/etc/esxcloud/run.sh`. This starts a new instance
of the housekeeper service using the configuration in `/etc/esxcloud/housekeeper.yml`.

In production scenarios, the configuration directory is mapped to `/etc/esxcloud/housekeeper` in the underlying VM.

## Debugging

For unit test failures, Gradle will produce a nice HTML report on the test failures with logs from the failing test
cases, which are usually sufficient to diagnose the failure.

For debugging issues with real service instances, you can take a look at the component logs. Inside the housekeeper
container, component logs are written to `/var/log/esxcloud/housekeeper.log`. In devbox scenarios, this directory is mapped
to `log/housekeeper` under the devbox directory; in production scenarios, this directory is mapped to
`/var/log/esxcloud/housekeeper` in the underlying VM.
