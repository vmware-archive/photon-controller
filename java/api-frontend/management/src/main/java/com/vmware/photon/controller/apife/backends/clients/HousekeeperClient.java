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

import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.housekeeper.xenon.ImageReplicatorService;
import com.vmware.photon.controller.housekeeper.xenon.ImageReplicatorServiceFactory;
import com.vmware.photon.controller.housekeeper.xenon.ImageSeederService;
import com.vmware.photon.controller.housekeeper.xenon.ImageSeederServiceFactory;
import com.vmware.xenon.common.Operation;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;

/**
 * Housekeeper Client Facade that exposes housekeeper functionality via high-level methods,
 * and hides Xenon protocol details.
 */
@Singleton
public class HousekeeperClient {
    private static final Logger logger = LoggerFactory.getLogger(HousekeeperClient.class);

    private static final String COMMA_DELIMITED_REGEX = "\\s*,\\s*";

    private HousekeeperXenonRestClient housekeeperXenonClient;
    private ApiFeXenonRestClient apiFeXenonRestClient;

    @Inject
    public HousekeeperClient(HousekeeperXenonRestClient housekeeperXenonClient, ApiFeXenonRestClient apiFeXenonClient)
            throws URISyntaxException {
        this.housekeeperXenonClient = housekeeperXenonClient;
        this.housekeeperXenonClient.start();
        this.apiFeXenonRestClient = apiFeXenonClient;
        this.apiFeXenonRestClient.start();
    }

    public ImageSeederService.State replicateImage(String datastoreId, ImageEntity image) {
        ImageSeederService.State postReq = new ImageSeederService.State();
        postReq.image = image.getId();
        postReq.sourceImageDatastore = datastoreId;

        // Create the operation and call for seeding.
        Operation op = housekeeperXenonClient.post(
            ImageSeederServiceFactory.SELF_LINK, postReq);

        switch (image.getReplicationType()) {
            case ON_DEMAND:
                break;
            case EAGER:
                triggerReplication(datastoreId, image);
                break;
            default:
                throw new IllegalArgumentException("ImageReplicationType unknown: " + image.getReplicationType());
        }

        return op.getBody(ImageSeederService.State.class);
    }

    private ImageReplicatorService.State triggerReplication(String datastoreId, ImageEntity image) {
        // Prepare replication service call.
        ImageReplicatorService.State postReq = new ImageReplicatorService.State();
        postReq.image = image.getId();
        postReq.datastore = datastoreId;

        // Create the operation and call for replication.
        Operation op = housekeeperXenonClient.post(
            ImageReplicatorServiceFactory.SELF_LINK, postReq);

        return op.getBody(ImageReplicatorService.State.class);
    }

}
