# Photon Controller Integration Tests

The integration tests for Photon Controller.

## Requirements

* ruby 1.9 or above is required (OSX 10.8+ system ruby should be fine)
* bundler (gem install bundler)

## Set up a LightWave Server (only required if running integration tests with auth-enabled)

### Create a Tenant (e.g., photon)

The groups and users below should be created on this new tenant

### Create the Following Groups

* Admin group (e.g., PhotonAdmins)
* Admin group for deployment (e.g., PhotonAdmin2Group)
* Admin group for a tenant (e.g., PhotonTenantAdminGroup)
* A project group (e.g., PhotonProjectUserGroup)

### Create the Following Users

* Admin user (e.g., ec-admin@photon) and put it into Admin group (e.g., PhotonAdmins)
* Deployment Admin user (e.g., ec-admin2@photon) and put it into deployment Admin group (e.g., PhotonAdmin2Group)
* Tenant Admin user (e.g., ec-tenant-admin@photon) and put it into tenant admin group (e.g.,
PhotonTenantAdminGroup)
* Project user (e.g., ec-project-user@photon) and put it into project group (e.g., PhotonProjectUserGroup)
* Regular user (e.g., ec-user@photon), which is not in any of the groups created above

## Set up Environment Variables

Environment variables required by integration test are to be filled out in file
[environment_setup.sh](ci/environment_setup.sh). Note that

* Not every section needs to be setup (For example, if vagrant box is not to be used, then that specific section
needs not to be filled out)
* If vagrant box is to be used, make sure the start_devbox.sh statement is not commented out
* Read the comment of each environment variable carefully, making sure the appropriate values are provided.

## Run the Integration Test Suite

### Use Local Vagrant Box

This is the way of testing/debugging that developers mostly likely choose to use.
In this model, the services (e.g., API-FE, House Keeper, etc.) are started on the local Vagrant box.
Extra hosts are needed when tests of uploading images and creating vms are to execute.

* Set the following environment variable

      export CUSTOM_TEST_CONFIG=<workspace>/tests/integration_tests/ci/environment_setup.sh

* Make sure the start_devbox.sh statement is not commented in file environment_setup.sh

* Run the integration test

      cd <workspace>/tests/integeration_test/ci
      ./run_test.sh

### Use a Real Deployment

The integration tests can also run on a real deployment. The procedure is the same as using local
vagrant box, but make sure the following things are properly set in environment_setup.sh

* The section for vagrant box (e.g., PUBLIC_NETWORK_IP) needs not to be filled
* The start_devbox.sh statement needs to be commented out
* Make sure API_ADDRESS points to the load balancer of the real deployment
