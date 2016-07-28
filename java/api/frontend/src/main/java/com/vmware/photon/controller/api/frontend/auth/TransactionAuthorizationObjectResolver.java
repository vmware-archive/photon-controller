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

package com.vmware.photon.controller.api.frontend.auth;

import com.vmware.photon.controller.api.frontend.resources.routes.ClusterResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.DiskResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.FlavorsResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.ImageResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.ProjectResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.ResourceTicketResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.SubnetResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TenantResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.VmResourceRoutes;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class determines what "security object" is the target of the "transaction" we need to
 * authorize.
 */
public class TransactionAuthorizationObjectResolver {
  public static final TransactionAuthorizationObject DEFAULT_OBJECT =
      new TransactionAuthorizationObject(TransactionAuthorizationObject.Kind.DEPLOYMENT);
  public static final String KIND_GROUP = "kind";
  public static final String ID_GROUP = "id";
  public static final String ACTION_GROUP = "action";
  public static final String EMPTY = "";

  /**
   * Regular expression used to parse the URL.
   */
  public static final Pattern URL_PARSER =
      Pattern.compile("^/?(?<kind>[^/]+)(?:/(?<id>[^/]+))?(?:/(?<action>[^/]+))?$", Pattern.CASE_INSENSITIVE);

  /**
   * Map or rules that enforce the authorization policy.
   */
  public static final Map<String, Rule[]> EVALUATION_RULES = new HashMap<>();

  static {
    // CLUSTER
    EVALUATION_RULES.put(
        ClusterResourceRoutes.API.substring(1),
        new Rule[]{
            new Rule(
                TransactionAuthorizationObject.Kind.CLUSTER,
                TransactionAuthorizationObject.Strategy.PARENT)
        });

    // DISK
    EVALUATION_RULES.put(
        DiskResourceRoutes.API.substring(1),
        new Rule[]{
            new Rule(
                TransactionAuthorizationObject.Kind.DISK,
                TransactionAuthorizationObject.Strategy.PARENT)
        });

    // FLAVOR
    EVALUATION_RULES.put(
        FlavorsResourceRoutes.API.substring(1),
        new Rule[]{
            new Rule(
                Pattern.compile("get", Pattern.CASE_INSENSITIVE),
                TransactionAuthorizationObject.Kind.NONE)
        });

    // IMAGE
    EVALUATION_RULES.put(
        ImageResourceRoutes.API.substring(1),
        new Rule[]{
            new Rule(TransactionAuthorizationObject.Kind.NONE)
        });

    // NETWORKS
    EVALUATION_RULES.put(
        SubnetResourceRoutes.API.substring(1),
        new Rule[]{
            new Rule(
                Pattern.compile("get", Pattern.CASE_INSENSITIVE),
                TransactionAuthorizationObject.Kind.NONE)
        });

    // PROJECT
    EVALUATION_RULES.put(
        ProjectResourceRoutes.API.substring(1),
        new Rule[]{
            new Rule(
                Pattern.compile("delete", Pattern.CASE_INSENSITIVE),
                Pattern.compile(".*"),
                TransactionAuthorizationObject.Kind.PROJECT,
                TransactionAuthorizationObject.Strategy.PARENT),
            new Rule(
                Pattern.compile(".*", Pattern.CASE_INSENSITIVE),
                Pattern.compile("set_security_groups"),
                TransactionAuthorizationObject.Kind.PROJECT,
                TransactionAuthorizationObject.Strategy.PARENT),
            new Rule(TransactionAuthorizationObject.Kind.PROJECT)
        });

    // RESOURCE_TICKET
    EVALUATION_RULES.put(
        ResourceTicketResourceRoutes.API.substring(1),
        new Rule[]{
            new Rule(
                TransactionAuthorizationObject.Kind.RESOURCE_TICKET,
                TransactionAuthorizationObject.Strategy.PARENT)
        });

    // TASK
    EVALUATION_RULES.put(
        TaskResourceRoutes.API.substring(1),
        new Rule[]{
            new Rule(TransactionAuthorizationObject.Kind.NONE)
        });

    // TENANT
    EVALUATION_RULES.put(
        TenantResourceRoutes.API.substring(1),
        new Rule[]{
            new Rule(
                Pattern.compile("get", Pattern.CASE_INSENSITIVE),
                TransactionAuthorizationObject.Kind.NONE),
            new Rule(
                Pattern.compile(".*"),
                Pattern.compile("projects"),
                TransactionAuthorizationObject.Kind.TENANT)
        });

    // VM
    EVALUATION_RULES.put(
        VmResourceRoutes.API.substring(1),
        new Rule[]{
            new Rule(
                TransactionAuthorizationObject.Kind.VM,
                TransactionAuthorizationObject.Strategy.PARENT)
        });
  }

