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

module EsxCloud::Cli
  module Commands
    class Vms < Base

      usage "vm create [<options>]"
      desc "Create a new VM"
      def create(args = [])
        tenant_name, project_name, options = nil, nil, {}

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant_name = tn }
          opts.on("-p", "--project NAME", "Project name") { |pn| project_name = pn }
          opts.on("-n", "--name NAME", "VM name") { |n| options[:name] = n; puts options.inspect }
          opts.on("-f", "--flavor NAME", "VM flavor") { |f| options[:flavor] = f }
          opts.on("-i", "--image ID", "Image id") { |id| options[:image_id] = id }
          opts.on("-d", "--disks DISKS", "VM disks") { |d| options[:disks] = d.strip.split(/\s*,\s*/) }
          opts.on("-e", "--environment K1=V1[,KX=VX]*", "VM environment") { |e| options[:environment] = e.split(/\s*,\s*/) }
          opts.on("-a", "--affinities AFFINITIES", "VM Locality") { |a| options[:affinities] = a.split(/\s*,\s*/) }
          opts.on("-w", "--networks NETWORKS", "VM Networks") { |w| options[:networks] = w.split(/\s*,\s*/) }
        end

        parse_options args, opts_parser

        project = ensure_project tenant_name, project_name, opts_parser
        options = ensure_vm_options options, opts_parser

        vm = project.create_vm options
        puts green("VM '#{vm.id}' created")
      end

      usage "vm list [<options>]"
      desc "List VMs in a project"
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

        tenant ||= tenant_required
        project = project_name ? find_project_by_name(tenant, project_name) : project_required

        vms = client.find_all_vms(project.id).items

        render_vms(vms, summary_view)
      end

      usage "vm delete [<id>]"
      desc "Delete VM"
      def delete_vm(args = [])

        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide VM id")
        end

        if confirmed?
          client.delete_vm(id)
          puts green("Deleted VM '#{id}'")
        else
          puts yellow("OK, canceled")
        end
      end

      usage "vm start <id>"
      desc "Start VM"
      def start(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide VM id")
        end

        if confirmed?
          client.start_vm(id)
          puts green("VM is started")
        else
          puts yellow("OK, canceled")
        end
      end

      usage "vm stop <id>"
      desc "Stop VM"
      def stop(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide VM id")
        end

        if confirmed?
          client.stop_vm(id)
          puts green("VM is stopped")
        else
          puts yellow("OK, canceled")
        end
      end

      usage "vm restart <id>"
      desc "Restart VM"
      def restart(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide VM id")
        end

        if confirmed?
          client.restart_vm(id)
          puts green("VM is restarted")
        else
          puts yellow("OK, canceled")
        end
      end

      usage "vm suspend <id>"
      desc "Suspend VM"
      def suspend(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide VM id")
        end

        if confirmed?
          client.suspend_vm(id)
          puts green("VM is suspended")
        else
          puts yellow("OK, canceled")
        end
      end

      usage "vm resume <id>"
      desc "Resume VM"
      def resume(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide VM id")
        end

        if confirmed?
          client.resume_vm(id)
          puts green("VM is resumed")
        else
          puts yellow("OK, canceled")
        end
      end

      usage "vm attach_disk <id>"
      desc "Attach disk to VM"
      def attach_disk(args = [])
        perform_vm_disk_operation("attach_disk", args)
      end

      usage "vm detach_disk <id>"
      desc "Detach disk from VM"
      def detach_disk(args = [])
        perform_vm_disk_operation("detach_disk", args)
      end

      usage "vm attach_iso <id> [<options>]"
      desc "Attach ISO to VM"
      def attach_iso(args = [])
        path, name = nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-p", "--path PATH", "Iso path") { |pn| path = pn }
          opts.on("-n", "--name NAME", "Iso name") { |n| name = n }
        end

        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide VM id", opts_parser)
        end

        parse_options(args, opts_parser)

        if interactive?
          path ||= ask("Iso path: ")
        end

        if path.blank?
          usage_error("Please provide iso path")
        end

        path = File.expand_path(path, Dir.pwd)
        puts "Attaching Iso file #{path}"

        unless File.exist?(path)
          usage_error("No such iso file at that path")
        end

        if interactive?
          default_name = File.basename(path)
          name ||= ask("Iso name (default: #{default_name}):")
          name = default_name if name.empty?
        end

        vm = client.perform_vm_iso_attach(id, path, name)
        puts green("Iso attached to vm (ID=#{vm.id})")
      end

      usage "vm detach_iso <id>"
      desc "Detach iso from VM"
      def detach_iso(args = [])
        vm = client.perform_vm_iso_detach(shift_keyword_arg(args))
        puts green("Iso detached from vm (ID=#{vm.id})")
      end

      usage "vm tasks <id>"
      desc "Show VM tasks"
      def tasks(args = [])
        state = nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-s", "--state VALUE", "Task state") {|ts| state = ts }
        end

        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide VM id", opts_parser)
        end

        parse_options(args, opts_parser)

        render_task_list(client.get_vm_tasks(id, state).items)
      end

      usage "vm networks <id>"
      desc "Show VM networks"
      def networks(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide VM id")
        end

        vm_networks = client.get_vm_networks(id)
        render_network_connection_list(vm_networks.network_connections)
      end

      usage "vm mks_ticket <id>"
      desc "Get VM mks ticket"
      def mks_ticket(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide VM id") if id.blank?

        mks_ticket = client.get_vm_mks_ticket(id)
        puts green("VM '#{id}' mks ticket is '#{mks_ticket}'")
      end

      usage "vm set_metadata <id> [<options>]"
      desc "Set Vm Metadata"
      def set_metadata(args = [])
        payload = nil

        id = shift_keyword_arg(args)
        usage_error("Please provide VM id") if id.blank?

        opts_parser = OptionParser.new do |opts|
          opts.on("-m", "--metadata METADATA", "metadata") { |m| payload = parse_metadata(m.split(/\s*,\s*/)) }
        end
        parse_options(args, opts_parser)
        usage_error("Please provide metadata", opts_parser) if payload.blank?

        client.perform_vm_metadata_set(id, payload)
        puts green("VM '#{id}' metadata is set")
      end

      usage "vm create_image <id> [<options>]"
      desc "Create image by cloning vm"
      def create_image(args = [])
        id = shift_keyword_arg(args)
        usage_error("Please provide VM id") if id.blank?

        image_name, image_replication = nil, nil
        opts_parser = OptionParser.new do |opts|
          opts.on("-n", "--name Image Name", "image name") { |n| image_name = n }
          opts.on("-r", "--replication Image Replication Type", "Image replication type") { |r| image_replication = r }
        end
        parse_options(args, opts_parser)

        if interactive?
          image_name ||= ask("Image name: ")

          default_image_replication = "EAGER"
          image_replication ||= ask("Image replication type (default: #{default_image_replication}): ")
          image_replication = default_image_replication if image_replication.blank?
        end

        initialize_client
        image = EsxCloud::Image.create_from_vm(id, EsxCloud::ImageCreateSpec.new(image_name, image_replication))
        puts green("Image '#{image.id}' created")
      end

      usage "vm show <id>"
      desc "Show VM info"
      def show(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide VM id")
        end

        vm = client.find_vm_by_id(id)
        begin
          vm_networks = client.get_vm_networks(id)
        rescue EsxCloud::ApiError => e
          vm_network_error = "Unable to get network info for VM #{id}."
        end

        puts "VM #{id}:"
        puts "  Name:          #{vm.name}"
        puts "  Flavor:        #{vm.flavor}"
        puts "  State:         #{vm.state}"
        puts "  Source Image:  #{vm.source_image_id}"
        puts "  Host:          #{vm.host}"
        puts "  Datastore:     #{vm.datastore}"
        puts "  Disks:"
        if vm.disks.empty?
          puts "\t-"
        else
          vm.disks.each_with_index do |disk, i|
            puts if i > 0
            puts "\tDisk #{i+1}:"
            puts "\t  Name:     #{disk.name}"
            puts "\t  Id:       #{disk.id}" if ["persistent-disk", "persistent"].include? disk.kind
            puts "\t  Kind:     #{disk.kind}"
            puts "\t  Flavor:   #{disk.flavor}"
            puts "\t  Capacity: #{disk.capacity_gb}GB"
            puts "\t  Boot:     #{disk.boot_disk ? "yes" : "no"}"
          end
        end

        puts "  Networks:"
        if vm_networks.nil? || vm_networks.network_connections.empty?
          if vm_network_error.blank?
            puts "\t-"
          else
            puts yellow vm_network_error
          end
        else
          vm_networks.network_connections.each_with_index do |net_conn, i|
            puts if i > 0
            puts "\tNetwork #{i+1}:"
            puts "\t  Name:       #{net_conn.network}"
            puts "\t  Ip Address: #{net_conn.ip_address}"
          end
        end

        puts "  Attached Iso:"
        if vm.isos.empty?
          puts "\t-"
        else
          vm.isos.each_with_index do |iso, i|
            puts if i > 0
            puts "\t  Name:   #{iso.name}"
            unless iso.size.blank?
              puts "\t  Size:   #{iso.size}"
            end
          end
        end
      end

      usage "vm find_by_name <name>"
      desc "List VMs with specified name"
      def find_by_name(args = [])
        tenant, project_name, summary_view = nil, nil, false

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant = find_tenant_by_name(tn) }
          opts.on("-p", "--project NAME", "Project name") { |pn| project_name = pn }
          opts.on("-s", "--summary", "Summary view") { |sflag| summary_view = true }
        end

        name = shift_keyword_arg(args)
        if name.blank?
          usage_error("Please provide VM name", opts_parser)
        end

        parse_options(args, opts_parser)

        tenant ||= tenant_required
        project = project_name ? find_project_by_name(tenant, project_name) : project_required

        vms = client.find_vms_by_name(project.id, name).items

        render_vms(vms, summary_view)
      end

      private

      def perform_vm_disk_operation(operation, args)
        disk_id = nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-d", "--disk ID", "Disk id") { |d| disk_id = d }
        end

        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide VM id", opts_parser)
        end

        parse_options(args, opts_parser)

        if disk_id.nil?
          usage_error("Please provide --disk flag", opts_parser)
        end

        client.perform_vm_disk_operation(id, operation, disk_id)
        puts green("VM '#{operation}' operation done")
      end

      def parse_network_connections(network_connections)
        return [] unless network_connections.is_a?(Enumerable)

        network_connections.map do |network_connection|
          if network_connection.is_a?(EsxCloud::NetworkConnectionCreateSpec)
            network_connection
          else
            network, ip_address, netmask = network_connection.split(/\s+/, 3)

            EsxCloud::NetworkConnectionCreateSpec.new(network, ip_address, netmask)
          end
        end
      end

      def parse_metadata(metadata)
        return {} unless metadata.is_a?(Enumerable)
        parsed_metadata = {}

        metadata.each do |data|
          key, value = data.strip.split(/\s*=\s*/, 2)
          parsed_metadata[key] = value
        end
        {metadata: parsed_metadata}
      end

    end
  end
end
