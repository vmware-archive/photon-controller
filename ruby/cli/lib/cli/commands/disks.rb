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
    class Disks < Base

      usage "disk create [<options>]"
      desc "Create a new DISK"
      def create(args = [])
        tenant, project_name, project, name, affinities, tags = nil, nil, nil, nil, nil, nil
        flavor, capacity_gb, kind = nil, nil, "persistent-disk"
        env = {}

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant = find_tenant_by_name(tn) }
          opts.on("-p", "--project NAME", "Project name") { |pn| project_name = pn }
          opts.on("-n", "--name NAME", "DISK name") { |n| name = n }
          opts.on("-f", "--flavor NAME", "DISK flavor") { |f| flavor = f }
          opts.on("-g", "--capacityGB N", "DISK capacity") { |cap| capacity_gb = cap.to_i }
          opts.on("-a", "--affinities AFFINITIES", "Disk Locality") { |a| affinities = a.split(/\s*,\s*/) }
          opts.on("-s", "--tags TAGS", "Disk tags") { |tg| tags = tg.split(/\s*,\s*/) }
        end

        parse_options(args, opts_parser)

        # If tenant has been provided via command line we need to make sure
        # that project is not looked up in config file.
        if tenant && project_name.nil?
          usage_error("Please provide --project flag along with --tenant flag", opts_parser)
        end

        if interactive?
          tenant ||= tenant_required
          project = project_name ? find_project_by_name(tenant, project_name) : project_required
          name ||= ask("DISK name: ")
          # TODO(olegs): hint flavors
          flavor ||= ask("DISK flavor: ")
          capacity_gb ||= ask("DISK capacity in GB: ");
        else
          project = find_project_by_name(tenant, project_name) if tenant && project_name
        end

        if tenant.blank? || project.blank? || name.blank? || flavor.blank? || capacity_gb.blank?
          usage_error("Please provide tenant, project, name, flavor and capacity", opts_parser)
        end

        puts
        puts "Tenant: #{tenant.name}, project: #{project.name}"
        puts "Creating a DISK: #{name} (#{flavor})"

        affinities = affinities ? parse_affinities(affinities) : []

        if confirmed?
          disk = project.create_disk(:name => name, :flavor => flavor, :kind => kind,
                              :capacity_gb => capacity_gb, :affinities => affinities, :tags => tags)
          puts green("DISK '#{disk.id}' created")
        else
          puts yellow("OK, canceled")
        end
      end

      usage "disk list [<options>]"
      desc "List DISKs in a project"
      def list(args = [])
        tenant, project_name, summary_view = nil, nil, false

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant = find_tenant_by_name(tn) }
          opts.on("-p", "--project NAME", "Project name") { |pn| project_name = pn }
          opts.on("-s", "--summary", "Summary view") { |sflag| summary_view = true }
        end

        parse_options(args, opts_parser)

        if tenant && project_name.nil?
          usage_error("Please provide --project flag along with --tenant flag", opts_parser)
        end

        tenant ||= tenant_required
        project = project_name ? find_project_by_name(tenant, project_name) : project_required

        disks = client.find_all_disks(project.id).items

        display_disks_in_table(summary_view, disks)
      end

      usage "disk delete [<id>]"
      desc "Delete DISK"
      def delete_disk(args = [])

        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide DISK id")
        end

        if confirmed?
          client.delete_disk(id)
          puts green("Deleted DISK '#{id}'")
        else
          puts yellow("OK, canceled")
        end
      end

      usage "disk tasks <id>"
      desc "Show DISK tasks"
      def tasks(args = [])
        state = nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-s", "--state VALUE", "Task state") {|ts| state = ts }
        end

        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide DISK id", opts_parser)
        end

        parse_options(args, opts_parser)

        render_task_list(client.get_disk_tasks(id, state).items)
      end

      usage "disk show <id>"
      desc "Show DISK info"
      def show(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide DISK id")
        end


        disk = client.find_disk_by_id(id)

        puts "DISK #{id}:"
        puts "  Name:   #{disk.name}"
        puts "  Kind: #{disk.kind}"
        puts "  Flavor: #{disk.flavor}"
        puts "  Capacity:  #{disk.capacity_gb} GB"
        puts "  State:   #{disk.state}"
        puts "  Datastore:   #{disk.datastore}"
        disk.vms.each do |vm|
          puts "  Attached to VM: #{vm}"
        end
        puts
      end

      usage "disk find_by_name <name>"
      desc "List DISKs with specified name"
      def find_by_name(args = [])
        tenant, project_name, summary_view = nil, nil, false

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant = find_tenant_by_name(tn) }
          opts.on("-p", "--project NAME", "Project name") { |pn| project_name = pn }
          opts.on("-s", "--summary", "Summary view") { |sflag| summary_view = true }
        end

        name = shift_keyword_arg(args)
        if name.blank?
          usage_error("Please provide DISK name", opts_parser)
        end

        parse_options(args, opts_parser)

        tenant ||= tenant_required
        project = project_name ? find_project_by_name(tenant, project_name) : project_required

        disks = client.find_disks_by_name(project.id, name).items

        display_disks_in_table(summary_view, disks)
      end

      private

      def display_disks_in_table(summary_view, disks)
        table = Terminal::Table.new
        table.headings = %w(ID State Name) unless summary_view
        summary = Hash.new(0)
        disks.each do |disk|
          table.add_row([disk.id, disk.state, disk.name]) unless summary_view
          table.add_separator unless disk == disks[-1] && !summary_view
          summary[disk.state] += 1
        end

        if !summary_view
          puts
          puts table
          puts
        end
        puts "Total: #{disks.size}"
        summary.each do |key, count|
          puts "#{key}: #{count}"
        end
      end
    end
  end
end
