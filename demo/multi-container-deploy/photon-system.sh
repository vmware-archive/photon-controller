#!/bin/bash -e

function usage() {
    echo "Usage $0 OPERATION USERNAME PASSWORD" 1>&2
    echo "OPERATION options: "
    echo "pause"
    echo "resume"
    echo
    exit 1
}

if [ "$#" -lt 3 ]; then
    usage
fi

operation=$1
username=$2
password=$3

if [[ $operation == "pause" ]]; then
    photon -n target login -u $username -p $password > /dev/null
    # Examines all CLOUD only hosts and stops all the VMs
    echo "Stopping all VMs on all CLOUD only hosts"
    host_ids=$(photon -n host list | awk '{ print $1 }')
    for host_id in $host_ids; do
        host_type=$(photon -n host show $host_id | awk '{ print $5 }')
        if [ $host_type == "CLOUD" ]; then
            vm_ids=$(photon -n host list-vms $host_id | awk '{print $1}')
            for vm_id in $vm_ids; do
                vm_state=$(photon -n vm show $vm_id)
                if [[ $vm_state == *"STARTED"* ]]; then
                    echo "Stopping vm $vm_id"
                    photon -n vm stop $vm_id
                fi
            done
        fi
    done

    photon -n target login -u $username -p $password > /dev/null
    # Pause Photon Controller
    photon -n deployment pause > /dev/null

    # Wait for started tasks to finish
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
        photon -n target login -u $username -p $password > /dev/null
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
    echo "Photon Controller is paused"
fi

if [[ $operation == "resume" ]]; then
    photon -n target login -u $username -p $password > /dev/null
    # Resume Photon Controller
    photon -n deployment resume > /dev/null

    echo "Photon Controller has resumed, it may take one or two minutes for CLOUD hosts to accept commands"
fi
