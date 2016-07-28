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

package com.vmware.photon.controller.apife.lib;

import com.vmware.transfer.nfc.HostServiceTicket;


/**
 * The Utils class for NfcClient.
 */
public class NfcClientUtils {

  public static HostServiceTicket convertToNfcHostServiceTicket(
      final com.vmware.photon.controller.resource.gen.HostServiceTicket ticket, final String targetHost) {

    HostServiceTicket nfcTicket = new HostServiceTicket();
    nfcTicket.setHost(targetHost);
    nfcTicket.setPort(ticket.getPort());
    nfcTicket.setSslThumbprint(ticket.getSsl_thumbprint());
    nfcTicket.setService(ticket.getService_type());
    nfcTicket.setServiceVersion(ticket.getService_version());
    nfcTicket.setSessionId(ticket.getSession_id());
    return nfcTicket;
  }

}
