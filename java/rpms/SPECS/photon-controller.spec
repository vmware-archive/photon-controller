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

BuildRequires:   ruby
Requires:        openjre >= 1.8

%define install_dir /opt/vmware/photon-controller
%define config_jq_filter '.[0] * .[1].dynamicParameters | with_entries(select (.key != "DEPLOYMENT_ID"))'
%define temp_config_file config_values.json
%description
Photon Controller is an open source system for managing hardware, containers, and clusters at scale.

%prep
%setup -q

%build
# Don't build the Java code here, expecting a distTar file present

%install
install -vdm 755 %{buildroot}%{install_dir}
cd configuration
jq -s %{config_jq_filter} ./installer.json ./photon-controller-core_release.json > %{temp_config_file}
mustache %{temp_config_file} ./photon-controller-core.yml > photon-controller-core_release.yml
mustache %{temp_config_file} ./management-api.yml > management-api_release.yml
cp -pr ../* %{buildroot}%{install_dir}

%files
%defattr(-,root,root)
%{install_dir}/*
