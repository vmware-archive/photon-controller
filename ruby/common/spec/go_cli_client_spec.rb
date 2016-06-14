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

require_relative "spec_helper"

describe EsxCloud::GoCliClient do
  before(:each) do
    @api_client = double(EsxCloud::ApiClient)
    EsxCloud::ApiClient.stub(:new).with(any_args).and_return(@api_client)

    EsxCloud::Config.stub(:logger).and_return(double(Logger, :info => nil, :debug => nil, :warn => nil))
    EsxCloud::Config.stub(:log_file_name).and_return("")

    allow(File).to receive(:executable?).with("/path/to/cli").and_return(true)
    allow(EsxCloud::CmdRunner).to receive(:run).with("/path/to/cli target set --nocertcheck localhost:9000")
  end

  let(:client) {
    EsxCloud::GoCliClient.new("/path/to/cli", "localhost:9000")
  }

  it "should have all methods defined by client spec" do
    base_client = EsxCloud::Client.new

    base_client.public_methods(false).each do |method_name|
      client.respond_to?(method_name).should be_true, "#{method_name} does not exist"
      client.method(method_name).arity.should == base_client.method(method_name).arity
    end
  end

  describe "#initialize" do
    it "complains if CLI binary doesn't exist" do
      expect(File).to receive(:executable?).with("/path/to/cli").and_return(false)
      expect { client }.to raise_error(ArgumentError)
    end
  end

  describe "#run_cli" do
    it "passes command to actual CLI" do
      cmd = "/path/to/cli --non-interactive  foo bar --arg value"

      expect(EsxCloud::CmdRunner).to receive(:run).with(cmd).and_return("bar")
      expect(client.run_cli("foo bar --arg value")).to eq("bar")
    end

    it "raises an exception if CLI command failed" do
      cmd = "/path/to/cli --non-interactive  foo bar --arg value"
      error = EsxCloud::CliError.new("bar");

      expect(EsxCloud::CmdRunner).to receive(:run).with(cmd).and_raise(error)

      begin
        client.run_cli("foo bar --arg value")
        fail("Non-zero CLI exit code should raise a CLI error")
      rescue EsxCloud::CliError => e
        expect(e.output).to eq("bar")
      end
    end
  end
end
