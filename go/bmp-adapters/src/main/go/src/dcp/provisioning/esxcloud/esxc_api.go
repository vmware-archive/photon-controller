package esxcloud

import (
	"bytes"
	"dcp"
	"encoding/json"
	"errors"
	"fmt"
	"io/ioutil"
	"net/http"
	"time"

	//"github.com/esxcloud/photon-go-sdk/photon"
	"github.com/golang/glog"
)

const apiVersion = "v1"

// EsxcapiService ... This is the main way to reference and hold context for
// the ESX Cloud 'api' here
type EsxcapiService struct {
	httpClient *http.Client
}

// EsxcapiNew ... This creates a new EsxcapiService from an http Client
func EsxcapiNew(client *http.Client) (*EsxcapiService, error) {
	if client == nil {
		return nil, errors.New("client is nil")
	}
	s := &EsxcapiService{httpClient: client}

	return s, nil
}

type EsxcapiOperation struct {
	ClientOperationID string `json:"clientOperationId,omitempty"`

	// CreationTimestamp: [Output Only] Creation timestamp in RFC3339 text
	// format (output only).
	CreationTimestamp string `json:"creationTimestamp,omitempty"`

	EndTime string `json:"endTime,omitempty"`

	HTTPErrorMessage string `json:"httpErrorMessage,omitempty"`

	HTTPErrorStatusCode int64 `json:"httpErrorStatusCode,omitempty"`

	// Id: [Output Only] Unique identifier for the resource; defined by the
	// server.
	ID string `json:"id,omitempty,string"`

	// InsertTime: [Output Only] The time that this operation was requested.
	// This is in RFC 3339 format.
	InsertTime string `json:"insertTime,omitempty"`

	// Kind: [Output Only] Type of the resource. Always kind#operation for
	// Operation resources.
	Kind string `json:"kind,omitempty"`

	// Name: [Output Only] Name of the resource (output only).
	Name string `json:"name,omitempty"`

	OperationType string `json:"operationType,omitempty"`

	Progress int64 `json:"progress,omitempty"`

	// Region: [Output Only] URL of the region where the operation resides
	// (output only).
	Region string `json:"region,omitempty"`

	// SelfLink: [Output Only] Server defined URL for the resource.
	SelfLink string `json:"selfLink,omitempty"`

	// StartTime: [Output Only] The time that this operation was started by
	// the server. This is in RFC 3339 format.
	StartTime string `json:"startTime,omitempty"`

	// Status: [Output Only] Status of the operation. Can be one of the
	// following: "PENDING", "RUNNING", or "DONE".
	Status string `json:"status,omitempty"`

	// StatusMessage: [Output Only] An optional textual description of the
	// current status of the operation.
	StatusMessage string `json:"statusMessage,omitempty"`

	// TargetId: [Output Only] Unique target id which identifies a
	// particular incarnation of the target.
	TargetID uint64 `json:"targetId,omitempty,string"`

	// TargetLink: [Output Only] URL of the resource the operation is
	// mutating (output only).
	TargetLink string `json:"targetLink,omitempty"`

	User string `json:"user,omitempty"`

	Errors []string
}

type EsxcapiAttachedDisk struct {
	ID                 string `json:"id,omitempty"`
	Kind               string `json:"kind,omitempty"`
	BootDisk           bool   `json:"bootDisk,omitempty"`
	DiskName           string `json:"name,omitempty"`
	DiskFlavor         string `json:"flavor,omitempty"`
	SourceImageID      string `json:"sourceImageId,omitempty"`
	DiskCapacityGBytes int64  `json:"capacityGb"` // Gigabytes
}

