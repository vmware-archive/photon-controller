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

BuildRequires:   openjdk >= 1.8
BuildRequires:   apache-maven
Requires:        openjre >= 1.8

%define install_dir /opt/vmware/photon-controller

%description
Photon Controller is an open source system for managing hardware, containers, and clusters at scale.

%prep
%setup -q

%build
cd java
./gradlew fatJar -x test

%install
install -vdm 755 %{buildroot}%{install_dir}/lib
cp -apr ./java/photon-controller-core/build/libs/photon-controller-core-with-dependencies* %{buildroot}%{install_dir}/lib/

%files
%defattr(-,root,root)
%{install_dir}/*
