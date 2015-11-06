# Cloud Store

The cloud store service is responsible for the persistence of the state maintained by Photon Controller. It is
a replicated/distributed document store and contains a collection of micro services, one for each document instance we
store and are accessible using a rest interface.

## Build

Cloud store is built through Gradle. The gradle wrapper script in the root directory will take care of installing the
appropriate Gradle binaries for you.

To build, run cloud store unit tests, and assemble the release JAR, run this command from the 'cloud-store' directory:

```
../gradlew clean build distTar
```

The resulting JAR file can be found in `build/distributions`.

To build, without running cloud store unit tests or assembling the release JAR, run this command from the
'cloud-store' directory:

```
../gradlew clean build -x test
```

## Runtime Configuration

Runtime configuration is specified in the files under `cloud-store/src/dist/configuration`.

## Debugging

For unit test failures, Gradle will produce a nice HTML report on the test failures with logs from the failing test
cases, which are usually sufficient to diagnose the failure.

For debugging issues with real service instances, you can take a look at the component logs. Inside the cloud store
container, component logs are written to `/var/log/esxcloud/cloud-store.log`. In devbox scenarios, this directory is
mapped to `log/cloud-store` under the devbox directory; in production scenarios, this directory is mapped to
`/var/log/esxcloud/cloud-store` in the underlying VM.
