# Deployer

The deployer service is responsible for the life cycle of a Photon Controller deployment and for the physical resources
-- hosts, data stores, networks -- which are under management by the deployment. This includes operations like
provisioning hardware, bringing up VMs for the management plane, and starting the services which comprise the management
plane.

In the future, the deployer service may also take on more responsibilities, including:
* Monitoring the health of other services
* Restarting failed services or bringing up replacement management images
* Upgrade of an existing deployment to a new version

The deployer is implemented as a Xenon service, with individual tasks implemented as collections of microservices.

## Build

The deployer is build through Gradle. The gradle wrapper script in the root directory will take care of installing the
appropriate Gradle binaries for you.

To build, run Deployer unit tests, and assemble the release JAR, run this command from the 'deployer' directory:

```
../gradlew clean build distTar
```

The resulting JAR file can be found in `build/distributions`.

## Runtime Configuration

When the deployer container is started, the container entrypoint is `/etc/esxcloud/run.sh`. This starts a new instance
of the deployer service using the configuration in `/etc/esxcloud/deployer.yml`.

In production scenarios, the configuration directory is mapped to `/etc/esxcloud/deployer` in the underlying VM.

## Debugging

For unit test failures, Gradle will produce a nice HTML report on the test failures with logs from the failing test
cases, which are usually sufficient to diagnose the failure.

For debugging issues with real service instances, you can take a look at the component logs. Inside the deployer
container, component logs are written to `/var/log/esxcloud/deployer.log`. In devbox scenarios, this directory is mapped
to `log/deployer` under the devbox directory; in production scenarios, this directory is mapped to
`/var/log/esxcloud/deployer` in the underlying VM.
