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

package com.vmware.photon.controller.model.resources;

import com.vmware.photon.controller.model.ModelServices;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantFactoryService;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.UUID;

/**
 * This class implements tests for the {@link FirewallService} class.
 */
public class FirewallServiceTest {

  private FirewallService.FirewallState buildValidStartState() {
    FirewallService.FirewallState firewallState = new FirewallService.FirewallState();
    firewallState.id = UUID.randomUUID().toString();
    firewallState.networkDescriptionLink = "http://networkDescriptionLink";
    firewallState.tenantLinks = new ArrayList<>();
    firewallState.tenantLinks.add("tenant-linkA");
    firewallState.ingress = getAllowIngressRules();
    firewallState.egress = getAllowEgressRules();
    firewallState.regionID = "regionID";
    firewallState.authCredentialsLink = "http://authCredentialsLink";
    firewallState.resourcePoolLink = "http://resourcePoolLink";
    try {
      firewallState.instanceAdapterReference = new URI("http://instanceAdapterReference");
    } catch (Exception e) {
      firewallState.instanceAdapterReference = null;
    }
    return firewallState;
  }

  public static ArrayList<FirewallService.FirewallState.Allow> getAllowIngressRules() {
    ArrayList<FirewallService.FirewallState.Allow> rules = new ArrayList<>();
    FirewallService.FirewallState.Allow ssh = new FirewallService.FirewallState.Allow();
    ssh.name = "ssh";
    ssh.protocol = "tcp";
    ssh.ipRange = "0.0.0.0/0";
    ssh.ports = new ArrayList<>();
    ssh.ports.add("22");
    rules.add(ssh);
    return rules;
  }

  public static ArrayList<FirewallService.FirewallState.Allow> getAllowEgressRules() {
    ArrayList<FirewallService.FirewallState.Allow> rules = new ArrayList<>();
    FirewallService.FirewallState.Allow out = new FirewallService.FirewallState.Allow();
    out.name = "out";
    out.protocol = "tcp";
    out.ipRange = "0.0.0.0/0";
    out.ports = new ArrayList<>();
    out.ports.add("1-65535");
    rules.add(out);
    return rules;
  }

  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {
    private FirewallService firewallService = new FirewallService();

    @BeforeMethod
    public void setupTest() {
      firewallService = new FirewallService();
    }

    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION);

      assertThat(firewallService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ModelServices.FACTORIES;
    }

    @Test
    public void testValidStartState() throws Throwable {
      FirewallService.FirewallState startState = buildValidStartState();
      FirewallService.FirewallState returnState = host.postServiceSynchronously(
          FirewallFactoryService.SELF_LINK,
          startState,
          FirewallService.FirewallState.class);

      assertNotNull(returnState);
      assertThat(returnState.id, is(startState.id));
      assertThat(returnState.networkDescriptionLink, is(startState.networkDescriptionLink));
      assertThat(returnState.regionID, is(startState.regionID));
      assertThat(returnState.authCredentialsLink, is(startState.authCredentialsLink));
      assertThat(returnState.resourcePoolLink, is(startState.resourcePoolLink));
      assertThat(returnState.instanceAdapterReference, is(startState.instanceAdapterReference));
      assertThat(returnState.ingress.get(0).name, is(getAllowIngressRules().get(0).name));
      assertThat(returnState.egress.get(0).name, is(getAllowEgressRules().get(0).name));
    }

