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

package com.vmware.photon.controller.chairman;

import com.vmware.photon.controller.api.AgentState;
import com.vmware.photon.controller.chairman.gen.Chairman;
import com.vmware.photon.controller.chairman.gen.RegisterHostRequest;
import com.vmware.photon.controller.chairman.gen.RegisterHostResponse;
import com.vmware.photon.controller.chairman.gen.RegisterHostResultCode;
import com.vmware.photon.controller.chairman.gen.ReportMissingRequest;
import com.vmware.photon.controller.chairman.gen.ReportMissingResponse;
import com.vmware.photon.controller.chairman.gen.ReportResurrectedRequest;
import com.vmware.photon.controller.chairman.gen.ReportResurrectedResponse;
import com.vmware.photon.controller.chairman.gen.UnregisterHostRequest;
import com.vmware.photon.controller.chairman.gen.UnregisterHostResponse;
import com.vmware.photon.controller.chairman.gen.UnregisterHostResultCode;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.dcp.exceptions.DcpException;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.zookeeper.DataDictionary;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.Network;
import com.vmware.photon.controller.resource.gen.NetworkType;
import com.vmware.photon.controller.status.gen.GetStatusRequest;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.xenon.common.Utils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * ChairmanService implements all methods required by Chairman thrift service definition.
 * It also has hooks for service node events (joining/leaving).
 * ChairmanService will also keep track of all Datastores, Networks and fault domains that
 * are registered.
 */
@Singleton
public class ChairmanService implements Chairman.Iface {

  private static final Logger logger = LoggerFactory.getLogger(ChairmanService.class);
  private final DataDictionary configDictionary;
  private final DcpRestClient dcpRestClient;
  private final BuildInfo buildInfo;
  private final Config config;
  private TSerializer serializer = new TSerializer();

  @Inject
  public ChairmanService(@HostConfigRegistry DataDictionary configDictionary,
                         DcpRestClient dcpRestClient,
                         BuildInfo buildInfo,
                         Config config) {
    this.configDictionary = configDictionary;
    this.dcpRestClient = dcpRestClient;
    this.buildInfo = buildInfo;
    this.config = config;
  }

  @Override
  public synchronized Status get_status(GetStatusRequest request) throws TException{
    Status response = new Status();
    response.setType(StatusType.READY);
    response.setBuild_info(buildInfo.toString());

    logger.info("Returning Chairman status {}", response);
    return response;
  }

  /**
   * Returns the cloud store document id for a given host.
   *
   * @param hostId id of a host.
   * @return link to the cloud store host document.
   */
  String getHostDocumentLink(String hostId) {
    return String.format("%s/%s", HostServiceFactory.SELF_LINK, hostId);
  }

  /**
   * Creates datastore documents and set the isImageDatastore field.
   *
   * This method first tries to create DatastoreService documents for all the
   * datastores specified in <code>datastores</code>. It ignores all the errors
   * since the documents might have already been created. Then, it goes through
   * all the datastore IDs in <code>imageDatastores</code> and set
   * {@link com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService.State#isImageDatastore}
   * to true.
   */
  void setDatastoreState(List<Datastore> datastores, List<String> imageDatastores) throws Throwable {
    if (datastores != null) {
      // Create datastore documents.
      for (Datastore datastore : datastores) {
        DatastoreService.State datastoreState = new DatastoreService.State();
        datastoreState.documentSelfLink = datastore.getId();
        datastoreState.id = datastore.getId();
        datastoreState.name = datastore.getName();
        datastoreState.type = datastore.getType().toString();
        datastoreState.tags = datastore.getTags();
        datastoreState.isImageDatastore = false;
        try {
          dcpRestClient.post(DatastoreServiceFactory.SELF_LINK, datastoreState);
        } catch (DcpException | DcpRuntimeException ex) {
          logger.debug("Ignoring datastore document creation failure", ex);
        }
      }
    }

    if (imageDatastores != null) {
      // Set isImageDatastore flag to true.
      for (String datastoreId : imageDatastores) {
        String link = DatastoreServiceFactory.getDocumentLink(datastoreId);
        DatastoreService.State datastoreState = new DatastoreService.State();
        datastoreState.isImageDatastore = true;
        dcpRestClient.patch(link, datastoreState);
      }
    }
  }