  /**
   * Logger.
   */
  private static final Logger logger = LoggerFactory.getLogger(TransactionAuthorizationObjectResolver.class);

  /**
   * Default constructor.
   */
  @Inject
  public TransactionAuthorizationObjectResolver() {
  }

  /**
   * Parses the URL and resolves it to an TransactionAuthorizationObject.
   *
   * @param request
   * @return
   */
  public TransactionAuthorizationObject evaluate(ContainerRequest request) {

    Matcher urlSegments = URL_PARSER.matcher(request.getPath(true));
    if (!urlSegments.matches()) {
      // we can't parse the URL so make it admin only
      logger.debug("Request path did not follow the expected URL pattern. ({})", request.getPath(true));
      return DEFAULT_OBJECT;
    }

    String kind = urlSegments.group(KIND_GROUP);
    String action = urlSegments.group(ACTION_GROUP);
    if (null == action) {
      action = EMPTY;
    }

    Rule[] ruleList = getEvaluationRules().get(kind.toLowerCase());
    if (null == ruleList) {
      logger.debug("Did not find a rule matching the KIND. ({})", kind);
      return DEFAULT_OBJECT;
    }

    for (Rule rule : ruleList) {
      if (rule.acceptedMethods.matcher(request.getMethod()).matches() &&
          rule.acceptedActions.matcher(action).matches()) {
        logger.debug("matched rule: {} {} {} {}", rule.acceptedMethods, rule.acceptedActions, rule.kind, rule.strategy);
        return new TransactionAuthorizationObject(
            rule.kind, rule.strategy, urlSegments.group(ID_GROUP));
      }
    }

    return DEFAULT_OBJECT;
  }

  @VisibleForTesting
  protected Map<String, Rule[]> getEvaluationRules() {
    return EVALUATION_RULES;
  }

  /**
   * Helper class used to encode the authoriztion rules.
   */
  protected static class Rule {
    public Pattern acceptedMethods;
    public Pattern acceptedActions;
    public TransactionAuthorizationObject.Kind kind;
    public TransactionAuthorizationObject.Strategy strategy;

    public Rule(TransactionAuthorizationObject.Kind kind) {
      this(Pattern.compile(".*"), Pattern.compile(".*"), kind, TransactionAuthorizationObject.Strategy.SELF);
    }

    public Rule(TransactionAuthorizationObject.Kind kind, TransactionAuthorizationObject.Strategy strategy) {
      this(Pattern.compile(".*"), Pattern.compile(".*"), kind, strategy);
    }

    public Rule(Pattern methods, TransactionAuthorizationObject.Kind kind) {
      this(methods, Pattern.compile(".*"), kind, TransactionAuthorizationObject.Strategy.SELF);
    }

    public Rule(Pattern methods, Pattern action, TransactionAuthorizationObject.Kind kind) {
      this(methods, action, kind, TransactionAuthorizationObject.Strategy.SELF);
    }

    public Rule(Pattern methods,
                Pattern actions,
                TransactionAuthorizationObject.Kind kind,
                TransactionAuthorizationObject.Strategy strategy) {
      this.acceptedMethods = methods;
      this.acceptedActions = actions;
      this.kind = kind;
      this.strategy = strategy;
    }
  }
}
