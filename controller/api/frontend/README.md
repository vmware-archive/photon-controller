# api-frontend

The api-frontend service is the component providing all the APIs for Photon Controller. Users can perform
operations (such as creating vms, attaching disks, etc.), also get, list, and modify resources (including hosts,
datastores, vms, disks, network, etc.) by calling Photon Controller APIs. In addition,
other internal components including deployer, cluster-manager call api-frontend to manage resources and perform
operations. It provides APIs to authorize users/user groups. All the APIs are protected utilizing authentication
service - LightWave.

The api-frontend provides a web-based documentation of all the APIs using Swagger UI framework. It can be found at
`root_url/api`.

## Build

The api-frontend is build through Gradle. The gradle wrapper script in the root directory will take care of installing the
appropriate Gradle binaries for you. The release JAR is built under 'api-frontend/management' directory,
run this command from the 'api-frontend/management' directory to run unit tests and build release JAR:

```
../../gradlew clean build distTar
```

The resulting JAR file can be found in `build/distributions`.

## Runtime Configuration

When the api-frontend container is started, the container entrypoint is `/etc/esxcloud/run.sh`. This starts a new instance
of the api-frontend service using the configuration in `/etc/esxcloud/photon-controller-core.yml`.

In production scenarios, the configuration directory is mapped to `/etc/esxcloud/management_api` in the underlying VM.

## Debugging

For unit test failures, Gradle will produce a nice HTML report on the test failures with logs from the failing test
cases, which are usually sufficient to diagnose the failure.

For debugging issues with real service instances, you can take a look at the component logs. Inside the management_api
container, component logs are written to `/var/log/esxcloud/management-api.log`. In devbox scenarios, this directory is mapped
to `log/management_api` under the devbox directory; in production scenarios, this directory is mapped to
`/var/log/esxcloud/management_api` in the underlying VM.
