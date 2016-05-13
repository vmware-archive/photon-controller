package com.vmware.photon.controller.apife.backends.clients;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vmware.photon.controller.apife.entities.HostEntity;
import com.vmware.photon.controller.apife.exceptions.external.SpecInvalidException;
import com.vmware.photon.controller.apife.lib.UsageTagHelper;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.deployer.dcp.task.ValidateHostTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ValidateHostTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;

/**
 * Deployer Client Facade that exposes deployer functionality via high-level methods,
 * and hides Xenon protocol details.
 */
@Singleton
public class DeployerClient {
    private static final Logger logger = LoggerFactory.getLogger(DeployerClient.class);

    private static final String COMMA_DELIMITED_REGEX = "\\s*,\\s*";

    private DeployerXenonRestClient dcpClient;
    private ApiFeXenonRestClient apiFeDcpClient;

    @Inject
    public DeployerClient(DeployerXenonRestClient dcpClient, ApiFeXenonRestClient apiFeDcpClient)
            throws URISyntaxException {
        this.dcpClient = dcpClient;
        this.dcpClient.start();
        this.apiFeDcpClient = apiFeDcpClient;
        this.apiFeDcpClient.start();
    }

    public ValidateHostTaskService createHost(HostEntity host)
            throws SpecInvalidException {
        ValidateHostTaskService.State state = new ValidateHostTaskService.State();
        state.hostAddress = host.getAddress();
        state.userName = host.getUsername();
        state.password = host.getPassword();
        state.metadata = host.getMetadata();
        state.usageTags = UsageTagHelper.deserializeToStringSet(host.getUsageTags());
        if (state.metadata.containsKey(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES)) {
            String[] allowedDataStores =
                    state.metadata.get(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES).
                            trim().split(COMMA_DELIMITED_REGEX);
            state.dataStores = new HashSet<>();
            Collections.addAll(state.dataStores, allowedDataStores);
        }

        if (state.metadata.containsKey(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS)) {
            String[] allowedNetworks =
                    state.metadata.get(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS).
                            trim().split(COMMA_DELIMITED_REGEX);
            state.networks = new HashSet<>();
            Collections.addAll(state.networks, allowedNetworks);
        }

        // Persist the database ID of the host to the DCP entity so we have a unified ID across the system
        state.documentSelfLink = host.getId();

        ValidateHostTaskService createTask = new ValidateHostTaskService();
        createTask.setState()
        Operation operation = dcpClient.post(
                ValidateHostTaskFactoryService.SELF_LINK, createTask);
        return operation.getBody(ValidateHostTaskService.class);
    }

}
