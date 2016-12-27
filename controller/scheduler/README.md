# Scheduler

The scheduler is part of the Photon Controller distributed-scheduler. The distributed-scheduler
is in charge of two operations: locate an object, select a host for placement of an object.
In both cases the distributed-scheduler return a host identifier.
The distributed-scheduler is structured as a tree, requests are posted to the scheduler.
The scheduler performs the following tasks:

* Concurrently forwards the requests to the other components of the distributed-scheduler,
* Collects and analizes responses,
* Responds with the best host identifier

Some of the logic in the scheduler is dedicated to forwarding the request to the sub-tree
that has the best chance of satisfying it.

The scheduler is implemented as Thrift Server running in the Dropwizard framework.

## Build

The scheduler is build through Gradle. The gradle wrapper script in the root directory will take care of installing the
appropriate Gradle binaries for you.

To build, run scheduler unit tests, and assemble the release JAR, run this command from the 'scheduler' directory:

```
../gradlew clean build distTar
```

The resulting JAR file can be found in `build/distributions`.

## Runtime Configuration

When the scheduler container is started, the container entrypoint is `/etc/esxcloud/run.sh`. This starts a new instance
of the scheduler service using the configuration in `/etc/esxcloud/scheduler.yml`.

In production scenarios, the configuration directory is mapped to `/etc/esxcloud/mustache/scheduler` in the underlying VM.

## Debugging

For unit test failures, Gradle will produce a nice HTML report on the test failures with logs from the failing test
cases, which are usually sufficient to diagnose the failure.

For debugging issues with real service instances, you can take a look
at the Photon Controller log file.