// EsxcapiInstance ... This is the structure for defining an Instance for ESX
// Cloud.
type EsxcapiInstance struct {
	// Id: Unique identifier for the resource; defined by the ESX Cloud server
	// (output only).
	EsxcloudID string `json:"id,omitempty"`

	// SelfLink: Server defined URL for this resource (output only).
	SelfLink string `json:"selfLink,omitempty"`

	// Name: Name of the resource; provided by the client when the resource
	// is created. The name must be 1-63 characters long, and comply with
	// RFC1035.
	Name string `json:"name,omitempty"`

	// MachineFlavor: name of the flavor resource describing which
	// machine type to use to host the instance; provided by the client when
	// the instance is created.
	MachineFlavor string `json:"flavor,omitempty"`

	// Tags: Not yet used
	Tags []string `json:"tags,omitempty"`

	// Kind: Type of the resource.
	Kind string `json:"kind,omitempty"`

	// State: Instance status.
	State string `json:"state,omitempty"`

	// Description: An optional textual description of the resource;
	// provided by the client when the resource is created.
	Description string `json:"description,omitempty"`

	// Source image ID
	SourceImageID string `json:"sourceImageId,omitempty"`

	// IP Address
	// TODO - decide how to handle multiple IP Addresses, netmask etc
	IPAddress string `json:"ipAddress,omitempty"`

	// Disks: Array of disks associated with this instance. Persistent disks
	// must be created before you can assign them.
	// Disks []*AttachedDisk `json:"disks,omitempty"`

	// NetworkInterfaces: Array of configurations for this interface. This
	// specifies how this interface is configured to interact with other
	// network services, such as connecting to the internet. Currently,
	// ONE_TO_ONE_NAT is the only access config supported. If there are no
	// accessConfigs specified, then this instance will have no external
	// internet access.
	// NetworkInterfaces []*NetworkInterface `json:"networkInterfaces,omitempty"`

	Disks []*EsxcapiAttachedDisk `json:"attachedDisks,omitempty"`

	// ISOFiles: Array of paths to ISO Files that should be attached to
	// the virtual machine on creation.
	ISOFiles []string `json:"isoFilePaths,omitempty"`
}

type EsxcapiInstanceList struct {
	// Id: Unique identifier for the resource; defined by the server (output
	// only).
	ID string `json:"id,omitempty"`

	// Items: A list of instance resources.
	Items []*EsxcapiInstance `json:"items,omitempty"`

	// Kind: Type of resource.
	Kind string `json:"kind,omitempty"`

	// NextPageToken: A token used to continue a truncated list request
	// (output only).
	NextPageToken string `json:"nextPageToken,omitempty"`

	// SelfLink: Server defined URL for this resource (output only).
	SelfLink string `json:"selfLink,omitempty"`
}

func setEsxCloudRestAPIHeaders(req *http.Request) {
	req.Header.Set("Content-Type", "application/json; charset=utf-8")
	req.Header.Set("User-Agent", "esxcloud-enatai-adapter-go-client/0.5")
	req.Header.Set("Data-Type", "json")
}

// esxcloudAPICreatevmDiskParams ... This should be a format that can be converted into a JSON Payload
// to be sent to ESX Cloud API Server. It will be referenced by esxcloudAPICreatevmParams
type esxcloudAPICreatevmDiskParams struct {
	ID         string `json:"id,omitempty"`
	Name       string `json:"name,omitempty"`
	Image      string `json:"image,omitempty"`
	Kind       string `json:"kind,omitempty"`
	Flavor     string `json:"flavor,omitempty"`
	CapacityGb int64  `json:"capacityGb,omitempty,string"`
	BootDisk   bool   `json:"bootDisk"`
}

func createDiskParamsForAttachedDisks(instance *EsxcapiInstance) []esxcloudAPICreatevmDiskParams {
	var diskParams []esxcloudAPICreatevmDiskParams
	for _, disk := range instance.Disks {
		diskParam := esxcloudAPICreatevmDiskParams{
			Name:       disk.DiskName,
			Kind:       disk.Kind,
			Flavor:     disk.DiskFlavor,
			CapacityGb: disk.DiskCapacityGBytes,
			BootDisk:   disk.BootDisk,
		}
		diskParams = append(diskParams, diskParam)
	}
	return diskParams
}

type esxcloudAPIVMOperations struct {
	Operation string `json:"operation,omitempty"`
}