    @DataProvider(name = "createInvalidValues")
    public Object[][] createInvalidValues() {
      FirewallService.FirewallState missingNetworkDescriptionLink = buildValidStartState();
      FirewallService.FirewallState missingIngress = buildValidStartState();
      FirewallService.FirewallState missingEgress = buildValidStartState();
      FirewallService.FirewallState missingIngressRuleName = buildValidStartState();
      FirewallService.FirewallState missingEgressRuleName = buildValidStartState();

      FirewallService.FirewallState missingIngressProtocol = buildValidStartState();
      FirewallService.FirewallState missingEgressProtocol = buildValidStartState();

      FirewallService.FirewallState invalidIngressProtocol = buildValidStartState();
      FirewallService.FirewallState invalidEgressProtocol = buildValidStartState();

      FirewallService.FirewallState invalidIngressIpRangeNoSubnet = buildValidStartState();
      FirewallService.FirewallState invalidIngressIpRangeInvalidIP = buildValidStartState();
      FirewallService.FirewallState invalidIngressIpRangeInvalidSubnet = buildValidStartState();

      FirewallService.FirewallState invalidEgressIpRangeNoSubnet = buildValidStartState();
      FirewallService.FirewallState invalidEgressIpRangeInvalidIP = buildValidStartState();
      FirewallService.FirewallState invalidEgressIpRangeInvalidSubnet = buildValidStartState();

      FirewallService.FirewallState invalidIngressPorts0 = buildValidStartState();
      FirewallService.FirewallState invalidIngressPorts1 = buildValidStartState();
      FirewallService.FirewallState invalidIngressPorts2 = buildValidStartState();
      FirewallService.FirewallState invalidIngressPorts3 = buildValidStartState();
      FirewallService.FirewallState invalidIngressPorts4 = buildValidStartState();

      FirewallService.FirewallState invalidEgressPorts0 = buildValidStartState();
      FirewallService.FirewallState invalidEgressPorts1 = buildValidStartState();
      FirewallService.FirewallState invalidEgressPorts2 = buildValidStartState();
      FirewallService.FirewallState invalidEgressPorts3 = buildValidStartState();
      FirewallService.FirewallState invalidEgressPorts4 = buildValidStartState();

      missingNetworkDescriptionLink.networkDescriptionLink = null;
      missingIngress.ingress = null;
      missingEgress.egress = null;

      missingIngressRuleName.ingress.get(0).name = null;
      missingEgressRuleName.egress.get(0).name = null;

      missingIngressProtocol.ingress.get(0).protocol = null;
      missingEgressProtocol.egress.get(0).protocol = null;

      invalidIngressProtocol.ingress.get(0).protocol = "not-tcp-udp-icmp-protocol";
      invalidEgressProtocol.egress.get(0).protocol = "not-tcp-udp-icmp-protocol";

      invalidIngressIpRangeNoSubnet.ingress.get(0).ipRange = "10.0.0.0";
      invalidIngressIpRangeInvalidIP.ingress.get(0).ipRange = "10.0.0.FOO";
      invalidIngressIpRangeInvalidSubnet.ingress.get(0).ipRange = "10.0.0.0/32";

      invalidEgressIpRangeNoSubnet.ingress.get(0).ipRange = "10.0.0.0";
      invalidEgressIpRangeInvalidIP.ingress.get(0).ipRange = "10.0.0.FOO";
      invalidEgressIpRangeInvalidSubnet.ingress.get(0).ipRange = "10.0.0.0/32";

      invalidIngressPorts0.ingress.get(0).ports.clear();
      invalidIngressPorts1.ingress.get(0).ports.add(0, "1-1024-6535");
      invalidIngressPorts2.ingress.get(0).ports.add(0, "-1");
      invalidIngressPorts3.ingress.get(0).ports.add(0, "badString");
      invalidIngressPorts4.ingress.get(0).ports.add(0, "100-1");

      invalidEgressPorts0.ingress.get(0).ports.clear();
      invalidEgressPorts1.ingress.get(0).ports.add(0, "1-1024-6535");
      invalidEgressPorts2.ingress.get(0).ports.add(0, "-1");
      invalidEgressPorts3.ingress.get(0).ports.add(0, "badString");
      invalidEgressPorts4.ingress.get(0).ports.add(0, "100-1");

      return new Object[][]{
          {missingNetworkDescriptionLink},
          {missingIngress},
          {missingEgress},
          {missingIngressRuleName},
          {missingEgressRuleName},
          {missingIngressProtocol},
          {missingEgressProtocol},
          {invalidIngressProtocol},
          {invalidEgressProtocol},
          {invalidIngressIpRangeNoSubnet},
          {invalidIngressIpRangeInvalidIP},
          {invalidIngressIpRangeInvalidSubnet},
          {invalidEgressIpRangeNoSubnet},
          {invalidEgressIpRangeInvalidIP},
          {invalidEgressIpRangeInvalidSubnet},
          {invalidIngressPorts0},
          {invalidIngressPorts1},
          {invalidIngressPorts2},
          {invalidIngressPorts3},
          {invalidIngressPorts4},
          {invalidEgressPorts0},
          {invalidEgressPorts1},
          {invalidEgressPorts2},
          {invalidEgressPorts3},
          {invalidEgressPorts4}
      };
    }

