# Photon Controller Agent

Photon controller agent runs inside ESX host and expose thrift interface to
control plane. There are 2 major services in agent: host and agent
control.

* Host supports the control to ESX Host, like life cycle management of VM, image
  and disk.
* Agent control supports homekeeping management like provision, heartbeat etc.

## Creating the vib package

To create the vib package, run:

    make vib

from python/ directory. The vib file is created under python/dist/.

## Installing the vib package

To install the vib package, scp the vib file to the ESX host:

    scp dist/photon-controller-agent-*.vib root@esxhost:

where esxhost is the IP address of your ESX host. Then, ssh to the ESX host and
run:

  esxcli software vib install -v file:/photon-controller-agent-0.1.0-5.5.0.vib -f

## Configuring the agent

Modify /etc/opt/vmware/photon/controller/config.json. Here is a sample configuration:

    {
      "log_level": "debug",
      "datastores": ["datastore1"],
      "vm_network": "VM Network",
      "fault_domain_id": "test"
    }

Then, run:

    /sbin/auto-backup.sh

to persist the change. Otherwise, the change will be lost when you reboot the
ESX host. Restart the agent to pick up the configuration change:

    /etc/init.d/photon-controller-agent restart

You can check the status of the agent by looking at these log files:

    /var/log/photon-controller-agent.log (log file)
    /var/run/log/agent.out (stdout)
    /var/run/log/agent.err (stderr)

## Running `test_remote_agent.py`

Follow python/README.md file to setup a virtualenv. Once the virtualenv is set
up, run:

    nosetests --tc agent_remote_test.servers:10.36.7.32 \
      --tc=agent_remote_test.datastores:datastore1 \
      agent.tests.integration.test_remote_agent:TestRemoteAgent

If you are lucky, some tests might pass.
