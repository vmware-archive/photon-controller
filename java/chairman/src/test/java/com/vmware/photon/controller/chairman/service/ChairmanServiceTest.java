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

package com.vmware.photon.controller.chairman.service;

import com.vmware.photon.controller.api.AgentState;
import com.vmware.photon.controller.chairman.Config;
import com.vmware.photon.controller.chairman.gen.RegisterHostRequest;
import com.vmware.photon.controller.chairman.gen.RegisterHostResponse;
import com.vmware.photon.controller.chairman.gen.RegisterHostResultCode;
import com.vmware.photon.controller.chairman.gen.ReportMissingRequest;
import com.vmware.photon.controller.chairman.gen.ReportResurrectedRequest;
import com.vmware.photon.controller.chairman.gen.UnregisterHostRequest;
import com.vmware.photon.controller.chairman.gen.UnregisterHostResponse;
import com.vmware.photon.controller.chairman.gen.UnregisterHostResultCode;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.zookeeper.DataDictionary;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.DatastoreType;
import com.vmware.photon.controller.resource.gen.NetworkType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;

import org.apache.thrift.TSerializer;
import org.hamcrest.Matchers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Tests {@link ChairmanService}.
 */
public class ChairmanServiceTest extends PowerMockTestCase {

  @Mock
  private ScheduledExecutorService executorService;

  @Mock
  private DataDictionary configDict;

  @Mock
  private DataDictionary rolesDict;

  @Mock
  private DcpRestClient dcpRestClient;

  @Mock
  private BuildInfo buildInfo;

  @Mock
  private Config config;

  @Captor
  private ArgumentCaptor<List<String>> missingCapture;

  private ChairmanService service;

  private Set<Datastore> datastores;

  private Set<Network> networks;

  public static RegisterHostRequest createRegReq(Set<Datastore> datastores) {
    return createRegReq(datastores, new LinkedHashSet<>(), null);
  }

  public static RegisterHostRequest createRegReq(Set<Datastore> datastores, Set<Network> networks,
                                                 Set<String> imageDatastoreIds) {
    return createRegReq("host", "DefaultAZ", datastores, networks, "192.168.0.1", 22,
                        imageDatastoreIds);
  }

  public static RegisterHostRequest createRegReq(String id, String fd, Set<Datastore> datastores,
                                                 Set<Network> networks, String addr, int port,
                                                 Set<String> imageDatastoreIds) {
    RegisterHostRequest request = new RegisterHostRequest();

    HostConfig hostConfig = new HostConfig();
    hostConfig.setAgent_id(id);
    hostConfig.setAddress(new ServerAddress("192.168.0.1", 22));
    hostConfig.setAvailability_zone("DefaultAZ");
    if (!datastores.isEmpty()) {
      ArrayList<com.vmware.photon.controller.resource.gen.Datastore> ds =
          new ArrayList<com.vmware.photon.controller.resource.gen.Datastore>();
      for (Datastore datastore : datastores) {
        com.vmware.photon.controller.resource.gen.Datastore newDs =
            new com.vmware.photon.controller.resource.gen.Datastore(datastore.getId());
        // Use ID as the name to make it easy to test
        newDs.setName(datastore.getId());
        newDs.setType(datastore.getType());
        newDs.setTags(datastore.getTags());
        ds.add(newDs);
      }
      hostConfig.setDatastores(ds);
      // use the first datastore as the image datastore
      hostConfig.setImage_datastore_ids(imageDatastoreIds);
    }
    if (!networks.isEmpty()) {
      ArrayList<com.vmware.photon.controller.resource.gen.Network> reqnetworks =
          new ArrayList<com.vmware.photon.controller.resource.gen.Network>();
      for (Network net : networks) {
        com.vmware.photon.controller.resource.gen.Network network =
            new com.vmware.photon.controller.resource.gen.Network(net.getId());
        network.setTypes(net.getType());
        reqnetworks.add(network);
      }
      hostConfig.setNetworks(reqnetworks);
    }
    request.setConfig(hostConfig);
    return request;
  }