    @Test(dataProvider = "createInvalidValues")
    public void testMissingValue(FirewallService.FirewallState startState) throws Throwable {
      host.postServiceSynchronously(
          FirewallFactoryService.SELF_LINK,
          startState,
          FirewallService.FirewallState.class, IllegalArgumentException.class);
    }
  }

  /**
   * This class implements tests for the handlePatch method.
   */
  public class HandlePatchTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ModelServices.FACTORIES;
    }

    @Test
    public void testPatch() throws Throwable {
      FirewallService.FirewallState startState = buildValidStartState();

      FirewallService.FirewallState returnState = host.postServiceSynchronously(
          FirewallFactoryService.SELF_LINK,
          startState,
          FirewallService.FirewallState.class);

      FirewallService.FirewallState.Allow newIngressrule = new FirewallService.FirewallState.Allow();
      newIngressrule.name = "ssh";
      newIngressrule.protocol = "tcp";
      newIngressrule.ipRange = "10.10.10.10/10";
      newIngressrule.ports = new ArrayList<>();
      newIngressrule.ports.add("44");

      FirewallService.FirewallState.Allow newEgressRule = new FirewallService.FirewallState.Allow();
      newEgressRule.name = "out";
      newEgressRule.protocol = "tcp";
      newEgressRule.ipRange = "0.0.0.0/0";
      newEgressRule.ports = new ArrayList<>();
      newEgressRule.ports.add("1-65535");

      FirewallService.FirewallState patchState = new FirewallService.FirewallState();
      patchState.name = "newName";
      patchState.ingress = new ArrayList<>();
      patchState.egress = new ArrayList<>();
      patchState.ingress.add(0, newIngressrule);
      patchState.egress.add(0, newEgressRule);
      patchState.customProperties = new HashMap<>();
      patchState.customProperties.put("customKey", "customValue");

      patchState.regionID = "patchRregionID";
      patchState.authCredentialsLink = "http://patchAuthCredentialsLink";
      patchState.resourcePoolLink = "http://patchResourcePoolLink";
      try {
        patchState.instanceAdapterReference = new URI("http://patchInstanceAdapterReference");
      } catch (Exception e) {
        patchState.instanceAdapterReference = null;
      }

      host.patchServiceSynchronously(
          returnState.documentSelfLink,
          patchState);

      returnState = host.getServiceSynchronously(
          returnState.documentSelfLink,
          FirewallService.FirewallState.class);

      assertThat(returnState.regionID, is(patchState.regionID));
      assertThat(returnState.authCredentialsLink, is(patchState.authCredentialsLink));
      assertThat(returnState.resourcePoolLink, is(patchState.resourcePoolLink));
      assertThat(returnState.instanceAdapterReference, is(patchState.instanceAdapterReference));

      assertThat(returnState.name, is(patchState.name));
      assertThat(returnState.ingress.get(0).name, is(patchState.ingress.get(0).name));
      assertThat(returnState.ingress.get(0).protocol, is(patchState.ingress.get(0).protocol));
      assertThat(returnState.ingress.get(0).ports, is(patchState.ingress.get(0).ports));
      assertThat(returnState.egress.get(0).name, is(patchState.egress.get(0).name));
      assertThat(returnState.egress.get(0).protocol, is(patchState.egress.get(0).protocol));
      assertThat(returnState.egress.get(0).ports, is(patchState.egress.get(0).ports));
      assertThat(returnState.customProperties.get("customKey"), is("customValue"));
    }
  }

  /**
   * This class implements tests for query.
   */
  public class QueryTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ModelServices.FACTORIES;
    }

    @Test
    public void testTenantLinksQuery() throws Throwable {
      FirewallService.FirewallState firewallState = buildValidStartState();
      URI tenantUri = UriUtils.buildUri(host, TenantFactoryService.class);
      firewallState.tenantLinks = new ArrayList<>();
      firewallState.tenantLinks.add(UriUtils.buildUriPath(tenantUri.getPath(), "tenantA"));
      FirewallService.FirewallState startState = host.postServiceSynchronously(
          FirewallFactoryService.SELF_LINK,
          firewallState,
          FirewallService.FirewallState.class);

      String kind = Utils.buildKind(FirewallService.FirewallState.class);
      String propertyName = QueryTask.QuerySpecification
          .buildCollectionItemName(ServiceDocumentDescription.FIELD_NAME_TENANT_LINKS);

      QueryTask q = host.createDirectQueryTask(kind, propertyName, firewallState.tenantLinks.get(0));
      q = host.querySynchronously(q);
      assertNotNull(q.results.documentLinks);
      assertThat(q.results.documentCount, is(1L));
      assertThat(q.results.documentLinks.get(0), is(startState.documentSelfLink));
    }
  }
}
