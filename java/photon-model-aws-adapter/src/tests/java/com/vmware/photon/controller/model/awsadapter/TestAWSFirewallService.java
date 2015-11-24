/*
 * Copyright (c) 2015 VMware, Inc. All Rights Reserved.
 */

package com.vmware.photon.controller.model.awsadapter;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.vmware.enatai.provisioning.services.ProvisioningTestCase;
import com.vmware.dcp.common.CommandLineArgumentParser;
import com.vmware.dcp.common.test.VerificationHost;
import com.vmware.dcp.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestAWSFirewallService extends ProvisioningTestCase {

    /*
    * This test requires the following four command line variables.
    * If they are not present the tests will be ignored
    * Pass them into the test with the -Ddcp.variable=value syntax
    * i.e -Ddcp.subnet="10.1.0.0/16"
    *
    *
    * privateKey & privateKeyId are credentials to an AWS VPC account
    * region is the ec2 region where the tests should be run (us-east-1)
    * subnet is the RFC-1918 subnet of the default VPC
    *
    * Test assumes the default CM Security group is NOT present in the provided
    * AWS account / zone -- if it is present the tests will fail
    */
    public String privateKey;
    public String privateKeyId;
    public String region;
    public String subnet;

    VerificationHost host;

    FirewallService svc;
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

            this.svc = new FirewallService();
            this.aws = new AWSAllocation(null);
            this.aws.amazonEC2Client = this.getClient(false);
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

    /*
     * request a group that doesn't exist
     */
    @Test
    public void testInvalidGetSecurityGroup() throws Throwable {
        expectedEx.expect(AmazonServiceException.class);
        svc.getSecurityGroup(aws, "foo-bar");
    }

    /*
     * Create the default CM group, get or describe the default group
     * and then delete the group
     */
    @Test
    public void testDefaultSecurityGroup() throws Throwable {
        // create the group
        svc.createSecurityGroup(aws);

        // get the default CM group
        svc.getSecurityGroup(aws);

        // lets delete the default CM group
        svc.deleteSecurityGroup(aws);
    }

    /*
     * Create the default CM group, get the group, verify the default
       permissions are in place.  Then delete the default group
     */

    @Test
    public void testDefaultSecurityGroupPorts() throws Throwable {
        // create the group
        String groupId = svc.createSecurityGroup(aws);

        // allow the default ports
        svc.updateSecurityGroupRules(aws, groupId, svc.getDefaultRules(this.subnet));

        // get the updated CM group
        SecurityGroup group = svc.getSecurityGroup(aws);

        List<IpPermission> rules = group.getIpPermissions();

        assertTrue(rules.size() > 0);
        validateDefaultRules(rules);

        // lets delete the default CM group
        svc.deleteSecurityGroup(aws);
    }

    /*
     * Negative test attempting to delete the non-existent
     * default CM security group
     */
    @Test
    public void testDeleteMissingGroup() throws Throwable {
        expectedEx.expect(AmazonServiceException.class);
        expectedEx.expectMessage("The security group 'cell-manager-security-group' does not exist");

        // lets delete the default CM group
        // which doesn't exist
        svc.deleteSecurityGroup(aws);
    }

    /*
     * create a new security group via the allocation method
     */
    @Test
    public void testAllocateSecurityGroup() throws Throwable {
        svc.allocateSecurityGroup(aws);
        SecurityGroup group = svc.getSecurityGroup(aws);
        validateDefaultRules(group.getIpPermissions());
        svc.deleteSecurityGroup(aws);
    }

    /*
     * update an existing security group to the required default ports
     */
    @Test
    public void testAllocateSecurityGroupUpdate() throws Throwable {
        String groupId = svc.createSecurityGroup(aws,
                FirewallService.DEFAULT_SECURITY_GROUP_NAME,
                FirewallService.DEFAULT_SECURITY_GROUP_DESC);

        List<IpPermission> rules = new ArrayList<>();
        rules.add(new IpPermission()
                .withIpProtocol(FirewallService.DEFAULT_PROTOCOL)
                .withFromPort(22)
                .withToPort(22)
                .withIpRanges(FirewallService.DEFAULT_ALLOWED_NETWORK));
        svc.updateSecurityGroupRules(aws, groupId, rules);
        svc.allocateSecurityGroup(aws);
        SecurityGroup updatedGroup = svc.getSecurityGroup(aws,
                FirewallService.DEFAULT_SECURITY_GROUP_NAME);
        validateDefaultRules(updatedGroup.getIpPermissions());
        svc.deleteSecurityGroup(aws);
    }

    private void validateDefaultRules(List<IpPermission> rules) throws Throwable {
        ArrayList<Integer> ports = new ArrayList<>();
        for (int port : FirewallService.DEFAULT_ALLOWED_PORTS) {
            ports.add(port);
        }

        for (IpPermission rule : rules) {
            assertTrue(rule.getIpProtocol().equalsIgnoreCase(FirewallService.DEFAULT_PROTOCOL));
            if (rule.getFromPort() == 1) {
                assertTrue(rule.getIpRanges().get(0)
                        .equalsIgnoreCase(this.subnet));
                assertTrue(rule.getToPort() == 65535);
            } else {
                assertTrue(rule.getIpRanges().get(0)
                        .equalsIgnoreCase(FirewallService.DEFAULT_ALLOWED_NETWORK));
                assertEquals(rule.getFromPort(), rule.getToPort());
                assertTrue(ports.contains(rule.getToPort()));
            }
        }
    }

    private AmazonEC2AsyncClient getClient(boolean isMockRequest) {
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.privateKey = this.privateKey;
        creds.privateKeyId = this.privateKeyId;
        return AWSUtils.getAsyncClient(creds, this.region, isMockRequest);
    }
}