// esxcloudAPICreatevmParams ... This should be a format that can be converted into a JSON Payload
// to be sent to ESX Cloud API Server.
type esxcloudAPICreatevmParams struct {
	Name          string                          `json:"name,omitempty"`
	Flavor        string                          `json:"flavor,omitempty"`
	SourceImageID string                          `json:"sourceImageId,omitempty"`
	AttachedDisks []esxcloudAPICreatevmDiskParams `json:"attachedDisks,omitempty"`
}

func closeBody(res *http.Response) {
	if res == nil || res.Body == nil {
		return
	}
	res.Body.Close()
}

func checkResponse(res *http.Response) error {
	if res.StatusCode >= 200 && res.StatusCode <= 299 {
		return nil
	}
	if res == nil {
		return fmt.Errorf("error response from http operation:  Response is nil")
	}
	return fmt.Errorf("error response from http operation: Status Code=%v", res.StatusCode)
}

// Note that this will set the instance.EsxcloudID if successful
func (esxcapi *EsxcapiService) EsxcapiInstanceInsert(projectID string, zone string, instance *EsxcapiInstance) (*EsxcapiOperation, error) {
	op := &EsxcapiOperation{}
	var err error

	// Construct request
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiInstanceInsert(zone=%q)...", zone)
	}

	attachedDisks := createDiskParamsForAttachedDisks(instance)

	postData := esxcloudAPICreatevmParams{
		Name:          instance.Name,
		Flavor:        instance.MachineFlavor,
		SourceImageID: instance.Disks[0].SourceImageID, // e.g "d19bce58-3e3a-40c2-a901-a2381a92abb8"
		AttachedDisks: attachedDisks,
	}

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiInstanceInsert() Data to JSON-encode is %+v: ", postData)
	}

	buf := &bytes.Buffer{}
	err = json.NewEncoder(buf).Encode(postData)

	if err != nil {
		glog.Infof("JSON Marshalling failed!")
		return nil, err
	}

	url := "http://" + zone + "/v1/projects/" + projectID + "/vms"

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiInstanceInsert() Post to URL=%q", url)
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiInstanceInsert() to Insert this Instance: %+v", buf.String())
	}

	// construct & send request
	req, _ := http.NewRequest("POST", url, buf)
	setEsxCloudRestAPIHeaders(req)
	resp, err := esxcapi.httpClient.Do(req)

	// Error/response handling
	if err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| EsxcapiInstanceInsert() failed to POST request")
		}

		return nil, err
	}

	defer closeBody(resp)
	bodyBytes, err2 := ioutil.ReadAll(resp.Body)
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiInstanceInsert() response was %s\n\n<<%s>>\n\n", resp, bodyBytes)
	}

	var data map[string]interface{}
	if err := json.Unmarshal(bodyBytes, &data); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| EsxcapiInstanceInsert() Unmarshal indicated error: %q", err)
		}
		return nil, err
	}

	if err := checkResponse(resp); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| EsxcapiInstanceInsert() CheckResponse indicated error: %q", err)
		}
		return nil, err
	}

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiInstanceInsert() response is parsed as data.entity=%+v\n", data["entity"])
	}

	// Extract the task id
	taskID := data["id"].(string)
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiInstanceInsert() response has taskID==%+v\n", taskID)
	}

	// Extract the entity ID. Luckily we get to know this syncrohonously from the create response
	var entityMap = data["entity"].(map[string]interface{})
	instance.EsxcloudID = entityMap["id"].(string)
	op.ID = instance.EsxcloudID

	if _, err := esxcapi.waitForTask(taskID, zone); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| EsxcapiInstanceInsert() failed to wait for task", instance.EsxcloudID)
		}
	}

	if glog.V(dcp.Debug) {
		glog.Infof("|ESXCLOUD-DEBUG| EsxcapiInstanceInsert() Created VM has ESX CLOUD id = %v", instance.EsxcloudID)
	}

	// Attach ISO files with the created VM
	for _, isoFile := range instance.ISOFiles {
		err := esxcapi.EsxcapiAttachISO(zone, instance.EsxcloudID, isoFile)
		if err != nil {
			return nil, err
		}
	}

	return op, err2
}

