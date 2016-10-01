#!/usr/bin/python
# -------------------------------------------------------------
# Copyright (C) 2016 VMware, Inc. All rights reserved.
# -------------------------------------------------------------
import httplib
import sys
import pyVmomi
from pyVmomi import vim
import ssl


if len(sys.argv) < 2:
    print "Enter HostIP RootPassword PemPath"
    print "Incorrect set of arguments passed."
    sys.exit()
host_ip = sys.argv[1]
password = sys.argv[2]
pem_path = sys.argv[3]

print "Connecting to hostd CertificateManager"
ssl._create_default_https_context = ssl._create_unverified_context
stub = pyVmomi.SoapStubAdapter(host=host_ip, version="vim.version.version10", path="/sdk")
si = vim.ServiceInstance("ServiceInstance", stub)
content = si.RetrieveServiceContent()
content.sessionManager.Login("root", password)
cert_manager = vim.host.CertificateManager('ha-certificate-manager', stub)

print "Loading certificate from file"
cert = open(pem_path, 'r').read()
print "Installing certificate"
dName = '/CN=%s' % host_ip
csr = cert_manager.GenerateCertificateSigningRequestByDn(dName)
cert_manager.InstallServerCertificate(cert=cert)

print "Notifying affected services"
try:
    cert_manager.NotifyAffectedServices()
except httplib.BadStatusLine:
  pass

print "Success"
