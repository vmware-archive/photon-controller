/*
 * Copyright (c) 2015 VMware, Inc. All Rights Reserved.
 */

package com.vmware.photon.controller.model.awsadapter;

import com.amazonaws.AmazonServiceException;
import com.vmware.enatai.provisioning.services.ProvisioningTestCase;
import com.vmware.dcp.common.CommandLineArgumentParser;
import com.vmware.dcp.common.test.VerificationHost;
import com.vmware.dcp.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TestAWSUtils extends ProvisioningTestCase {
    /*
    * This test requires the following three command line variables.
    * If they are not present the tests will be ignored
    * Pass them into the test with the -Ddcp.variable=value syntax
    * i.e -Ddcp.privateKey="XXXXXXXXXXXXXXXXXXXX"
    *     -Ddcp.privateKeyId="YYYYYYYYYYYYYYYYYY"
    *     -Ddcp.region="us-east-1"
    *
    * privateKey & privateKeyId are credentials to an AWS VPC account
    * region is the ec2 region where the tests should be run (us-east-1)
    */

    // command line options
    public String privateKey;
    public String privateKeyId;
    public String region;

    VerificationHost host;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        // ignore if any of the required properties are missing
        org.junit.Assume.assumeTrue(TestUtils.isNull(privateKey, privateKeyId, region));

        this.host = VerificationHost.create(0, null);
        try {
            this.host.start();
            this.startProvisioningServices(this.host);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (this.host == null) {
            return;
        }

        this.host.tearDownInProcessPeers();
        this.host.toggleNegativeTestMode(false);
        this.host.tearDown();
    }

    @Test
    public void testClientCreation() throws Throwable {
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = this.privateKey;
        creds.privateKeyId = this.privateKeyId;
        AWSUtils.getAsyncClient(creds, this.region, false);
    }

    @Test
    public void testInvalidClientCredentials() throws Throwable {
        expectedEx.expect(AmazonServiceException.class);
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = "bar";
        creds.privateKeyId = "foo";
        AWSUtils.getAsyncClient(creds, this.region, false);
    }
}
