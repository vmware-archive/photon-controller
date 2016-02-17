package esx

//import (
//	"dcp/client"
//	"dcp/common"
//	"dcp/common/test"
//	"dcp/host"
//	"dcp/operation"
//	"dcp/provisioning"
//	"dcp/uri"
//	"flag"
//	"os"
//	"path"
//	"testing"
//	"time"
//
//	"golang.org/x/net/context"
//
//	"code.google.com/p/go-uuid/uuid"
//)
//
//var (
//	testDescriptionLink string
//	testDisks           []*provisioning.DiskState
//)
//
//func healthRequest(t *testing.T, ref uri.URI, u uri.URI) *provisioning.ComputeHealthResponse {
//	ctx := context.Background()
//	req := &provisioning.ComputeHealthRequest{
//		ComputeReference: ref,
//	}
//
//	op := operation.NewPatch(ctx, u, nil)
//	op = client.Send(op.SetBody(req))
//	if err := op.Wait(); err != nil {
//		t.Fatal(err)
//	}
//
//	res := &provisioning.ComputeHealthResponse{}
//	if err := op.DecodeBody(res); err != nil {
//		t.Fatal(err)
//	}
//
//	return res
//}
//
//func createWithESX(t *testing.T, f func(*test.ServiceHost, uri.URI)) {
//	// Any esx box will do, using a vagrant box for example:
//	// export DCP_ESX_HOST_REF=https://root:vagrant@localhost:18443/sdk
//	hostRef := os.Getenv("DCP_ESX_HOST_REF")
//	if hostRef == "" {
//		t.SkipNow()
//	}
//
//	ctx := context.Background()
//	th := test.NewServiceHost(t)
//	defer th.Stop()
//
//	services := []struct {
//		uri string
//		svc host.Service
//	}{
//		{provisioning.InstanceServiceEsx, NewInstanceService()},
//		{provisioning.HealthServiceEsx, NewHealthService()},
//	}
//
//	for _, s := range services {
//		if err := th.StartServiceSync(s.uri, s.svc); err != nil {
//			t.Fatalf("Error starting service %s: %s", s.uri, err)
//		}
//	}
//
//	pool := provisioning.ResourcePoolState{
//		ID: uuid.New(),
//	}
//	poolURI := th.StartMock(&pool)
//	pool.SelfLink = poolURI.Path
//
//	desc := provisioning.ComputeDescription{
//		SupportedChildren:           []string{provisioning.ComputeTypeDockerContainer},
//		TotalMemoryBytes:            1 << 30,
//		EnumerationAdapterReference: "http://localhost:8082/provisioning/esx/enumeration-service",
//		TenantLinks:                 []string{"/core/tenant/random_tenant"},
//	}
//	descURI := th.StartMock(&desc)
//	testDescriptionLink = descURI.Path
//
//	initialComputeStateID := uuid.New()
//	chsd := provisioning.ComputeStateWithDescription{
//		ComputeState: provisioning.ComputeState{
//			ID:                         initialComputeStateID,
//			PowerState:                 provisioning.PowerStateUnknown,
//			ResourcePoolLink:           poolURI.Path,
//			DescriptionLink:            descURI.Path,
//			TenantLinks:                []string{"/core/tenant/random_tenant"},
//			AdapterManagementReference: hostRef,
//		},
//		Description: desc,
//	}
//
//	parentChs := provisioning.ComputeStateWithDescription{
//		ComputeState: provisioning.ComputeState{
//			ID: uuid.New(),
//			AdapterManagementReference: hostRef,
//		},
//		Description: provisioning.ComputeDescription{
//			SupportedChildren: []string{provisioning.ComputeTypeVMGuest},
//		},
//	}
//	parentChsURI := th.StartMock(&parentChs)
//	chsd.ParentLink = parentChsURI.Path
//
//	for _, diskState := range testDisks {
//		diskURI := th.StartMock(diskState)
//		diskState.SelfLink = diskURI.Path
//		diskState.ID = path.Base(diskState.SelfLink)
//		chsd.DiskLinks = append(chsd.DiskLinks, diskState.SelfLink)
//	}
//
//	chsURI := th.StartMock(&chsd)
//	chsd.SelfLink = chsURI.Path
//
//	taskState := provisioning.NewComputeSubTaskState()
//	taskState.TaskInfo.Stage = common.TaskStageCreated
//	taskURI := th.StartMock(taskState)
//
//	req := &provisioning.ComputeInstanceRequest{
//		ComputeReference:          chsURI,
//		ProvisioningTaskReference: taskURI,
//		RequestType:               provisioning.InstanceRequestTypeCreate,
//	}
//
//	op := operation.NewPatch(ctx, uri.Extend(th.URI(), provisioning.InstanceServiceEsx), nil)
//	if err := client.Send(op.SetBody(req)).Wait(); err != nil {
//		t.Fatalf("Error issuing PATCH: %s", err)
//	}
//
//	err := test.WaitForTaskCompletion(time.Now().Add(operation.DefaultTimeout), taskURI)
//	if err != nil {
//		t.Fatal(err)
//	}
//
//	// Check ComputeStateID is patched with instanceUUID
//	if chsd.ID == initialComputeStateID {
//		t.Error("expected ComputeStateID to be patched with instanceUUID")
//	}
//	f(th, chsURI)
//
//	checks := []struct {
//		path string
//		ref  uri.URI
//	}{
//		{provisioning.HealthServiceEsx, parentChsURI},
//		{provisioning.HealthServiceEsx, chsURI},
//	}
//
//	health := make(map[string]*provisioning.ComputeHealthResponse)
//
//	for _, check := range checks {
//		healthURI := uri.Extend(th.URI(), check.path)
//		res := healthRequest(t, check.ref, healthURI)
//		health[check.ref.Path] = res
//	}
//
//	// just check that we properly dispatch VM/Host health
//	if health[chsURI.Path].TotalMemoryBytes != desc.TotalMemoryBytes {
//		t.Error("VM TotalMemoryBytes mismatch")
//	}
//
//	// ESX host will be something > 1Gb
//	if health[parentChsURI.Path].TotalMemoryBytes == desc.TotalMemoryBytes {
//		t.Error("Host TotalMemoryBytes mismatch")
//	}
//
//	req.RequestType = provisioning.InstanceRequestTypeDelete
//	op = operation.NewPatch(ctx, uri.Extend(th.URI(), provisioning.InstanceServiceEsx), nil)
//	if err := client.Send(op.SetBody(req)).Wait(); err != nil {
//		t.Fatalf("Error issuing PATCH: %s", err)
//	}
//
//	err = test.WaitForTaskCompletion(time.Now().Add(operation.DefaultTimeout), taskURI)
//	if err != nil {
//		t.Fatal(err)
//	}
//
//	if chsd.PrimaryMAC == "" {
//		t.Error("expected PrimaryMAC to be set")
//	}
//}
//
//func TestInstanceServiceCreateWithESX(t *testing.T) {
//	createWithESX(t, func(*test.ServiceHost, uri.URI) {})
//}
//
//// example:
//// go test -run TestInstanceServiceCreateLinkedCloneWithESX -esx.vmdk discus/discus-disk1.vmdk
//var vmdkSource = flag.String("esx.vmdk", "", "vmdk source relative to datastore")
//
//func TestInstanceServiceCreateLinkedCloneWithESX(t *testing.T) {
//	if *vmdkSource == "" {
//		t.SkipNow()
//	}
//
//	testDisks = []*provisioning.DiskState{
//		{
//			DiskType:             provisioning.DiskTypeHDD,
//			SourceImageReference: "file:///" + *vmdkSource,
//		},
//	}
//
//	createWithESX(t, func(*test.ServiceHost, uri.URI) {})
//}
