# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy
# of the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, without
# warranties or conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the
# License for then specific language governing permissions and limitations
# under the License.

"""Common helper functions to test the agent code"""

import json
import logging
import os
import shutil
import subprocess
import tempfile
import threading
import time
import uuid

import pystache
import yaml
from common.file_util import mkdir_p
from common.photon_thrift.direct_client import DirectClient
from gen.agent import AgentControl
from gen.host import Host
from thrift.transport import TTransport

from base_kazoo_test import DEFAULT_ZK_PORT

logger = logging.getLogger(__name__)
CLEAN = "clean"
INSTALL = "install"
WAIT = 20
SLEEP_STEP = 3


def get_default_java_path():
    """
    Get the default java path assuming the default python path is somewhere
    above the current working directory
    """
    file_dir = os.path.dirname(os.path.abspath(__file__))
    curr_path = file_dir
    # Find the python directory
    # Assumes test is being run from with python, there might be a better
    # way of doing this, but for now this works.
    for i in xrange(len(file_dir.split('/'))):
        (head, tail) = os.path.split(curr_path)
        if tail == "python":
            java_dir = os.path.join(head, "java")
            break
        curr_path = head
    return java_dir


def get_default_config(service):
    """
    Creates a config file by binding ${service}_test.json.
    """
    config_path = os.path.join(get_default_java_path(), service,
                               "src/dist/configuration")
    template_path = os.path.join(config_path, "%s.yml" % service)
    json_path = os.path.join(config_path, "%s_test.json" % service)
    with open(template_path) as template:
        with open(json_path) as params:
            with tempfile.NamedTemporaryFile(delete=False) as config:
                value = pystache.render(template.read(), json.load(params))
                config.write(value)
    return config.name


def start_service(service_name, config_path=None, log_file=None, flag="w"):
    """ Starts a service using gradlew """
    java_home = get_default_java_path()
    config = config_path if config_path else get_default_config(service_name)

    if not os.path.exists(config):
        raise Exception("%s doesn't exist" % config)

    run_command = "./build/install/%s/bin/%s %s" % (service_name,
                                                    service_name,
                                                    config)
    service_path = "%s/%s" % (java_home, service_name)
    env = os.environ.copy()
    env["JVM_OPTS"] = "-Dcurator-dont-log-connection-problems=true"
    # Popen will start the shell in a parent process and the service in a
    # child process, exec will make run_command inherit the parent process,
    # thus killing proc.pid will shutdown the service
    if log_file:
        with open(log_file, flag) as fd:
            proc = subprocess.Popen("exec " + run_command, cwd=service_path,
                                    env=env, shell=True, stdout=fd)
    else:
        proc = subprocess.Popen("exec " + run_command, cwd=service_path,
                                env=env, shell=True)
    return proc


def _wait_for_transport(transport, num_retries=20, sleep_sec=1):
    """Open thrift transport.

    transport Transport to open
    num_retries Number of times to try opening transport before giving up
    sleep_sec Interval in seconds between retries
    """
    for i in xrange(num_retries):
        try:
            transport.open()
            return
        except TTransport.TTransportException:
            time.sleep(sleep_sec)
    code = TTransport.TTransportException.TIMED_OUT
    raise TTransport.TTransportException(code,
                                         "Failed to open %s" % transport)


def stop_service(proc):
    """ Stop the service process by killing it

    proc - Handle to the process returned from start_service
    """
    if proc is not None:
        try:
            proc.terminate()
            proc.wait()
            if hasattr(proc, 'cleanup'):
                proc.cleanup()
            proc = None
        except OSError:
            # Don't worry about it
            pass


def _wait_on_code(func, code_ok, req=None,
                  sleep_interval=SLEEP_STEP, wait_timeout=WAIT):
    """
    Call func and wait till the response contains
    code_ok
    """
    for attempt in xrange(wait_timeout):
        if req:
            resp = func(req())
        else:
            resp = func()
        if resp.result == code_ok:
            return
        time.sleep(sleep_interval)
    raise Exception("Timed out waiting for code %s", code_ok)


