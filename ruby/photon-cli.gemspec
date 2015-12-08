# coding: utf-8

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

lib = File.expand_path('../common/lib', __FILE__)
$LOAD_PATH.unshift(lib) unless $LOAD_PATH.include?(lib)

require 'common/version'

Gem::Specification.new do |spec|
  spec.name          = "photon-cli"
  spec.version       = EsxCloud::VERSION
  spec.authors       = ["VMWare Inc"]
  spec.summary       = "Photon CLI"
  spec.description   = "Photon CLI"

  spec.files         = `git ls-files bin`.split($/)
  spec.files         += `git ls-files cli`.split($/)
  spec.files         += `git ls-files common`.split($/)
  spec.files         += Dir['cli/assets/*']

  spec.executables   = "photon"

  spec.require_paths = ["cli/lib", "common/lib"]

  spec.add_development_dependency "bundler"

  spec.add_runtime_dependency "faraday", "~>0.8.7"
  spec.add_runtime_dependency "highline", "~>1.7.3"
  spec.add_runtime_dependency "json", "~>1.8"
  spec.add_runtime_dependency "terminal-table", "~>1.4.5"
  spec.add_runtime_dependency "netaddr", "~>1.5.0"
  spec.add_runtime_dependency "formatador", "~>0.2.5"
end
