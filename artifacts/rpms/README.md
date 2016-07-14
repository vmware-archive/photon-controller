# RPM Packaging
The aim of this document is to provide enough information to new developer to maintian the code and scripts in this folder, which are used for building PRM package of Photon-Controller service.

## Basics

### RPM
RPM is packaging format used in many Linux distributions. PhotonOS uses RPM format for its packages.
Following command will install an RPM named `foo.rpm` on the PhotonOS if all dependencies of foo are already installed.
```
rpm -i foo.rpm
```

### tdnf/yum
`yum` is package manager that installs an RPM package from remote repo.
It handles all dependency resolution by itself.
PhotonOS has light weight package manager called `tdnf` which is similar in functionality to `yum`.
Following tdnf command will install foo and all its dependencies from remote repo.
```
tdnf install foo
```

## Building an RPM package
To build an RPM, you need following things.

 * Source of your software
 * Spec file that tells the package builder about the the final RPM
 * A runtime system that can compile Spec+Source into an RPM.
   This runtime system is just any OS with `rpmbuild` installed in it. We use PhotonOS docker container for that purpose so that created RPM has
   right PhotonOS dependencies markted in it

More info: http://www.tldp.org/HOWTO/RPM-HOWTO/build.html

## Building Photon-Controller RPM package

### Directory structure
 * java/rpms/SPEC folder is used for storing the RPM spec file.
 * java/rpms/SOURCES folder holds the distTar file which is used as Source for Spec file.
 * java/build/RPMS folder will have the final RPM and its RPM repo output.
 * java/rpms/scripts folder contain mainly two scripts
   * create-rpm.sh script actually prepares all the files and folder neccesseroy to bulid the RPM and then call the docker container to build RPM.
   * create-rpm-helper.sh script is executed inside the container to call the `rpmbuild` command.

You may be asking why these folder names are in capital case. The answer is that rpm build commands expact certain folder structure by default with hardcoded names.

### Docker Docker Docker
Because the user will not have all the right dependencies installed to build the RPM, we use PhotonOS container to build RPM inside it.
This container's Dockerfile is located at `java/containers/rpmbuidler/Dockerfile`.

This Docker container is already pushed to hub.docker.com and available to use by the scripts.
You will rarely need to run this container for debugging purpose, and instead this container will be used by create-rpm.sh script.

### Build Instructions
You can use following commands to build the rpms and rpm repo.

```
cd java
./gradlew distTar -x test
./rpms/scripts/create-rpm.sh
```

Or

```
cd java
./gradlew rpm
```

Gradle task `rpm` is most used way to build RPM. It will create the distTar file before calling the rpm script.
The output rpms will be created in `java/build/RPMS` folder.

#### Troubleshooting

Call the script with DEBUG flag to launch the container without running the `rpmbuild` command.
Then you can run the `rpmbuild` manually in the container to debug it.
```
DEBUG=true ./rpm/script/create-rpm.sh
```
