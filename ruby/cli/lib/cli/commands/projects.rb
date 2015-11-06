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
    class Projects < Base

      usage "project create [<options>]"
      desc "Create a new project"
      def create(args = [])
        tenant, ticket_name, name, limits, percent, security_groups  = nil, nil, nil, nil, nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant = find_tenant_by_name(tn) }
          opts.on("-r", "--resource-ticket NAME", "Resource ticket name") { |rtn| ticket_name = rtn }
          opts.on("-n", "--name NAME", "Project name") { |n| name = n }
          opts.on("-l", "--limits LIMITS", "Project limits") { |l| limits = l.split(/\s*,\s*/) }
          opts.on("-p", "--percent PERCENT", Float, "Allocate percentage of a resource ticket") { |p| percent = p }
          opts.on("-g", "--security_groups SECURITY_GROUPS", "Comma-separated ") { |g| security_groups = g}
        end

        parse_options(args, opts_parser)

        if limits && percent
          err("Can only specify one of '--limits' or '--percent'")
        end

        if percent
          limits = [EsxCloud::QuotaLineItem.new("subdivide.percent", percent, "COUNT")]
        end

        if interactive?
          tenant ||= tenant_required
          ticket_name ||= ask("Resource ticket name: ")
          name ||= ask("Project name: ")

          limits = limits ? parse_limits(limits) : ask_for_limits
        else
          # TODO(olegs): provide a hint on which keys are available in the resource ticket
          limits = parse_limits(limits)
        end

        if tenant.blank? || ticket_name.blank? || name.blank? || limits.blank?
          usage_error("Please provide tenant, resource ticket, name and limits", opts_parser)
        end

        puts
        puts "Tenant: #{tenant.name}, resource ticket: #{ticket_name}"
        puts "Creating project: #{name}"

        if interactive?
          puts
          puts "Please make sure limits below are correct:"
          puts

          limits.each_with_index do |qli, i|
            puts "#{i + 1}: #{qli.key}, #{qli.value}, #{qli.unit}"
          end
          puts
        end

        if confirmed?
          security_groups_array = security_groups.split(/\s*,\s*/) unless security_groups.nil?
          tenant.create_project(:name => name,
                                :resource_ticket_name => ticket_name,
                                :limits => limits,
                                :security_groups => security_groups_array)
          puts green("Project created")
        else
          puts yellow("OK, canceled")
        end
      end

      usage "project set <name>"
      desc "Select project to work with"
      def select(args = [])
        tenant = tenant_required
        name = shift_keyword_arg(args)
        parse_options(args)

        if name.nil?
          usage_error("Please provide project name")
        end

        projects = client.find_projects_by_name(tenant.id, name)
        if projects.items.empty?
          err("Project '#{name}' not found")
        end

        cli_config.project = projects.items[0]
        cli_config.save

        puts green("Project set to '#{name}'")
      end

      usage "project show"
      desc "Show current project"
      def show_current(args = [])
        parse_options(args)
        project = cli_config.project

        if project
          puts "Current project is '#{project.name}' (#{project.id})"
        else
          puts "No project selected"
        end
      end

      usage "project list [<options>]"
      desc "List projects for a current tenant"
      def list(args = [])
        tenant = nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant = find_tenant_by_name(tn) }
        end

        parse_options(args, opts_parser)
        tenant ||= tenant_required

        projects = client.find_all_projects(tenant.id).items

        table = Terminal::Table.new
        table.headings = ["Name", "Limits", "Usage"]
        projects.each do |p|
          table.add_row(format_project(p))
          table.add_separator unless p == projects[-1]
        end

        puts
        puts table
        puts
        puts "Total: #{projects.size}"
      end

      usage "project delete <name> [<options>]"
      desc "Delete project"
      def delete(args = [])
        tenant, name, batch = get_common_options(args)
        project = find_project_by_name(tenant, name)

        confirm
        client.delete_project(project.id)
        puts green("Deleted project '#{name}'")
      end

      usage "project tasks <name> [<options>]"
      desc "Show project tasks"
      def tasks(args = [])
        tenant, state, kind = nil, nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant = find_tenant_by_name(tn) }
          opts.on("-s", "--state VALUE", "Task state") {|ts| state = ts }
          opts.on("-k", "--kind VALUE", "Task kind") {|tk| kind = tk}
        end

        name = shift_keyword_arg(args)
        if name.nil?
          usage_error("Please provide project name", opts_parser)
        end

        parse_options(args, opts_parser)
        tenant ||= tenant_required
        project = find_project_by_name(tenant, name)

        render_task_list(client.get_project_tasks(project.id, state, kind).items)
      end

      usage "project purge <name> [<options>]"
      desc "Delete project along with its VMs"
      def purge(args = [])
        tenant, batch = nil, 100

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant = find_tenant_by_name(tn) }
          opts.on("-b", "--batch N", "Issue N simultaneous requests") {
            |b| batch = b.to_i
          }
        end

        name = shift_keyword_arg(args)
        if name.nil?
          usage_error("Please provide project name", opts_parser)
        end

        parse_options(args, opts_parser)
        tenant ||= tenant_required

        project = find_project_by_name(tenant, name)

        vms = client.find_all_vms(project.id).items
        puts "Found #{vms.size} VM#{vms.size == 1 ? '' : 's'}"
        confirm
        delete_vms_in_parallel(vms, batch)

        puts "Deleting project '#{project.name}'"
        project.delete
      end

      usage "project start <name>"
      desc "Start all stopped VMs in the project"
      def start(args = [])
        tenant, name, batch = get_common_options(args)
        project = find_project_by_name(tenant, name)
        vms = client.find_all_vms(project.id).items
        if vms.empty?
          puts "No VMs in this project"
        else
          confirm
          start_vms_in_parallel(vms, batch)
        end
      end

      usage "project stop <name>"
      desc "Stop all started VMs in the project"
      def stop(args = [])
        tenant, name, batch = get_common_options(args)
        project = find_project_by_name(tenant, name)
        vms = client.find_all_vms(project.id).items
        if vms.empty?
          puts "No VMs in this project"
        else
          confirm
          stop_vms_in_parallel(vms, batch)
        end
      end

      usage "project set_security_groups <project_name> [options]"
      desc "Set the security groups of the project"
      def set_security_groups(args = [])
        tenant_name, security_groups = nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn|
            tenant_name = tn
          }
          opts.on("-g", "--security_groups Security Groups", "Comma-separated list of security groups") { |sg|
            security_groups = sg
          }
        end

        project_name = shift_keyword_arg(args)
        parse_options(args, opts_parser)

        # If interactive is enabled, and no data can be found, then prompt for it
        if interactive?
          project_name ||= ask("Project name: ")
          tenant_name ||= ask("Tenant name: ")
          security_groups ||= ask("Security groups (comma-separated, e.g, sg1,sg2): ")
        end

        if project_name.nil?
          usage_error("Please provide project name", opts_parser)
        end
        if tenant_name.nil?
          usage_error("Please provide tenant name", opts_parser)
        end
        if security_groups.nil?
          usage_error("Please provide comma-separated list of security groups", opts_parser)
        end

        tenant = find_tenant_by_name(tenant_name)
        project = find_project_by_name(tenant, project_name)

        security_groups_in_hash = {items: security_groups.split(/\s*,\s*/)}
        client.set_project_security_groups(project.id, security_groups_in_hash)

        puts green("Security groups of project '#{project.id}' has been changed to #{security_groups}")
      end

      usage "project get_security_groups <name>"
      desc "Show project security groups"
      def get_security_groups(args = [])
        tenant_name = nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn|
            tenant_name = tn
          }
        end

        project_name = shift_keyword_arg(args)
        parse_options(args, opts_parser)

        # If interactive is enabled, and no data can be found, then prompt for it
        if interactive?
          project_name ||= ask("Project name: ")
          tenant_name ||= ask("Tenant name: ")
        end

        if project_name.nil?
          usage_error("Please provide project name", opts_parser)
        end
        if tenant_name.nil?
          usage_error("Please provide tenant name", opts_parser)
        end

        tenant = find_tenant_by_name(tenant_name)
        project = find_project_by_name(tenant, project_name)

        puts green("Security groups of project '#{project.id}' are:")
        project.security_groups.each do |sg|
          source = sg["inherited"]? "inherited" : "self"
          puts green("#{sg["name"]}(#{source})")
        end
      end

      private

      def get_common_options(args)
        tenant, batch = nil, 100

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant = find_tenant_by_name(tn) }
          opts.on("-b", "--batch N", "Issue N simultaneous requests") {
            |b| batch = b.to_i
          }
        end

        name = shift_keyword_arg(args)
        if name.nil?
          usage_error("Please provide project name", opts_parser)
        end

        parse_options(args, opts_parser)
        [tenant || tenant_required, name, batch]
      end

      def format_project(project)
        if project.resource_ticket.nil?
          return [project.name, "n/a"]
        end

        limits = project.resource_ticket.limits.map {|qli| "#{qli.key}, #{qli.value}, #{qli.unit}"}
        usages = project.resource_ticket.usage.map {|qli| "#{qli.key}, #{qli.value}, #{qli.unit}"}
        [project.name, limits.join("\n"), usages.join("\n")]
      end
    end
  end
end
