
# auth-tool

auth-tool is a command line tool that performs auth-related tasks, including getting access token and refresh token
from LightWave server and registering client to LightWave server.

## Build

The auth-tool is built through Gradle.

The gradle wrapper script in the root directory will take care of installing the appropriate Gradle binaries for you.

To build, run auth-tool unit tests, and assemble the release JAR, run this command from the 'auth-tool' directory:

```
../gradlew clean build distTar
```

The resulting JAR file can be found in `build/distributions`.
