# Set the following if real agent is to be used
export REAL_AGENT=1

# Set the following environment variables

# The network address of the load balancer
export API_ADDRESS=
# The listening port of the API-FE service on load balancer
export API_FE_PORT=9000
# The IP of the host where the integration tests are going to run on.
# For example, creating vms on it.
export ESX_IP=
# The datastore name that the integration tests are going to run on.
# For example, uploading images to it.
export ESX_DATASTORE=
# The ID for the datastore
export ESX_DATASTORE_ID=
# The portgroup for management VMs
export ESX_MGMT_PORT_GROUP=
# The portgroup for regular VMs
export ESX_VM_PORT_GROUP=
# Username to access esx host
export ESX_USERNAME=
# Password for the user to access esx host
export ESX_PASSWORD=
# DNS server address for management VMs
export MGMT_VM_DNS_SERVER=
# Gateway for management VMs
export MGMT_VM_GATEWAY=
# Static IP for a management VM
export MGMT_VM_IP=
# Network mask for the management VM
export MGMT_VM_NETMASK=
# Network address assigned to the deployer
export DEPLOYER_ADDRESS=
# Network address for the zookeeper service
export ZOOKEEPER_ADDRESS=
# Listening port for the zookeeper service
export ZOOKEEPER_PORT=2181


# Set the following environment variables if a vagrant box needs
# to be set up to run the services.

# Vagrant box IP
export PUBLIC_NETWORK_IP=
# Vagrant box network mask
export PUBLIC_NETWORK_NETMASK=
# Vagrant box network gateway
export PUBLIC_NETWORK_GATEWAY=
# Network bridge between vagrant box and host
export BRIDGE_NETWORK=

# Set the following environment variables if authentication and
# authorization are enabled at API-FE service.

# Group for admin users
# Group name must be prefix with the name of the domain that it is on
# e.g., photon\\PhotonAdmins, where photon is the domain name and PhotonAdmins is the group name.
export PHOTON_ADMIN_GROUP=
# The admin user for LightWave
export PHOTON_USERNAME_ADMIN=
# The password for the admin user of LightWave
export PHOTON_PASSWORD_ADMIN=
# The group for deployment admins
# Group name must be prefix with the name of the domain that it is on
# e.g., photon\\PhotonAdmin2Group, where photon is the domain name and PhotonAdmin2Group is the group name.
export PHOTON_ADMIN2_GROUP=
# The admin user for a deployment
export PHOTON_USERNAME_ADMIN2=
# The password for the admin user of a deployment
export PHOTON_PASSWORD_ADMIN2=
# Group for tenant admins
# Group name must be prefix with the name of the domain that it is on
# e.g., photon\\PhotonTenantAdminGroup, where photon is the domain name and PhotonTenantAdminGroup is the group name.
export PHOTON_TENANT_ADMIN_GROUP=
# The admin user for a tenant on a photon controller deployment
export PHOTON_USERNAME_TENANT_ADMIN=
# The password for the admin user of a photon controller tenant
export PHOTON_PASSWORD_TENANT_ADMIN=
# Group for project users
# Group name must be prefix with the name of the domain that it is on
# e.g., photon\\PhotonProjectUserGroup, where photon is the domain name and PhotonProjectUserGroup is the group name.
export PHOTON_PROJECT_USER_GROUP=
# The project user name
export PHOTON_USERNAME_PROJECT_USER=
# The password for the project user
export PHOTON_PASSWORD_PROJECT_USER=
# A guest user on LightWave that has nothing to do with photon controller
export PHOTON_USERNAME_NON_ADMIN=
# The password for the guest user
export PHOTON_PASSWORD_NON_ADMIN=
# The LightWave network address
export PHOTON_AUTH_LS_ENDPOINT=
# The port of LightWave
export PHOTON_AUTH_SERVER_PORT=
# The name of the tenant that was created on LightWave
export PHOTON_AUTH_SERVER_TENANT=
# The login URL to access LightWave from swagger api webpage
export PHOTON_SWAGGER_LOGIN_URL=
# The logout URL to access LightWave from swagger api webpage
export PHOTON_SWAGGER_LOGOUT_URL=

# Set the following environment variables if image-related integration tests
# are to execute

# Path to a stream-optimized VMDK image file
export ESXCLOUD_DISK_IMAGE=
# Path to a NOT stream-optimized VMDK image file
export ESXCLOUD_BAD_DISK_IMAGE=
# Path to an OVA image file
export ESXCLOUD_DISK_OVA_IMAGE=
# Path to an bootable OVA image file
export ESXCLOUD_DISK_BOOTABLE_OVA_IMAGE=
# Path to an ISO file
export ESXCLOUD_ISO_FILE=
# Path to Photon controller management vm VMDK file
export MGMT_IMAGE=

# Set the following environment variables if cluster management
# integration is to execute

# The DNS server for all cluster managers, including MESOS, KUBERNETES, and docker SWARM
export MESOS_ZK_DNS=
# The Gateway for all cluster managers, including MESOS, KUBERNETES, and docker SWARM
export MESOS_ZK_GATEWAY=
# The network mask for all cluster managers, including MESOS, KUBERNETES, and docker SWARM
export MESOS_ZK_NETMASK=
# The IP of the primary MESOS zookeeper container
export MESOS_ZK_1_IP=
# The ETCD IP of KUBERNETES
export KUBERNETES_ETCD_1_IP=
# The IP of KUBERNETES
export KUBERNETES_MASTER_IP=
# The IP of docker SWARM
export SWARM_ETCD_1_IP=
# Path to the KUBERNETES image file
export KUBERNETES_IMAGE=
# Path to the MESOS image file
export MESOS_IMAGE=
# Path to the docker SWARM image
export SWARM_IMAGE=

# Run the following command if Vagrant box is to be used to host photon controller services
source $(dirname $BASH_SOURCE)/start_devbox.sh
