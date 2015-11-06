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

module EsxCloud
  module Cli
    class Runner

      class ParseTreeNode < Hash
        attr_accessor :command
      end

      # @param [Array] args
      def initialize(args)
        @args = args
        @option_parser = OptionParser.new do |opts|
          opts.banner = "Usage: photon [-n] [-t] [-v] [-h] <command> [<args>]"
          opts.separator("")

          opts.on("-n", "--non-interactive", "Run in non-interactive mode") do
            cli_config.interactive = false
          end

          @target = nil
          opts.on("-t", "--target URL", "Set API target for command") do |url|
            @target = url
          end

          opts.on("-v", "--version", "Print version and exit") do
            puts "ESXCloud CLI #{EsxCloud::Cli::VERSION}"
            exit(0)
          end

          opts.on("-h", "--help", "Print help message and exit") do
            @args = []
          end
        end
        @parse_tree = nil
      end

      def run
        @option_parser.order!(@args)

        cli_config.target = @target if @target
        build_tree
        command = find_command(@parse_tree)

        if command.nil?
          unknown_command
          exit(1)
        end

        command.run(@args)

      rescue OptionParser::ParseError => e
        puts red(e)
        exit(1)
      rescue EsxCloud::ApiError => e
        puts red("API error: #{e.message}")
        exit(1)
      rescue EsxCloud::Error => e
        puts red("Error: #{e.message}")
        exit(1)
      end

      def build_tree
        @parse_tree = ParseTreeNode.new

        # By this time all classes that implement commands should already be loaded.
        # What we need to do is to build a tree that allows us to look them up efficiently.

        CliConfig.commands.each do |command|
          p = @parse_tree
          keywords = command.usage.split(/\s+/).take_while { |word| word =~ /^[a-z]/i }

          keywords.each_with_index do |kw, i|
            p[kw] ||= ParseTreeNode.new
            p = p[kw]
            if i == keywords.size - 1
              p.command = command
            end
          end
        end
      end

      def find_command(node)
        return nil if node.nil?
        arg = @args.shift

        longer_command = find_command(node[arg])

        if longer_command
          longer_command
        else
          @args.unshift(arg) if arg
          node.command
        end
      end

      def unknown_command
        unless @args.empty?
          puts "Unknown command: #{@args.join(" ")}"
          puts ""
        end

        node = @parse_tree
        prefix = []
        @args.each do |keyword|
          if node.has_key?(keyword)
            prefix << keyword
            node = node[keyword]
          end
        end

        puts @option_parser.to_s
        puts
        extra = prefix.size > 0 ? " for '#{prefix.join(" ")}'" : ""
        puts "Available subcommands#{extra}: "
        puts

        node.keys.sort.each do |cmd|
          puts "    #{cmd}"
        end
        puts ""
      end

    end
  end
end
