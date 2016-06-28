#!/usr/bin/env bash

password=$1

lwcli="docker exec lightwave opt/vmware/bin/dir-cli"

# Add users to Lightwave
# Currently 2 administrators, 1 tenant administrator, 1 project user, and 1 normal user.

$lwcli ssogroup create --name ESXCloudAdmins --password $1
$lwcli user create --account ec-admin --user-password 'Passw0rd!' --first-name ec-admin --last-name ec-admin --password $1
$lwcli group modify --name ESXCloudAdmins --add ec-admin --password $1

$lwcli ssogroup create --name EsxcloudAdmin2Group1 --password $1
$lwcli user create --account ec-admin2 --user-password 'Passw0rd!' --first-name ec-admin2 --last-name ec-admin2 --password $1
$lwcli group modify --name ESXCloudAdmin2Group1 --add ec-admin2 --password $1

$lwcli ssogroup create --name EsxcloudTenantAdminGroup1 --password $1
$lwcli user create --account ec-tenant-admin --user-password 'Passw0rd!' --first-name ec-tenant-admin --last-name ec-tenant-admin --password $1
$lwcli group modify --name EsxcloudTenantAdminGroup1 --add ec-tenant-admin --password $1

$lwcli ssogroup create --name EsxcloudProjectUserGroup1 --password $1
$lwcli user create --account ec-project-user --user-password 'Passw0rd!' --first-name ec-project-user --last-name ec-project-user --password $1
$lwcli group modify --name EsxcloudProjectUserGroup1 --add ec-project-user --password $1

$lwcli user create --account ec-user --user-password 'Passw0rd!' --first-name ec-user --last-name ec-user --password $1
