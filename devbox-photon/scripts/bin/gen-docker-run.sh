#!/usr/bin/env bash
# This script generates the "docker run" command for containers.
# It takes a service config JSON file (e.g. "zookeeper_release.json")
# and cmdputs the Docker command line to start the container with the
# port bindings, volume bindings, etc. taken from the config file.
infile=$1
entrypoint=$2
entrypointArgs=$3
dockerArgs=$4

# This jq cmdput is in the format of:
# '2181' 2181
# 'var/log' '/var/log'
# For ports and volumes, respectively.
jqPorts=$(jq -r '.portBindings | to_entries[] | [.key, .value] | @sh' "$infile")
jqVolumes=$(jq -r '.volumeBindings | to_entries[] | [.key, .value] | @sh' "$infile")
# Strip single quotes
jqPorts=${jqPorts//"'"/}
jqVolumes=${jqVolumes//"'"/}

containerImage=$(jq -r '.containerImage' "$infile")
if [[ "$containerImage" == "null" ]]; then
  printf "containerImage missing from configuration %s" "$infile"
  exit 1
fi
containerName=$(jq -r '.containerName' "$infile")
if [[ "$containerName" == "null" ]]; then
  printf "containerName missing from configuration %s" "$infile"
  exit 1
fi

declare -A ports
declare -A volumes

while read -r line; do
  if [[ -z $line ]]; then continue; fi
  t=($line)
  ports[${t[0]}]=${t[1]}
done <<< "$jqPorts"

while read -r line; do
  if [[ -z $line ]]; then continue; fi
  t=($line)
  volumes[${t[0]}]=${t[1]}
done <<< "$jqVolumes"

cmd="docker run -d --net=host --restart=always $dockerArgs"

for p in "${!ports[@]}"; do
  cmd+=" -p $p:${ports[$p]}"
done

for v in "${!volumes[@]}"; do
  cmd+=" -v $v:${volumes[$v]}"
done

cmd+=" --name $containerName --entrypoint $entrypoint $containerImage $entrypointArgs"

echo -e "$cmd"
