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

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;

import org.apache.commons.net.util.SubnetUtils;

import java.net.URI;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a firewall resource.
 */
public class FirewallService extends StatefulService {
  public static final String[] PROTOCOL = {"tcp", "udp", "icmp"};

  /**
   * Firewall State document.
   */
  public static class FirewallState extends ServiceDocument {
    public String id;
    public String name;

    /**
     * Region identifier of this firewall service instance.
     */
    public String regionID;

    /**
     * Link to secrets.  Required
     */
    public String authCredentialsLink;

    /**
     * The pool which this resource is a part of.
     */
    public String resourcePoolLink;

    /**
     * The adapter to use to create the firewall.
     */
    public URI instanceAdapterReference;

    // network that FW will protect
    public String networkDescriptionLink;

    public Map<String, String> customProperties;

    /**
     * A list of tenant links can access this firewall resource.
     */
    public List<String> tenantLinks;

    // incoming rules
    public List<Allow> ingress;

    // outgoing rules
    public List<Allow> egress;

    /**
     * Represents a firewall allow rule.
     */
    public static class Allow {
      public String name;
      public String protocol;
      // IP range that rule will be applied to
      // expressed in CIDR notation
      public String ipRange;

      // port or port range for rule
      // ie. "22", "80", "1-65535"
      public List<String> ports;
    }
  }

  public FirewallService() {
    super(FirewallState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    try {
      if (!start.hasBody()) {
        throw new IllegalArgumentException("body is required");
      }
      validateState(start.getBody(FirewallState.class));
      start.complete();
    } catch (Throwable e) {
      start.fail(e);
    }
  }

  public static void validateState(FirewallState state) {
    if (state.networkDescriptionLink == null || state.networkDescriptionLink.isEmpty()) {
      throw new IllegalArgumentException("a network description link is required");
    }
    // for now require a minimum of one rule
    if (state.ingress == null || state.ingress.size() == 0) {
      throw new IllegalArgumentException("a minimum of one ingress rule is required");
    }
    if (state.egress == null || state.egress.size() == 0) {
      throw new IllegalArgumentException("a minimum of one egress rule is required");
    }

    if (state.regionID == null || state.regionID.isEmpty()) {
      throw new IllegalArgumentException("regionID required");
    }

    if (state.authCredentialsLink == null || state.authCredentialsLink.isEmpty()) {
      throw new IllegalArgumentException("authCredentialsLink required");
    }

    if (state.resourcePoolLink == null || state.resourcePoolLink.isEmpty()) {
      throw new IllegalArgumentException("resourcePoolLink required");
    }

    if (state.instanceAdapterReference == null) {
      throw new IllegalArgumentException("instanceAdapterReference required");
    }

    validateRules(state.ingress);
    validateRules(state.egress);
  }

  /**
   * Ensure that the allow rules conform to standard firewall
   * practices.
   */
  public static void validateRules(List<FirewallState.Allow> rules) {
    for (FirewallState.Allow rule : rules) {
      validateRuleName(rule.name);
      // validate protocol and convert to lower case
      rule.protocol = validateProtocol(rule.protocol);

      // IP range must be in CIDR notation
      // creating new SubnetUtils to validate
      SubnetUtils subnetUtils = new SubnetUtils(rule.ipRange);
      validatePorts(rule.ports);
    }
  }

  /*
   * validate port list
   */
  public static void validatePorts(List<String> ports) {
    if (ports == null || ports.size() == 0) {
      throw new IllegalArgumentException("an allow rule requires a minimum of one port, none supplied");
    }

    for (String port : ports) {

      String[] pp = port.split("-");
      if (pp.length > 2) {
        // invalid port range
        throw new IllegalArgumentException("invalid allow rule port range supplied");
      }
      int previousPort = 0;
      if (pp.length > 0) {
        for (String aPp : pp) {
          try {
            int iPort = Integer.parseInt(aPp);
            if (iPort < 1 || iPort > 65535) {
              throw new IllegalArgumentException("allow rule port numbers must be between 1 and 65535");
            }
            if (previousPort > 0 && previousPort > iPort) {
              throw new IllegalArgumentException("allow rule from port is greater than to port");
            }
            previousPort = iPort;
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException("allow rule port numbers must be between 1 and 65535");
          }
        }
      }
    }
  }

  /*
   * Ensure rule name is populated
   */
  public static void validateRuleName(String name) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("a rule name is required");
    }
  }

  /*
   * Protocol must be tcp, udp or icmpi
   */
  public static String validateProtocol(String protocol) {

    if (protocol == null || protocol.isEmpty()) {
      throw new IllegalArgumentException("only tcp, udp or icmp protocols are supported, none supplied");
    }

    String proto = protocol.toLowerCase();

    if (!Arrays.asList(PROTOCOL).contains(proto)) {
      throw new IllegalArgumentException("only tcp, udp or icmp protocols are supported, provide a supported protocol");
    }
    return proto;
  }

  @Override
  public void handlePatch(Operation patch) {
    FirewallState currentState = getState(patch);
    FirewallState patchBody = patch.getBody(FirewallState.class);

    boolean isChanged = false;

    if (patchBody.name != null && !patchBody.name.equalsIgnoreCase(currentState.name)) {
      currentState.name = patchBody.name;
      isChanged = true;
    }

    if (patchBody.regionID != null && !patchBody.regionID.equalsIgnoreCase(currentState.regionID)) {
      currentState.regionID = patchBody.regionID;
      isChanged = true;
    }

    if (patchBody.authCredentialsLink != null
            && !patchBody.authCredentialsLink.equalsIgnoreCase(currentState.authCredentialsLink)) {
      currentState.authCredentialsLink = patchBody.authCredentialsLink;
      isChanged = true;
    }

    if (patchBody.resourcePoolLink != null
            && !patchBody.resourcePoolLink.equalsIgnoreCase(currentState.resourcePoolLink)) {
      currentState.resourcePoolLink = patchBody.resourcePoolLink;
      isChanged = true;
    }

    if (patchBody.instanceAdapterReference != null
            && !patchBody.instanceAdapterReference.equals(
                               currentState.instanceAdapterReference)) {
      currentState.instanceAdapterReference = patchBody.instanceAdapterReference;
      isChanged = true;
    }

    // allow rules are overwritten -- it's not a merge
    // will result in a new version of the service on every call
    // as ingress & egress are never null
    if (patchBody.ingress != null) {
      currentState.ingress = patchBody.ingress;
      isChanged = true;
    }

    if (patchBody.egress != null) {
      currentState.egress = patchBody.egress;
      isChanged = true;
    }

    if (patchBody.customProperties != null && !patchBody.customProperties.isEmpty()) {
      if (currentState.customProperties == null || currentState.customProperties.isEmpty()) {
        currentState.customProperties = patchBody.customProperties;
      } else {
        for (Map.Entry<String, String> e : patchBody.customProperties.entrySet()) {
          currentState.customProperties.put(e.getKey(), e.getValue());
        }
      }
      isChanged = true;
    }

    if (!isChanged) {
      patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
    }

    patch.complete();
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument td = super.getDocumentTemplate();
    FirewallState template = (FirewallState) td;

    ServiceDocumentDescription.expandTenantLinks(td.documentDescription);

    template.id = UUID.randomUUID().toString();
    template.name = "firewall-one";

    return template;
  }
}