class RuntimeUtils(object):

    CLOUD_STORE = "cloud-store"

    # Base directory to put all the files generated by the runtime util.
    BASE_DIR = "/tmp/photon-controller-python"

    # All the files for a given run goes to
    # /tmp/photon-controller-python/$RUNTIME_ID
    RUNTIME_ID = str(uuid.uuid4())

    def __init__(self, test_id):
        """
        test_id - Used to create a directory to put all the files generated by
                  this instance of runtime utils. The directory path is
                  /tmp/photon-controller-python/$RUNTIME_ID/$test_id
        """
        self.test_id = test_id
        self.test_dir = os.path.join(self.BASE_DIR, self.RUNTIME_ID, test_id)
        mkdir_p(self.test_dir)
        self.agent_procs = []
        self.root_procs = []
        self.thrift_procs = []

    def cleanup(self):
        # Stop all started agents
        for agent in self.agent_procs:
            try:
                self.stop_agent(agent)
            except:
                pass

        # Stop all root-schedule procs
        for root_proc in self.root_procs:
            stop_service(root_proc)

    def _configure_logging(self, config, service_name):
        filename = os.path.join(self.test_dir, "%s.log" % service_name)
        logging_config = {}
        logging_config['logging'] = {}
        logging_config['logging']['console'] = {}
        logging_config['logging']['console']['enabled'] = False
        logging_config['logging']['file'] = {}
        logging_config['logging']['file']['archive'] = False
        logging_config['logging']['file']['enabled'] = True
        logging_config['logging']['file']['currentLogFilename'] = filename
        config.update(logging_config)

    def start_cloud_store(self, host="localhost", port=40000,
                          zk_port=DEFAULT_ZK_PORT):
        # There is no default config file for cloud store.
        conf = {}
        conf["bind"] = host
        conf["registrationAddress"] = host
        conf["port"] = port
        conf["storagePath"] = "/tmp/cloud-store"
        conf['zookeeper'] = {}
        conf['zookeeper']['quorum'] = "localhost:%i" % zk_port
        self._configure_logging(conf, self.CLOUD_STORE)
        with tempfile.NamedTemporaryFile(delete=False) as conffile:
            with open(conffile.name, 'w+') as f:
                f.write(yaml.dump(conf, default_flow_style=False))
                proc = start_service(self.CLOUD_STORE, conffile.name)
                # HACK piggybacking cleanup code in the process object since
                # I'm too lazy to change the return type of start_service().
                proc.cleanup = lambda: shutil.rmtree(conf["storagePath"])
                return proc

    def start_agent(self, config):
        """
        config - Use get_default_agent_config() to get the default config, and
                 modify the dict as needed.
        """
        address = config["--hostname"]
        port = int(config["--port"])
        mkdir_p(config["--config-path"])
        arg_list = ["photon-controller-agent"]
        for (key, val) in config.items():
            arg_list.append(key)
            if val:
                arg_list.append(val)

        # Keeping track of what is created for clean up purposes
        agent_client = DirectClient("Host", Host.Client, address, port)
        control_client = DirectClient("AgentControl", AgentControl.Client,
                                      address, port)
        try:
            agent_client.connect()
            agent_client.close()
            raise Exception("Agent already running on port %s" % port)
        except TTransport.TTransportException:
            pass

        proc = subprocess.Popen(arg_list)
        self.agent_procs.append(proc)

        def wait(process):
            if process:
                try:
                    os.waitpid(process.pid, os.WUNTRACED)
                except OSError:
                    # Process might already exit
                    pass
        threading.Thread(target=wait, args=(proc,)).start()

        # Back off on failure to connect to agent
        max_sleep_time = 5
        sleep_time = 0.1
        while sleep_time < max_sleep_time:
            try:
                agent_client.connect()
                control_client.connect()
                return (proc, agent_client, control_client)
            except TTransport.TTransportException:
                time.sleep(sleep_time)
                sleep_time *= 2
        return (None, None, None)

    def stop_agent(self, agent_proc):
        if not agent_proc:
            return
        try:
            agent_proc.kill()
            agent_proc.wait()
        except OSError:
            # Doesn't matter
            pass

    def get_default_agent_config(self):
        """
        Get the default agent configuration.

        This method returns a dict of options, where the keys are option names
        and the values are option parameters. If the option doesn't take any
        parameters, the value is set to None.

        Use this method to get an agent config to pass to start_agent.
        """
        host_id = str(uuid.uuid4())
        config_path = os.path.join(self.test_dir, "agent-conf", host_id)
        log_file = os.path.join(self.test_dir, "%s.log" % host_id)
        return {
            "--hostname": "localhost",
            "--port": "8835",
            "--host-id": host_id,
            "--stats-enabled": "True",
            "--stats-store-endpoint": "10.1.1.20",
            "--stats-store-port": "8081",
            "--stats-host-tags": "CLOUD,MGMT",
            "--hypervisor": "fake",
            "--logging-level": "debug",
            "--no-syslog": None,
            "--vm-network": "VM Network",
            "--heartbeat-interval-sec": "3",
            "--heartbeat-timeout-factor": "6",
            "--config-path": config_path,
            "--datastores": "ds1",
            "--logging-file": log_file,
        }
