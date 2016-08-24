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

require "time"

module EsxCloud::Cli
  module Commands
    module UI

      # @param [Array<EsxCloud::Task>] tasks
      def render_task_list(tasks)
        table = Terminal::Table.new
        table.headings = ["Task", "Start Time", "Duration"]
        tasks = tasks.sort_by { |t| t.start_time }

        tasks.each do |task|
          table.add_row(["#{task.id}\n#{task.operation}, #{task.state}",
                         format_time(task.start_time),
                         format_duration(task.duration)])
          table.add_separator unless task == tasks[-1]
        end

        puts
        puts table
        puts
        puts "You can run 'photon task show <id>' for more information"
        puts
        puts "Total: #{tasks.size}"
      end

      # @param [Boolean] summary_view
      # @param [Array<EsxCloud::Vm>] vms
      def render_vms(vms, summary_view = false)
        table = Terminal::Table.new
        table.headings = %w(ID State Name) unless summary_view
        summary = Hash.new(0)
        vms.each do |vm|
          table.add_row([vm.id, vm.state, vm.name]) unless summary_view
          table.add_separator unless vm == vms[-1] && !summary_view
          summary[vm.state] += 1
        end

        if !summary_view
          puts
          puts table
          puts
          puts "You can run 'photon vm show <id>' for more information"
          puts
        end
        puts "Total: #{vms.size}"
        summary.each do |key, count|
          puts "#{key}: #{count}"
        end
      end

      # @param [Boolean] summary_view
      # @param [Array<EsxCloud::Cluster>] clusters
      def render_clusters(clusters, summary_view = false)
        table = Terminal::Table.new
        table.headings = %w(ID Name Type State Size) unless summary_view
        summary = Hash.new(0)
        clusters.each do |cluster|
          table.add_row([cluster.id, cluster.name, cluster.type, cluster.state, cluster.worker_count]) unless summary_view
          table.add_separator unless cluster == clusters[-1] && !summary_view
          summary[cluster.name] += 1
        end

        if !summary_view
          puts
          puts table
          puts
          puts "You can run 'photon cluster show <id>' for more information"
          puts
        end
        puts "Total: #{clusters.size}"
        summary.each do |key, count|
          puts "#{key}: #{count}"
        end
      end

      # @param [EsxCloud::VmNetworks] network_connections
      def render_network_connection_list(network_connections)
        table = Terminal::Table.new
        table.headings = ["Network", "MAC Address", "IP Address", "Netmask", "IsConnected"]

        network_connections.each do |network_connection|
          table.add_row(["#{network_connection.network}", "#{network_connection.mac_address}",
                         "#{network_connection.ip_address}", "#{network_connection.netmask}",
                         "#{network_connection.is_connected}"])
          table.add_separator unless network_connection == network_connections[-1]
        end

        puts
        puts table
        puts
      end

      # @param [Array<EsxCloud::Network>]
      def render_networks(networks)
        table = Terminal::Table.new
        table.headings = ["ID", "Name", "Port Groups", "Description", "State", "Default"]

        networks.each do |network|
          table.add_row([network.id, network.name,
                         network.portgroups.join(", "), network.description, network.state, network.is_default])
          table.add_separator unless network == networks[-1]
        end

        puts
        puts table
        puts
      end

      # @param [Array<EsxCloud::Host>]
      def render_hosts(hosts)
        table = Terminal::Table.new
        table.headings = ["ID", "Usage Tags", "Address", "State"]

        hosts.each do |host|
          table.add_row([host.id, host.usage_tags.join(", "),
                         host.address, host.state])
          table.add_separator unless host == hosts[-1]
        end

        puts
        puts table
        puts
        puts green("#{hosts.length} hosts totally")
      end

      # @param [Array<EsxCloud::Deployment>]
      def render_deployments(deployments)
        table = Terminal::Table.new
        table.headings = ["ID"]

        deployments.each do |deployment|
          table.add_row(["#{deployment.id}"])
        end

        puts
        puts table
        puts
        puts green("#{deployments.length} deployments totally")
      end

      # @param [Array<[EsxCloud::VM, String]>] data
      def render_deployment_summary(data)
        render_deployment_jobs data
        puts
        render_deployment_vms data
      end

      # @param [Array<EsxCloud::Cluster>] clusters
      def render_cluster_list(clusters)
        table = Terminal::Table.new
        table.headings = ["Cluster", "Type", "Master Count", "Worker Count"]
        clusters = clusters.sort_by { |c| c.name }

        clusters.each do |cluster|
          table.add_row([
            "#{cluster.id}\n#{cluster.name}",
            cluster.type,
            cluster.master_count,
            cluster.worker_count])
          table.add_separator unless cluster == clusters[-1]
        end

        puts
        puts table
        puts
        puts "You can run 'photon cluster show <id>' for more information"
        puts
        puts "Total: #{clusters.size}"
      end

      private

      # @param [Array<[EsxCloud::VM, String]>] data
      def render_deployment_jobs(data)
        table = Terminal::Table.new
        table.headings = ["Job", "VM IP(s)", "Ports"]

        deployment_info = {}
        data.each do |vm, ips|
          vm.metadata.each do |key, value|
            next if not key.start_with? "CONTAINER_"
            deployment_info[value] ||= {port: [], vm_ips: []}
            deployment_info[value][:port] << to_port(key)
            deployment_info[value][:vm_ips] << ips
          end
        end

        deployment_info.sort_by { |k, _v| k }.each do |job, info|
          table.add_row([job, info[:vm_ips].uniq.sort.join(", "), info[:port].uniq.sort.join(", ")])
        end

        puts table
      end

      # @param [Array<[EsxCloud::VM, String]>] data
      def render_deployment_vms(data)
        table = Terminal::Table.new
        table.headings = ["VM IP", "Host IP", "VM ID", "VM Name"]

        data.sort_by { |r| r[1] }.each do |vm, ips|
          table.add_row([ips, vm.host, vm.id, vm.name])
        end

        puts table
      end

      # @param [Array<[EsxCloud::VM, String]>] data
      def render_cluster_vms(data)
        table = Terminal::Table.new
        table.headings = ["VM Name", "VM ID", "VM IP"]

        data.sort_by { |r| r[0].name }.each do |vm, ips|
          table.add_row([vm.name, vm.id, ips])
        end

        puts table
      end

      # @param [Numeric] time_ms Time in milliseconds
      def format_time(time_ms)
        return "N/A" if time_ms.nil?
        Time.at(time_ms.to_i / 1000).strftime("%Y-%m-%d %H:%M:%S")
      end

      # @param [Numeric] duration_ms Duration in milliseconds
      def format_duration(duration_ms)
        return "N/A" if duration_ms.nil?
        ts = duration_ms.to_i / 1000
        sprintf("%02d:%02d:%02d.%02d", ts / 3600, (ts / 60) % 60, ts % 60, duration_ms % 1000)
      end

      # Extract port from Vm metadata key
      def to_port(container_port)
        container_port.sub("CONTAINER_", "")
      end

    end
  end
end
