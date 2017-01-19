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

import com.vmware.photon.controller.api.client.ApiClient;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.AgentControlClientProvider;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.HostClientProvider;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.manifest.BuildInfo;
import com.vmware.photon.controller.common.provider.SystemConfigProvider;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.CloudStoreHelperProvider;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.XenonHostInfoProvider;
import com.vmware.photon.controller.common.xenon.XenonServiceGroup;
import com.vmware.photon.controller.common.xenon.serializer.KryoSerializerCustomization;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.photon.controller.nsxclient.NsxClientFactoryProvider;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.UtilsHelper;
import com.vmware.xenon.services.common.RootNamespaceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * This class implements the Xenon service host object. It is the only Xenon host for Photon Controller
 * and all previous Xenon host objects are now Xenon service groups which register with this host.
 */
public class PhotonControllerXenonHost
        extends AbstractServiceHost
        implements AgentControlClientProvider,
        HostClientProvider,
        NsxClientFactoryProvider,
        XenonHostInfoProvider,
        CloudStoreHelperProvider {

    public static final int DEFAULT_CONNECTION_LIMIT_PER_HOST = 1024;

    public static final int INDEX_SEARCHER_COUNT_THRESHOLD = 1024;

    private static final Logger logger = LoggerFactory.getLogger(PhotonControllerXenonHost.class);
    public static final String FACTORY_SERVICE_FIELD_NAME_SELF_LINK = "SELF_LINK";
    public static final String KEYSTORE_FILE = "/keystore.jks";
    public static final String KEYSTORE_PASSWORD = UUID.randomUUID().toString();

    private AgentControlClientFactory agentControlClientFactory;
    private HostClientFactory hostClientFactory;
    private final NsxClientFactory nsxClientFactory;
    private CloudStoreHelper cloudStoreHelper;
    private ApiClient apiClient;
    private BuildInfo buildInfo;

    private final List<XenonServiceGroup> xenonServiceGroups = Collections.synchronizedList(new ArrayList<>());
    private XenonServiceGroup cloudStore;
    private XenonServiceGroup scheduler;
    private XenonServiceGroup housekeeper;
    private XenonServiceGroup deployer;
    private SystemConfigProvider systemConfigProvider;
    private ServiceClient serviceClient;

    // This flag is set to true only in the installer based deployment and it is used to override the Xenon service
    // client for a non-auth installer to be able to talk to auth enabled management plane.
    private boolean inInstaller = false;

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
     * @throws Throwable
     */
    public PhotonControllerXenonHost(XenonConfig xenonConfig,
                                     HostClientFactory hostClientFactory,
                                     AgentControlClientFactory agentControlClientFactory,
                                     NsxClientFactory nsxClientFactory,
                                     CloudStoreHelper cloudStoreHelper,
                                     SSLContext sslContext) throws Throwable {
        super(xenonConfig);
        this.buildInfo = BuildInfo.get(this.getClass());

        if (hostClientFactory == null || agentControlClientFactory == null) {
            ThriftModule thriftModule = new ThriftModule(sslContext);
            if (hostClientFactory == null) {
                hostClientFactory = thriftModule.getHostClientFactory();
            }

            if (agentControlClientFactory == null) {
                agentControlClientFactory = thriftModule.getAgentControlClientFactory();
            }
        }

        this.hostClientFactory = hostClientFactory;
        this.agentControlClientFactory = agentControlClientFactory;
        this.nsxClientFactory = nsxClientFactory;
        this.cloudStoreHelper = cloudStoreHelper;
    }

    public void regenerateThriftClients(SSLContext sslContext) {
      try {
        ThriftModule thriftModule = new ThriftModule(sslContext);
        this.agentControlClientFactory = thriftModule.getAgentControlClientFactory();
        this.hostClientFactory = thriftModule.getHostClientFactory();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public HostClient getHostClient() {
      // The request ID is in the Xenon thread context
      // We need to put it in the MDC for use by calling agent Thrift
      String requestId = UtilsHelper.getThreadContextId();
      if (requestId != null) {
        LoggingUtils.setRequestId(requestId);
      }

      return hostClientFactory.create();
    }

    public ApiClient getApiClient() {
      return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
      this.apiClient = apiClient;
    }

    @Override
    public AgentControlClient getAgentControlClient() {
      // The request ID is in the Xenon thread context
      // We need to put it in the MDC for use by calling agent Thrift
      String requestId = UtilsHelper.getThreadContextId();
      if (requestId != null) {
        LoggingUtils.setRequestId(requestId);
      }

      return this.agentControlClientFactory.create();
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
         * Add customized Kryo serialization for both object and document serializers.
         */
        KryoSerializerCustomization kryoSerializerCustomization = new KryoSerializerCustomization();
        Utils.registerCustomKryoSerializer(kryoSerializerCustomization, true);
        Utils.registerCustomKryoSerializer(kryoSerializerCustomization, false);

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

    @SuppressWarnings("rawtypes")
    @Override
    public Class[] getFactoryServices() {
        return FACTORY_SERVICES;
    }

    @Override
    public NsxClientFactory getNsxClientFactory() {
      return this.nsxClientFactory;
    }

    public void setInInstaller(boolean inInstaller) {
      this.inInstaller = inInstaller;
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

    public void registerHousekeeper(XenonServiceGroup housekeeper) {
        this.housekeeper = housekeeper;
        addXenonServiceGroup(housekeeper);
    }

    public XenonServiceGroup getHousekeeper() {
      return this.housekeeper;
    }

    public void registerDeployer(XenonServiceGroup deployer) {
      this.deployer = deployer;
      addXenonServiceGroup(deployer);
    }

    public XenonServiceGroup getDeployer() {
      return this.deployer;
    }


    private void addXenonServiceGroup(XenonServiceGroup xenonServiceGroup) {
        xenonServiceGroups.add(xenonServiceGroup);
        xenonServiceGroup.setPhotonControllerXenonHost(this);
    }

    /**
     * Adds a service to a privileged list, allowing it to operate on authorization.
     * context
     */
    @Override
    public void addPrivilegedService(Class<? extends Service> serviceType) {
      super.addPrivilegedService(serviceType);
    }

    public SystemConfigProvider getSystemConfigProvider() {
        return systemConfigProvider;
    }

    public SystemConfigProvider setSystemConfigProvider(SystemConfigProvider systemConfigProvider) {
        return this.systemConfigProvider = systemConfigProvider;
    }

    public void setCloudStoreHelper(CloudStoreHelper cloudStoreHelper) {
        this.cloudStoreHelper = cloudStoreHelper;
    }

    @Override
    public CloudStoreHelper getCloudStoreHelper() {
        return cloudStoreHelper;
    }
}
