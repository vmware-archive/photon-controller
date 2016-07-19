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

package com.vmware.photon.controller.clustermanager.testtool;

import com.vmware.photon.controller.api.model.ClusterType;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

/**
 * Class that represents command line arguments for this tool.
 */
public class Arguments {

  private static final String CLUSTER_TYPE_ATTR_NAME = "clusterType";
  private static final String CLUSTER_COUNT_ATTR_NAME = "clusterCount";
  private static final String SLAVE_COUNT_ATTR_NAME = "slaveCount";
  private static final String MANAGEMENT_ADDR_ATTR_NAME = "managementAddress";
  private static final String PROJECT_ID_ATTR_NAME = "projectId";

  private static final String DNS_ATTR_NAME = "dns";
  private static final String GATEWAY_ATTR_NAME = "gateway";
  private static final String NETMASK_ATTR_NAME = "netmask";
  private static final String STATIC_IPS_ATTR_NAME = "staticIps";

  private String clusterType;
  private int clusterCount;
  private int slaveCount;
  private String managementAddress;
  private String projectId;
  private String dns;
  private String gateway;
  private String netmask;
  private String staticIps;

  private Arguments() {
  }

  public int getClusterCount() {
    return this.clusterCount;
  }

  public int getSlaveCount() {
    return this.slaveCount;
  }

  public String getManagementVmAddress() {
    return this.managementAddress;
  }

  public String getProjectId() {
    return this.projectId;
  }

  public String getDns() {
    return this.dns;
  }

  public String getGateway() {
    return this.gateway;
  }

  public String getNetmask() {
    return this.netmask;
  }

  public ClusterType getClusterType() {
    return Enum.valueOf(ClusterType.class, this.clusterType);
  }

  public String[] getStaticIps() {
    return this.staticIps.split(",");
  }

  public static Arguments parseArguments(String[] args) throws ArgumentParserException {
    ArgumentParser argumentParser = buildArgumentParser();
    try {
      Namespace res = argumentParser.parseArgs(args);

      Arguments returnVal = new Arguments();
      returnVal.clusterType = res.getString(CLUSTER_TYPE_ATTR_NAME);
      returnVal.clusterCount = Integer.parseInt(res.getString(CLUSTER_COUNT_ATTR_NAME));
      returnVal.slaveCount = Integer.parseInt(res.getString(SLAVE_COUNT_ATTR_NAME));
      returnVal.managementAddress = res.getString(MANAGEMENT_ADDR_ATTR_NAME);
      returnVal.projectId = res.getString(PROJECT_ID_ATTR_NAME);
      returnVal.dns = res.getString(DNS_ATTR_NAME);
      returnVal.gateway = res.getString(GATEWAY_ATTR_NAME);
      returnVal.netmask = res.getString(NETMASK_ATTR_NAME);
      returnVal.staticIps = res.getString(STATIC_IPS_ATTR_NAME);

      return returnVal;
    } catch (Exception ex) {
      argumentParser.printHelp();
      throw ex;
    }
  }

  private static ArgumentParser buildArgumentParser() {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("cluster-mgr-test-tool")
        .defaultHelp(true)
        .description("Cluster Manager Test Tool");

    parser.addArgument("--clusterType", "-t")
        .dest(CLUSTER_TYPE_ATTR_NAME)
        .required(true)
        .help("Type of the cluster to create KUBERNETES, MESOS or SWARM");

    parser.addArgument("--clusterCount", "-c")
        .dest(CLUSTER_COUNT_ATTR_NAME)
        .required(true)
        .help("number of clusters to be created");

    parser.addArgument("--slaveCount", "-s")
        .dest(SLAVE_COUNT_ATTR_NAME)
        .required(false)
        .setDefault(100)
        .help("number of slaves to be created per cluster");

    parser.addArgument("--managementAddress", "-a")
        .dest(MANAGEMENT_ADDR_ATTR_NAME)
        .required(true)
        .help("IP Address of the Management VM");

    parser.addArgument("--projectId", "-p")
        .dest(PROJECT_ID_ATTR_NAME)
        .required(true)
        .help("Project Id used for this test");

    parser.addArgument("--dns")
        .dest(DNS_ATTR_NAME)
        .required(true)
        .help("Dns used for the cluster");

    parser.addArgument("--gateway")
        .dest(GATEWAY_ATTR_NAME)
        .required(true)
        .help("Gateway used for the cluster");

    parser.addArgument("--netmask")
        .dest(NETMASK_ATTR_NAME)
        .required(true)
        .help("Netmask used for the cluster");

    parser.addArgument("--staticIps")
        .dest(STATIC_IPS_ATTR_NAME)
        .required(true)
        .help("Static Ips used for different roles in the VM.");

    return parser;
  }
}
