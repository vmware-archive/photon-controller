# DHCP Agent

The DHCP agent service acts as an interface of DHCP server with other services of Photon Controller.

## Build

DHC agent is built through Gradle. The gradle wrapper script in the root directory will take care of installing the
appropriate Gradle binaries for you.

To build, run dhcp agent unit tests, and assemble the release JAR, run this command from the 'dhcp-agent' directory:

```
../gradlew clean build distTar
```

The resulting JAR file can be found in `build/distributions`.

To build, without running dhcp agent unit tests or assembling the release JAR, run this command from the
'dhcp-agent' directory:

```
../gradlew clean build -x test
```

## Runtime Configuration

Runtime configuration is specified in the files under `dhcp-agent/src/dist/configuration`.

## Debugging

For unit test failures, Gradle will produce a nice HTML report on the test failures with logs from the failing test
cases, which are usually sufficient to diagnose the failure.

For debugging issues with real service instances, you can take a look at the component logs. Inside the DHCP agent
container, component logs are written to `/var/log/photoncontroller/dhcp-agent.log`. In devbox scenarios, this directory is
mapped to `log/dhcp-agent` under the devbox directory; in production scenarios, this directory is mapped to
`/var/log/photoncontroller/dhcp-agent` in the underlying VM.