  public static byte[] serialize(HostConfig config) throws Exception {
    TSerializer serializer = new TSerializer();
    return serializer.serialize(config);
  }

  @BeforeMethod
  public void setUp() {
    service = new ChairmanService(configDict, dcpRestClient, buildInfo, config);
    this.datastores = new LinkedHashSet<>();
    this.networks = new LinkedHashSet<>();
  }

  @Test
  public void testSimpleRegistration() throws Throwable {
    // Set up initial host state in cloudstore.
    String hostId = "host1";
    String link = service.getHostDocumentLink(hostId);
    DatastoreService.State ds1 = new DatastoreService.State();
    ds1.id = "ds1";
    ds1.name = "ds1";
    ds1.type = "SHARED_VMFS";
    ds1.tags = new LinkedHashSet<>();

    DatastoreService.State ds2 = new DatastoreService.State();
    ds2.id = "ds2";
    ds2.name = "ds2";
    ds2.type = "SHARED_VMFS";
    ds2.tags = new LinkedHashSet<>();

    String dsLink1 = DatastoreServiceFactory.getDocumentLink(ds1.id);
    String dsLink2 = DatastoreServiceFactory.getDocumentLink(ds2.id);
    HostService.State hostState = new HostService.State();
    hostState.agentState = AgentState.MISSING;
    Operation result = mock(Operation.class);
    when(result.getBody(HostService.State.class)).thenReturn(hostState);
    when(dcpRestClient.get(link)).thenReturn(result);

    datastores.add(new Datastore(ds1.id, DatastoreType.SHARED_VMFS));
    datastores.add(new Datastore(ds2.id, DatastoreType.SHARED_VMFS));

    // Non-VM networks should get filtered out.
    networks.add(new Network("nw1", Arrays.asList(NetworkType.VM)));
    networks.add(new Network("nw2", Arrays.asList(NetworkType.VM, NetworkType.VMOTION)));
    networks.add(new Network("nw3", Arrays.asList(NetworkType.MANAGEMENT, NetworkType.VMOTION)));
    RegisterHostRequest request = createRegReq(datastores, networks, new LinkedHashSet<>(Arrays.asList("ds1", "ds2")));
    request.setId(hostId);
    request.getConfig().setAgent_id(hostId);
    RegisterHostResponse response = service.register_host(request);
    assertThat(response.getResult(), Matchers.is(RegisterHostResultCode.OK));

    verify(configDict).write(hostId, serialize(request.getConfig()));

    // Verify that patch gets called with "READY" state.
    ArgumentCaptor<String> arg1 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<ServiceDocument> arg2 = ArgumentCaptor.forClass(ServiceDocument.class);
    verify(dcpRestClient, times(3)).patch(arg1.capture(), arg2.capture());
    assertThat(arg1.getAllValues().get(0), is(link));
    HostService.State newState = (HostService.State) (arg2.getAllValues().get(0));
    assertThat(newState.agentState, is(AgentState.ACTIVE));
    assertThat(newState.reportedDatastores, containsInAnyOrder("ds1", "ds2"));
    assertThat(newState.reportedNetworks, containsInAnyOrder("nw1", "nw2"));
    assertThat(newState.reportedImageDatastores, containsInAnyOrder("ds1", "ds2"));

    // Verify that the isImageDatastore flag gets set on ds1 and ds2.
    assertThat(arg1.getAllValues().get(1), is(dsLink1));
    DatastoreService.State newDsState = (DatastoreService.State) (arg2.getAllValues().get(1));
    assertThat(newDsState.isImageDatastore, is(true));
    assertThat(arg1.getAllValues().get(2), is(dsLink2));
    newDsState = (DatastoreService.State) (arg2.getAllValues().get(2));
    assertThat(newDsState.isImageDatastore, is(true));

    // Verify that chairman attempted to create datastore documents.
    arg1 = ArgumentCaptor.forClass(String.class);
    arg2 = ArgumentCaptor.forClass(ServiceDocument.class);
    verify(dcpRestClient, times(2)).post(arg1.capture(), arg2.capture());
    DatastoreService.State actualDs1 = (DatastoreService.State) (arg2.getAllValues().get(0));
    DatastoreService.State actualDs2 = (DatastoreService.State) (arg2.getAllValues().get(1));
    assertThat(arg1.getAllValues(), contains(DatastoreServiceFactory.SELF_LINK, DatastoreServiceFactory.SELF_LINK));
    verifyDatastore(ds1, actualDs1);
    verifyDatastore(ds2, actualDs2);
  }

