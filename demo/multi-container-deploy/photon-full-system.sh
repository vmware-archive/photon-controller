#!/bin/bash -e

function usage() {
    echo "Usage $0 OPERATION" 1>&2
    echo "OPERATION options: "
    echo "shutdown - Safely power off the Photon Controller and managed VMs"
    echo "turnon - Safely power on Photon Controller after proper shutdown"
    exit 1
}

if [ "$#" -lt 1 ]; then
    usage
fi

operation=$1
# FILL OUT THE FOLLOWING PARAMETERS:
# Username and password to login to photon controller cli
USERNAME=""
PASSWORD=""
HAPROXY_DOCKER_ID=haproxy
# List of each Photon Controller config directory
PC_CONFIG_DIRS=()
# List of each Photon Controller docker container id or name
PC_DOCKER_IDS=(photon-controller-1 photon-controller-2 photon-controller-3)
# List of each Photon Controller IP
PC_IPS=()
# List of each Lightwave docker container id or name
LW_DOCKER_IDS=(lightwave-1 lightwave-2 lightwave-3)

if [ -z ${USERNAME} ]; then
    echo "USERNAME for photon login needs to be set"
fi

if [ -z ${PASSWORD} ]; then
    echo "PASSWORD for photon login needs to be set"
fi

if [ -z ${PC_CONFIG_DIRS} ]; then
    echo "PC_CONFIG_DIRS needs to be set"
fi

if [ -z ${PC_DOCKER_IDS} ]; then
    echo "PC_DOCKER_IDS needs to be set"
fi

if [ -z ${PC_IPS} ]; then
    echo "PC_IPS needs to be set"
fi

if [ -z ${LW_DOCKER_IDS} ]; then
    echo "LW_DOCKER_IDS needs to be set"
fi

pc_id=${PC_DOCKER_IDS[0]}

curl_opts="--key /etc/keys/machine.privkey --cert /etc/keys/machine.crt --capath /etc/ssl/certs/ --silent"

function photon_login() {
    photon -n target login -u $USERNAME -p $PASSWORD > /dev/null
}

function update_membership_quorum() {
    local membership_quorum=$1
    docker exec $pc_id curl $curl_opts https://${PC_IPS[0]}:19000/core/node-groups/default \
           -H "Content-type: application/json" \
           -X "PATCH" \
           -d '{ "isGroupUpdate": true, "membershipQuorum" : '$membership_quorum', "kind": "com:vmware:xenon:services:common:NodeGroupService:UpdateQuorumRequest" }' \
           > /dev/null

    for pc_ip in ${PC_IPS[*]}; do
        verify_quorum $pc_ip $membership_quorum
    done

    echo "Membership for all nodes are updated to $membership_quorum"
}

