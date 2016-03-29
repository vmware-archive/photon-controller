#!/bin/bash -xe

package_dir='/var/esxcloud/packages'
mkdir -p $package_dir

if [ "$1" = 'management-api' ]; then
  cp /archive/management*.tar $package_dir/
  archive="$package_dir/management*.tar"
  install_path='/usr/lib/esxcloud/management-api'
  rm -rf $install_path
  mkdir -p $install_path
  tar xf $archive --strip=1 -C $install_path

elif [ "$1" = 'root-scheduler' ]; then
  cp /archive/root-scheduler*.tar $package_dir/
  archive="$package_dir/root-scheduler*.tar"
  install_path='/usr/lib/esxcloud/root-scheduler'
  rm -rf $install_path
  mkdir -p $install_path
  tar xf $archive --strip=1 -C $install_path

elif [ "$1" = 'housekeeper' ]; then
  cp /archive/housekeeper*.tar $package_dir/
  archive="$package_dir/housekeeper*.tar"
  install_path='/usr/lib/esxcloud/housekeeper'
  clean_sandbox='true'
  dcp_storage_path='/etc/esxcloud/housekeeper/sandbox_16000'
  rm -rf $install_path
  mkdir -p $install_path
  tar xf $archive --strip=1 -C $install_path

  # Clean sandbox if clean_sandbox option is set
  if [ "$clean_sandbox" = 'true' ]; then
    rm -rf $dcp_storage_path
  fi

elif [ "$1" = 'cloud-store' ]; then
  cp /archive/cloud-store*.tar $package_dir/
  archive="$package_dir/cloud-store*.tar"
  install_path='/usr/lib/esxcloud/cloud-store'
  clean_sandbox='true'
  dcp_storage_path='/etc/esxcloud/cloud-store/sandbox_19000'
  rm -rf $install_path
  mkdir -p $install_path
  tar xf $archive --strip=1 -C $install_path

  # Clean sandbox if clean_sandbox option is set
  if [ "$clean_sandbox" = 'true' ]; then
    rm -rf $dcp_storage_path
  fi
elif [ "$1" = 'bare-metal-provisioner' ]; then
  cp /archive/bare-metal-provisioner*.tar $package_dir/
  archive="$package_dir/bare-metal-provisioner*.tar"
  install_path='/usr/lib/esxcloud/bare-metal-provisioner'
  clean_sandbox='true'
  dcp_storage_path='/etc/esxcloud/bare-metal-provisioner/sandbox_21000'
  rm -rf $install_path
  mkdir -p $install_path
  tar xf $archive --strip=1 -C $install_path

  # Clean sandbox if clean_sandbox option is set
  if [ "$clean_sandbox" = 'true' ]; then
    rm -rf $dcp_storage_path
  fi
elif [ "$1" = 'deployer' ]; then
  cp /archive/deployer*.tar $package_dir/
  archive="$package_dir/deployer*.tar"
  install_path='/usr/lib/esxcloud/deployer'
  script_directory="$install_path/scripts"
  script_log_directory='/vagrant/log/script_logs'
  vib_directory='/var/esxcloud/packages'
  clean_sandbox='true'
  dcp_storage_path='/etc/esxcloud/deployer/sandbox_18000'
  config_dir='/etc/esxcloud-deployer/configurations'
  mustache_dir='/etc/esxcloud-deployer/mustache'
  tmp_dir='/tmp/configuration'

  # Clean sandbox if clean_sandbox option is set
  if [ "$clean_sandbox" = 'true' ]; then
    rm -rf $dcp_storage_path
  fi

  # Untar the files into installation path
  rm -rf $install_path
  mkdir -p $install_path
  tar xf $archive --strip=1 -C $install_path

  # Create a script directory and extract jar into it
  mkdir -p $script_directory
  cd $install_path
  $JAVA_HOME/bin/jar xf $install_path/lib/deployer*.jar scripts
  chmod +x scripts/*
  cd $install_path/scripts
  $JAVA_HOME/bin/jar xf $install_path/lib/cm-backend*.jar scripts
  mv scripts clusters

  # Cleanup and create script log directory
  rm -rf $script_log_directory
  mkdir -p $script_log_directory
  chmod 0755 $script_log_directory

  mkdir -p $config_dir
  mkdir -p $mustache_dir

  # Untar all the other services and copy them to configuration directory
  tar --wildcards -xf /archive/management*.tar --strip=1 -C /tmp management*/configuration
  cp -r $tmp_dir/ $config_dir/management-api/
  rm -rf $tmp_dir

  tar --wildcards -xf /archive/root-scheduler*.tar --strip=1 -C /tmp root-scheduler*/configuration
  cp -r $tmp_dir/ $config_dir/root-scheduler/
  rm -rf $tmp_dir

  tar --wildcards -xf /archive/housekeeper*.tar --strip=1 -C /tmp housekeeper*/configuration
  cp -r $tmp_dir/ $config_dir/housekeeper/
  rm -rf $tmp_dir

  tar --wildcards -xf /archive/cloud-store*.tar --strip=1 -C /tmp cloud-store*/configuration
  cp -r $tmp_dir/ $config_dir/cloud-store/
  rm -rf $tmp_dir

  tar --wildcards -xf /archive/bare-metal-provisioner*.tar --strip=1 -C /tmp bare-metal-provisioner*/configuration
  cp -r $tmp_dir/ $config_dir/bare-metal-provisioner/
  rm -rf $tmp_dir

  cp -r $install_path/configuration $config_dir/deployer/
  cp -r $install_path/configuration-haproxy/ $config_dir/haproxy/
  cp -r $install_path/configuration-postgresql/ $config_dir/postgresql/
  cp -r $install_path/configuration-zookeeper/ $config_dir/zookeeper/
  cp -r $install_path/configuration-lightwave/ $config_dir/lightwave/
  cp -r $install_path/configuration-management-ui/ $config_dir/management-ui/

  cp -r $config_dir/* $mustache_dir
else

  echo "No command specified"
  exit 1
fi
