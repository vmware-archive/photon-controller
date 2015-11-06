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
    class Tenants < Base

      usage "tenant create <name> [options]"
      desc "Create a new tenant"
      def create(args = [])
        options = parse_tenant_creation_arguments(args)

        options = read_tenant_arguments_interactively(options)

        validate_tenant_arguments(options)

        if confirmed?
          initialize_client
          security_groups =
              options[:security_groups].split(/\s*,\s*/) unless options[:security_groups].nil?
          spec = EsxCloud::TenantCreateSpec.new(
                 options[:name],
                 security_groups
          )
          tenant = EsxCloud::Tenant.create(spec)
          puts green("Tenant '#{tenant.name}' created (ID=#{tenant.id})")
        else
          puts yellow("OK, canceled")
        end
      end

      usage "tenant delete <name>"
      desc "Delete a tenant"
      def delete(args = [])
        name = shift_keyword_arg(args)
        parse_options(args)

        if name.blank?
          usage_error("Please provide tenant name")
        end

        client.delete_tenant_by_name(name)
        puts green("Tenant '#{name}' has been deleted")
      end

      usage "tenant set <name>"
      desc "Select tenant to work with"
      def select(args = [])
        name = shift_keyword_arg(args)

        if name.blank?
          usage_error("Please provide tenant name")
        end

        tenant = client.find_tenants_by_name(name)
        if tenant.items.empty?
          err("Tenant '#{name}' not found")
        end

        cli_config.tenant = tenant.items[0]
        cli_config.project = nil
        cli_config.save

        puts green("Tenant set to '#{name}'")
      end

      usage "tenant show"
      desc "Show current tenant"
      def show_current(args = [])
        parse_options(args)
        tenant = cli_config.tenant
        vm_limit = 0
        vm_count = 0
        vm_cpu = 0
        vm_memory = 0

        projects = client.find_all_projects(tenant.id).items
        projects.each do |p|
          p.resource_ticket.limits.each do |l|
            if l.key == "vm"
              vm_limit += l.value
              break
            end
          end
          p.resource_ticket.usage.each do |u|
            case u.key
              when "vm.cost"
                vm_count += u.value
              when "vm.cpu"
                vm_cpu += u.value
              when "vm.memory"
                vm_memory += u.value
            end
          end
        end

        if tenant
          puts "Current tenant is '#{tenant.name}' (#{tenant.id})"
          puts "  Limits: "
          puts "    vm: #{vm_limit} COUNT"
          puts "  Usage:"
          puts "    vm: #{vm_count} COUNT"
          puts "    vm cpu: #{vm_cpu} COUNT"
          puts "    vm memory: #{vm_memory} GB"
        else
          puts "No tenant selected"
        end
      end

      usage "tenant list"
      desc "List tenants"
      def list(args = [])
        parse_options(args)
        tenants = client.find_all_tenants.items

        table = Terminal::Table.new
        table.headings = %w(Name)
        table.rows = tenants.map { |t| [t.name] }

        puts
        puts table
        puts
        puts "Total: #{tenants.size}"
      end

      usage "tenant purge <name> [<options>]"
      desc "Delete tenant along with its projects and VMs"
      def purge(args = [])

        name = shift_keyword_arg(args)
        parse_options(args, opts_parser)

        tenants = client.find_tenants_by_name(name)
        if tenants.items.empty?
          err("Tenant '#{name}' not found")
        end

        tenant = tenants.items[0]
        projects = client.find_all_projects(tenant.id).items

        puts
        puts "Found #{projects.size} project#{projects.size == 1 ? '' : 's'}"
        vms = projects.map {|p| client.find_all_vms(p.id).items }.flatten
        puts "Found #{vms.size} VM#{vms.size == 1 ? '' : 's'}"

        unless confirmed?
          puts yellow("OK, canceled")
          exit(0)
        end

        delete_vms_in_parallel(vms)

        projects.each do |project|
          puts "Deleting project '#{project.name}'"
          project.delete
        end

        puts "Deleting tenant '#{tenant.name}'"
        tenant.delete
      end

      usage "tenant tasks <name>"
      desc "Show tenant tasks"
      def tasks(args = [])
        state = nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-s", "--state VALUE", "Task state") {|ts| state = ts }
        end

        name = shift_keyword_arg(args)
        if name.nil?
          usage_error("Please provide tenant name", opts_parser)
        end

        parse_options(args, opts_parser)
        tenant = find_tenant_by_name(name)

        render_task_list(client.get_tenant_tasks(tenant.id, state).items)
      end

      usage "tenant set_security_groups <tenant_name> [options]"
      desc "Set the security groups of the tenant"
      def set_security_groups(args = [])
        tenant_name, security_groups = nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-g", "--security_groups Security Groups", "Comma-separated list of security groups") { |sg|
            security_groups = sg
          }
        end

        tenant_name = shift_keyword_arg(args)
        parse_options(args, opts_parser)

        if interactive?
          tenant_name ||= ask("Tenant name: ")
          security_groups ||= ask("Security groups (comma-separated, e.g, sg1,sg2): ")
        end

        if tenant_name.nil?
          usage_error("Please provide tenant name", opts_parser)
        end
        if security_groups.nil?
          usage_error("Please provide comma-separated list of security groups", opts_parser)
        end

        tenant = find_tenant_by_name(tenant_name)
        security_groups_in_hash = {items: security_groups.split(/\s*,\s*/)}

        client.set_tenant_security_groups(tenant.id, security_groups_in_hash)

        puts green("Security groups of tenant '#{tenant.id}' has been changed to #{security_groups}")
      end

      usage "tenant get_security_groups <tenant_name>"
      desc "get the security groups of the tenant"
      def get_security_groups(args = [])
        tenant_name = shift_keyword_arg(args)

        if interactive?
          tenant_name ||= ask("Tenant name: ")
        end

        if tenant_name.nil?
          usage_error("Please provide tenant name")
        end

        tenant = find_tenant_by_name(tenant_name)

        puts green("Security groups of tenant '#{tenant.id}' are:")
        tenant.security_groups.each do |t|
          source = t["inherited"]? "inherited" : "self"
          puts green("#{t["name"]}(#{source})")
        end
      end

      def parse_tenant_creation_arguments(args)
        options = {
            :name => nil,
            :security_groups => nil
        }
        name = shift_keyword_arg(args)
        options[:name] = name
        opts_parser = OptionParser.new do |opts|
          opts.banner = "Usage: tenant create <name> [options]"
          opts.on('-g', '--security_groups SECURITY_GROUPS', 'Comma-separated list of security groups') do |g|
            options[:security_groups] = g
          end
        end

        parse_options(args, opts_parser)

        options
      end

      def read_tenant_arguments_interactively(options)
        options[:name] ||= ask("Tenant name: ")
        options
      end

      def validate_tenant_arguments(options)
        if options[:name].blank?
          usage_error("Please provide tenant name")
        end
      end

    end
  end
end
