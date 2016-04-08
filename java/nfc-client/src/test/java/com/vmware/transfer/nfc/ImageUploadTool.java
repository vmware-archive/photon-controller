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

import com.vmware.photon.controller.host.gen.CreateImageRequest;
import com.vmware.photon.controller.host.gen.CreateImageResponse;
import com.vmware.photon.controller.host.gen.FinalizeImageRequest;
import com.vmware.photon.controller.host.gen.FinalizeImageResponse;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.ServiceTicketRequest;
import com.vmware.photon.controller.host.gen.ServiceTicketResponse;
import com.vmware.photon.controller.host.gen.ServiceType;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFastFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import java.io.FileInputStream;
import java.util.UUID;

/**
 * A tool for testing image upload directly against agent/host.
 */
public class ImageUploadTool {

  String hostAddress;
  int hostPort = 8835;
  Host.Client hostClient;
  String datastore;
  String filePath;

  private void init() throws TTransportException {
    TTransport transport = new TFastFramedTransport(new TSocket(hostAddress, hostPort));
    transport.open();

    TProtocol proto = new TCompactProtocol(transport);
    TMultiplexedProtocol mproto = new TMultiplexedProtocol(proto, "Host");
    hostClient = new Host.Client(mproto);
  }

  private HostServiceTicket getTicket() throws TException {
    ServiceTicketRequest request = new ServiceTicketRequest();
    request.setDatastore_name(datastore);
    request.setService_type(ServiceType.NFC);
    ServiceTicketResponse response = hostClient.get_service_ticket(request);
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

    CreateImageRequest createRequest = new CreateImageRequest();
    createRequest.setImage_id(imageId);
    createRequest.setDatastore(datastore);
    CreateImageResponse createResponse = hostClient.create_image(createRequest);

    NfcClient nfcClient = new NfcClient(ticket, 0);
    try (FileInputStream inputStream = new FileInputStream(filePath)) {
      nfcClient.putStreamOptimizedDisk(createResponse.getUpload_folder() + "/" + imageId + ".vmdk", inputStream);
    }
    nfcClient.close();

    FinalizeImageRequest finalizeRequest = new FinalizeImageRequest();
    finalizeRequest.setDatastore(datastore);
    finalizeRequest.setImage_id(imageId);
    finalizeRequest.setTmp_image_path(createResponse.getUpload_folder());
    FinalizeImageResponse finalizeResponse = hostClient.finalize_image(finalizeRequest);
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
