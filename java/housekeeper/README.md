# Housekeeper

Housekeeper service is the component for triggering and coordinating image replication and cleaning, 
also periodically triggers deleting staled images on datastores.

Housekeeper is implemented as a DCP service, with individual tasks implemented as collections of microservices.

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

When the housekeeper container is started, the container entrypoint is `/etc/esxcloud/run.sh`. This starts a new instance
of the housekeeper service using the configuration in `/etc/esxcloud/housekeeper.yml`.

In production scenarios, the configuration directory is mapped to `/etc/esxcloud/housekeeper` in the underlying VM.

## Debugging

For unit test failures, Gradle will produce a nice HTML report on the test failures with logs from the failing test
cases, which are usually sufficient to diagnose the failure.

For debugging issues with real service instances, you can take a look at the component logs. Inside the housekeeper
container, component logs are written to `/var/log/esxcloud/housekeeper.log`. In devbox scenarios, this directory is mapped
to `log/housekeeper` under the devbox directory; in production scenarios, this directory is mapped to
`/var/log/esxcloud/housekeeper` in the underlying VM.
