package com.vmware.photon.controller.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vmware.photon.controller.api.model.base.VisibleModel;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin API representation
 */
@ApiModel(value = "The tenancy model of Photon Controller is designed to provide tenants with a sub-dividable " +
        "virtual resource pool expressed in terms of quota limits, capabilities, and SLA. " +
        "A tenant contains a collection of projects and resource tickets. Projects are created by carving off " +
        "quota limits from one of the tenant's resource tickets.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Admin extends VisibleModel {

    public static final String KIND = "administrator";

    @JsonProperty
    @ApiModelProperty(value = "kind=\"administrator\"", required = true)
    private String kind = KIND;


    @Override
    public String getKind() {
        return kind;
    }

}