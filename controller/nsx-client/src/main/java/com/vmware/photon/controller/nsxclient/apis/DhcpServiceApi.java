/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.nsxclient.apis;

import com.vmware.photon.controller.nsxclient.RestClient;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayProfile;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayProfileCreateSpec;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayService;
import com.vmware.photon.controller.nsxclient.models.DhcpRelayServiceCreateSpec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.http.HttpStatus;

import java.io.IOException;

/**
 * This is the class of the NSX DHCP Service API implementation.
 */
public class DhcpServiceApi extends NsxClientApi {

  public static final String SERVICE_PROFILES_BASE_PATH = BASE_PATH + "/service-profiles";
  public static final String SERVICES_BASE_PATH = BASE_PATH + "/services";

  /**
   * Constructs a DhcpServiceApi object.
   */
  public DhcpServiceApi(RestClient restClient) {
    super(restClient);
  }

  /**
   * Creates a DHCP relay service profile.
   */
  public void createDhcpRelayProfile(DhcpRelayProfileCreateSpec request,
                                     FutureCallback<DhcpRelayProfile> responseCallback)
      throws IOException {
    postAsync(SERVICE_PROFILES_BASE_PATH,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<DhcpRelayProfile>() {},
        responseCallback);
  }

  /**
   * Gets a DHCP relay service profile.
   */
  public void getDhcpRelayProfile(String id,
                                  FutureCallback<DhcpRelayProfile> responseCallback)
      throws IOException {
    getAsync(SERVICE_PROFILES_BASE_PATH + "/" + id,
        HttpStatus.SC_OK,
        new TypeReference<DhcpRelayProfile>() {},
        responseCallback);
  }

  /**
   * Deletes a DHCP relay service profile.
   */
  public void deleteDhcpRelayProfile(String id,
                                     FutureCallback<Void> responseCallback)
      throws IOException {
    deleteAsync(SERVICE_PROFILES_BASE_PATH + "/" + id,
        HttpStatus.SC_OK,
        responseCallback);
  }

  /**
   * Creates a DHCP relay service.
   */
  public void createDhcpRelayService(DhcpRelayServiceCreateSpec request,
                                     FutureCallback<DhcpRelayService> responseCallback)
      throws IOException {
    postAsync(SERVICES_BASE_PATH,
        serializeObjectAsJson(request),
        HttpStatus.SC_CREATED,
        new TypeReference<DhcpRelayService>() {},
        responseCallback);
  }

  /**
   * Gets a DHCP relay service.
   */
  public void getDhcpRelayService(String id,
                                  FutureCallback<DhcpRelayService> responseCallback)
      throws IOException {
    getAsync(SERVICES_BASE_PATH + "/" + id,
        HttpStatus.SC_OK,
        new TypeReference<DhcpRelayService>() {},
        responseCallback);
  }

  /**
   * Deletes a DHCP relay service.
   */
  public void deleteDhcpRelayService(String id,
                                     FutureCallback<Void> responseCallback)
      throws IOException {
    deleteAsync(SERVICES_BASE_PATH + "/" + id,
        HttpStatus.SC_OK,
        responseCallback);
  }
}
