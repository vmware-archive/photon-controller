# Service configuration

## Layout

Alongside the “main” and “test” directories for each component, you’ll find a “configuration” directory containing at least four files:
- A {service name}.yml template file containing the template which is used to generate the service configuration file
- A {service name}_release.json file containing the static configuration values for real deployment scenarios
- A {service name}_test.json file containing the static configuration values for devbox deployment scenarios
- A run.sh file which is used as the container entry point (e.g. it’s what’s run when your container is started)
When a service container is started, someone — either the deployer or Vagrant — runs Mustache to turn the template .yml file into a full service configuration file for the container based on two inputs: the appropriate static configuration file for the scenario and a set of dynamic parameters containing information about the deployment. The configuration file is then mapped into the container in some fashion so that it can be consumed by the service at startup time.

## Process

When making changes to service configuration going forward, use the following process:
- If you are adding a static configuration parameter to your service configuration which does not change from devbox to deployment, then simply add it to the .yml file.
- If you are adding a static configuration parameter to your service configuration which is different from devbox to deployment scenarios, then add your value as a dynamic parameter to the dynamicParameters list in _release.json and to the value set in _test.json for your service.
- If you are adding a configuration parameter which must be computed dynamically at deployment time — this should usually be the case only for information related to the deployment topology — then you’ll need to add this value both to the Java code in the deployer and to the Vagrantfile in devbox.