function verify_quorum() {
    local pc_endpoint=$1
    local membership_quorum=$2
    attempts=1
    total_attempts=100
    while [ $attempts -lt $total_attempts ]; do
        quorum_response=$(docker exec $pc_id curl $curl_opts https://$pc_endpoint:19000/core/node-groups/default | grep "membershipQuorum" | awk '{print $2}' | sed 's/,$//')
        count=0
        for status in $quorum_response; do
            if [ $status -eq $membership_quorum ]; then
                count=$[$count+1]
            fi
        done
        if [ $count -eq ${#PC_DOCKER_IDS[@]} ]; then
            return
        fi
        attempts=$[$attempts+1]
        sleep 5
    done

    if [ $attempts -eq $total_attempts ]; then
        echo "Node $pc_endpoint failed to update quorum membership after $total_attempts attempts"
        exit 1
    fi
}

function stop_vms() {
    echo "Stopping all VMs on each host"
    host_ids=$(photon -n host list | awk '{ print $1 }')
    for host_id in $host_ids; do
        photon_login
        vm_ids=$(photon -n host list-vms $host_id | awk '{print $1}')
        for vm_id in $vm_ids; do
            vm_state=$(photon -n vm show $vm_id)
            if [[ $vm_state == *"STARTED"* ]]; then
                echo "Stopping vm $vm_id"
                photon -n vm stop $vm_id > /dev/null
            fi
        done
    done
}

function wait_for_incomplete_tasks() {
    echo "Waiting for incomplete tasks to finish"
    task_ids=$(photon -n task list | awk '{print $1}')
    started_tasks=()
    for task_id in $task_ids; do
        task_state=$(photon -n task show $task_id | head -1 | awk '{print $2}')
        if [ "$task_state" == "STARTED" ]; then
            started_tasks+=($task_id)
        fi
    done

    attempts=1
    total_attempts=300
    while [ $attempts -lt $total_attempts ] && [ ${#started_tasks[@]} -gt 0 ]; do
        photon_login
        if [  ${#started_tasks[@]} -eq 0 ]; then
            break;
        fi
        finished_tasks=()
        for i in ${!started_tasks[@]}; do
            task_state=$(photon -n task show $task_id | head -1 | awk '{print $2}')
            if [ "$task_state" != "STARTED" ]; then
                echo "Waiting for ${started_tasks[$i]} to finish"
                finished_tasks+=(${started_tasks[$i]})
            fi
        done
        for finished_task_id in ${finished_tasks[*]}; do
            started_tasks=(${started_tasks[@]/$finished_task_id})
        done
        attempts=$[$attempts+1]
        sleep 10
    done

    if [ $attempts -eq $total_attempts ]; then
        echo "Started tasks failed to complete after $total_attempts attempts"
        exit 1
    fi
}

function verify_xenon_stopped() {
    local pc_endpoint=$1
    attempts=1
    total_attempts=50
    while [ $attempts -lt $total_attempts ]; do
        http_code=$(docker exec $pc_id curl -w "%{http_code}" $curl_opts https://$pc_endpoint:19000/core/node-groups/default | grep "statusCode" | awk '{print $2}' | sed 's/,$//')
        if [ $http_code -eq 404 ]; then
            return
        fi
        attempts=$[$attempts+1]
        sleep 5
    done

    if [ $attempts -eq $total_attempts ]; then
        echo "Node $pc_endpoint failed to stop after $total_attempts attempts"
        exit 1
    fi
}

function verify_lightwave_up() {
    local lw_container=$1
    attempts=1
    reachable="false"
    total_attempts=50
    while [ $attempts -lt $total_attempts ] && [ $reachable != "true" ]; do
        http_code=$(docker exec -t $lw_container curl -I -so /dev/null -w "%{response_code}" -s -X GET -k https://127.0.0.1) || true
        # The curl returns 000 when it fails to connect to the Lightwave server
        if [ "$http_code" == "000" ]; then
            echo "Waiting for Lightwave server to startup at $lw_container (attempt $attempts/$total_attempts), will try again."
            attempts=$[$attempts+1]
            sleep 5
        else
            reachable="true"
            return
        fi
    done
    if [ $attempts -eq $total_attempts ]; then
        echo "Could not connect to Lightwave REST client at $lw_container after $total_attempts attempts"
        exit 1
    fi
}

function verify_pc_available() {
    local pc_container=$1
    attempts=1
    reachable="false"
    total_attempts=50
    while [ $attempts -lt $total_attempts ] && [ $reachable != "true" ]; do
        http_code=$(docker exec -t $pc_container curl -I -so /dev/null -w "%{response_code}" -s -X GET -k https://127.0.0.1:9000/available) || true
        # The curl returns 000 when it fails to connect to the Photon Controller API server
        if [ "$http_code" == "000" ]; then
            echo "Waiting for Photon Controller API server to startup at $pc_container (attempt $attempts/$total_attempts),  will try again."
            attempts=$[$attempts+1]
            sleep 5
        else
            reachable="true"
            return
        fi
    done
    if [ $attempts -eq $total_attempts ]; then
        echo "Could not connect to Photon Controller API server at $pc_container after $total_attempts attempts"
        exit 1
    fi
}

if [[ $operation == "shutdown" ]]; then
    photon_login
    # Examines and stops all the VMs
    stop_vms

    photon_login
    # Pause Photon Controller
    photon -n deployment pause > /dev/null

    wait_for_incomplete_tasks

    echo "Stopping internal services"
    # Prevent synchronization issues during shutdown and startup
    update_membership_quorum ${#PC_DOCKER_IDS[@]}
    for pc_ip in ${PC_IPS[*]}; do
        docker exec $pc_id curl $curl_opts https://$pc_ip:19000/core/management -X "DELETE"
        verify_xenon_stopped $pc_ip
    done

    # Update Photon Controller config for restart
    for folder in ${PC_CONFIG_DIRS[*]}; do
        config_file=$folder/photon-controller-core.yml
        if ! grep -q "quorumSize: ${#PC_DOCKER_IDS[@]}" $config_file; then
            echo "quorumSize: ${#PC_DOCKER_IDS[@]}" >> $config_file
        fi
    done

    echo "Stopping Photon Controller containers"
    # Stop Photon Controller containers
    for pc_id in ${PC_DOCKER_IDS[*]}; do
        docker stop $pc_id
    done

    echo "Stopping Lightwave containers"
    for lw_id in ${LW_DOCKER_IDS[*]}; do
        docker stop $lw_id
    done

    docker stop $HAPROXY_DOCKER_ID
    echo "Photon Controller is off"
    exit 0
fi

if [[ $operation == "turnon" ]]; then
    docker start $HAPROXY_DOCKER_ID
    echo "Starting Lightwave"

    for lw_id in ${LW_DOCKER_IDS[*]}; do
        docker start $lw_id
        verify_lightwave_up $lw_id
    done

    echo "Starting Photon Controller"
    for pc_id in ${PC_DOCKER_IDS[*]}; do
        docker start $pc_id
        verify_pc_available $pc_id
    done

    echo "Wait for all nodes to start and synchronize among all 3 nodes"
    sleep 20

    for pc_ip in ${PC_IPS[*]}; do
        verify_quorum $pc_ip ${#PC_DOCKER_IDS[@]}
    done

    # Revert changes to quorum membership
    majority=`expr ${#PC_DOCKER_IDS[@]} / 2 + 1`
    update_membership_quorum $majority

    photon_login
    photon -n deployment resume > /dev/null
    echo "Photon Controller is online"
    exit 0
fi
