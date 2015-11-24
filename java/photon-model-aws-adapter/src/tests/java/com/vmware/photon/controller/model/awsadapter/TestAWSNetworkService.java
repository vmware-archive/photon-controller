/*
 * Copyright (c) 2015 VMware, Inc. All Rights Reserved.
 */

package com.vmware.photon.controller.model.awsadapter;

import com.vmware.enatai.provisioning.services.ProvisioningTestCase;
import com.vmware.enatai.provisioning.services.ProvisioningUtils;
import com.vmware.dcp.common.CommandLineArgumentParser;
import com.vmware.dcp.common.test.VerificationHost;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertTrue;

public class TestAWSNetworkService extends ProvisioningTestCase {
    /*
    * This test requires the following four command line variables.
    * If they are not present the tests will be ignored.
    * Pass them into the test with the -Ddcp.variable=value syntax
    * i.e -Ddcp.subnet="10.1.0.0/16"
    *
    * privateKey & privateKeyId are credentials to an AWS VPC account
    * region is the ec2 region where the tests should be run (us-east-1)
    * subnet is the RFC-1918 subnet of the default VPC
    */
    public String privateKey;
    public String privateKeyId;
    public String region;
    public String subnet;

    VerificationHost host;

    NetworkService netSvc;
    AWSAllocation aws;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        // ignore if any of the required properties are missing
        org.junit.Assume.assumeTrue(TestUtils.isNull(privateKey, privateKeyId, region, subnet));

        this.host = VerificationHost.create(0, null);
        try {
            this.host.start();
            this.startProvisioningServices(this.host);

            this.netSvc = new NetworkService();
            this.aws = new AWSAllocation(null);
            this.aws.amazonEC2Client = TestUtils.getClient(this.privateKeyId,
                    this.privateKey, this.region, false);
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
    public void testGetDefaultVPCSubnet() throws Throwable {
        String sub = this.netSvc.getDefaultVCPSubnet(aws);
        assertTrue(sub.equalsIgnoreCase(this.subnet));
        // should always return an RFC1918 address
        ProvisioningUtils.isRFC1918(sub);
    }
}