  void verifyDatastore(DatastoreService.State expected, DatastoreService.State actual) {
    assertThat(actual.id, is(expected.id));
    assertThat(actual.name, is(expected.name));
    assertThat(actual.type, is(expected.type));
    assertThat(actual.tags, is(expected.tags));
    assertThat(actual.documentSelfLink, is(expected.id));
  }

  /**
   * Verify that reportedDatastores/reportedImageDatastores/reportedNetworks
   * don't get reset when datastores/imageDatastores/networks are null.
   */
  @Test
  public void testSetHostStateEmpty() throws Throwable {
    service.setHostState("host-id", AgentState.ACTIVE, null, null, null);
    ArgumentCaptor<String> arg1 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<ServiceDocument> arg2 = ArgumentCaptor.forClass(ServiceDocument.class);
    verify(dcpRestClient).patch(arg1.capture(), arg2.capture());
    HostService.State patch = (HostService.State) arg2.getValue();
    assertThat(patch.agentState, is(AgentState.ACTIVE));
    assertThat(patch.reportedDatastores, is(nullValue()));
    assertThat(patch.reportedImageDatastores, is(nullValue()));
    assertThat(patch.reportedNetworks, is(nullValue()));
    verifyNoMoreInteractions(dcpRestClient);
  }

  @Test
  public void testSimpleRegistrationFail() throws Exception {
    datastores.add(new Datastore("ds1", DatastoreType.SHARED_VMFS));
    RegisterHostRequest request = createRegReq(datastores, networks, new LinkedHashSet<>(Arrays.asList("ds1")));
    request.setId("host1");
    request.getConfig().setAgent_id("host1");

    doThrow(new Exception()).when(configDict).write("host1", serialize(request.getConfig()));
    RegisterHostResponse response = service.register_host(request);
    assertThat(response.getResult(), is(RegisterHostResultCode.NOT_IN_MAJORITY));
  }

  @Test
  public void testUnregisterHost() throws Exception {
    UnregisterHostRequest request = new UnregisterHostRequest();
    request.setId("host1");
    UnregisterHostResponse response = service.unregister_host(request);

    assertThat(response.getResult(), Matchers.is(UnregisterHostResultCode.OK));
    verify(configDict).write("host1", null);
  }

  @Test
  public void testUnregisterHostFail() throws Exception {
    UnregisterHostRequest request = new UnregisterHostRequest();
    request.setId("host1");

    doThrow(new Exception()).when(configDict).write("host1", null);
    UnregisterHostResponse response = service.unregister_host(request);
    assertThat(response.getResult(), is(UnregisterHostResultCode.NOT_IN_MAJORITY));
  }

  private ReportMissingRequest createMissingChildrenReport(List<String> missingHostIds,
                                                           List<String> missingSchedulerIds) {
    ReportMissingRequest request = new ReportMissingRequest();
    request.setScheduler_id("reporter");

    for (String hostId : missingHostIds) {
      request.addToHosts(hostId);
    }

    for (String schedulerId : missingSchedulerIds) {
      request.addToSchedulers(schedulerId);
    }

    return request;
  }

  private ReportResurrectedRequest createResurrectedRequest(List<String> resurrectedHostIds,
                                                            List<String> resurrectedSchedulerIds) {
    ReportResurrectedRequest request = new ReportResurrectedRequest();
    request.setScheduler_id("reporter");

    for (String hostId : resurrectedHostIds) {
      request.addToHosts(hostId);
    }

    for (String schedulerId : resurrectedSchedulerIds) {
      request.addToSchedulers(schedulerId);
    }

    return request;
  }
}
