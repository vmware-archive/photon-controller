#!/bin/bash -xe

admin_password=$1
user_password='Passw0rd!'

lwcli="docker exec lightwave opt/vmware/bin/dir-cli"
lwdnscli="docker exec lightwave opt/vmware/bin/vmdns-cli"

# Add users to Lightwave
# Currently 2 administrators, 1 tenant administrator, 1 project user, and 1 normal user.

$lwcli ssogroup create --name ESXCloudAdmins --password $admin_password
$lwcli user create --account ec-admin --user-password $user_password --first-name ec-admin --last-name ec-admin --password $admin_password
$lwcli group modify --name ESXCloudAdmins --add ec-admin --password $admin_password

$lwcli ssogroup create --name EsxcloudAdmin2Group1 --password $admin_password
$lwcli user create --account ec-admin2 --user-password $user_password --first-name ec-admin2 --last-name ec-admin2 --password $admin_password
$lwcli group modify --name ESXCloudAdmin2Group1 --add ec-admin2 --password $admin_password

$lwcli ssogroup create --name EsxcloudTenantAdminGroup1 --password $admin_password
$lwcli user create --account ec-tenant-admin --user-password $user_password --first-name ec-tenant-admin --last-name ec-tenant-admin --password $admin_password
$lwcli group modify --name EsxcloudTenantAdminGroup1 --add ec-tenant-admin --password $admin_password

$lwcli ssogroup create --name EsxcloudProjectUserGroup1 --password $admin_password
$lwcli user create --account ec-project-user --user-password $user_password --first-name ec-project-user --last-name ec-project-user --password $admin_password
$lwcli group modify --name EsxcloudProjectUserGroup1 --add ec-project-user --password $admin_password

$lwcli user create --account ec-user --user-password $user_password --first-name ec-user --last-name ec-user --password $admin_password

# Add DNS forwarder to lightwave VM. 10.0.2.3 is a DNS resolver provided by Vagrant.
$lwdnscli add-forwarder 10.0.2.3 --server 127.0.0.1 --username administrator --domain esxcloud --password $admin_password