  /**
   * Updates the state of a host in cloudstore.
   *
   * This method updates the cloudstore host document. It ignores all the errors
   * from cloudstore, which means that ZooKeeper and cloudstore will be in
   * inconsistent states if this method fails to update cloudstore. The
   * long-term plan is to store the host state only in cloudstore so that we
   * don't have this issue.
   *
   * Another thing to keep in mind is that the deployer doesn't deploy agents
   * in uwsym deployments, so the host documents don't exist. For now this is
   * fine since we simply ignore all the errors, but remember to take care of
   * uwsym deployments once we start handling different errors from cloudstore.
   *
   * @param hostId Id of the host.
   * @param datastores datastores on this host, or null if the datastore list
   *                   doesn't need to be updated.
   * @param networks networks on this host, or null if the network list doesn't
   *                 need to be updated.
   * @param imageDatastores list of image datastore IDs, or null if the image
   *                        datastore list doesn't need to be updated.
   */
  void setHostState(String hostId, AgentState state, List<Datastore> datastores,
                    List<Network> networks, List<String> imageDatastores) throws Throwable {
    String link = null;
    HostService.State hostState = null;

    try {
      link = getHostDocumentLink(hostId);
      hostState = new HostService.State();
      hostState.agentState = state;

      if (datastores != null) {
        hostState.reportedDatastores = new HashSet<>();
        hostState.datastoreServiceLinks = new HashMap<>();
        for (Datastore datastore : datastores) {
          hostState.reportedDatastores.add(datastore.getId());
          hostState.datastoreServiceLinks
              .put(datastore.getName(), DatastoreServiceFactory.getDocumentLink(datastore.getId()));
        }
      }

      if (networks != null) {
        hostState.reportedNetworks = new HashSet<>();
        for (Network network : networks) {
          if (network.getTypes() != null && network.getTypes().contains(NetworkType.VM)) {
            // TEMPORARY WORKAROUND: Currently the portgroup document doesn't
            // contain the network type information, so we are filtering them
            // here so that chairman only sees VM networks while building the
            // scheduler tree.
            hostState.reportedNetworks.add(network.getId());
          }
        }
      }

      if (imageDatastores != null) {
        hostState.reportedImageDatastores = new HashSet<>();
        for (String datastoreId : imageDatastores) {
          hostState.reportedImageDatastores.add(datastoreId);
        }
      }
      dcpRestClient.patch(link, hostState);

      // Update datastore state
      setDatastoreState(datastores, imageDatastores);
      logger.info("Updated {} with new state: {}", link, Utils.toJson(hostState));
    } catch (Throwable ex) {
      logger.warn("Failed to update {} with state: {}", link, Utils.toJson(hostState), ex);
      throw ex;
    }
  }

  @Override
  public synchronized RegisterHostResponse register_host(RegisterHostRequest request) throws TException {
    RegisterHostResponse response = new RegisterHostResponse();

    /* Serialize the hostconfig and persist it to the data dictionary. */
    byte[] serializedHostConfig;
    serializedHostConfig = serializer.serialize(request.getConfig());
    try {
      configDictionary.write(request.getId(), serializedHostConfig);
    } catch (Exception e) {
      logger.error("Failed to register {}", request, e);
      response.setResult(RegisterHostResultCode.NOT_IN_MAJORITY);
      return response;
    }
    response.setResult(RegisterHostResultCode.OK);

    try {
      setHostState(request.getId(), AgentState.ACTIVE,
          request.getConfig().getDatastores(),
          request.getConfig().getNetworks(),
          new ArrayList<>(request.getConfig().getImage_datastore_ids()));
    } catch (Throwable ex) {
      response.setResult(RegisterHostResultCode.SYSTEM_ERROR);
      response.setError(ex.toString());
    }
    logger.info("Registration response: {} , {}", request, response);
    return response;
  }

  @Override
  public ReportResurrectedResponse report_resurrected(ReportResurrectedRequest request) throws TException {
    logger.info("Received resurrected children report: {}", request);
    return new ReportResurrectedResponse();
  }

  @Override
  public ReportMissingResponse report_missing(ReportMissingRequest request) throws TException {
    logger.info("Received missing children report: {}", request);
    return new ReportMissingResponse();
  }

  @Override
  public UnregisterHostResponse unregister_host(UnregisterHostRequest request)
    throws TException {
    UnregisterHostResponse response = new UnregisterHostResponse();

    try {
      // Delete the host id from /hosts
      configDictionary.write(request.getId(), null);
    } catch (Exception e) {
      logger.error("Failed to unregister {}", request, e);
      response.setResult(UnregisterHostResultCode.NOT_IN_MAJORITY);
      return response;
    }

    response.setResult(UnregisterHostResultCode.OK);
    logger.info("Unregistered host: {} , {}", request, response);
    return response;
  }

}