func (esxcapi *EsxcapiService) EsxcapiAttachISO(zone string, esxcloudVMID string, isoFilePath string) error {
	var err error

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiAttachISO(zone=%q, esxcloudVMID=%q, isoFilePath=%q)...", zone, esxcloudVMID, isoFilePath)
	}

//	url := "http://" + zone
//	client := photon.NewClient(url, nil)
//
//	attachTask, err := client.VMs.AttachISO(esxcloudVMID, isoFilePath)
//	if err != nil {
//		if glog.V(dcp.Debug) {
//			glog.Infof("|ESXCLOUD-DEBUG| EsxcapiAttachISO() failed to attach ISO. Error: %q", err)
//		}
//		return err
//	}
//
//	_, err = client.Tasks.Wait(attachTask.ID)
//	if err != nil {
//		if glog.V(dcp.Debug) {
//			glog.Infof("|ESXCLOUD-DEBUG| EsxcapiAttachISO() failed while waiting on task completion. Error: %q", err)
//		}
//		return err
//	}

	return err
}

func (esxcapi *EsxcapiService) EsxcapiGetInstance(projectID string, zone string, esxcloudVMID string) (*EsxcapiInstance, error) {
	var err error
	instance := &EsxcapiInstance{}

	url := "http://" + zone + "/v1/vms/" + esxcloudVMID
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiGetInstance(zone=%q, esxcloudVMID=%q)...", zone, esxcloudVMID)
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiGetInstance() to GET against: URL=%q", url)
	}

	// construct request
	req, _ := http.NewRequest("GET", url, nil)
	setEsxCloudRestAPIHeaders(req)

	// Send main VM info request. This will get us the VM and disk info but NOT the network info. That's more (async) work
	resp, err := esxcapi.httpClient.Do(req)

	// Error/response handling
	if err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| EsxcapiGetInstance() failed to GET")
		}
		return nil, err
	}

	defer closeBody(resp)
	bodyBytes, err2 := ioutil.ReadAll(resp.Body)

	if glog.V(dcp.Debug) {
		glog.Infof("|ESXCLOUD-DEBUG| EsxcapiGetInstance() response was %s\n\n<<%s>>\n\n", resp, bodyBytes)
	}

	if err := json.Unmarshal(bodyBytes, instance); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| EsxcapiGetInstance() Unmarshal indicated error: %q", err)
		}
		return nil, err
	}

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiGetInstance() response is parsed as instance=%+v\n", instance)
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiGetInstance() response is parsed as instance.disk0=%+v\n", instance.Disks[0])
	}
	// Assign the source image id of the VM to the boot disk
	instance.Disks[0].SourceImageID = instance.SourceImageID

	if err := checkResponse(resp); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| EsxcapiGetInstance() CheckResponse indicated error: %q", err)
		}
		return nil, err
	}

	return instance, err2
}

