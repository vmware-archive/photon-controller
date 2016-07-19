# Photon Controller CLI

The Photon Controller CLI, written in Ruby, provides command-line interfaces to access API-FE services.

## Requirements

* ruby 1.9 or above is required (OSX 10.8+ system ruby should be fine)
* bundler (gem install bundler)

## Running CLI

      cd ruby/cli
      bundle install
      ./bin/photon <command> # or export 'bin' to your $PATH

Each command can work in interactive and non-interactive mode. In interactive mode (default)
CLI will ask you for options that were not provided on command line. It will also use simple
wizards to compose things like limits and disk specs.

Use '-n' flag to trigger non-interactive mode (for scripting).

### Workflow

      # The first step of using CLI commands is to set the target
      photon target set https://<load-balancer-IP>

      # If authentication is enabled on API-FE, user login is required
      photon auth login --username joe --password pwd

      # Create a tenant
      photon -n tenant create joe

      # Create a resource ticket and project
      photon -n resource-ticket create --tenant joe --name t1 --limits "vm.memory 100 GB, vm 100 COUNT"
      photon -n project create --tenant joe --resource-ticket t1 --name dev --limits "vm.memory 1 GB, vm 50 COUNT"

      # Set the security groups of a project
      photon project set_security_groups project1 --tenant joe

      # Read the security groups of a project
      photon project get_security_groups project1 --tenant joe

      # Creating a host with just cloud tag with no metadata
      photon host create -u 'u1' -t 'CLOUD' -p 'p1' -i "192.168.x.X"

      # Host creation with management tag with metadata
      photon host create -u 'u1' --usage_tags "MGMT,CLOUD" -p 'p1' -i "192.168.x.x"
              -m "{\"ALLOWED_DATASTORES\":\"datastore1, datastore2\", \"MANAGEMENT_DATASTORE\":\"datastore1\", \"MANAGEMENT_NETWORK_DNS_SERVER\":\"<dns_server>\", \"MANAGEMENT_NETWORK_GATEWAY\":\"<network_gateway>\", \"MANAGEMENT_NETWORK_IP\":\"<network_ip>\", \"MANAGEMENT_NETWORK_NETMASK\":\"<network_mask>\", \"MANAGEMENT_PORTGROUP\":\"<port_group>\"}"
      photon host show <host-id>
      photon host list
      photon host list_vms <host-id>

      # Create flavors
      photon -n flavor create --name "core-100" --kind "vm" --cost "vm 1.0 COUNT, vm.flavor.core-100 1.0 COUNT, vm.cpu 1.0 COUNT, vm.memory 2.0 GB, vm.cost 1.0 COUNT"
      photon -n flavor create --name "core-200" --kind "vm" --cost "vm 1.0 COUNT, vm.flavor.core-200 1.0 COUNT, vm.cpu 2.0 COUNT, vm.memory 4.0 GB, vm.cost 2.0 COUNT"
      photon -n flavor create --name "core-100" --kind "ephemeral-disk" --cost "ephemeral-disk 1.0 COUNT, ephemeral-disk.flavor.core-100 1.0 COUNT, ephemeral-disk.cost 1.0 COUNT"
      photon -n flavor create --name "core-200" --kind "ephemeral-disk" --cost "ephemeral-disk 1.0 COUNT, ephemeral-disk.flavor.core-200 1.0 COUNT, ephemeral-disk.cost 1.8 COUNT"
      photon -n flavor create --name "core-100" --kind "persistent-disk" --cost "persistent-disk 1.0 COUNT, persistent-disk.flavor.core-100 1.0 COUNT, persistent-disk.cost 1.0 COUNT"
      photon -n flavor create --name "core-200" --kind "persistent-disk" --cost "persistent-disk 1.0 COUNT, persistent-disk.flavor.core-200 1.0 COUNT, persistent-disk.cost 1.8 COUNT"

      # List flavors
      photon flavor list

      # Upload flavors using a file
      photon flavor upload ../../../../common/test_files/flavors/vm.yml

      # Create a VM
      photon -n vm create --name vm-1 --tenant joe --project dev \
        --flavor core-100 --disks "disk-10 core-100 boot=true, disk-11 core-100 10"

      # Create a VM with networks
      photon -n vm create --name vm-1 --tenant joe --project dev \
        --flavor core-100 --disks "disk-10 core-100 boot=true, disk-11 core-100 10" \
        --networks "public, private"

      # Create a VM with disk affinities settings
      photon -n vm create --name vm-2 --tenant joe --project dev \
        --flavor core-200 --disks "new-disk core-200 boot=true" \
        --affinities "disk exsiting-disk-id1, disk exsiting-disk-id2"

      # Create a VM with host affinities settings
      # note that host affinity cannot be used together with other affinities.
      photon -n vm create --name vm-2 --tenant joe --project dev \
        --flavor core-200 --disks "new-disk core-200 boot=true" \
        --affinities "host host-ip"

      # Create a DISK
      photon -n disk create --name disk-1 --tenant joe --project dev \
        --flavor core-100 --capacity "2gb" --tags "tag1"

      # Create a DISK with affinities settings
      photon -n disk create --name disk-1 --tenant joe --project dev \
        --flavor core-100 --capacity "2gb" --affinities "vm existing-vm-id1, vm existing-vm-id2"

      # Upload an image with EAGER replication option
      photon -n image upload "path-to-image" -i EAGER

      # Upload an image with ON_DEMAND replication option
      photon -n image upload "path-to-image" -i ON_DEMAND

      # Create an image by cloning vm
      photon -n vm create_image <vm-id> -n image_name -r ON_DEMAND
      photon -n vm create_image <vm-id> -n image_name -r EAGER

      # Attach Iso
      photon -n vm attach_iso <vm-id> -p "path/to/isoFile" -n "iso-name"

      # Create a network
      photon network create --name n1 --portgroups "PG1, PG2"

      # Delete a network
      photon network delete <network-id>

      # Update network's portgroups
      photon network set_portgroups <network-id> --portgroups "PG1, PG2"

      # Show or list network information
      photon network show <network-id>
      photon network list

      # Get Vm networks information
      photon vm networks <id> --tenant joe --project dev

      # Set vm metadata
      photon vm set_metadata <vm_id> --metadata "metadata_key1 = metadata_value1, metadata_key2 = metadata_value2"

