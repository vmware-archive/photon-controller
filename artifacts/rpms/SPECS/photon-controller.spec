Summary:    The Photon Controller management plane
Name:       photon-controller
Version:    %{pkg_version}
Release:    1%{?dist}
License:    Apache License 2.0
URL:        https://github.com/vmware/photon-controller
Group:      Applications
Vendor:     VMware, Inc.
Distribution: Photon
Source0:    https://github.com/vmware/photon-controller/archive/%{name}-%{version}.tar
Source1:    photon-controller.service

Requires:        gawk
Requires:        openjre >= 1.8

%define install_dir /usr/lib/esxcloud/photon-controller-core
%define config_jq_filter '.[0] * .[1].dynamicParameters | with_entries(select (.key != "DEPLOYMENT_ID"))'
%define content_file config_values.json
%description
Photon Controller is an open source system for managing hardware, containers, and clusters at scale.

%prep
%setup -q

%build
# Don't build the Java code here, we expect a distTar file being present in SOURCE folder.

%install
install -vdm 755 %{buildroot}%{install_dir}
install -vdm 755 %{buildroot}/etc/systemd/system/photon-controller.service.d
install -vdm 755 %{buildroot}/usr/lib/systemd/system/
install -vdm 755 %{buildroot}/usr/bin/

# Add folder for deployer configuration.
install -vdm 755 %{buildroot}/etc/esxcloud-deployer

# Add folder for photon-controller vibs.
install -vdm 755 %{buildroot}/var/esxcloud/packages

# Add folder for configuration of photon-controller-core.
install -vdm 755 %{buildroot}%{install_dir}/configuration/photon-controller-core

cp /usr/src/photon/SOURCES/photon-controller.service %{buildroot}/usr/lib/systemd/system/
cp /usr/src/photon/SOURCES/*.vib %{buildroot}/var/esxcloud/packages
cd configuration
jq -s %{config_jq_filter} ./installer.json ./photon-controller-core_release.json > %{content_file}
content="`cat %{content_file}`"

# Apply configuration using mustache.
pystache "`cat ./photon-controller-core.yml`" "$content" > photon-controller-core-out.yml
pystache "`cat ./run.sh`" "$content" > run-out.sh
pystache "`cat ./swagger-config.js`" "$content" > swagger-config-out.js
mv ./photon-controller-core-out.yml ./photon-controller-core.yml
mv ./run-out.sh ./run.sh
mv ./swagger-config-out.js ./swagger-config.js
chmod 755 ./run.sh

cp -pr ../* %{buildroot}%{install_dir}
cp ../configuration/*.json ../configuration/*.yml %{buildroot}%{install_dir}/configuration/photon-controller-core/
ln -sf %{install_dir}/bin/photon-controller-core  %{buildroot}/usr/bin/photon-controller-core
ln -sf %{install_dir}/configuration/run.sh  %{buildroot}/usr/bin/run.sh
ln -sf %{install_dir}/configuration %{buildroot}/etc/esxcloud
ln -sf %{install_dir}/configuration %{buildroot}/etc/esxcloud-deployer/configurations

%files
%defattr(-,root,root)
%{install_dir}/*
/var/esxcloud/packages/*
/etc/esxcloud
/etc/esxcloud-deployer/*
/usr/bin/run.sh
/usr/bin/photon-controller-core
/usr/lib/systemd/system/photon-controller.service
%dir /etc/systemd/system/photon-controller.service.d/