// This is intended to be called in conjunction with EsxcapiGetInstance(). This functiom
// Gets just the IP info and adds it to the instance. In ESX Cloud, getting
// Network info for a VM is an async task. This is unlike the normal get vm info
// API call which is direct and synchronous
func (esxcapi *EsxcapiService) EsxcapiGetInstanceNetworkInfo(projectID string, zone string, esxcloudVMID string) (ipAddress string, err error) {
	ipAddress = ""

	url := "http://" + zone + "/v1/vms/" + esxcloudVMID + "/networks"

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiGetInstanceNetworkInfo(zone=%q, esxcloudVMID=%q)...", zone, esxcloudVMID)
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiGetInstanceNetworkInfo() to GET against: URL=%q", url)
	}

	// construct request
	req, _ := http.NewRequest("GET", url, nil)
	setEsxCloudRestAPIHeaders(req)

	// Send main VM info request. This will get us the VM and disk info but NOT the network info. That's more (async) work
	resp, err := esxcapi.httpClient.Do(req)

	// Error/response handling
	if err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| EsxcapiGetInstanceNetworkInfo() failed to GET")
		}
		return "", err
	}

	defer closeBody(resp)
	bodyBytes, err2 := ioutil.ReadAll(resp.Body)

	if glog.V(dcp.Debug) {
		glog.Infof("|ESXCLOUD-DEBUG| EsxcapiGetInstanceNetworkInfo() response was %s\n\n<<%s>>\n\n", resp, bodyBytes)
	}

	var data map[string]interface{}
	if err := json.Unmarshal(bodyBytes, &data); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| instanceOperationSend() Unmarshal indicated error: %q", err)
		}
		return "", err
	}

	if err := checkResponse(resp); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| EsxcapiGetInstanceNetworkInfo() CheckResponse indicated error: %q", err)
		}
		return "", err
	}

	// Extract the task id
	taskID := data["id"].(string)
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiGetInstanceNetworkInfo() response has taskID==%+v\n", taskID)
	}

	var taskData map[string]interface{}
	if taskData, err = esxcapi.waitForTask(taskID, zone); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| EsxcapiGetInstanceNetworkInfo() failed to wait for task: %q", err)
		}
	}

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiGetInstanceNetworkInfo() waitFortask got taskData= %+v", taskData)
	}

	// extract the ip Address
	if _, ok := taskData["resourceProperties"]; ok {
		resourcePropertiesArray := taskData["resourceProperties"].([]interface{})
		if resourcePropertiesArray != nil {
			rp := resourcePropertiesArray[0].(map[string]interface{})
			ipAddressStruct := rp["ip_address"] // Could be nil or .(map[string]interface{})
			if ipAddressStruct != nil {
				ipAddressStruct2 := ipAddressStruct.(map[string]interface{})
				ipAddress = ipAddressStruct2["ip_address"].(string)
			}
		}
	}
	if glog.V(dcp.Debug) {
		glog.Infof("\n\n\n|ESXCLOUD-DEBUG| EsxcapiGetInstanceNetworkInfo() got ipAddress= %q", ipAddress)
	}

	return ipAddress, err2
}

func (esxcapi *EsxcapiService) EsxcapiInstanceDelete(projectID string, zone string, esxcloudVMID string) (*EsxcapiOperation, error) {
	op := &EsxcapiOperation{}
	var err error

	// Construct request
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiInstanceDelete(zone=%q, esxcloudVMID=%q)...", zone, esxcloudVMID)
	}

	// make URL
	url := "http://" + zone + "/v1/vms/" + esxcloudVMID
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiInstanceDelete() to DELETE against: %s", url)
	}

	// construct request
	req, _ := http.NewRequest("DELETE", url, nil)
	setEsxCloudRestAPIHeaders(req)

	// Send request
	resp, err := esxcapi.httpClient.Do(req)

	// Error/response handling
	if err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| EsxcapiInstanceDelete() failed to DELETE")
		}
		return nil, err
	}

	defer closeBody(resp)
	bodyBytes, err2 := ioutil.ReadAll(resp.Body)

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiInstanceDelete() response was %s\n\nHTTP_RESPONSE_BODY=%q\n\n", resp, bodyBytes)
	}

	var data map[string]interface{}
	if err := json.Unmarshal(bodyBytes, &data); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| EsxcapiInstanceDelete() Unmarshal indicated error: %q", err)
		}
		return nil, err
	}

	if err := checkResponse(resp); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| EsxcapiInstanceDelete() CheckResponse indicated error: %q", err)
		}
		return nil, err
	}

	return op, err2
}

// PowerOn the instance and WAIT for task to progress from STARTED
func (esxcapi *EsxcapiService) EsxcapiInstancePowerOn(projectID string, zone string, esxcloudVMID string) (*EsxcapiOperation, error) {
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiInstancePowerOn(zone=%q, esxcloudVMID=%q)...", zone, esxcloudVMID)
	}
	return esxcapi.instanceOperationSend(projectID, zone, esxcloudVMID, "START_VM")
}

// PowerOff the instance and WAIT for task to progress from STARTED
func (esxcapi *EsxcapiService) EsxcapiInstancePowerOff(projectID string, zone string, esxcloudVMID string) (*EsxcapiOperation, error) {
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiInstancePowerOn(zone=%q, esxcloudVMID=%q)...", zone, esxcloudVMID)
	}
	return esxcapi.instanceOperationSend(projectID, zone, esxcloudVMID, "STOP_VM")
}

