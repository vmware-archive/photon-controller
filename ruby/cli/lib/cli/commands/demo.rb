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
    class Demo < Base

      usage "demo threads set <count>"
      desc "Configures the max number of threads to use for parallel operations."
      def threads_set(args = [])
        count = shift_keyword_arg(args)
        parse_options(args)

        if count.blank?
          usage_error("Please provide a thread count")
        end

        cli_config.demo_threads = count
        cli_config.save

        puts green("Thread count set to '#{count}'")
      end

      usage "demo threads show"
      desc "Shows the max number of threads to use for parallel operations."
      def threads_show(args = [])
        parse_options(args)

        threads = cli_config.demo_threads || max_threads_default
        puts "Max number of threads: #{threads} (Default: #{max_threads_default})"
      end

      usage "demo vms create [<options>]"
      desc "Create a batch of VMs with specified name prefix."
      def vms_create(args = [])
        tenant_name, project_name, options, count = nil, nil, {}, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant_name = tn }
          opts.on("-p", "--project NAME", "Project name") { |pn| project_name = pn }
          opts.on("-n", "--name NAME", "VM name") { |n| options[:name] = n }
          opts.on("-f", "--flavor NAME", "VM flavor") { |f| options[:flavor] = f }
          opts.on("-i", "--image ID", "Image id") { |id| options[:image_id] = id }
          opts.on("-d", "--disks DISKS", "VM disks") { |d| options[:disks] = d.strip.split(/\s*,\s*/) }
          opts.on("-e", "--environment K1=V1[,KX=VX]*", "VM environment") { |e| options[:environment] = e.split(/\s*,\s*/) }
          opts.on("-a", "--affinities AFFINITIES", "VM Locality") { |a| options[:affinities] = a.split(/\s*,\s*/) }
          opts.on("-c", "--count COUNT", "Count of VMs to create") { |c| count = c }
        end

        parse_options args, opts_parser

        project = ensure_project tenant_name, project_name, opts_parser
        options = ensure_vm_options options, opts_parser

        if interactive?
          puts
          count ||= ask("VM count: ")
        else
          count ||= 1
        end

        puts
        stats = parallel_run(Integer(count), "Creating VMs") do |c|
          project.create_vm options.merge(name: "#{options[:name]}-#{c}")
        end

        puts
        render_stats stats
      end

      usage "demo vms delete [<options]"
      desc "Delete all VMs that match name prefix."
      def vms_delete(args = [])
        vms = ensure_vms args

        puts
        stats = parallel_run(vms.size, "Deleting VMs") do |c|
          vms[c-1].delete
        end

        puts
        render_stats stats
      end

      usage "demo vms start [<options]"
      desc "Start all VMs that match the name prefix."
      def vms_start(args = [])
        vms = ensure_vms args

        puts
        stats = parallel_run(vms.size, "Start VMs") do |c|
          vms[c-1].start!
        end

        puts
        render_stats stats
      end

      usage "demo vms stop [<options]"
      desc "Stop all VMs that match the name prefix."
      def vms_stop(args = [])
        vms = ensure_vms args

        puts
        stats = parallel_run(vms.size, "Stop VMs") do |c|
          vms[c-1].stop!
        end

        puts
        render_stats stats
      end

      private

      def ensure_vms(args = [])
        tenant_name, project_name, name_prefix = nil, nil, nil
        opts_parser = OptionParser.new do |opts|
          opts.on("-t", "--tenant NAME", "Tenant name") { |tn| tenant_name = tn }
          opts.on("-p", "--project NAME", "Project name") { |pn| project_name = pn }
          opts.on("-x", "--name-prefix PREFIX", "Name prefix for VMs to delete") { |np| name_prefix = np }
        end

        parse_options(args, opts_parser)

        project = ensure_project tenant_name, project_name, opts_parser

        if interactive?
          name_prefix ||= ask("VM name prefix: ")
        end

        vms = client.find_all_vms(project.id).items.select { |vm| vm.name.start_with? name_prefix }
        unless vms.size > 0
          puts "No VMs found with name prefix '#{name_prefix}'."
          exit 0
        end

        vms
      end

      def render_stats(stats = {})
        puts "Succeeded: #{stats[:items] - stats[:errors].size}, Failed: #{stats[:errors].size}"

        unless stats[:errors].size == 0
          stats[:errors].each { |_k, e| logger.err e.message }
        end
      end

      def parallel_run(count, label)
        client.task_tracker = nil
        progress = Formatador::ProgressBar.new count, label: label, started_at: Time.now

        errors = {}
        (0..(count / max_threads)).each do |iteration|
          threads = []
          (1..[count - iteration * max_threads, max_threads].min).each do |c|
            item = c + iteration * max_threads
            threads << Thread.new do
              begin
                yield item
              rescue EsxCloud::Error => e
                errors[item] = e
              ensure
                progress.increment
              end
            end
          end
          threads.each { |thr| thr.join }
        end

        { items: count, errors: errors }
      end

      def max_threads
        cli_config.demo_threads || max_threads_default
      end

      def max_threads_default
        100
      end
    end
  end
end
