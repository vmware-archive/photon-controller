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
    class Base
      extend EsxCloud::Cli::CommandDsl
      include EsxCloud::Cli::Commands::Input
      include EsxCloud::Cli::Commands::UI

      attr_accessor :usage

      # @param [Array] args
      # @param [OptionParser] parser
      def parse_options(args, parser = OptionParser.new)
        enhance_parser(parser)

        begin
          parser.parse!(args)
        rescue OptionParser::ParseError => e
          usage_error(e.message, parser)
        end

        if args.size > 0
          usage_error("Unknown arguments: #{args.join(" ")}", parser)
        end
      end

      # @param [String] error
      # @param [OptionParser] parser
      def usage_error(error, parser = OptionParser.new)
        enhance_parser(parser)

        puts red(error)
        puts
        puts parser
        puts
        exit(1)
      end

      def enhance_parser(parser)
        parser.banner = "Usage: #{self.usage}\n\n"

        parser.on_tail("-h", "--help", "Print this help message and exit") do
          puts parser
          puts
          exit(0)
        end
      end

      def shift_keyword_arg(args)
        args.shift unless args[0].to_s.start_with?("-")
      end

      def confirmed?(message = "Are you sure? ")
        !interactive? || agree(message)
      end

      def tenant_required
        if cli_config.tenant.nil?
          err("Please set tenant first using 'tenant set <name>' command")
        end

        cli_config.tenant
      end

      def project_required
        if cli_config.project.nil?
          err("Please set project first using 'project set <name>' command")
        end

        cli_config.project
      end

      def initialize_client
        client
      end

      def find_tenant_by_name(name)
        tenants = client.find_tenants_by_name(name)
        if tenants.items.size != 1
          err("Can't find tenant '#{name}'")
        end

        tenants.items[0]
      end

      def find_resource_ticket_by_name(tenant, name)
        tickets = client.find_resource_tickets_by_name(tenant.id, name)
        if tickets.items.size != 1
          err("Can't find resource ticket '#{name}'")
        end

        tickets.items[0]
      end

      def find_project_by_name(tenant, name)
        projects = client.find_projects_by_name(tenant.id, name)
        if projects.items.size != 1
          err("Can't find project '#{name}'")
        end

        projects.items[0]
      end

      def find_flavor_by_name_kind(name, kind)
        flavors = client.find_flavors_by_name_kind(name, kind)
        if flavors.items.size != 1
          err("Can't find Flavor named '#{name}', kind '#{kind}")
        end

        flavors.items[0]
      end

      def interactive?
        cli_config.interactive?
      end

      def confirm
        unless confirmed?
          puts yellow("OK, canceled")
          exit(0)
        end
      end

      def ask_for_limits(prompt='Limit')
        limits, index = [], 1

        while true
          puts
          puts "#{prompt} #{index} (ENTER to finish): "
          key = ask("Key: ")
          break if key.empty?

          value = ask("Value: ")
          unit = ask("Unit: ")

          limits << EsxCloud::QuotaLineItem.new(key, value, unit)
          index += 1
        end

        limits
      end

      def parse_limits(limits)
        return [] unless limits.is_a?(Enumerable)

        limits.map do |limit|
          if limit.is_a?(EsxCloud::QuotaLineItem)
            limit
          else
            key, value, unit = limit.split(/\s+/, 3)
            EsxCloud::QuotaLineItem.new(key, value, unit)
          end
        end
      end

      def with_array_in_parallel(array, n_threads = 10)
        queue = []
        array.each { |element| queue.push(element) }
        lock = Mutex.new
        threads = []

        (1..[n_threads, array.size].min).each do
          threads << Thread.new do
            loop do
              element = lock.synchronize { queue.shift }
              break if element.nil?
              yield element, lock
            end
          end
        end

        threads.each { |thr| thr.join }
      end

      # TODO: *_vms_in_parallel methods currently result in too much output, as
      #       it all gets multiplexed form individual API calls. We should fix that
      #       preferably by adding some kind of nice progress bar.

      def delete_vms_in_parallel(vms, n_threads = 10)
        with_array_in_parallel(vms, n_threads) do |vm, lock|
          begin
            if vm.state == "STARTED"
              lock.synchronize { puts "\nStopping VM '#{vm.name}'" }
              vm.stop!
            end

            lock.synchronize { puts "\nDeleting VM '#{vm.name}'" }
            vm.delete
          rescue EsxCloud::ApiError => e
            lock.synchronize { puts red("\nVM #{vm.id}: #{e.message}") }
          end
        end
      end

      def start_vms_in_parallel(vms, n_threads = 10)
        with_array_in_parallel(vms, n_threads) do |vm, lock|
          begin
            if vm.state == "STOPPED"
              lock.synchronize { puts "\nStarting VM '#{vm.name}'" }
              vm.start!
            else
              lock.synchronize { puts "\nVM '#{vm.name}' is #{vm.state}, skipping" }
            end
          rescue EsxCloud::ApiError => e
            lock.synchronize { puts red("\nVM #{vm.id}: #{e.message}") }
          end
        end
      end

      def stop_vms_in_parallel(vms, n_threads = 10)
        with_array_in_parallel(vms, n_threads) do |vm, lock|
          begin
            if vm.state == "STARTED"
              lock.synchronize { puts "\nStopping VM '#{vm.name}'" }
              vm.stop!
            else
              lock.synchronize { puts "\nVM '#{vm.name}' is #{vm.state}, skipping" }
            end
          rescue EsxCloud::ApiError => e
            lock.synchronize { puts red("\nVM #{vm.id}: #{e.message}") }
          end
        end
      end

      def parse_affinities(affinities)
        return [] unless affinities.is_a?(Enumerable)

        affinities.map do |affinity|
          if affinity.is_a?(EsxCloud::Locality)
            affinity
          else
            kind, id = affinity.split(/\s+/, 2)

            EsxCloud::Locality.new(id, kind)
          end
        end
      end

    end
  end
end
