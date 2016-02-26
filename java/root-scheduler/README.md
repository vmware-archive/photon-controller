# Root Scheduler

The root-scheduler is part of the Photon Controller distributed-scheduler. The distributed-scheduler
is in charge of two operations: locate an object, select a host for placement of an object.
In both cases the distributed-scheduler return a host identifier.
The distributed-scheduler is structured as a tree, requests are posted to the root-scheduler.
The root-scheduler performs the following tasks:

* Concurrently forwards the requests to the other components of the distributed-scheduler,
* Collects and analizes responses,
* Responds with the best host identifier

Some of the logic in the root-scheduler is dedicated to forwarding the request to the sub-tree
that has the best chance of satisfying it.

The root-scheduler is implemented as Thrift Server running in the Dropwizard framework.

## Build

The root-scheduler is build through Gradle. The gradle wrapper script in the root directory will take care of installing the
appropriate Gradle binaries for you.

To build, run root-scheduler unit tests, and assemble the release JAR, run this command from the 'root-scheduler' directory:

```
../gradlew clean build distTar
```

The resulting JAR file can be found in `build/distributions`.

## Runtime Configuration

When the root-scheduler container is started, the container entrypoint is `/etc/esxcloud/run.sh`. This starts a new instance
of the root-scheduler service using the configuration in `/etc/esxcloud/root-scheduler.yml`.

In production scenarios, the configuration directory is mapped to `/etc/esxcloud/mustache/root-scheduler` in the underlying VM.

## Debugging

For unit test failures, Gradle will produce a nice HTML report on the test failures with logs from the failing test
cases, which are usually sufficient to diagnose the failure.

For debugging issues with real service instances, you can take a look at the component logs. Inside the RootScheduler
container, component logs are written to `/var/log/esxcloud/root-scheduler.log`. In devbox scenarios, this directory is mapped
to `log/root_scheduler` under the devbox directory; in production scenarios, this directory is mapped to
`/var/log/esxcloud` in the underlying VM.
