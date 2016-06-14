Summary:    The Photon Controller management plane
Name:       photon-controller
Version:    %{pkg_version}
Release:    1%{?dist}
License:    Apache License 2.0
URL:        https://github.com/vmware/photon-controller
Group:      Applications
Vendor:     VMware, Inc.
Distribution: Photon
Source0:    https://github.com/vmware/photon-controller/archive/%{name}-%{version}.tar.gz

BuildRequires:   openjdk
BuildRequires:   openjre
BuildRequires:   apache-maven
Requires:        openjre

%description
Photon Controller is an open-source system for managing hardware, containers, and clusters at scale.

%prep
%setup -q

%build
cd java
./gradlew fatJar -x test

%install
install -vdm 755 %{buildroot}/var/opt/vmware/photon-controller
cp -apr ./java/photon-controller-core/build/libs/ %{buildroot}/var/opt/vmware/photon-controller/
cp -apr ./java/deployer/build/libs/ %{buildroot}/var/opt/vmware/photon-controller/
cp -apr ./java/root-scheduler/build/libs/ %{buildroot}/var/opt/vmware/photon-controller/
cp -apr ./java/housekeeper/build/libs/ %{buildroot}/var/opt/vmware/photon-controller/
cp -apr ./java/cloud-store/build/libs/ %{buildroot}/var/opt/vmware/photon-controller/
cp -apr ./java/api-frontend/management/build/libs/ %{buildroot}/var/opt/vmware/photon-controller/

%files
%defattr(-,root,root)
/var/opt/vmware/photon-controller/libs/*
