/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.transfer.nfc;

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.ssl.KeyStoreUtils;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.host.gen.CreateImageResponse;
import com.vmware.photon.controller.host.gen.FinalizeImageResponse;
import com.vmware.photon.controller.host.gen.ServiceTicketResponse;

import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;

import javax.net.ssl.SSLContext;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * A tool for testing image upload directly against agent/host.
 */
public class ImageUploadTool {

  String hostAddress;
  int hostPort = 8835;
  HostClient hostClient;
  String datastore;
  String filePath;

  private void init() throws TTransportException, IOException {
    KeyStoreUtils.generateKeys("/etc/vmware/ssl/");
    SSLContext sslContext = KeyStoreUtils.acceptAllCerts(KeyStoreUtils.THRIFT_PROTOCOL);

    ThriftModule thriftModule = new ThriftModule(sslContext);
    hostClient = thriftModule.getHostClientFactory().create();
    hostClient.setIpAndPort(hostAddress, hostPort);
  }

  private HostServiceTicket getTicket() throws TException, RpcException, InterruptedException {
    ServiceTicketResponse response = hostClient.getNfcServiceTicket(datastore);
    com.vmware.photon.controller.resource.gen.HostServiceTicket ticket = response.getTicket();
    HostServiceTicket nfcTicket = new HostServiceTicket();
    nfcTicket.setHost(hostAddress);
    nfcTicket.setPort(ticket.getPort());
    nfcTicket.setSslThumbprint(ticket.getSsl_thumbprint());
    nfcTicket.setService(ticket.getService_type());
    nfcTicket.setServiceVersion(ticket.getService_version());
    nfcTicket.setSessionId(ticket.getSession_id());
    return nfcTicket;
  }

  private void upload(HostServiceTicket ticket) throws Exception {
    String imageId = UUID.randomUUID().toString();
    System.out.println("image-id: " + imageId);

    CreateImageResponse createResponse = hostClient.createImage(imageId, datastore);

    NfcClient nfcClient = new NfcClient(ticket, 0);
    try (FileInputStream inputStream = new FileInputStream(filePath)) {
      nfcClient.putStreamOptimizedDisk(createResponse.getUpload_folder() + "/" + imageId + ".vmdk", inputStream);
    }
    nfcClient.close();

    FinalizeImageResponse finalizeResponse = hostClient.finalizeImage(imageId, datastore,
        createResponse.getUpload_folder());
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      System.out.println("Usage: ImageUploadTool [hostAddress] [datastore] [image file]");
      return;
    }

    ImageUploadTool uploadTool = new ImageUploadTool();
    uploadTool.hostAddress = args[0];
    uploadTool.datastore = args[1];
    uploadTool.filePath = args[2];

    uploadTool.init();
    HostServiceTicket ticket = uploadTool.getTicket();
    uploadTool.upload(ticket);
  }
}
