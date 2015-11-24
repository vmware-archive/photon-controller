/*
 * Copyright (c) 2015 VMware, Inc. All Rights Reserved.
 */

package com.vmware.photon.controller.model.awsadapter;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.vmware.dcp.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class TestUtils {

    public static AmazonEC2AsyncClient getClient(String privateKeyId, String privateKey,
            String region, boolean isMockRequest) {
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = privateKey;
        creds.privateKeyId = privateKeyId;
        return AWSUtils.getAsyncClient(creds, region, isMockRequest);
    }

    // validate that the passed items are not null
    public static boolean isNull(String... options) {
        for (String option : options) {
            if (option == null) {
                return false;
            }
        }
        return true;
    }
}
