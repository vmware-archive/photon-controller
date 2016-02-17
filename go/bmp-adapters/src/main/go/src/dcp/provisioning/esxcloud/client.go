package esxcloud

import (
	"dcp"
	"dcp/provisioning"
	"fmt"
	"net/http"
	"time"

	"github.com/golang/glog"
	"golang.org/x/net/context"
)

type Client struct {
	service *EsxcapiService
}

// NewClient returns a 'esxcloud Client' struct suitable for use against an ESX Cloud API endpoint
func NewClient() (*Client, error) {

	newHTTPClient := &http.Client{}
	esxcapi, err := EsxcapiNew(newHTTPClient)

	if err != nil {
		return nil, err
	}

	// Construct a simplistic un-authenticated http client
	esxc := &Client{
		service: esxcapi,
	}
	return esxc, nil
}

// InsertInstance creates an instance with the given description and name.
func (esxc *Client) InsertInstance(ctx context.Context, project, zone string, instance *EsxcapiInstance) (*EsxcapiOperation, error) {
	var err error
	var op *EsxcapiOperation
	donec := make(chan bool)

	go func() {

		if glog.V(dcp.Trace) {
			glog.Infof("|ESXCLOUD-TRACE| InsertInstance(zone=%q)", zone)
		}
		op, err = esxc.service.EsxcapiInstanceInsert(project, zone, instance)
		if err != nil {
			if glog.V(dcp.Debug) {
				glog.Infof("|ESXCLOUD-DEBUG| InsertInstance(zone=%q) failed with err=%q", zone, err)
			}
		}
		if op != nil {
			if glog.V(dcp.Debug) {
				glog.Infof("|ESXCLOUD-DEBUG| InsertInstance(): insert(%s): Posted operation %s", instance.Name, op.Name)
			}
		}
		close(donec)
	}()

	select {
	case <-donec:
	case <-ctx.Done():
		err = fmt.Errorf("insert(%s): %s", instance.Name, ctx.Err())
	}

	if err != nil {
		return nil, err
	}
	// No need to wait further with ESX Cloud - the create is synchronous.
	return op, nil
}

// DeleteInstance deletes an instance with the given description and name.
func (esxc *Client) DeleteInstance(ctx context.Context, projectID, zone, esxcloudVMID string) (*EsxcapiOperation, error) {
	var err error
	var op *EsxcapiOperation
	donec := make(chan bool)

	go func() {
		if glog.V(dcp.Trace) {
			glog.Infof("|ESXCLOUD-TRACE| DeleteInstance(projectID=%q, zone=%q, esxCloudVMID=%q)", projectID, zone, esxcloudVMID)
		}
		op, err = esxc.service.EsxcapiInstanceDelete(projectID, zone, esxcloudVMID)
		if err != nil {
			if glog.V(dcp.Debug) {
				glog.Infof("|ESXCLOUD-DEBUG| DeleteInstance(zone=%q) failed with err=%q", zone, err)
			}
		}

		if op != nil {
			if glog.V(dcp.Debug) {
				glog.Infof("|ESXCLOUD-DEBUG| DeleteInstance(id=%s): Posted operation %s", esxcloudVMID, op.Name)
			}
		}
		close(donec)
	}()

	select {
	case <-donec:
	case <-ctx.Done():
		err = fmt.Errorf("delete(id=%s): %s", esxcloudVMID, ctx.Err())
	}

	if err != nil {
		return nil, err
	}

	return op, nil
}

// PowerOnInstance Power-ons an instance with the given description and name.
func (esxc *Client) PowerOnInstance(ctx context.Context, projectID, zone, esxcloudVMID string) (*EsxcapiOperation, error) {
	return esxc.powerChangeInstance(ctx, projectID, zone, esxcloudVMID, true)
}

// PowerOffInstance Power-ffs an instance with the given description and name.
func (esxc *Client) PowerOffInstance(ctx context.Context, projectID, zone, esxcloudVMID string) (*EsxcapiOperation, error) {
	return esxc.powerChangeInstance(ctx, projectID, zone, esxcloudVMID, false)
}

