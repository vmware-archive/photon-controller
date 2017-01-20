#!/bin/bash -e

# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
# specific language governing permissions and limitations under the License.

# This script can be used to configure NSX manager. It automates the set up
# process described in https://github.com/vmware/photon-controller/wiki/Setting-Up-NSX

# This function checkes whether tool is installed or not

# Default values
NETWORK_MANAGER_USERNAME="admin"
NETWORK_MANAGER_PASSWORD=$NSX_COMMON_PASSWORD
NETWORK_TUNNEL_IP_POOL_CIDR=${NETWORK_TUNNEL_IP_POOL_CIDR:="192.168.150.0/24"}
NETWORK_TUNNEL_IP_POOL_ALLOCATION_START=${NETWORK_TUNNEL_IP_POOL_ALLOCATION_START:="192.168.150.200"}
NETWORK_TUNNEL_IP_POOL_ALLOCATION_END=${NETWORK_TUNNEL_IP_POOL_ALLOCATION_END:="192.168.150.250"}
NETWORK_DHCP_SUBNET_IP_ADDRESS="192.168.1.253"
NETWORK_DHCP_SUBNET_PREFIX_LENGTH="24"
NETWORK_HOST_UPLINK_PNIC=${NETWORK_HOST_UPLINK_PNIC:="vmnic1"}

function check_tool() {
  cmd=${1}
  which "${cmd}" > /dev/null || {
    echo "Can't find ${cmd} in PATH. Please install and retry."
    exit 1
  }
}

# This function verifies whether environment variable is set or not
function check_environment_variable() {
  if [[ -z "${1}" ]]; then
    echo "Environment vaiable ${2} is not set. Please set this variable first."
    exit 1
  fi
}

# This function returns REST response. Currently based on payload it decides whether to use
# GET or POST
function get_rest_response() {
  local api=${1}
  local payload=${2}

  if [[ ! -n "$payload" ]]; then
    curl -sS -k -u $NETWORK_MANAGER_USERNAME:$NETWORK_MANAGER_PASSWORD https://$NSX_MANAGER_IP/$api
  else
    curl -sS -H "Content-Type: application/json" -k -u $NETWORK_MANAGER_USERNAME:$NETWORK_MANAGER_PASSWORD -X POST -d "$payload" https://$NSX_MANAGER_IP/$api
  fi

}

# This function checkes whether rest response has error code. If it has error code, it prints
# the error code and error message with the response body and exits.
function check_for_error() {
  local response=${1}
  local error_code=$(echo $response | jq .error_code | tr -d '""')

  if [ "$error_code" != "null" ]; then
    local error_message=$(echo $response | jq .error_message | tr -d '""')
    echo "NSX configuration setup FAILED"
    echo "error code: $error_code"
    echo "error message: $error_message"
    echo "response:" $response
    exit 1
  fi

}

# This function returns id from rest response
function get_response_id() {
  echo ${1} | jq .id | tr -d '""'
}

# This function creates transport zone in NSX Manager
function create_transport_zone() {
  local transport_zone_json="{ \
    \"display_name\":\"${1}\", \
    \"host_switch_name\":\"${2}\",\
    \"description\":\"${3}\",\
    \"transport_type\":\"${4}\" \
  }"

  get_rest_response "api/v1/transport-zones" "$transport_zone_json"
}

# This function creates an uplink profile in NSX Manager
function create_uplink_profile() {
  local uplink_profile_json="{ \
    \"resource_type\": \"UplinkHostSwitchProfile\", \
    \"display_name\": \"tz-uplink-profile\", \
    \"mtu\": 1600, \
    \"teaming\": { \
        \"standby_list\": [], \
        \"active_list\": [ \
            { \
              \"uplink_name\": \"uplink-1\",
              \"uplink_type\": \"PNIC\"
            } \
          ], \
          \"policy\": \"FAILOVER_ORDER\" \
      }, \
      \"transport_vlan\": 0 \
  }"

  get_rest_response "api/v1/host-switch-profiles" "$uplink_profile_json"
}

# This function creates an IP address pool in NSX Manager
function create_ip_pool() {
  local ip_pool_json="{ \
    \"display_name\": \"${1}\", \
    \"description\": \"IP pool\", \
    \"subnets\": [ \
      { \
          \"dns_nameservers\": [], \
          \"allocation_ranges\": [ \
              { \
                  \"start\": \"$NETWORK_TUNNEL_IP_POOL_ALLOCATION_START\", \
                  \"end\": \"$NETWORK_TUNNEL_IP_POOL_ALLOCATION_END\"
              } \
          ], \
          \"cidr\": \"$NETWORK_TUNNEL_IP_POOL_CIDR\"
      }
    ]
  }"

  get_rest_response "api/v1/pools/ip-pools" "$ip_pool_json"
}