// VMOperation is one of START_VM, STOP_VM, .... See the ESXCLOUD API for details
// This will start the operation and wait for the request to progress from STARTED
func (esxcapi *EsxcapiService) instanceOperationSend(projectID string, zone string, esxcloudVMID string, VMOperation string) (op *EsxcapiOperation, err error) {
	op = &EsxcapiOperation{}

	// Construct request
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| instanceOperationSend(zone=%q, esxcloudVMID=%q, VMOperation=%q)...", zone, esxcloudVMID, VMOperation)
	}

	// make URL
	url := "http://" + zone + "/v1/vms/" + esxcloudVMID + "/operations"
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| instanceOperationSend() to POST against: %q", url)
	}

	postData := esxcloudAPIVMOperations{
		Operation: VMOperation,
	}

	buf := &bytes.Buffer{}
	err = json.NewEncoder(buf).Encode(postData)

	// construct & send request
	req, _ := http.NewRequest("POST", url, buf)
	setEsxCloudRestAPIHeaders(req)
	resp, err := esxcapi.httpClient.Do(req)

	// Error/response handling
	if err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| instanceOperationSend() failed to POST")
		}
		return nil, err
	}

	defer closeBody(resp)
	bodyBytes, err2 := ioutil.ReadAll(resp.Body)

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| instanceOperationSend() response was %s\n\nHTTP_RESPONSE_BODY=%q\n\n", resp, bodyBytes)
	}

	var data map[string]interface{}
	if err := json.Unmarshal(bodyBytes, &data); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| instanceOperationSend() Unmarshal indicated error: %q", err)
		}
		return nil, err
	}

	// Extract the task id
	taskID := data["id"].(string)
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| instanceOperationSend(%q) response has taskID==%+v\n", VMOperation, taskID)
	}

	if _, err := esxcapi.waitForTask(taskID, zone); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| instanceOperationSend(%q) failed to wait for task", VMOperation)
		}
	}

	if err := checkResponse(resp); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| instanceOperationSend() CheckResponse indicated error: %q", err)
		}
		return nil, err
	}

	return op, err2
}

// This will wait and loop while the taskState=="STARTED"
func (esxcapi *EsxcapiService) waitForTask(taskID string, zone string) (data map[string]interface{}, err error) {
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| EsxcapiWaitForTask(zone=%q, taskID=%q)...", zone, taskID)
	}
	err = nil
	for err == nil {
		// TODO Check with devs if this sleep pattern is verbotten; it seems the select {} with timeout style is better
		time.Sleep(1000 * time.Millisecond)
		taskState, data, err := esxcapi.getTaskStateOnce(taskID, zone)
		if taskState != "STARTED" {
			return data, err
		}
	}
	return data, err
}

func (esxcapi *EsxcapiService) getTaskStateOnce(taskID string, zone string) (taskState string, data map[string]interface{}, err error) {
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| getTaskStateOnce(zone=%q, taskID=%q)...", zone, taskID)
	}

	// make URL
	url := "http://" + zone + "/v1/tasks/" + taskID
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| getTaskStateOnce() to GET against: %q", url)
	}

	// construct request
	req, _ := http.NewRequest("GET", url, nil)
	setEsxCloudRestAPIHeaders(req)

	// Send request
	resp, err := esxcapi.httpClient.Do(req)

	// Error/response handling
	if err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| getTaskStateOnce() failed to GET")
		}
		return "", nil, err
	}

	defer closeBody(resp)
	bodyBytes, err2 := ioutil.ReadAll(resp.Body)

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| getTaskStateOnce() response was %s\n\nHTTP_RESPONSE_BODY=%q\n\n", resp, bodyBytes)
	}

	if err := json.Unmarshal(bodyBytes, &data); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| getTaskStateOnce() Unmarshal indicated error: %q", err)
		}
		return "", nil, err
	}

	taskState = data["state"].(string)
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| getTaskStateOnce() taskState = %s", taskState)
	}

	if err := checkResponse(resp); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| getTaskStateOnce() CheckResponse indicated error: %q", err)
		}
		return "", nil, err
	}

	return taskState, data, err2
}
