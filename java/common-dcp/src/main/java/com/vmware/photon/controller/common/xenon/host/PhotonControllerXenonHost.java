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

import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.XenonHostInfoProvider;
import com.vmware.photon.controller.common.xenon.XenonServiceGroup;
import com.vmware.photon.controller.common.zookeeper.ServiceConfig;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigProvider;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.LuceneDocumentIndexService;
import com.vmware.xenon.services.common.RootNamespaceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class implements the Xenon service host object. It is the only Xenon host for Photon Controller
 * and all previous Xenon host objects are now Xenon service groups which register with this host.
 */
public class PhotonControllerXenonHost
        extends AbstractServiceHost
        implements HostClientProvider,
        ServiceConfigProvider,
        XenonHostInfoProvider {

    public static final int DEFAULT_CONNECTION_LIMIT_PER_HOST = 1024;

    public static final int INDEX_SEARCHER_COUNT_THRESHOLD = 1024;

    private static final Logger logger = LoggerFactory.getLogger(PhotonControllerXenonHost.class);
    public static final String FACTORY_SERVICE_FIELD_NAME_SELF_LINK = "SELF_LINK";

    private final XenonConfig xenonConfig;
    private final AgentControlClientFactory agentControlClientFactory;
    private final HostClientFactory hostClientFactory;
    private final ServiceConfigFactory serviceConfigFactory;
    private CloudStoreHelper cloudStoreHelper;
    private BuildInfo buildInfo;
    private final List<XenonServiceGroup> xenonServiceGroups = Collections.synchronizedList(new ArrayList<>());
    private XenonServiceGroup cloudStore;
    private XenonServiceGroup scheduler;

    @SuppressWarnings("rawtypes")
    public static final Class[] FACTORY_SERVICES = {
            RootNamespaceService.class,
    };

    /**
     * This is the constructor for the merged XenonHost in which all PhotonController services reside.  Some
     * of these arguments can be removed once Thrift and Zookeeper are gone.  The ZookeeperConfig and
     * ServiceConfigFactory arguments can be removed once Zookeeper is gone and are currently mutually exclusive.
     * The ZookeeperConfig will only be used if the ServiceConfigFactory passed in is null.
     *
     * @param xenonConfig
     * @param hostClientFactory
     * @param agentControlClientFactory
     * @param serviceConfigFactory
     * @throws Throwable
     */
    public PhotonControllerXenonHost(XenonConfig xenonConfig,
                                     HostClientFactory hostClientFactory,
                                     AgentControlClientFactory agentControlClientFactory,
                                     ServiceConfigFactory serviceConfigFactory,
                                     CloudStoreHelper cloudStoreHelper) throws Throwable {
        super(xenonConfig);
        this.xenonConfig = xenonConfig;
        this.buildInfo = BuildInfo.get(this.getClass());

        if (hostClientFactory == null || agentControlClientFactory == null) {
            ThriftModule thriftModule = new ThriftModule();
            if (hostClientFactory == null) {
                hostClientFactory = thriftModule.getHostClientFactory();
            }

            if (agentControlClientFactory == null) {
                agentControlClientFactory = thriftModule.getAgentControlClientFactory();
            }

        }

        this.hostClientFactory = hostClientFactory;
        this.agentControlClientFactory = agentControlClientFactory;
        this.serviceConfigFactory = serviceConfigFactory;
        this.cloudStoreHelper = cloudStoreHelper;
    }

    @Override
    public HostClient getHostClient() {
        return hostClientFactory.create();
    }

    /**
     * This method starts the default Xenon core services and the services associated to any of the
     * registered Xenon service groups.  All service groups that want to be active in this Xenon host
     * should be registered before calling this method.
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

        for (XenonServiceGroup xenonServiceGroup : xenonServiceGroups) {
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


    /**
     * This method returns a handle to the API_FE service config so that its paused state
     * can be checked.  This method is used during maintenance to quiesce calls to the
     * service.  It is only relevant to services which extend TaskTriggerService.
     *
     * @return
     */
    @Override
    public ServiceConfig getServiceConfig() {
        return serviceConfigFactory.create(Constants.APIFE_SERVICE_NAME);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Class[] getFactoryServices() {
        return FACTORY_SERVICES;
    }

    public void registerCloudStore(XenonServiceGroup cloudStore) {
        this.cloudStore = cloudStore;
        addXenonServiceGroup(cloudStore);
    }

    public XenonServiceGroup getCloudStore() {
        return this.cloudStore;
    }

    public void registerScheduler(XenonServiceGroup scheduler) {
        this.scheduler = scheduler;
        addXenonServiceGroup(scheduler);
    }

    public XenonServiceGroup getScheduler() {
        return this.scheduler;
    }

    private void addXenonServiceGroup(XenonServiceGroup xenonServiceGroup) {
        xenonServiceGroups.add(xenonServiceGroup);
        xenonServiceGroup.setPhotonControllerXenonHost(this);
    }

    public ServiceConfigFactory getServiceConfigFactory() {
        return serviceConfigFactory;
    }

    public void setCloudStoreHelper(CloudStoreHelper cloudStoreHelper) {
        this.cloudStoreHelper = cloudStoreHelper;
    }

    public CloudStoreHelper getCloudStoreHelper() {
        return cloudStoreHelper;
    }
}
