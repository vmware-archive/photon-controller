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

package com.vmware.photon.controller.apife.backends.clients;

import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.exceptions.external.SpecInvalidException;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.deployer.dcp.task.ValidateHostTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ValidateHostTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;

/**
 * Housekeeper Client Facade that exposes housekeeper functionality via high-level methods,
 * and hides Xenon protocol details.
 */
@Singleton
public class HousekeeperClient {
    private static final Logger logger = LoggerFactory.getLogger(HousekeeperClient.class);

    private static final String COMMA_DELIMITED_REGEX = "\\s*,\\s*";

    private HousekeeperXenonRestClient dcpClient;
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    public HousekeeperClient(HousekeeperXenonRestClient dcpClient, ApiFeXenonRestClient apiFeXenonClient)
            throws URISyntaxException {
        this.dcpClient = dcpClient;
        this.dcpClient.start();
        this.apiFeXenonRestClient = apiFeXenonClient;
        this.apiFeXenonRestClient.start();
    }

    public ValidateHostTaskService.State replicateImage(String datastore, String image, ImageReplication replicationType)
            throws SpecInvalidException {
        ImageSeederService.State postReq = new ImageSeederService.State();
        postReq.image = image;
        postReq.sourceImageDatastore = datastoreId;

        // Create the operation and call for seeding.
        Operation postOperation = Operation
            .createPost(UriUtils.buildUri(dcpHost, ImageSeederServiceFactory.class))
            .setBody(postReq)
            .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
            .setExpiration(Utils.getNowMicrosUtc() + dcpOperationTimeoutMicros)
            .setContextId(LoggingUtils.getRequestId());

        Operation op = ServiceHostUtils.sendRequestAndWait(dcpHost, postOperation, REFERRER_PATH);

        // Return operation id.
        return op.getBody(ImageSeederService.State.class).documentSelfLink;



        ValidateHostTaskService.State state = new ValidateHostTaskService.State();
        state.hostAddress = host.getAddress();
        state.userName = host.getUsername();
        state.password = host.getPassword();
        state.metadata = host.getMetadata();
        state.usageTags = UsageTagHelper.deserializeToStringSet(host.getUsageTags());
        if (state.metadata.containsKey(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES)) {
            String[] allowedDataStores =
                    state.metadata.get(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES).
                            trim().split(COMMA_DELIMITED_REGEX);
            state.dataStores = new HashSet<>();
            Collections.addAll(state.dataStores, allowedDataStores);
        }

        if (state.metadata.containsKey(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS)) {
            String[] allowedNetworks =
                    state.metadata.get(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS).
                            trim().split(COMMA_DELIMITED_REGEX);
            state.networks = new HashSet<>();
            Collections.addAll(state.networks, allowedNetworks);
        }

        // Persist the database ID of the host to the DCP entity so we have a unified ID across the system
        state.documentSelfLink = host.getId();

        Operation operation = dcpClient.post(
            ValidateHostTaskFactoryService.SELF_LINK, state);

        return operation.getBody(ValidateHostTaskService.State.class);
    }

    public ValidateHostTaskService.State getHostCreationStatus(String creationTaskLink)
        throws DocumentNotFoundException {
        Operation operation = dcpClient.get(creationTaskLink);
        return operation.getBody(ValidateHostTaskService.State.class);
    }

}