# This function configures a transport node from the edge node
function configure_edge_transport_node() {
  get_edge_node_response=$(get_rest_response "api/v1/fabric/nodes")
  local edge_node_id=$(echo  $get_edge_node_response | jq '.results | .[] | select(.resource_type=="EdgeNode") | .id' | tr -d '""')

  local configure_edge_json="{ \
    \"description\":\"Edge Transport Node\", \
    \"display_name\":\"tn-edge\", \
    \"node_id\":\"$edge_node_id\", \
    \"host_switches\": [ \
      { \
        \"host_switch_name\": \"overlay-host-switch\", \
        \"static_ip_pool_id\": \"${4}\", \
        \"host_switch_profile_ids\": [ \
          { \
            \"key\":\"UplinkHostSwitchProfile\", \
            \"value\":\"${3}\" \
          } \
        ], \
        \"pnics\": [ \
          { \
            \"uplink_name\":\"uplink-1\", \
            \"device_name\":\"fp-eth0\" \
          } \
        ] \
      }, \
      { \
        \"host_switch_name\": \"vlan-host-switch\", \
        \"host_switch_profile_ids\": [ \
          { \
            \"key\":\"UplinkHostSwitchProfile\", \
            \"value\":\"${3}\" \
          } \
        ], \
        \"pnics\": [ \
          { \
            \"uplink_name\":\"uplink-1\", \
            \"device_name\":\"fp-eth1\" \
          } \
        ] \
      } \
    ],\
    \"transport_zone_endpoints\":[ \
      { \
        \"transport_zone_id\":\"${2}\" \
      }, \
      { \
        \"transport_zone_id\":\"${1}\" \
      } \
    ] \
  }"

  get_rest_response "api/v1/transport-nodes" "$configure_edge_json"
}

# This function first creates a cluster profile and uses that to create an edge cluster
function create_edge_cluster() {
  local create_cluster_profile_json="{ \
    \"resource_type\": \"EdgeHighAvailabilityProfile\", \
    \"display_name\": \"edge-cluster-profile\", \
    \"bfd_probe_interval\": 1000, \
    \"bfd_declare_dead_multiple\": 3, \
    \"bfd_allowed_hops\": 1 \
  }"

  local profile_response=$(get_rest_response "api/v1/cluster-profiles" "$create_cluster_profile_json")
  local cluster_profile_id=$(get_response_id "$profile_response")

  local create_edge_cluster_json="{ \
    \"display_name\": \"edge-cluster\", \
    \"cluster_profile_bindings\": [ \
      { \
          \"profile_id\":\"$cluster_profile_id\", \
          \"resource_type\": \"EdgeHighAvailabilityProfile\" \
      } \
    ], \
  \"members\": [ \
      { \
          \"transport_node_id\":\"${1}\"
      } \
    ] \
  }"

  get_rest_response "api/v1/edge-clusters" "$create_edge_cluster_json"
}

# This function creates a router
function create_router() {
  local create_router_json="{ \
    \"resource_type\": \"LogicalRouter\", \
    \"display_name\": \"${1}\", \
    \"edge_cluster_id\": \"${2}\", \
    \"router_type\": \"${3}\", \
    \"high_availability_mode\": \"${4}\" \
  }"

  get_rest_response "api/v1/logical-routers" "$create_router_json"
}

# This function creates a logical switch
function create_logical_switch() {
  local create_logical_switch_json="{ \
    \"transport_zone_id\": \"${1}\", \
    \"replication_mode\": \"MTEP\", \
    \"admin_state\": \"UP\", \
    \"display_name\": \"${2}\", \
    \"vlan\": 0 \
  }"

  get_rest_response "api/v1/logical-switches" "$create_logical_switch_json"
}

# This function creates a logical port
function create_logical_port() {
  local create_logical_port_json="{ \
    \"display_name\": \"${1}\", \
    \"logical_switch_id\": \"${2}\", \
    \"admin_state\": \"UP\"
  }"

  get_rest_response "api/v1/logical-ports" "$create_logical_port_json"
}

# This function creates a uplink router port
function create_uplink_router_port() {
  local create_uplink_router_port_json="{ \
    \"display_name\":\"${1}\", \
    \"resource_type\": \"LogicalRouterUpLinkPort\", \
    \"logical_router_id\": \"${2}\", \
    \"linked_logical_switch_port_id\": { \
        \"target_type\": \"LogicalPort\", \
        \"target_id\": \"${3}\" \
      }, \
    \"edge_cluster_member_index\": [0], \
    \"subnets\": [ \
        { \
          \"ip_addresses\": [ \
            \"${4}\" \
          ], \
          \"prefix_length\": ${5} \
        } \
    ] \
  }"

  get_rest_response "api/v1/logical-router-ports" "$create_uplink_router_port_json"
}

