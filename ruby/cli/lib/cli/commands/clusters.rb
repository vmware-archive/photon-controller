# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED. See the License for the
# specific language governing permissions and limitations under the License.

require "yaml"

module EsxCloud::Cli
  module Commands
    class Clusters < Base

      usage "cluster create [<options>]"
      desc "Create a new cluster"
      def create(args = [])
        tenant, project_name, name, type = nil, nil, nil, nil
        vm_flavor, disk_flavor, network_id, worker_count, batch_size = nil, nil, nil, nil, nil
        dns, gateway, netmask = nil, nil, nil
        master_ip, container_network = nil, nil
        zookeeper1, zookeeper2, zookeeper3 = nil, nil, nil
        etcd1, etcd2, etcd3 = nil, nil, nil
        wait_for_ready = false

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant TENANT", "Tenant name") { |v| tenant = find_tenant_by_name(v) }
          opts.on("-p", "--project PROJECT", "Project name") { |v| project_name = v }
          opts.on("-n", "--name NAME", "Cluster name") { |v| name = v }
          opts.on("-k", "--type TYPE",
                  "Cluster type. Accepted values are KUBERNETES, MESOS, or SWARM.") { | v | type = v }
          opts.on("-v", "--vm_flavor VM_FLAVOR", "VM flavor name") { |v| vm_flavor = v }
          opts.on("-d", "--disk_flavor DISK_FLAVOR", "Disk flavor name") { |v| disk_flavor = v }
          opts.on("-w", "--network_id NETWORK_ID", "VM network ID") { |v| network_id = v }
          opts.on("-s", "--worker_count COUNT", "Worker count") { |v| worker_count = v }
          opts.on("--dns DNS_SERVER", "DNS server.") { |v| dns = v }
          opts.on("--gateway GATEWAY_SERVER", "Gateway server.") { |v| gateway = v }
          opts.on("--netmask NETMASK", "netmask.") { |v| netmask = v }
          opts.on("--master-ip MASTER_IP",
                  "Master ip. Required for Kubernetes cluster.") { |v| master_ip = v }
          opts.on("--container-network CONTAINER_NETWORK",
                  "Container network info, e.g. 10.2.0.0/16. Required for Kubernetes cluster.") { |v| container_network = v }
          opts.on("--zookeeper1 ZOOKEEPER_IP1",
                  "Zookeeper server 1. Required for Mesos cluster.") { |v| zookeeper1 = v }
          opts.on("--zookeeper2 ZOOKEEPER_IP2",
                  "Zookeeper server 2. Optional for Mesos cluster.") { |v| zookeeper2 = v }
          opts.on("--zookeeper3 ZOOKEEPER_IP3",
                  "Zookeeper server 3. Optional for Mesos cluster.") { |v| zookeeper3 = v }
          opts.on("--etcd1 ETCD_IP1",
                  "Etcd server 1. Required for Kubernetes and Swarm cluster.") { |v| etcd1 = v }
          opts.on("--etcd2 ETCD_IP2",
                  "Etcd server 2. Optional for Kubernetes and Swarm cluster.") { |v| etcd2 = v }
          opts.on("--etcd3 ETCD_IP3",
                  "Etcd server 3. Optional for Kubernetes and Swarm cluster.") { |v| etcd3 = v }
          opts.on("--batch-size BATCH_SIZE",
                  "Size of the batch used during worker expansion process.") { |v| batch_size = v }
          opts.on("--wait-for-ready", "Waits for the cluster to become ready.") { |v| wait_for_ready = true }
        end

        parse_options(args, opts_parser)

        default_worker_count = 1
        default_batch_size = 0
        default_container_network = "10.2.0.0/16"

        # If tenant has been provided via command line we need to make sure
        # that project is not looked up in config file.
        if tenant && project_name.nil?
          usage_error("Please provide --project flag along with --tenant flag", opts_parser)
        end
        initialize_client

        if interactive?
          tenant ||= tenant_required
          project = project_name ? find_project_by_name(tenant, project_name) : project_required

          name ||= ask("Cluster Name: ")
          type ||= ask("Cluster Type: ")

          worker_count ||= ask("Worker Nodes Count (default: #{default_worker_count}): ")
          worker_count = default_worker_count if worker_count.empty?
        else
          project = find_project_by_name(tenant, project_name) if tenant && project_name
        end

        if tenant.blank? || project.blank? || name.blank? || type.blank?
          usage_error("Please provide tenant, project, name, and type", opts_parser)
        end

        if interactive?
          dns        ||= ask("DNS: ")
          gateway    ||= ask("Gateway: ")
          netmask    ||= ask("Netmask: ")
        end
        if dns.blank? || gateway.blank? || netmask.blank?
          usage_error("Please provide dns, gateway, and netmask", opts_parser)
        end
        extended_properties = Hash.new
        extended_properties["dns"]     = dns
        extended_properties["gateway"] = gateway
        extended_properties["netmask"] = netmask

        type = type.upcase
        if type === "KUBERNETES"
          if interactive?
            etcd1 ||= ask("Kubernetes Etcd IP 1: ")
            etcd2 ||= ask("Kubernetes Etcd IP 2: ")
            if !etcd2.blank?
              etcd3 ||= ask("Kubernetes Etcd IP 3: ")
            end
            master_ip ||= ask("Kubernetes Master IP: ")
            container_network ||= ask("Kubernetes Container network (e.g.: #{default_container_network}): ")
            container_network = default_container_network if container_network.empty?
          end
          extended_properties["etcd_ip1"] = etcd1
          if !etcd2.blank?
            extended_properties["etcd_ip2"] = etcd2
            if !etcd3.blank?
              extended_properties["etcd_ip3"] = etcd3
            end
          end
          extended_properties["master_ip"] = master_ip
          extended_properties["container_network"] = container_network
        elsif type === "MESOS"
          if interactive?
            zookeeper1 ||= ask("Mesos Zookeeper IP 1: ")
            zookeeper2 ||= ask("Mesos Zookeeper IP 2: ")
            if !zookeeper2.blank?
              zookeeper3 ||= ask("Mesos Zookeeper IP 3: ")
            end
          end
          extended_properties["zookeeper_ip1"] = zookeeper1
          if !zookeeper2.blank?
            extended_properties["zookeeper_ip2"] = zookeeper2
            if !zookeeper3.blank?
              extended_properties["zookeeper_ip3"] = zookeeper3
            end
          end
        elsif type === "SWARM"
          if interactive?
            etcd1 ||= ask("Swarm Etcd IP 1: ")
            etcd2 ||= ask("Swarm Etcd IP 2: ")
            if !etcd2.blank?
              etcd3 ||= ask("Swarm Etcd IP 3: ")
            end
          end
          extended_properties["etcd_ip1"] = etcd1
          if !etcd2.blank?
            extended_properties["etcd_ip2"] = etcd2
            if !etcd3.blank?
              extended_properties["etcd_ip3"] = etcd3
            end
          end
        else
          usage_error("Unsupported cluster type: #{type}", opts_parser)
        end

        batch_size = default_batch_size if batch_size.nil?

        puts
        puts "Tenant: #{tenant.name}, project: #{project.name}"
        puts "Creating #{type} cluster: '#{name}'"
        puts

        if confirmed?
          cluster = project.create_cluster(
            name: name,
            type: type,
            vm_flavor: vm_flavor,
            disk_flavor: disk_flavor,
            network_id: network_id,
            worker_count: worker_count,
            batch_size: batch_size,
            extended_properties: extended_properties)

          puts green("Cluster '#{cluster.id}' created")
          puts "  Name: #{cluster.name}"
          puts "  Type: #{cluster.type.upcase}"
          puts "  Number of Worker Nodes: #{cluster.worker_count}"
          puts

          if (wait_for_ready)
            wait_for_cluster_state(cluster.id, "READY")
            puts green("Cluster '#{cluster.id}' is ready now")
          else
            puts "Note: cluster is up with minimum resources. You may start to use the cluster now."
            puts "A background task is running to gradually expand the cluster to its target capacity."
            puts "You may run 'cluster show #{cluster.id}' to see the state of the cluster."
            puts "If the creation is still in progress, the cluster state will show as CREATING. Once"
            puts "the cluster is fully created, the cluster state will show as READY."
          end
        else
          puts yellow("OK, Create cluster job canceled")
        end
      end

      usage "cluster show <id>"
      desc "Show Cluster info"
      def show(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide a Cluster id")
        end
        initialize_client

        cluster = client.find_cluster_by_id(id)
        puts
        puts green("Cluster #{cluster.id}:")
        puts "  Name: #{cluster.name}"
        puts "  Type: #{cluster.type.upcase}"
        puts "  State: #{cluster.state.upcase}"
        puts "  Number of Worker Nodes: #{cluster.worker_count}"
        puts

        puts "Querying Cluster VMs ..."
        client.task_tracker = nil
        vms = client.get_cluster_vms(id).items
        vms_detail = Array.new
        vms.map do |vm|
          vm.tags.map do |tag|
            if (tag.count(':') == 2) && (!tag.downcase.include? "worker")
              connections = client.get_vm_networks(vm.id).network_connections.select { |n| !n.network.blank? }
              vms_detail << [vm, connections.first.ip_address]
              break
            end
          end
        end
        render_cluster_vms(vms_detail)
      end

      usage "cluster list [<options>]"
      desc "List Clusters in a project"
      def list(args = [])
        tenant, project_name, summary_view = nil, nil, false

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant = find_tenant_by_name(tn) }
          opts.on("-p", "--project NAME", "Project name") { |pn| project_name = pn }
          opts.on("-s", "--summary", "Summary view") { |_sflag| summary_view = true }
        end
        parse_options(args, opts_parser)

        if tenant && project_name.nil?
          usage_error("Please provide --project flag along with --tenant flag", opts_parser)
        end
        initialize_client

        tenant ||= tenant_required
        project = project_name ? find_project_by_name(tenant, project_name) : project_required

        clusters = client.get_project_clusters(project.id).items
        render_clusters(clusters, summary_view)
      end

      usage "cluster list_vms <id>"
      desc "Show all the vms in the cluster"
      def list_vms(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide cluster id")
        end
        initialize_client

        render_vms(client.get_cluster_vms(id).items)
      end

      usage "cluster resize <id> <new_worker_count> <options>"
      desc "Resize the cluster"
      def resize(args = [])
        wait_for_ready = false

        opts_parser = OptionParser.new do |opts|
          opts.on("--wait-for-ready", "Waits for the cluster to become ready.") { |v| wait_for_ready = true }
        end

        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide a cluster id")
        end
        new_worker_count = shift_keyword_arg(args)
        if new_worker_count.blank?
          usage_error("Please provide new worker count.")
        end
        parse_options(args, opts_parser)

        initialize_client

        confirm
        client.resize_cluster(id, new_worker_count)
        puts green("Cluster '#{id}' resized.")

        if (wait_for_ready)
          wait_for_cluster_state(id, "READY")
          puts green("Cluster '#{id}' is ready now")
        else
          puts "Note: a background task is running to gradually to resize the cluster to its target capacity."
          puts "You may continue to use the cluster. You may run 'cluster show #{id}'"
          puts "to see the state of the cluster. If the resizing is still in progress, the cluster state"
          puts "will show as RESIZING. Once the cluster is fully resized, the cluster state will show as READY."
        end
      end

      usage "cluster delete <id>"
      desc "Delete the cluster"
      def delete(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide a Cluster id")
        end
        initialize_client

        confirm
        client.delete_cluster(id)
        puts green("Cluster '#{id}' deleted.")
      end

      private

      def wait_for_cluster_state(cluster_id, target_cluster_state, timeout_s = 3600, poll_interval_s = 2)

        deadline = Time.now + timeout_s
        animate_thread = nil
        formatador = Formatador.new

        puts "Waiting for cluster #{cluster_id} to become #{target_cluster_state}"

        while true
          if Time.now > deadline
            fail "Timed out while waiting for cluster #{cluster_id} to become #{target_cluster_state}"
          end

          cluster = client.find_cluster_by_id(cluster_id)

          if cluster.state == target_cluster_state
            cluster = nil
            animate_thread.join unless animate_thread.nil?
            animate_thread = nil
            return
          end

          animate_thread ||= Thread.new do
            formatador.display_line
            iteration = 0
            loop do
              break if cluster.nil?
              cursor = "|#{' ' * (iteration % 3)}=#{' ' * (2 - iteration % 3)}|"
              formatador.redisplay("[green]#{cursor} [/]: Current cluster state is #{cluster.state}")
              iteration += 1
              sleep 0.5
            end
            formatador.redisplay("\r")
          end

          sleep([poll_interval_s, [deadline - Time.now, 0].max].min)
        end
      end
    end
  end
end
