#!/bin/bash -e

# The default NSX version is 1.0.1
NSX_DOWNLOADS_DIR=${NSX_DOWNLOADS_DIR:="/tmp/nsx-downloads"}
NSX_MANAGER_OVA_FILE=$NSX_DOWNLOADS_DIR/nsx-manager.ova
NSX_CONTROLLER_OVA_FILE=$NSX_DOWNLOADS_DIR/nsx-controller.ova
NSX_EDGE_OVA_FILE=$NSX_DOWNLOADS_DIR/nsx-edge.ova

NSX_MANAGER_NAME=${NSX_MANAGER_NAME:=nsx-manager}
NSX_CONTROLLER_NAME=${NSX_CONTROLLER_NAME:=nsx-controller}
NSX_EDGE_NAME=${NSX_EDGE_NAME:=nsx-edge}

function print_help()
{
  echo "Usage: $0.sh [OPTIONS]

Script to install NSX.

Options:

  -s              Experimental feature to reduce memory requirement of NSX OVAs
  -h              Print usage
"
}

while getopts "h?s" opt; do
    case "$opt" in
    h|\?)
        print_help
        exit 0
        ;;
    s)  SMALL_MEMORY_OVA=1
    esac
done

shift $((OPTIND-1))
[ "$1" = "--" ] && shift


function check_tool() {
  cmd=${1}
  which "${cmd}" > /dev/null || {
    echo "Can't find ${cmd} in PATH. Please install and retry."
    exit 1
  }
}

function download_file() {
  local url=${1}
  local file=${2}

  if [[ ! -s "$file" ]]; then
    if [[ ! -n "$url" ]]; then
      echo "URL must be specified before downloading."
      exit 1
    fi

    echo "Downloading from $url to $file"
    wget --no-proxy --no-check-certificate -nv $url -O $file
  else
    echo "$file already exists. Skip downloading."
  fi
}

function download_nsx_ovas() {
  echo "=== Download NSX OVA files ==="

  echo "NSX ova files will be saved to $NSX_DOWNLOADS_DIR"
  if [[ ! -d $NSX_DOWNLOADS_DIR ]]; then
    mkdir -p $NSX_DOWNLOADS_DIR
  fi

  download_file "$NSX_MANAGER_OVA_URL" "$NSX_MANAGER_OVA_FILE"
  download_file "$NSX_CONTROLLER_OVA_URL" "$NSX_CONTROLLER_OVA_FILE"
  download_file "$NSX_EDGE_OVA_URL" "$NSX_EDGE_OVA_FILE"
}

function reduce_ova_memory_all() {
  reduce_ova_memory $NSX_MANAGER_OVA_FILE
  reduce_ova_memory $NSX_CONTROLLER_OVA_FILE
}