# This function creates a downlink router port
function create_downlink_router_port() {
  local create_downlink_router_port_json="{ \
    \"display_name\":\"${1}\", \
    \"resource_type\": \"LogicalRouterDownLinkPort\", \
    \"logical_router_id\": \"${2}\", \
    \"linked_logical_switch_port_id\": { \
        \"target_type\": \"LogicalPort\", \
        \"target_id\": \"${3}\" \
      }, \
    \"subnets\": [ \
        { \
          \"ip_addresses\": [ \
            \"${4}\" \
          ], \
          \"prefix_length\": ${5} \
        } \
    ] \
  }"

  get_rest_response "api/v1/logical-router-ports" "$create_downlink_router_port_json"
}

# This function creates a link router port on tier-0
function create_T0_link_router_port() {
  local create_T0_link_router_port_json="{ \
      \"display_name\":\"${1}\", \
      \"resource_type\": \"LogicalRouterLinkPortOnTIER0\", \
      \"logical_router_id\": \"${2}\"
    }"

  get_rest_response "api/v1/logical-router-ports" "$create_T0_link_router_port_json"
}

# This function creates a link router port on tier-1 and links to tier-0
function create_T1_link_router_port() {
  local create_T1_link_router_port_json="{ \
      \"display_name\":\"${1}\", \
      \"resource_type\": \"LogicalRouterLinkPortOnTIER1\", \
      \"logical_router_id\": \"${2}\",
      \"linked_logical_router_port_id\":{ \
          \"target_type\": \"LogicalRouterLinkPortOnTIER0\", \
          \"target_id\": \"${3}\" \
        } \
    }"

  get_rest_response "api/v1/logical-router-ports" "$create_T1_link_router_port_json"
}

# This function adds static route.
function add_static_route() {
  local add_static_route_json="{ \
    \"resource_type\": \"StaticRoute\", \
    \"network\": \"0.0.0.0/0\", \
    \"next_hops\": [ \
        { \
          \"administrative_distance\": \"1\", \
          \"ip_address\": \"${2}\" \
        } \
    ] \
  }"

  get_rest_response "api/v1/logical-routers/${1}/routing/static-routes" "$add_static_route_json"
}

# This function updates router advertisement. Currently it enables all advertising properties.
function edit_router_advertisement() {
  local edit_router_advertisement_json="{ \
    \"resource_type\": \"AdvertisementConfig\", \
    \"advertise_nsx_connected_routes\": true, \
    \"advertise_static_routes\": true, \
    \"advertise_nat_routes\": true, \
    \"enabled\": true, \
    \"_revision\": 0 \
  }"

  curl -sS -H "Content-Type: application/json" -k -u $NETWORK_MANAGER_USERNAME:$NETWORK_MANAGER_PASSWORD -X PUT -d "$edit_router_advertisement_json" https://$NSX_MANAGER_IP/api/v1/logical-routers/${1}/routing/advertisement
}

check_tool "jq"
check_environment_variable "$NSX_MANAGER_IP" "NSX_MANAGER_IP"
check_environment_variable "$NSX_COMMON_PASSWORD" "NSX_COMMON_PASSWORD"
check_environment_variable "$NETWORK_T0_SUBNET_IP_ADDRESS" "NETWORK_T0_SUBNET_IP_ADDRESS"
check_environment_variable "$NETWORK_T0_SUBNET_PREFIX_LENGTH" "NETWORK_T0_SUBNET_PREFIX_LENGTH"
check_environment_variable "$NETWORK_T0_GATEWAY" "NETWORK_T0_GATEWAY"

echo "Configuring NSX..."

# Step 1: Creating VLAN transport zone
echo "Step 1: Creating VLAN transport zone"
response=$(create_transport_zone "tz-vlan" "vlan-host-switch" "VLAN Trasnsport Zone" "VLAN")
check_for_error "$response"
vlan_transport_id=$(get_response_id "$response")

# Step 2: Creating OVERLAY transport zone
echo "Step 2: Creating OVERLAY transport zone"
response=$(create_transport_zone "tz-overlay" "overlay-host-switch" "Overlay Trasnsport Zone" "OVERLAY")
check_for_error "$response"
overlay_transport_id=$(get_response_id "$response")

# Step 3: Creating uplink profile
echo "Step 3: Creating uplink profile"
response=$(create_uplink_profile)
check_for_error "$response"
uplink_profile_id=$(get_response_id "$response")

# Step 4: Create IP address pool
echo "Step 4: Creating IP address pool"
response=$(create_ip_pool "tunnel-ip-pool")
check_for_error "$response"
ip_pool_id=$(get_response_id "$response")

