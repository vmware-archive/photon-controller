package esx

//import (
//	"dcp/client"
//	"dcp/common"
//	"dcp/common/test"
//	"dcp/operation"
//	"dcp/provisioning"
//	"dcp/uri"
//	"strings"
//	"testing"
//	"time"
//
//	"golang.org/x/net/context"
//)
//
//func TestEnumerationServiceWithESX(t *testing.T) {
//	ctx := context.Background()
//
//	createWithESX(t, func(th *test.ServiceHost, chsURI uri.URI) {
//
//		state := &provisioning.ComputeStateWithDescription{}
//		err := provisioning.GetComputeStateWithDescription(ctx, chsURI, state)
//		if err != nil {
//			t.Fatal(err)
//		}
//
//		// mock a compute factory
//		computeFactoryMock := th.StartFactoryMock(provisioning.Compute, func() interface{} { return &provisioning.ComputeState{} })
//
//		// Mock a compute state and add it to compute mock factory to simulate a compute without physical VM to trigger a delete
//		existingVMComputeMockURI := startOneCompouteMockService(th, &computeFactoryMock)
//
//		// mock a compute description factory
//		computeDescFactoryMock := th.StartFactoryMock(provisioning.ComputeDescriptions, func() interface{} { return &provisioning.ComputeDescription{} })
//
//		// mock a odata query, it will return empty set and trigger a VM's compute post
//		queryTask := &common.QueryTask{}
//		th.StartMockWithSelfLink(queryTask, common.ODataQuery)
//
//		if err := th.StartServiceSync(provisioning.EnumerateServiceEsx, NewEnumerationService()); err != nil {
//			t.Fatal(err)
//		}
//
//		taskState := provisioning.NewResourceEnumerationTaskState()
//		taskState.TaskInfo.Stage = common.TaskStageCreated
//		taskURI := th.StartMock(taskState)
//
//		req := provisioning.ComputeEnumerateRequest{
//			ResourcePoolLink:           state.ResourcePoolLink,
//			ComputeDescriptionLink:     state.DescriptionLink,
//			AdapterManagementReference: state.AdapterManagementReference,
//			EnumerationTaskReference:   taskURI,
//			ParentComputeLink:          state.SelfLink,
//		}
//
//		op := operation.NewPatch(ctx, uri.Extend(th.URI(), provisioning.EnumerateServiceEsx), nil)
//		if err := client.Send(op.SetBody(&req)).Wait(); err != nil {
//			t.Errorf("Error issuing PATCH: %s", err)
//		}
//
//		err = test.WaitForTaskCompletion(time.Now().Add(operation.DefaultTimeout), taskURI)
//		if err != nil {
//			t.Error(err)
//		}
//
//		// Check VM's compute is created properly
//		if len(computeFactoryMock.ServiceMap) == 0 {
//			t.Error("Expected at least one new compute posted for VM")
//		} else {
//			found := false
//			for _, val := range computeFactoryMock.ServiceMap {
//				newCompute := val.(*provisioning.ComputeState)
//				if newCompute.ResourcePoolLink == state.ResourcePoolLink &&
//					strings.HasPrefix(newCompute.DescriptionLink, provisioning.ComputeDescriptions) {
//					found = true
//				}
//				if len(newCompute.TenantLinks) == 0 {
//					t.Error("Tenant links are not populated properly!")
//				}
//
//				if computeType, ok := newCompute.CustomProperties[provisioning.FieldNameComputeType]; ok {
//					if computeType != provisioning.ComputeTypeVMGuest {
//						t.Error("Compute type are not populated properly!")
//					}
//				} else {
//					t.Error("Compute type are not populated properly!")
//				}
//			}
//			if !found {
//				t.Errorf("Did not get the expected new VM %+v", computeFactoryMock.ServiceMap)
//			}
//		}
//
//		// Check Host's compute description is updated properly
//		if len(computeDescFactoryMock.ServiceMap) == 0 {
//			t.Errorf("Host was not sync properly")
//		} else {
//			for _, val := range computeDescFactoryMock.ServiceMap {
//				compDesc := val.(*provisioning.ComputeDescription)
//				if !validateComputeDesc(compDesc) || len(compDesc.EnumerationAdapterReference) == 0 {
//					t.Errorf("Inventory update failed, compute desc was not populated properly %+v", compDesc)
//				}
//				if len(compDesc.TenantLinks) == 0 {
//					t.Error("Tenant links are not populated properly!")
//				}
//			}
//		}
//
//		deletedComputeState := &provisioning.ComputeState{}
//		err = provisioning.GetComputeState(ctx, existingVMComputeMockURI, deletedComputeState)
//
//		if len(deletedComputeState.SelfLink) > 0 {
//			t.Errorf("VM compute should be deleted since VM doesn't exist on host any more!")
//		}
//	})
//}
//
//// Just validate those specs are not zero
//func validateComputeDesc(desc *provisioning.ComputeDescription) bool {
//	if desc.CPUMhzPerCore == 0 ||
//		desc.CPUCount == 0 ||
//		desc.TotalMemoryBytes == 0 {
//		return false
//	}
//	return true
//}
//
//// Start a compute state mock and add it to compute mock factory
//func startOneCompouteMockService(th *test.ServiceHost, computeFactoryMock *test.MockFactoryService) uri.URI {
//	state := provisioning.ComputeState{}
//	state.SelfLink = provisioning.Compute + "/TO_BE_DELTED"
//	state.TenantLinks = []string{"/core/tenants/abc"}
//	state.CustomProperties = map[string]string{}
//	state.CustomProperties[FieldNameManagedObjectReference] = "NotExistVMMoref"
//	state.CustomProperties[provisioning.FieldNameComputeType] = provisioning.ComputeTypeVMGuest
//	computeFactoryMock.ServiceMap[state.SelfLink] = &state
//	return th.StartMockWithSelfLink(&state, state.SelfLink)
//}
