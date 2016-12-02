#!/bin/bash -e

# The default NSX version is 1.0.1
NSX_DOWNLOADS_DIR=${NSX_DOWNLOADS_DIR:="/tmp/nsx-downloads"}
NSX_MANAGER_OVA_FILE=$NSX_DOWNLOADS_DIR/nsx-manager.ova
NSX_CONTROLLER_OVA_FILE=$NSX_DOWNLOADS_DIR/nsx-controller.ova
NSX_EDGE_OVA_FILE=$NSX_DOWNLOADS_DIR/nsx-edge.ova

function check_tool() {
  cmd=${1}
  which "${cmd}" > /dev/null || {
    echo "Can't find ${cmd} in PATH. Please install and retry."
    exit 1
  }
}

function download_file() {
  url=${1}
  file=${2}

  if [[ ! -s $file ]]; then
    if [[ ! -n $url ]]; then
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

function install_nsx_manager() {
  echo "=== Install NSX Manager ==="

  # Share the common host variables if installing NSX components on the same host.
  NSX_MANAGER_HOST_IP=${NSX_MANAGER_HOST_IP:=$NSX_HOST_COMMON_IP}
  NSX_MANAGER_HOST_USERNAME=${NSX_MANAGER_HOST_USERNAME:=$NSX_HOST_COMMON_USERNAME}
  NSX_MANAGER_HOST_PASSWORD=${NSX_MANAGER_HOST_PASSWORD:=$NSX_HOST_COMMON_PASSWORD}
  NSX_MANAGER_HOST_DATASTORE=${NSX_MANAGER_HOST_DATASTORE:=$NSX_HOST_COMMON_DATASTORE}
  NSX_MANAGER_HOST_NETWORK=${NSX_MANAGER_HOST_NETWORK:=$NSX_HOST_COMMON_NETWORK0}

  # Some network settings are also commonly shared among NSX components.
  NSX_MANAGER_DOMAIN=${NSX_MANAGER_DOMAIN:=$NSX_COMMON_DOMAIN}
  NSX_MANAGER_NETMASK=${NSX_MANAGER_NETMASK:=$NSX_COMMON_NETMASK}
  NSX_MANAGER_GATEWAY=${NSX_MANAGER_GATEWAY:=$NSX_COMMON_GATEWAY}
  NSX_MANAGER_DNS=${NSX_MANAGER_DNS:=$NSX_COMMON_DNS}
  NSX_MANAGER_NTP=${NSX_MANAGER_NTP:=$NSX_COMMON_NTP}

  # Password of NSX components can be the same.
  NSX_MANAGER_PASSWORD=${NSX_MANAGER_PASSWORD:=$NSX_COMMON_PASSWORD}

  if [[ ! -n $NSX_MANAGER_HOST_IP ]]; then
    echo "NSX_MANAGER_HOST_IP must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_MANAGER_HOST_USERNAME ]]; then
    echo "NSX_MANAGER_HOST_USERNAME must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_MANAGER_HOST_PASSWORD ]]; then
    echo "NSX_MANAGER_HOST_PASSWORD must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_MANAGER_HOST_DATASTORE ]]; then
    echo "NSX_MANAGER_HOST_DATASTORE must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_MANAGER_HOST_NETWORK ]]; then
    echo "NSX_MANAGER_HOST_NETWORK must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_MANAGER_NAME ]]; then
    echo "NSX_MANAGER_NAME must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_MANAGER_IP ]]; then
    echo "NSX_MANAGER_IP must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_MANAGER_NETMASK ]]; then
    echo "NSX_MANAGER_NETMASK must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_MANAGER_GATEWAY ]]; then
    echo "NSX_MANAGER_GATEWAY must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_MANAGER_DNS ]]; then
    echo "NSX_MANAGER_DNS must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_MANAGER_NTP ]]; then
    echo "NSX_MANAGER_NTP must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_MANAGER_PASSWORD ]]; then
    echo "NSX_MANAGER_PASSWORD must be specified."
    exit 1
  fi

  cmd="ovftool --name=\"$NSX_MANAGER_NAME\" --X:injectOvfEnv --X:logFile=ovftool.log --X:logLevel=verbose \
--allowExtraConfig --datastore=\"$NSX_MANAGER_HOST_DATASTORE\" --network=\"$NSX_MANAGER_HOST_NETWORK\" \
--acceptAllEulas --noSSLVerify --diskMode=thin --powerOn --prop:\"nsx_ip_0=$NSX_MANAGER_IP\" \
--prop:\"nsx_netmask_0=$NSX_MANAGER_NETMASK\" --prop:\"nsx_gateway_0=$NSX_MANAGER_GATEWAY\" \
--prop:\"nsx_dns1_0=$NSX_MANAGER_DNS\" --prop:\"nsx_domain_0=$NSX_MANAGER_DOMAIN\" \
--prop:\"nsx_ntp_0=$NSX_MANAGER_NTP\" --prop:nsx_isSSHEnabled=True \
--prop:\"nsx_passwd_0=$NSX_MANAGER_PASSWORD\" --prop:\"nsx_cli_passwd_0=$NSX_MANAGER_PASSWORD\" \
--prop:\"nsx_hostname=$NSX_MANAGER_NAME\" \"$NSX_MANAGER_OVA_FILE\" \
vi://$NSX_MANAGER_HOST_USERNAME:$NSX_MANAGER_HOST_PASSWORD@$NSX_MANAGER_HOST_IP"

  echo "Executing $cmd"
  eval $cmd
}

function install_nsx_controller {
  echo "=== Install NSX Controller ==="

  # Share the common host variables if installing NSX components on the same host.
  NSX_CONTROLLER_HOST_IP=${NSX_CONTROLLER_HOST_IP:=$NSX_HOST_COMMON_IP}
  NSX_CONTROLLER_HOST_USERNAME=${NSX_CONTROLLER_HOST_USERNAME:=$NSX_HOST_COMMON_USERNAME}
  NSX_CONTROLLER_HOST_PASSWORD=${NSX_CONTROLLER_HOST_PASSWORD:=$NSX_HOST_COMMON_PASSWORD}
  NSX_CONTROLLER_HOST_DATASTORE=${NSX_CONTROLLER_HOST_DATASTORE:=$NSX_HOST_COMMON_DATASTORE}
  NSX_CONTROLLER_HOST_NETWORK=${NSX_CONTROLLER_HOST_NETWORK:=$NSX_HOST_COMMON_NETWORK0}

  # Some network settings are also commonly shared among NSX components.
  NSX_CONTROLLER_DOMAIN=${NSX_CONTROLLER_DOMAIN:=$NSX_COMMON_DOMAIN}
  NSX_CONTROLLER_NETMASK=${NSX_CONTROLLER_NETMASK:=$NSX_COMMON_NETMASK}
  NSX_CONTROLLER_GATEWAY=${NSX_CONTROLLER_GATEWAY:=$NSX_COMMON_GATEWAY}
  NSX_CONTROLLER_DNS=${NSX_CONTROLLER_DNS:=$NSX_COMMON_DNS}
  NSX_CONTROLLER_NTP=${NSX_CONTROLLER_NTP:=$NSX_COMMON_NTP}

  # Password of NSX components can be the same.
  NSX_CONTROLLER_PASSWORD=${NSX_CONTROLLER_PASSWORD:=$NSX_COMMON_PASSWORD}

  if [[ ! -n $NSX_CONTROLLER_HOST_IP ]]; then
    echo "NSX_CONTROLLER_HOST_IP must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_CONTROLLER_HOST_USERNAME ]]; then
    echo "NSX_CONTROLLER_HOST_USERNAME must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_CONTROLLER_HOST_PASSWORD ]]; then
    echo "NSX_CONTROLLER_HOST_PASSWORD must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_CONTROLLER_HOST_DATASTORE ]]; then
    echo "NSX_CONTROLLER_HOST_DATASTORE must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_CONTROLLER_HOST_NETWORK ]]; then
    echo "NSX_CONTROLLER_HOST_NETWORK must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_CONTROLLER_NAME ]]; then
    echo "NSX_CONTROLLER_NAME must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_CONTROLLER_IP ]]; then
    echo "NSX_CONTROLLER_IP must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_CONTROLLER_NETMASK ]]; then
    echo "NSX_CONTROLLER_NETMASK must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_CONTROLLER_GATEWAY ]]; then
    echo "NSX_CONTROLLER_GATEWAY must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_CONTROLLER_DNS ]]; then
    echo "NSX_CONTROLLER_DNS must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_CONTROLLER_NTP ]]; then
    echo "NSX_CONTROLLER_NTP must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_CONTROLLER_PASSWORD ]]; then
    echo "NSX_CONTROLLER_PASSWORD must be specified."
    exit 1
  fi

  cmd="ovftool --name=\"$NSX_CONTROLLER_NAME\" --X:injectOvfEnv --X:logFile=ovftool.log --X:logLevel=verbose \
--allowExtraConfig --datastore=\"$NSX_CONTROLLER_HOST_DATASTORE\" --network=\"$NSX_CONTROLLER_HOST_NETWORK\" \
--acceptAllEulas --noSSLVerify --diskMode=thin --powerOn --prop:\"nsx_ip_0=$NSX_CONTROLLER_IP\" \
--prop:\"nsx_netmask_0=$NSX_CONTROLLER_NETMASK\" --prop:\"nsx_gateway_0=$NSX_CONTROLLER_GATEWAY\" \
--prop:\"nsx_dns1_0=$NSX_CONTROLLER_DNS\" --prop:\"nsx_domain_0=$NSX_CONTROLLER_DOMAIN\" \
--prop:\"nsx_ntp_0=$NSX_CONTROLLER_NTP\" --prop:nsx_isSSHEnabled=True \
--prop:\"nsx_passwd_0=$NSX_CONTROLLER_PASSWORD\" --prop:\"nsx_cli_passwd_0=$NSX_CONTROLLER_PASSWORD\" \
--prop:\"nsx_hostname=$NSX_CONTROLLER_NAME\" \"$NSX_CONTROLLER_OVA_FILE\" \
vi://$NSX_CONTROLLER_HOST_USERNAME:$NSX_CONTROLLER_HOST_PASSWORD@$NSX_CONTROLLER_HOST_IP"

  echo "Executing $cmd"
  eval $cmd
}

function install_nsx_edge {
  echo "=== Install NSX Edge ==="

  # Share the common host variables if installing NSX components on the same host.
  NSX_EDGE_HOST_IP=${NSX_EDGE_HOST_IP:=$NSX_HOST_COMMON_IP}
  NSX_EDGE_HOST_USERNAME=${NSX_EDGE_HOST_USERNAME:=$NSX_HOST_COMMON_USERNAME}
  NSX_EDGE_HOST_PASSWORD=${NSX_EDGE_HOST_PASSWORD:=$NSX_HOST_COMMON_PASSWORD}
  NSX_EDGE_HOST_DATASTORE=${NSX_EDGE_HOST_DATASTORE:=$NSX_HOST_COMMON_DATASTORE}
  NSX_EDGE_HOST_NETWORK0=${NSX_EDGE_HOST_NETWORK0:=$NSX_HOST_COMMON_NETWORK0}
  NSX_EDGE_HOST_NETWORK1=${NSX_EDGE_HOST_NETWORK1:=$NSX_HOST_COMMON_NETWORK1}
  NSX_EDGE_HOST_NETWORK2=${NSX_EDGE_HOST_NETWORK2:=$NSX_HOST_COMMON_NETWORK2}
  NSX_EDGE_HOST_NETWORK3=${NSX_EDGE_HOST_NETWORK3:=$NSX_HOST_COMMON_NETWORK3}

  # Some network settings are also commonly shared among NSX components.
  NSX_EDGE_DOMAIN=${NSX_EDGE_DOMAIN:=$NSX_COMMON_DOMAIN}
  NSX_EDGE_NETMASK=${NSX_EDGE_NETMASK:=$NSX_COMMON_NETMASK}
  NSX_EDGE_GATEWAY=${NSX_EDGE_GATEWAY:=$NSX_COMMON_GATEWAY}
  NSX_EDGE_DNS=${NSX_EDGE_DNS:=$NSX_COMMON_DNS}
  NSX_EDGE_NTP=${NSX_EDGE_NTP:=$NSX_COMMON_NTP}

  # Password of NSX components can be the same.
  NSX_EDGE_PASSWORD=${NSX_EDGE_PASSWORD:=$NSX_COMMON_PASSWORD}

  if [[ ! -n $NSX_EDGE_HOST_IP ]]; then
    echo "NSX_EDGE_HOST_IP must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_EDGE_HOST_USERNAME ]]; then
    echo "NSX_EDGE_HOST_USERNAME must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_EDGE_HOST_PASSWORD ]]; then
    echo "NSX_EDGE_HOST_PASSWORD must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_EDGE_HOST_DATASTORE ]]; then
    echo "NSX_EDGE_HOST_DATASTORE must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_EDGE_HOST_NETWORK0 ]]; then
    echo "NSX_EDGE_HOST_NETWORK0 must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_EDGE_HOST_NETWORK1 ]]; then
    echo "NSX_EDGE_HOST_NETWORK1 must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_EDGE_HOST_NETWORK2 ]]; then
    echo "NSX_EDGE_HOST_NETWORK2 must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_EDGE_HOST_NETWORK3 ]]; then
    echo "NSX_EDGE_HOST_NETWORK3 must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_EDGE_NAME ]]; then
    echo "NSX_EDGE_NAME must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_EDGE_IP ]]; then
    echo "NSX_EDGE_IP must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_EDGE_NETMASK ]]; then
    echo "NSX_EDGE_NETMASK must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_EDGE_GATEWAY ]]; then
    echo "NSX_EDGE_GATEWAY must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_EDGE_DNS ]]; then
    echo "NSX_EDGE_DNS must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_EDGE_NTP ]]; then
    echo "NSX_EDGE_NTP must be specified."
    exit 1
  fi

  if [[ ! -n $NSX_EDGE_PASSWORD ]]; then
    echo "NSX_EDGE_PASSWORD must be specified."
    exit 1
  fi

  cmd="ovftool --name=\"$NSX_EDGE_NAME\" --X:injectOvfEnv --X:logFile=ovftool.log --X:logLevel=verbose \
--allowExtraConfig --datastore=\"$NSX_EDGE_HOST_DATASTORE\" --net:\"Network 0=$NSX_EDGE_HOST_NETWORK0\" \
--net:\"Network 1=$NSX_EDGE_HOST_NETWORK1\" --net:\"Network 2=$NSX_EDGE_HOST_NETWORK2\" \
--net:\"Network 3=$NSX_EDGE_HOST_NETWORK3\" --acceptAllEulas --noSSLVerify --diskMode=thin --powerOn \
--prop:\"nsx_ip_0=$NSX_EDGE_IP\" --prop:\"nsx_netmask_0=$NSX_EDGE_NETMASK\" --prop:\"nsx_gateway_0=$NSX_EDGE_GATEWAY\" \
--prop:\"nsx_dns1_0=$NSX_EDGE_DNS\" --prop:\"nsx_domain_0=$NSX_EDGE_DOMAIN\" \
--prop:\"nsx_ntp_0=$NSX_EDGE_NTP\" --prop:nsx_isSSHEnabled=True \
--prop:\"nsx_passwd_0=$NSX_EDGE_PASSWORD\" --prop:\"nsx_cli_passwd_0=$NSX_EDGE_PASSWORD\" \
--prop:\"nsx_hostname=$NSX_EDGE_NAME\" \"$NSX_EDGE_OVA_FILE\" \
vi://$NSX_EDGE_HOST_USERNAME:$NSX_EDGE_HOST_PASSWORD@$NSX_EDGE_HOST_IP"

  echo "Executing $cmd"
  eval $cmd
}

check_tool "wget"
check_tool "ovftool"

echo "Stage 1: installing NSX"
download_nsx_ovas
install_nsx_manager
install_nsx_controller
install_nsx_edge
