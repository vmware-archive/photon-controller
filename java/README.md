# Building Java11

If you will be changing any Java code, then you'll want to build Java and run unit tests in your desktop environment using your favorite IDE. This document will help guide you through that process.

## Requirements

### OS X

#### 1. Java 8

Install the JDK from [oracle.com](http://www.oracle.com/technetwork/java/javase/downloads/index.html) -- 8u20 or newer is best.
* **Note**: You should be able to use the [OpenJDK](http://openjdk.java.net/projects/jdk8/) interchangeably.

#### 2. Thrift

Install [Thrift](https://thrift.apache.org/) using [Brew](http://brew.sh):

~~~bash
brew update
brew install thrift
~~~

As of this writing, these steps caused Thrift 0.9.3 to be installed.

#### 3. Maven

The version of [Maven](https://maven.apache.org/) which is bundled with OS X is dated. Install an up-to-date version using [Brew](http://brew.sh):

~~~bash
brew update
brew install maven
~~~

As of this writing, this workflow caused Maven 3.3.3 to be installed.

### Linux

These instructions cover installation on Ubuntu 14.04 LTS. You will need to install these tools using the appropriate steps for your distro.

#### 1. Java 8

Install the JDK from [oracle.com](http://www.oracle.com/technetwork/java/javase/downloads/index.html) or, if you trust the webupd8team apt-get repository, install using apt-get:

~~~bash
sudo add-apt-repository ppa:webupd8team/java -y
sudo apt-get update
sudo apt-get install oracle-java8-installer
~~~

To automatically set up the Java 8 environment variables:

~~~bash
sudo apt-get install oracle-java8-set-default
~~~

#### 2. Thrift

Install [Thrift](https://thrift.apache.org) and its prerequisites using the [instructions](https://thrift.apache.org/docs/install/debian) for your distro.

As of this writing, these steps caused Thrift 0.9.3 to be installed.

#### 3. Maven

Install [Maven](https://maven.apache.org) and its prerequisites using the [instructions](https://maven.apache.org/install.html) or install using apt-get:

~~~bash
sudo apt-get install maven
~~~

As of this writing, this step caused Maven 3.0.5 to be installed.

## Building

Builds are done with [Gradle](http://gradle.org/). The gradle wrapper script in the root of the `java` directory will take care of installing a version of Gradle -- 2.6, as of this writing -- and invoking it.

### Full-world builds

To clean, build, and run unit tests, run `./gradlew clean build` from the root of the Java directory. This will take on the order of half an hour on a newer MacBook Pro.

If a unit test fails, Gradle will produce a nice HTML output file describing the failures, which you can load in a browser.

### Building without running tests

To clean and build without running unit tests, run `./gradlew clean build -x test` from the root of the Java directory. This will take on the order of five minutes on a newer MacBook Pro.

### Building a specific component

To build and run tests for only one component, cd to the root directory for that component. So, for example, to build and run tests only for the housekeeper component, run `../gradlew clean build` from the `housekeeper` subdirectory.

## Editing code

Gradle most likely has integration with your favorite IDE. You can browse the [Gradle user guide](https://docs.gradle.org/current/userguide/userguide.html) to find out.

* **Note**: IDEs generally cannot run Gradle plugins such as the Thrift plugin, so it's best to do a full build through Gradle before attempting to open the project in your IDE. This will ensure that the generated Thrift code is present.

### Eclipse

To edit source code in Eclipse, we recommend using Eclipse's built-in
support for importing Gradle projects (Buildship) or Enide Gralde for
Eclipse.

Before you import into Eclipse, pleease do one build from the
command-line using: `./gradlew clean build -x text` (as above). This
will generate source code using Thrift and is not done within
Eclipse.

Code should follow our formatting guidelines. You will find an Eclipse Java
formatter profile in java/ide-support/photon-controller-eclipse.xml.

When you import Photon Controller into Eclipse, the project is
automatically configured with a Resource Filter that ignores anything
in the "build" directory. Unfortunately, the thrift sources that were
generated in the previous step are in build/generated-sources, and are
therefore ignored. The only way to fix this is to edit each project to
remove the Resource Filter for the build folder.

Please note: If you use Buildship to import Photon Controller, it will
re-create the resource filter every time you do "Refresh Gradle
Project". It looks like a future version of Buildship will allow you
to disable Buildship's generation of the Resource Filter. Until that
time, you have two options:

1. Use Enide Gradle instead of Buildship
2. Remove the resource filters every time you refresh the Gradle
project. You only need to remove them from the seven projects that use
generated sources:
  * api
  * api-frontend
  * chairman
  * common
  * deployer
  * housekeeper
  * root-scheduler

For details on the upcoming changes to Buildship to address this problem:
* [Discssion](https://discuss.gradle.org/t/build-folder-keeps-disappearing/12570)
* [Bug](https://bugs.eclipse.org/bugs/show_bug.cgi?id=478530)
* [Possible Buildship Change](https://github.com/eclipse/buildship/pull/180)

### IntelliJ

To generate an .ipr file which can be opened in IntelliJ, run `./gradlew cleanIdea idea` from the root of the Java directory.

## Proxy settings

Gradle doesn't honor the system proxy environment variables. Instead one has to
write the proxy config into a gradle.properties file and place it into
~/.gradle/.

Example file:

~~~bash
systemProp.http.proxyHost=proxy.myorg.net
systemProp.http.proxyPort=1234
systemProp.http.nonProxyHosts=*.myorg.net|myorg.net|localhost

systemProp.https.proxyHost=proxy.myorg.net
systemProp.https.proxyPort=1234
systemProp.https.nonProxyHosts=*.myorg.net|myorg.net|localhost

systemProp.ftp.proxyHost=proxy.myorg.net
systemProp.ftp.proxyPort=1234
systemProp.ftp.nonProxyHosts=*.myorg.net|myorg.net|localhost
~~~
