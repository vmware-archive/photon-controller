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
    class Flavors < Base

      usage "flavor create [<options>]"
      desc "Create a new flavor"
      def create(args = [])
        name, kind, cost = nil, nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-n", "--name NAME", "Flavor name") { |n| name = n }
          opts.on("-k", "--kind KIND",
            "Flavor kind. Accepted value is persistent-disk, ephemeral-disk or vm.")\
            { |k| kind = k }
          opts.on("-c", "--cost COST", "Flavor cost") { |c| cost = c.split(/\s*,\s*/) }
        end

        parse_options(args, opts_parser)

        if interactive?
          name ||= ask("Flavor name: ")
          kind ||= ask("Flavor kind: ")
          cost = cost ? parse_limits(cost) : ask_for_limits('Cost')
        else
          cost = parse_limits(cost)
        end

        if name.blank? || kind.blank? || cost.blank?
          usage_error("Please provide name, kind and cost", opts_parser)
        end

        unless /(persistent\-disk|ephemeral\-disk|vm)/.match(kind)
          usage_error("Invalid kind '#{kind}', should be persistent-disk, ephemeral-disk or vm.", opts_parser)
        end

        puts
        puts "Creating Flavor: '#{name}', Kind: '#{kind}'"

        if interactive?
          puts
          puts "Please make sure cost below are correct:"
          puts

          cost.each_with_index do |qli, i|
            puts "#{i + 1}: #{qli.key}, #{qli.value}, #{qli.unit}"
          end
        end

        if confirmed?
          initialize_client
          spec = EsxCloud::FlavorCreateSpec.new(name, kind, cost)
          EsxCloud::Flavor.create(spec)
          puts green("Flavor created")
        else
          puts yellow("OK, canceled")
        end
      end

      usage "flavor upload [<path_to_flavor_file>] [<options>]"
      desc "Upload flavors using a file"
      def upload(args = [])
        force = false
        opts_parser = OptionParser.new do |opts|
          opts.on("-u", "--[no-]update", "Update existing flavors if not in use") { |f| force = f }
        end

        file = shift_keyword_arg(args)
        usage_error("Please provide flavor file", opts_parser) if file.blank?
        parse_options(args, opts_parser)

        file = File.expand_path(file, Dir.pwd)
        puts "Uploading file #{file}"
        initialize_client
        EsxCloud::Flavor.upload_flavor(file, force).each do |flavor|
          if force
            puts red("Flavor #{flavor["kind"]}: #{flavor["name"]} could not be updated. It might be in use.")
          else
            puts red("Flavor #{flavor["kind"]}: #{flavor["name"]} already exists")
          end
        end
        puts green("Flavor file uploaded")
      end

      usage "flavor show [<options>]"
      desc "Show flavor info"
      def show(args = [])
        name, kind = nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-n", "--name NAME", "Flavor name") { |n| name = n }
          opts.on("-k", "--kind KIND", "Flavor kind") { |k| kind = k }
        end

        parse_options(args, opts_parser)

        if interactive?
          name ||= ask("Flavor name: ")
          kind ||= ask("Flavor kind: ")
        end

        if name.blank? || kind.blank?
          usage_error("Please provide name and kind", opts_parser)
        end

        begin
          flavor = find_flavor_by_name_kind(name, kind)
          flavor_cost = format_cost(flavor)

          puts "Flavor #{flavor.id}:"
          puts "  Name: #{flavor.name}"
          puts "  Kind: #{flavor.kind}"
          puts "  Cost: #{flavor_cost}"
          puts

        rescue EsxCloud::CliError => e
          puts yellow("Flavor '#{name}', kind '#{kind}' not found")
        end
      end

      usage "flavor list"
      desc "List all Flavors"
      def list(args = [])

        parse_options(args)
        flavors = client.find_all_flavors.items

        table = Terminal::Table.new
        table.headings = %w(Name Kind Cost)
        flavors.each do |flavor|
          table.add_row([flavor.name, flavor.kind, format_cost(flavor)])
          table.add_separator unless flavor == flavors[-1]
        end

        puts
        puts table
        puts
        puts "Total: #{flavors.size}"
      end

      usage "flavor delete [<options>]"
      desc "Delete flavor"
      def delete(args = [])
        name, kind = nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-n", "--name NAME", "Flavor name") { |n| name = n }
          opts.on("-k", "--kind KIND", "Flavor kind") { |k| kind = k }
        end

        parse_options(args, opts_parser)

        if interactive?
          name ||= ask("Flavor name: ")
          kind ||= ask("Flavor kind: ")
        end

        if name.blank? || kind.blank?
          usage_error("Please provide name and kind", opts_parser)
        end

        confirm
        client.delete_flavor_by_name_kind(name, kind)
        puts green("Deleted flavor '#{name}', kind '#{kind}")
      end

      usage "flavor tasks <id> [<options>]"
      desc "Show flavor tasks"
      def tasks(args = [])
        state = nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-s", "--state VALUE", "Tasks state") {|ts| state = ts}
        end

        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide Flavor id", opts_parser)
        end

        parse_options(args, opts_parser)

        render_task_list(client.get_flavor_tasks(id, state).items)
      end

      def format_cost(flavor)
        cost = flavor.cost.map {|qli| "#{qli.key} #{qli.value} #{qli.unit}"}
        cost.join(", ")
      end

    end
  end
end