# Step 5: Configure Edge Trasnsport node
echo "Step 5: Configuring Edge transport node"
response=$(configure_edge_transport_node $vlan_transport_id $overlay_transport_id $uplink_profile_id $ip_pool_id)
check_for_error "$response"
transport_node_id=$(get_response_id "$response")

# Step 6: Create Edge Cluster
echo "Step 6: Creating Edge cluster"
response=$(create_edge_cluster $transport_node_id)
check_for_error "$response"
edge_cluster_id=$(get_response_id "$response")

# Step 7: Create T0 router
echo "Step 7: Creating T0 router"
response=$(create_router "tier-0-router" $edge_cluster_id "TIER0" "ACTIVE_ACTIVE")
check_for_error "$response"
t0_router_id=$(get_response_id "$response")

# Step 8: Create Logical switch
echo "Step 8: Creating logical switch"
response=$(create_logical_switch $vlan_transport_id "vlan-logical-switch")
check_for_error "$response"
logical_switch_id=$(get_response_id "$response")

# Step 9: Create Logical port
echo "Step 9: Creating logical port"
response=$(create_logical_port "to-tier0-router" $logical_switch_id)
check_for_error "$response"
logical_port_id=$(get_response_id "$response")

# Step 10: Create Router port
echo "Step 10: Creating router port"
response=$(create_uplink_router_port "to-vlan-logical-switch" $t0_router_id $logical_port_id $NETWORK_T0_SUBNET_IP_ADDRESS $NETWORK_T0_SUBNET_PREFIX_LENGTH)
check_for_error "$response"
t0_rourter_port_id=$(get_response_id "$response")

# Step 11: Add static route
echo "Step 11: Adding static route"
response=$(add_static_route $t0_router_id $NETWORK_T0_GATEWAY)
check_for_error "$response"
t0_static_route_id=$(get_response_id "$response")

# DHCP Steps: Create T1 router
echo "DHCP Configure: Creating T1 router"
response=$(create_router "dhcp-server-router" $edge_cluster_id "TIER1" "ACTIVE_STANDBY")
check_for_error "$response"
dhcp_router_id=$(get_response_id "$response")

# DHCP Steps: Create router port in T0 for DHCP router
echo "DHCP Configure: Creating router port in T0 for DHCP router"
response=$(create_T0_link_router_port "link_to_dhcp_port" $t0_router_id)
check_for_error "$response"
t0_link_router_port_for_dhcp_router_id=$(get_response_id "$response")

# DHCP Steps: Create router port in DHCP router and link it to T0 router port
echo "DHCP Configure: Creating router port in DHCP router and link it to T0 router port"
response=$(create_T1_link_router_port "link_to_t0_port" $dhcp_router_id $t0_link_router_port_for_dhcp_router_id)
check_for_error "$response"
dhch_router_port_link_t0_id=$(get_response_id "$response")

# DHCP Steps: Create logical switch to connect to dhcp server
echo "DHCP Configure: Creating logical switch to connect to dhcp server"
response=$(create_logical_switch $overlay_transport_id "dhcp-server-switch")
check_for_error "$response"
dhcp_logical_switch_id=$(get_response_id "$response")

# DHCP Steps: Create logical port to connect to dhcp server
echo "DHCP Configure: Creating logical port to connect to dhcp server"
response=$(create_logical_port "to-dchp-router" $dhcp_logical_switch_id)
check_for_error "$response"
dhcp_logical_port_id=$(get_response_id "$response")

# DHCP Steps: Create router port connect to dhcp server
echo "DHCP Configure: Creating router port to connect to dhcp server"
response=$(create_downlink_router_port "to-dhcp-switch-port" $dhcp_router_id $dhcp_logical_port_id $NETWORK_DHCP_SUBNET_IP_ADDRESS $NETWORK_DHCP_SUBNET_PREFIX_LENGTH)
check_for_error "$response"
dhcp_rourter_port_id=$(get_response_id "$response")

# DHCP Steps: Configure router advertisement configuration
echo "DHCP Configure: Configuring router advertisement configuration"
response=$(edit_router_advertisement $dhcp_router_id)
check_for_error "$response"

echo "NSX configuration completed successfully"
echo
echo
echo "DEPLOYMENT YAML"
echo "================"
echo "---"
echo "deployment:"
echo "  sdn_enabled:" $ENABLE_NSX
echo "  network_manager_address:" $NSX_MANAGER_IP
echo "  network_manager_username:" $NETWORK_MANAGER_USERNAME
echo "  network_manager_password:" $NETWORK_MANAGER_PASSWORD
echo "  network_top_router_id:" $t0_router_id
echo "  network_zone_id:" $vlan_transport_id
echo "  network_edge_ip_pool_id:" $ip_pool_id
echo "  network_host_uplink_pnic:" $NETWORK_HOST_UPLINK_PNIC