// powerChangeInstance Power-ons/offs an instance with the given description and name.
func (esxc *Client) powerChangeInstance(ctx context.Context, projectID, zone, esxcloudVMID string, powerOn bool) (*EsxcapiOperation, error) {
	var err error
	var op *EsxcapiOperation
	donec := make(chan bool)

	go func() {
		if powerOn == true {
			op, err = esxc.service.EsxcapiInstancePowerOn(projectID, zone, esxcloudVMID)
		} else {
			op, err = esxc.service.EsxcapiInstancePowerOff(projectID, zone, esxcloudVMID)
		}

		if err != nil {
			if glog.V(dcp.Debug) {
				glog.Infof("|ESXCLOUD-DEBUG| powerChangeInstance(zone=%q) failed with err=%q", zone, err)
			}
		}

		if op != nil {
			if glog.V(dcp.Debug) {
				glog.Infof("|ESXCLOUD-DEBUG| powerChangeInstance(id=%s): Posted operation %s", esxcloudVMID, op.Name)
			}
		}
		close(donec)
	}()

	select {
	case <-donec:
	case <-ctx.Done():
		err = fmt.Errorf("powerChangeInstance(id=%s): %s", esxcloudVMID, ctx.Err())
	}

	if err != nil {
		return nil, err
	}

	return op, nil
}

// ListInstance lists all instances in the project/zone
func (esxc *Client) ListInstance(ctx context.Context, project, zone string) (*EsxcapiInstanceList, error) {
	var err error
	var list *EsxcapiInstanceList
	donec := make(chan bool)

	go func() {
		// TODO(dgolds)	list, err = esxc.Service.Instances.List(project, zone).Do()
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| ListInstance() UNIMPLEMENTED HERE")
		}
		close(donec)
	}()

	select {
	case <-donec:
	case <-ctx.Done():
		err = fmt.Errorf("ListInstance(%s): %s", zone, ctx.Err())
	}

	return list, err
}

func (esxc *Client) ListComputeHosts(ctx context.Context, pool *provisioning.ResourcePoolState,
	description *provisioning.ComputeDescription, apiURI string) ([]*provisioning.ComputeState, error) {

	i := &Instance{
		pool: pool,
		computeHost: &provisioning.ComputeStateWithDescription{
			Description: *description,
			ComputeState: provisioning.ComputeState{
				AdapterManagementReference: apiURI,
			},
		},
	}

	esxcloudInstances, err := esxc.ListInstance(ctx, i.Project(), i.Zone())
	if err != nil {
		return nil, err
	}

	var instances []*provisioning.ComputeState
	for _, esxcloudInst := range esxcloudInstances.Items {
		instances = append(instances, newInstance(pool, i.computeHost, nil).ToComputeState(esxcloudInst))
	}

	return instances, nil

}

// GetInstance gets an instance's properties
func (esxc *Client) GetInstance(ctx context.Context, project, zone, esxcloudVMID string) (*EsxcapiInstance, error) {
	var ci *EsxcapiInstance
	var err error
	donec := make(chan bool)

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| GetInstance(project=%+v, zone = %+v, esxcloudVMID=%q)...", project, zone, esxcloudVMID)
	}

	ci = &EsxcapiInstance{}
	ci.Name = esxcloudVMID

	go func() {
		ci, err = esxc.service.EsxcapiGetInstance(project, zone, esxcloudVMID)
		for i := 0; i < 120 && ci.IPAddress == ""; i++ {

			glog.Infof("|ESXCLOUD-TRACE| GetInstance(getNetworkAttempt #%v)", i)

			ci.IPAddress, err = esxc.service.EsxcapiGetInstanceNetworkInfo(project, zone, esxcloudVMID)
			if ci.IPAddress == "" {
				time.Sleep(5000)
			}
		}
		if ci.IPAddress == "" {
			ci.IPAddress = "0.0.0.0"
		}
		close(donec)
	}()

	select {
	case <-donec:
	case <-ctx.Done():
		return nil, fmt.Errorf("get instance(id=%s): %s", esxcloudVMID, ctx.Err())
	}

	return ci, err
}
