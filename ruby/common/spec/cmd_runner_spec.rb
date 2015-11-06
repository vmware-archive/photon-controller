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

describe EsxCloud::CmdRunner do

  before(:each) do
    EsxCloud::Config.stub(:logger).and_return(double(Logger, :info => nil, :debug => nil, :warn => nil))
  end

  it "passes valid command to popen3 and gets back output" do
    cmd = 'Foo'
    output = 'Bar'

    process_status = double(Process::Status)
    expect(process_status).to receive(:success?).and_return(true)

    wait_thread = double(Thread)
    expect(wait_thread).to receive(:value).and_return(process_status)

    stdin = double(IO)
    stderr = double(IO)

    stdout = double(IO)
    expect(stdout).to receive(:read).and_return(output)

    expect(Open3).to receive(:popen3).with(cmd).and_yield(stdin, stdout, stderr, wait_thread)

    expect(EsxCloud::CmdRunner.run(cmd, true, nil)).to eq(output)
  end

  it "passes valid command to popen2e and gets back output" do
    cmd = 'Foo'
    output = 'Bar'
    error = "Exception"
    expected = output + error

    process_status = double(Process::Status)
    expect(process_status).to receive(:success?).and_return(true)

    wait_thread = double(Thread)
    expect(wait_thread).to receive(:value).and_return(process_status)

    stdin = double(IO)
    std_out_err = double(IO)
    expect(std_out_err).to receive(:read).and_return(expected)

    expect(Open3).to receive(:popen2e).with(cmd).and_yield(stdin, std_out_err, wait_thread)

    expect(EsxCloud::CmdRunner.run(cmd, nil, nil)).to eq(expected)
  end

  it "passes invalid command to popen3 and gets back errorcode" do
    cmd = 'Foo'
    err = 'Exception: invalid username/password'

    process_status = double(Process::Status)
    expect(process_status).to receive(:success?).and_return(false)

    wait_thread = double(Thread)
    expect(wait_thread).to receive(:value).and_return(process_status)

    stdin = double(IO)
    stdout = double(IO)

    stderr = double(IO)
    expect(stderr).to receive(:read).and_return(err)

    expect(Open3).to receive(:popen3).with(cmd).and_yield(stdin, stdout, stderr, wait_thread)

    begin
      EsxCloud::CmdRunner.run(cmd, true, nil)
      fail("Non-zero exit code should raise an error")
    rescue EsxCloud::CliError => e
      expect(e.output).to eq(err)
    end
  end

  it "passes invalid command to popen2e and gets back errorcode" do
    cmd = 'Foo'
    output = 'Bar'
    error = "Exception"
    expected = output + error

    process_status = double(Process::Status)
    expect(process_status).to receive(:success?).and_return(false)

    wait_thread = double(Thread)
    expect(wait_thread).to receive(:value).and_return(process_status)

    stdin = double(IO)
    std_out_err = double(IO)
    expect(std_out_err).to receive(:read).and_return(expected)

    expect(Open3).to receive(:popen2e).with(cmd).and_yield(stdin, std_out_err, wait_thread)

    begin
      EsxCloud::CmdRunner.run(cmd, nil, nil)
      fail("Non-zero exit code should raise an error")
    rescue EsxCloud::CliError => e
      expect(e.output).to eq(expected)
    end
  end

  it "filter out unwanted error information" do
    cmd = 'Foo'
    err1 = 'Exception: invalid username/password'
    err2 = 'Java line 100'

    process_status = double(Process::Status)
    expect(process_status).to receive(:success?).and_return(false)

    wait_thread = double(Thread)
    expect(wait_thread).to receive(:value).and_return(process_status)

    stdin = double(IO)
    stdout = double(IO)

    stderr = double(IO)
    expect(stderr).to receive(:gets).and_return(err1, err2, nil)

    expect(Open3).to receive(:popen3).with(cmd).and_yield(stdin, stdout, stderr, wait_thread)

    begin
      EsxCloud::CmdRunner.run(cmd, true, /.*Exception:.*/)
      fail("Non-zero exit code should raise an error")
    rescue EsxCloud::CliError => e
      expect(e.output).to eq(err1)
    end
  end
end