# To reduce memory requirements of the OVA
# 1. Un-tar the OVA
# 2. Replace 16GB to 4GB in extracted OVF file for memory
# 3. Pack all files back into the OVA
function reduce_ova_memory() {
  echo "Reducing memory requirements for the OVA (experiemental)"
  OVA_FILE=$1
  rm -rf tmp
  mkdir tmp
  tar -xf $OVA_FILE -C tmp
  cp ${OVA_FILE} ${OVA_FILE}.backup

  # TODO: Following statment is the cause of belittlement for this scripts.  It is
  # wild sed statment, which can cause every instance of 16384 to be replaced with
  # 4096, and can kill some innocent installation attempts.  Hopefully there would
  # not be many things in OVF file with that number, but if there are then we are
  # breaking something we don't know. But we know; no risk, no gain!  Fix this as
  # soon as you have nothing else to do. Perhaps replace with awk which you don't
  # know, or rewrite this script in python or perl.
  sed -i -e 's/16384/4096/g' tmp/*.ovf

  rm -rf tmp/*.ovf-e
  (cd tmp; tar -cf $OVA_FILE *.ovf *.mf *.vmdk *.cert)
  rm -rf tmp
}

function enforce_parameter() {
  local value=${1}
  local param_desc=${2}

  if [[ ! -n "$value" ]]; then
    echo "$param_desc must be specified."
    exit 1
  fi
}

function delete_old_vms() {
  echo "Deleting old NSX VMs"
  rm -rf tmp.sh
  cat << EOF > tmp.sh
    vmid=\$(vim-cmd vmsvc/getallvms | grep \$1 | cut -d ' ' -f1)
    vim-cmd vmsvc/power.off \$vmid || true
    vim-cmd vmsvc/unregister \$vmid || true
EOF
  chmod +x tmp.sh
  eval sshpass -p $NSX_HOST_COMMON_PASSWORD scp ./tmp.sh $NSX_HOST_COMMON_USERNAME@$NSX_HOST_COMMON_IP:/tmp/ || true
  eval sshpass -p $NSX_HOST_COMMON_PASSWORD ssh $NSX_HOST_COMMON_USERNAME@$NSX_HOST_COMMON_IP -o StrictHostKeyChecking=no "/tmp/tmp.sh $NSX_MANAGER_NAME"
  eval sshpass -p $NSX_HOST_COMMON_PASSWORD ssh $NSX_HOST_COMMON_USERNAME@$NSX_HOST_COMMON_IP -o StrictHostKeyChecking=no "/tmp/tmp.sh $NSX_CONTROLLER_NAME"
  eval sshpass -p $NSX_HOST_COMMON_PASSWORD ssh $NSX_HOST_COMMON_USERNAME@$NSX_HOST_COMMON_IP -o StrictHostKeyChecking=no "/tmp/tmp.sh $NSX_EDGE_NAME"
  eval sshpass -p $NSX_HOST_COMMON_PASSWORD ssh $NSX_HOST_COMMON_USERNAME@$NSX_HOST_COMMON_IP -o StrictHostKeyChecking=no "rm -rf /tmp/tmp.sh"
  rm -rf tmp.sh
}

function install_nsx_manager() {
  echo "=== Install NSX Manager ==="

  # Share the common host variables if installing NSX components on the same host.
  local host_ip=${NSX_MANAGER_HOST_IP:=$NSX_HOST_COMMON_IP}
  local host_username=${NSX_MANAGER_HOST_USERNAME:=$NSX_HOST_COMMON_USERNAME}
  local host_password=${NSX_MANAGER_HOST_PASSWORD:=$NSX_HOST_COMMON_PASSWORD}
  local host_datastore=${NSX_MANAGER_HOST_DATASTORE:=$NSX_HOST_COMMON_DATASTORE}
  local host_network=${NSX_MANAGER_HOST_NETWORK:=$NSX_HOST_COMMON_NETWORK0}

  # Some network settings are also commonly shared among NSX components.
  local name=${NSX_MANAGER_NAME}
  local ip=$NSX_MANAGER_IP
  local domain=${NSX_MANAGER_DOMAIN:=$NSX_COMMON_DOMAIN}
  local netmask=${NSX_MANAGER_NETMASK:=$NSX_COMMON_NETMASK}
  local gateway=${NSX_MANAGER_GATEWAY:=$NSX_COMMON_GATEWAY}
  local dns=${NSX_MANAGER_DNS:=$NSX_COMMON_DNS}
  local ntp=${NSX_MANAGER_NTP:=$NSX_COMMON_NTP}
  local password=${NSX_MANAGER_PASSWORD:=$NSX_COMMON_PASSWORD}

  enforce_parameter "$host_ip" "NSX manager host IP"
  enforce_parameter "$host_username" "NSX manager host username"
  enforce_parameter "$host_password" "NSX manager host password"
  enforce_parameter "$host_datastore" "NSX manager host datastore"
  enforce_parameter "$host_network" "NSX manager host network"
  enforce_parameter "$name" "NSX manager name"
  enforce_parameter "$ip" "NSX manager IP"
  enforce_parameter "$domain" "NSX manager domain"
  enforce_parameter "$netmask" "NSX manager netmask"
  enforce_parameter "$gateway" "NSX manager gateway"
  enforce_parameter "$dns" "NSX manager DNS"
  enforce_parameter "$ntp" "NSX manager NTP"
  enforce_parameter "$password" "NSX manager password"

  cmd="ovftool --name=\"$name\" --X:injectOvfEnv --X:logFile=ovftool.log --X:logLevel=verbose \
--allowExtraConfig $overwrite --datastore=\"$host_datastore\" --network=\"$host_network\" \
--acceptAllEulas --noSSLVerify --diskMode=thin --powerOn --prop:\"nsx_ip_0=$ip\" \
--prop:\"nsx_netmask_0=$netmask\" --prop:\"nsx_gateway_0=$gateway\" \
--prop:\"nsx_dns1_0=$dns\" --prop:\"nsx_domain_0=$domain\" \
--prop:\"nsx_ntp_0=$ntp\" --prop:nsx_isSSHEnabled=True --prop:nsx_allowSSHRootLogin=True \
--prop:\"nsx_passwd_0=$password\" --prop:\"nsx_cli_passwd_0=$password\" \
--prop:\"nsx_hostname=$name\" \"$NSX_MANAGER_OVA_FILE\" \
vi://$host_username:$host_password@$host_ip"

  echo "Executing $cmd"
  eval $cmd
}

function install_nsx_controller {
  echo "=== Install NSX Controller ==="

  # Share the common host variables if installing NSX components on the same host.
  local host_ip=${NSX_CONTROLLER_HOST_IP:=$NSX_HOST_COMMON_IP}
  local host_username=${NSX_CONTROLLER_HOST_USERNAME:=$NSX_HOST_COMMON_USERNAME}
  local host_password=${NSX_CONTROLLER_HOST_PASSWORD:=$NSX_HOST_COMMON_PASSWORD}
  local host_datastore=${NSX_CONTROLLER_HOST_DATASTORE:=$NSX_HOST_COMMON_DATASTORE}
  local host_network=${NSX_CONTROLLER_HOST_NETWORK:=$NSX_HOST_COMMON_NETWORK0}

  # Some network settings are also commonly shared among NSX components.
  local name=${NSX_CONTROLLER_NAME}
  local ip=$NSX_CONTROLLER_IP
  local domain=${NSX_CONTROLLER_DOMAIN:=$NSX_COMMON_DOMAIN}
  local netmask=${NSX_CONTROLLER_NETMASK:=$NSX_COMMON_NETMASK}
  local gateway=${NSX_CONTROLLER_GATEWAY:=$NSX_COMMON_GATEWAY}
  local dns=${NSX_CONTROLLER_DNS:=$NSX_COMMON_DNS}
  local ntp=${NSX_CONTROLLER_NTP:=$NSX_COMMON_NTP}
  local password=${NSX_CONTROLLER_PASSWORD:=$NSX_COMMON_PASSWORD}

  enforce_parameter "$host_ip" "NSX controller host IP"
  enforce_parameter "$host_username" "NSX controller host username"
  enforce_parameter "$host_password" "NSX controller host password"
  enforce_parameter "$host_datastore" "NSX controller host datastore"
  enforce_parameter "$host_network" "NSX controller host network"
  enforce_parameter "$name" "NSX controller name"
  enforce_parameter "$ip" "NSX controller IP"
  enforce_parameter "$domain" "NSX controller domain"
  enforce_parameter "$netmask" "NSX controller netmask"
  enforce_parameter "$gateway" "NSX controller gateway"
  enforce_parameter "$dns" "NSX controller DNS"
  enforce_parameter "$ntp" "NSX controller NTP"
  enforce_parameter "$password" "NSX controller password"

  cmd="ovftool --name=\"$name\" --X:injectOvfEnv --X:logFile=ovftool.log --X:logLevel=verbose \
--allowExtraConfig $overwrite --datastore=\"$host_datastore\" --network=\"$host_network\" \
--acceptAllEulas --noSSLVerify --diskMode=thin --powerOn --prop:\"nsx_ip_0=$ip\" \
--prop:\"nsx_netmask_0=$netmask\" --prop:\"nsx_gateway_0=$gateway\" \
--prop:\"nsx_dns1_0=$dns\" --prop:\"nsx_domain_0=$domain\" \
--prop:\"nsx_ntp_0=$ntp\" --prop:nsx_isSSHEnabled=True --prop:nsx_allowSSHRootLogin=True \
--prop:\"nsx_passwd_0=$password\" --prop:\"nsx_cli_passwd_0=$password\" \
--prop:\"nsx_hostname=$name\" \"$NSX_CONTROLLER_OVA_FILE\" \
vi://$host_username:$host_password@$host_ip"

  echo "Executing $cmd"
  eval $cmd
}

function install_nsx_edge {
  echo "=== Install NSX Edge ==="

  # Share the common host variables if installing NSX components on the same host.
  local host_ip=${NSX_EDGE_HOST_IP:=$NSX_HOST_COMMON_IP}
  local host_username=${NSX_EDGE_HOST_USERNAME:=$NSX_HOST_COMMON_USERNAME}
  local host_password=${NSX_EDGE_HOST_PASSWORD:=$NSX_HOST_COMMON_PASSWORD}
  local host_datastore=${NSX_EDGE_HOST_DATASTORE:=$NSX_HOST_COMMON_DATASTORE}
  local host_network0=${NSX_EDGE_HOST_NETWORK0:=$NSX_HOST_COMMON_NETWORK0}
  local host_network1=${NSX_EDGE_HOST_NETWORK1:=$NSX_HOST_COMMON_NETWORK1}
  local host_network2=${NSX_EDGE_HOST_NETWORK2:=$NSX_HOST_COMMON_NETWORK2}
  local host_network3=${NSX_EDGE_HOST_NETWORK3:=$NSX_HOST_COMMON_NETWORK3}

  # Some network settings are also commonly shared among NSX components.
  local name=${NSX_EDGE_NAME}
  local ip=$NSX_EDGE_IP
  local domain=${NSX_EDGE_DOMAIN:=$NSX_COMMON_DOMAIN}
  local netmask=${NSX_EDGE_NETMASK:=$NSX_COMMON_NETMASK}
  local gateway=${NSX_EDGE_GATEWAY:=$NSX_COMMON_GATEWAY}
  local dns=${NSX_EDGE_DNS:=$NSX_COMMON_DNS}
  local ntp=${NSX_EDGE_NTP:=$NSX_COMMON_NTP}
  local password=${NSX_EDGE_PASSWORD:=$NSX_COMMON_PASSWORD}

  enforce_parameter "$host_ip" "NSX edge host IP"
  enforce_parameter "$host_username" "NSX edge host username"
  enforce_parameter "$host_password" "NSX edge host password"
  enforce_parameter "$host_datastore" "NSX edge host datastore"
  enforce_parameter "$host_network0" "NSX edge host network0"
  enforce_parameter "$host_network1" "NSX edge host network1"
  enforce_parameter "$host_network2" "NSX edge host network2"
  enforce_parameter "$host_network3" "NSX edge host network3"
  enforce_parameter "$name" "NSX edge name"
  enforce_parameter "$ip" "NSX edge IP"
  enforce_parameter "$domain" "NSX edge domain"
  enforce_parameter "$netmask" "NSX edge netmask"
  enforce_parameter "$gateway" "NSX edge gateway"
  enforce_parameter "$dns" "NSX edge DNS"
  enforce_parameter "$ntp" "NSX edge NTP"
  enforce_parameter "$password" "NSX edge password"

  cmd="ovftool --name=\"$name\" --X:injectOvfEnv --X:logFile=ovftool.log --X:logLevel=verbose \
--allowExtraConfig $overwrite --datastore=\"$host_datastore\" --net:\"Network 0=$host_network0\" \
--net:\"Network 1=$host_network1\" --net:\"Network 2=$host_network2\" \
--net:\"Network 3=$host_network3\" --acceptAllEulas --noSSLVerify --diskMode=thin --powerOn \
--deploymentOption=small \
--prop:\"nsx_ip_0=$ip\" --prop:\"nsx_netmask_0=$netmask\" --prop:\"nsx_gateway_0=$gateway\" \
--prop:\"nsx_dns1_0=$dns\" --prop:\"nsx_domain_0=$domain\" \
--prop:\"nsx_ntp_0=$ntp\" --prop:nsx_isSSHEnabled=True --prop:nsx_allowSSHRootLogin=True \
--prop:\"nsx_passwd_0=$password\" --prop:\"nsx_cli_passwd_0=$password\" \
--prop:\"nsx_hostname=$name\" \"$NSX_EDGE_OVA_FILE\" \
vi://$host_username:$host_password@$host_ip"

  echo "Executing $cmd"
  eval $cmd
}

function provision_nsx() {
  echo "=== Provision NSX ==="

  local manager_ip=$NSX_MANAGER_IP
  local manager_password=${NSX_MANAGER_PASSWORD:=$NSX_COMMON_PASSWORD}
  local controller_ip=$NSX_CONTROLLER_IP
  local controller_password=${NSX_CONTROLLER_PASSWORD:=$NSX_COMMON_PASSWORD}
  local edge_ip=$NSX_EDGE_IP
  local edge_password=${NSX_EDGE_PASSWORD:=$NSX_COMMON_PASSWORD}

  sed -i -e "/^$manager_ip/ d" ~/.ssh/known_hosts || true
  sed -i -e "/^$controller_ip/ d" ~/.ssh/known_hosts || true
  sed -i -e "/^$edge_ip/ d" ~/.ssh/known_hosts || true

  echo "Get NSX manager thumbprint"
  local manager_thumbprint=`eval sshpass -p $manager_password ssh -o StrictHostKeyChecking=no root@$manager_ip "/opt/vmware/nsx-cli/bin/scripts/nsxcli -c \"get certificate api thumbprint\""`

  echo "Join NSX controller to management plane"
  eval sshpass -p $controller_password ssh root@$controller_ip -o StrictHostKeyChecking=no "/opt/vmware/nsx-cli/bin/scripts/nsxcli -c \"join management-plane $manager_ip username admin thumbprint $manager_thumbprint password $manager_password\""
  eval sshpass -p $controller_password ssh root@$controller_ip -o StrictHostKeyChecking=no "/opt/vmware/nsx-cli/bin/scripts/nsxcli -c \"set control-cluster security-model shared-secret secret $controller_password\""
  eval sshpass -p $controller_password ssh root@$controller_ip -o StrictHostKeyChecking=no "/opt/vmware/nsx-cli/bin/scripts/nsxcli -c \"initialize control-cluster\""

  echo "Join NSX edge to management plane"
  eval sshpass -p $edge_password ssh root@$edge_ip -o StrictHostKeyChecking=no "/opt/vmware/nsx-cli/bin/scripts/nsxcli -c \"join management-plane $manager_ip username admin thumbprint $manager_thumbprint password $manager_password\""
}

check_tool "wget"
check_tool "ovftool"
check_tool "sshpass"

if [ "$NSX_OVERWRITE" == "true" ]; then
  overwrite="--overwrite"
fi

echo "Stage 1: installing NSX"
delete_old_vms
download_nsx_ovas

if [ "$SMALL_MEMORY_OVA" == "1" ]; then
  reduce_ova_memory_all
fi

install_nsx_manager
install_nsx_controller
install_nsx_edge

echo "Stage 2: provisioning NSX"
provision_nsx
