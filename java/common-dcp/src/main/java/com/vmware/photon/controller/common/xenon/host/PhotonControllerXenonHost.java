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
package com.vmware.photon.controller.common.xenon.host;

import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.XenonHostInfoProvider;
import com.vmware.photon.controller.common.xenon.XenonServiceGroup;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.LuceneDocumentIndexService;
import com.vmware.xenon.services.common.RootNamespaceService;

import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class implements the Xenon service host object
 * for the Scheduler service.
 */
public class PhotonControllerXenonHost
        extends AbstractServiceHost
        implements XenonHostInfoProvider,
        HostClientProvider {

    public static final int DEFAULT_CONNECTION_LIMIT_PER_HOST = 1024;

    public static final int INDEX_SEARCHER_COUNT_THRESHOLD = 1024;

    private static final Logger logger = LoggerFactory.getLogger(PhotonControllerXenonHost.class);
    public static final String FACTORY_SERVICE_FIELD_NAME_SELF_LINK = "SELF_LINK";

    private final XenonConfig xenonConfig;
    private final ZookeeperModule zookeeperModule;
    private final ThriftModule thriftModule;
    private final AgentControlClientFactory agentControlClientFactory;
    private final HostClientFactory hostClientFactory;
    private final ServiceConfigFactory serviceConfigFactory;
    private BuildInfo buildInfo;
    private final List<XenonServiceGroup> xenonServiceGroups = Collections.synchronizedList(new ArrayList<>());

    @SuppressWarnings("rawtypes")
    public static final Class[] FACTORY_SERVICES = {
            RootNamespaceService.class,
    };

    public PhotonControllerXenonHost(XenonConfig xenonConfig,
                                     ZookeeperModule zookeeperModule,
                                     ThriftModule thriftModule) throws Throwable {
        super(xenonConfig);
        this.xenonConfig = xenonConfig;
        this.buildInfo = BuildInfo.get(this.getClass());
        this.zookeeperModule = zookeeperModule;
        this.thriftModule = thriftModule;

        this.hostClientFactory = thriftModule.getHostClientFactory();
        this.agentControlClientFactory = thriftModule.getAgentControlClientFactory();

        CuratorFramework zkClient = zookeeperModule.getCuratorFramework();
        this.serviceConfigFactory = zookeeperModule.getServiceConfigFactory(zkClient);
    }

    @Override
    public HostClient getHostClient() {
        return hostClientFactory.create();
    }

    /**
     * This method starts the default Xenon core services and the scheduler-specific factory service
     * factories.
     *
     * @return
     * @throws Throwable
     */
    @Override
    public ServiceHost start() throws Throwable {
        super.start();
        /**
         * Xenon currently uses a garbage collection algorithm for its Lucene index searchers which
         * results in index searchers being closed while still in use by paginated queries. As a
         * temporary workaround until the issue is fixed on the framework side (v0.7.6), raise the
         * threshold at which index searcher garbage collection is triggered to limit the impact of
         * this issue.
         */
        LuceneDocumentIndexService.setSearcherCountThreshold(INDEX_SEARCHER_COUNT_THRESHOLD);

        this.getClient().setConnectionLimitPerHost(DEFAULT_CONNECTION_LIMIT_PER_HOST);
        startDefaultCoreServicesSynchronously();

        // Start all core factories
        ServiceHostUtils.startServices(this, getFactoryServices());

        for ( XenonServiceGroup xenonServiceGroup : xenonServiceGroups) {
           xenonServiceGroup.start();
        }

        ServiceHostUtils.startService(this, StatusService.class);

        return this;
    }

    /**
     * This method returns whether the services started above have come up.
     *
     * @return
     */
    @Override
    public boolean isReady() {
        try {
            // If any service is not ready the host is not ready
            for (XenonServiceGroup xenonServiceGroup : xenonServiceGroups) {
                if (xenonServiceGroup.isReady() == false) {
                    return false;
                }
            }

            return ServiceHostUtils.areServicesReady(
                    this, FACTORY_SERVICE_FIELD_NAME_SELF_LINK, getFactoryServices());
        } catch (Throwable t) {
            logger.debug("IsReady failed: {}", t);
            return false;
        }
    }

    @Override
    public BuildInfo getBuildInfo() {
        return this.buildInfo;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Class[] getFactoryServices() {
        return FACTORY_SERVICES;
    }

    public void addXenonServiceGroup(XenonServiceGroup xenonServiceGroup) {
        xenonServiceGroups.add(xenonServiceGroup);
        xenonServiceGroup.setPhotonControllerXenonHost(this);
    }

    public ServiceConfigFactory getServiceConfigFactory() {
        return serviceConfigFactory;
    }
}
