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
    class ResourceTickets < Base

      usage "resource-ticket create [<options>]"
      desc "Create a new resource ticket"
      def create(args = [])
        tenant, name, limits = nil, nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant = find_tenant_by_name(tn) }
          opts.on("-n", "--name NAME", "Resource ticket name") { |n| name = n }
          opts.on("-l", "--limits LIMITS", "Resource ticket limits") { |l| limits = l.split(/\s*,\s*/) }
        end

        parse_options(args, opts_parser)

        if interactive?
          tenant ||= tenant_required
          name ||= ask("Resource ticket name: ")
          limits = limits ? parse_limits(limits) : ask_for_limits
        else
          limits = parse_limits(limits)
        end

        if tenant.blank? || name.blank? || limits.blank?
          usage_error("Please provide tenant, name and limits", opts_parser)
        end

        puts
        puts "Tenant: #{tenant.name}"
        puts "Creating resource ticket: #{name}"

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
          tenant.create_resource_ticket(:name => name, :limits => limits)
          puts green("Resource ticket created")
        else
          puts yellow("OK, canceled")
        end
      end

      usage "resource-ticket list [<options>]"
      desc "List resource tickets for a current tenant"
      def list(args = [])
        tenant = nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant = find_tenant_by_name(tn) }
        end

        parse_options(args, opts_parser)
        tenant ||= tenant_required

        tickets = client.find_all_resource_tickets(tenant.id).items

        table = Terminal::Table.new
        table.headings = %w(Name Limits)
        tickets.each do |t|
          table.add_row(format_ticket(t))
          table.add_separator unless t == tickets[-1]
        end

        puts
        puts table
        puts
        puts "Total: #{tickets.size}"
      end

      usage "resource-ticket tasks <name>"
      desc "Show resource ticket tasks"
      def tasks(args = [])
        tenant, state = nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant = find_tenant_by_name(tn) }
          opts.on("-s", "--state VALUE", "Task state") {|ts| state = ts }
        end

        name = shift_keyword_arg(args)
        if name.nil?
          usage_error("Please provide resource ticket name", opts_parser)
        end

        parse_options(args, opts_parser)

        tenant ||= tenant_required
        resource_ticket = find_resource_ticket_by_name(tenant, name)

        render_task_list(client.get_resource_ticket_tasks(resource_ticket.id, state).items)
      end

      private

      def format_ticket(ticket)
        limits = ticket.limits.map {|qli| "#{qli.key}, #{qli.value}, #{qli.unit}"}
        [ticket.name, limits.join("\n")]
      end

    end
  end
end
