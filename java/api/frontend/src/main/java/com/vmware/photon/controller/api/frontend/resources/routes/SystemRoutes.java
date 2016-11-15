package com.vmware.photon.controller.api.frontend.resources.routes;

/**
 * String constants of routes used by System related resource classes.
 */
public class SystemRoutes {
    public static final String API = "/system";

    public static final String GET_DATA_QUORUM = API+ "/data-quorum";

    public static final String CREATE_QUORUM = "update/{id}";
}