If authentication is enabled on the API-FE side, photon controller commands would fail unless the user
logins with valid credentials.

      # To get authentication information such as Authentication ServiceLocator URL
      photon auth show

      # To login a user
      photon auth login --username joe --password pwd

      # To logout a user
      photon auth logout

Some commands affect your local state (by writing to ~/.photon file).
      photon target set https://<load-balancer-IP>
      photon tenant set joe
      photon project set dev

After your local state is set you can use commands without giving them an explicit
tenant name or project name:

      # Non-interactive version:
      photon -n vm create --name vm-1 --tenant joe --project dev --flavor core-100 \
        --disks "disk-1 core-100 boot=true, disk-2 core-100 5"

      # Interactive version:
      photon target set https://172.31.253.66
      photon tenant set joe
      photon project set dev
      photon vm create

      VM name: vm-1
      VM flavor: core-100

      Disk 1 (ENTER to finish):
      Name: disk-1
      Flavor: core-100
      Capacity (in GB): 10
      Boot disk? [y/n]: y

      Disk 2 (ENTER to finish):
      Name: disk-2
      Flavor: core-100
      Capacity (in GB): 2
      Boot disk? [y/n]: n

      Disk 3 (ENTER to finish):
      Name:

      Tenant: joe, project: dev
      Creating VM: vm-1 (core-100)

      Please make sure disk specs below are correct:

      1: disk-1, ephemeral-disk, core-100, 10 GB, boot
      2: disk-2, ephemeral-disk, core-100, 2 GB, non-boot

      Are you sure? yes

      VM '<id>' created

It's also possible to list and delete things

      photon vm list
      photon disk list
      photon vm find_by_name <name>
      photon disk find_by_name <name>
      photon project list
      photon resource-ticket list
      photon tenant list

      photon vm delete <id>
      photon disk delete <id>
      photon project delete dev
      photon tenant delete joe

Also you can query tasks of tenant, project and etc

      photon tenant tasks joe
      photon project tasks dev --tenant joe
      photon vm tasks <id> --tenant joe --project dev
      photon disk tasks <id> --tenant joe --project dev
      photon resource-ticket tasks t1 --tenant joe
      photon image tasks image-1
      photon flavor tasks flavor-1

      photon task list
      photon task show <id>
      photon task monitor <id>

For overall system

      # View system status
      photon system status

      # Create hosts and deployment by parsing yml file
      photon system deploy <path_to_file>
      Sample deployment yml file: esxcloud/ruby/common/test_files/deployment/deployment-sample-config.yml

      # Remove a deployment and unregister all hosts
      photon system destroy

      # Pause/Resume system
       photon deployment pause_system <deployment_id>
       photon deployment resume_system <deployment_id>

### Installation

       # Create a deployment
       photon deployment create -i <image_datastore>
       photon deployment create -i <image_datastore> -o <oauth_endpoint> -s <syslog_endpoint> -n <ntp_endpoint> -v

       # Delete a deployment
       photon deployment delete <id>

       # List all deployments
       photon deployment list

       # List vms associated with deployment
       photon deployment list_vms <id>

       # List hosts associated with deployment
       photon deployment list_hosts <id>

       # Show deployment info
       photon deployment show <id>

       # Update the security groups of a deployment
       photon deployment update <id> -g g1,g2,g3


### Demo

       # Create vms in bulk
       photon demo vms create
       photon demo vms create --name demo --tenant joe --project dev \
         --flavor core-100 --disks "disk-10 core-100 boot=true, disk-11 core-100 10" --count 200

       # Delete vms matching a name prefix
       photon demo vms delete
       photon demo vms delete --tenant joe --project dev --name-prefix demo

       # Start vms matching a name prefix
       photon demo vms start
       photon demo vms start --tenant joe --project dev --name-prefix demo

       # Stop vms matching a name prefix
       photon demo vms stop
       photon demo vms stop --tenant joe --project dev --name-prefix demo

       # Configure number of threads to use for bulk creation (default: 100)
       photon demo threads set 200
       photon demo threads show
