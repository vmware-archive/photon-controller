#!/bin/bash -xe

AGENT_BIN="/usr/lib/esxcloud/agent/bin"
AGENT_CONFIG="/etc/esxcloud/agent"

ret=$(/etc/rc.d/init.d/rsyslog start)

sleep 5

{{#AGENT_COUNT}}
start_command="$AGENT_BIN/python -m agent.agent --config-path=$AGENT_CONFIG --agent-count={{{AGENT_COUNT}}}"
{{/AGENT_COUNT}}
{{^AGENT_COUNT}}
start_command="$AGENT_BIN/python -m agent.agent --config-path=$AGENT_CONFIG"
{{/AGENT_COUNT}}

$start_command
