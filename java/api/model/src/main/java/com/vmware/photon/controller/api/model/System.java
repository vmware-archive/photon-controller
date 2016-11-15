package com.vmware.photon.controller.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

/**
 * System API representation
 */

@ApiModel(value = "Information about the Data Quorum.")
@JsonIgnoreProperties(ignoreUnknown = true)
public class System {
    @JsonProperty
    @ApiModelProperty(value = "The Data Quorum for the Photon Controller Deployment", required = true)
    private String dataQuorum;

    public String getDataQuorum() {
        return dataQuorum;
    }
}