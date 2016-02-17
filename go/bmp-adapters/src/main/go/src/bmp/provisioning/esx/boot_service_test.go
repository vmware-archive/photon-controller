package esx

//import (
//	"dcp/client"
//	"dcp/common"
//	"dcp/common/test"
//	"dcp/operation"
//	"dcp/provisioning"
//	"dcp/uri"
//	"testing"
//	"time"
//
//	"golang.org/x/net/context"
//
//	"github.com/golang/glog"
//)
//
//var (
//	testBootOrder []string
//)
//
//func bootWithESX(t *testing.T) {
//	ctx := context.Background()
//	mockIP = "127.0.0.1"
//
//	createWithESX(t, func(th *test.ServiceHost, chsURI uri.URI) {
//		if err := th.StartServiceSync(provisioning.BootServiceEsx, NewBootService()); err != nil {
//			t.Fatal(err)
//		}
//
//		taskState := provisioning.NewComputeSubTaskState()
//		taskState.TaskInfo.Stage = common.TaskStageCreated
//		taskURI := th.StartMock(taskState)
//
//		req := provisioning.ComputeBootRequest{
//			ComputeReference:          chsURI,
//			ProvisioningTaskReference: taskURI,
//			BootDeviceOrder:           testBootOrder,
//		}
//
//		op := operation.NewPatch(ctx, uri.Extend(th.URI(), provisioning.BootServiceEsx), nil)
//		if err := client.Send(op.SetBody(&req)).Wait(); err != nil {
//			t.Errorf("Error issuing PATCH: %s", err)
//		}
//
//		err := test.WaitForTaskCompletion(time.Now().Add(operation.DefaultTimeout), taskURI)
//		if err != nil {
//			t.Error(err)
//		}
//
//		state := &provisioning.ComputeStateWithDescription{}
//		if err := provisioning.GetComputeStateWithDescription(ctx, chsURI, state); err != nil {
//			t.Error(err)
//		}
//
//		if state.Address != mockIP {
//			t.Errorf("ComputeState was not PATCHed with Address=%s (%s)", mockIP, state.Address)
//		}
//
//		for i, disk := range testDisks {
//			if disk.DiskType == provisioning.DiskTypeNetwork {
//				continue
//			}
//			if disk.DiskStatus != provisioning.DiskStatusAttached {
//				t.Errorf("DiskStatus %d was not PATCHed with DiskStatusAttached", i)
//			}
//		}
//	})
//}
//
//func TestBootServiceWithESX(t *testing.T) {
//	testBootOrder = []string{
//		provisioning.BootDeviceNetwork,
//		provisioning.BootDeviceDisk,
//	}
//
//	testDisks = []*provisioning.DiskState{
//		{
//			DiskType:             provisioning.DiskTypeNetwork,
//			SourceImageReference: "http://example.com/ttylinux-pc_i486-16.1.iso",
//		},
//		{
//			DiskType:       provisioning.DiskTypeHDD,
//			CapacityMBytes: 10,
//		},
//	}
//
//	bootWithESX(t)
//}
//
//func TestBootServiceCdromWithESX(t *testing.T) {
//	testBootOrder = []string{
//		provisioning.BootDeviceCdrom,
//	}
//
//	testDisks = []*provisioning.DiskState{
//		{
//			DiskType:             provisioning.DiskTypeCdrom,
//			SourceImageReference: "file:///ttylinux-pc_i486-16.1.iso",
//		},
//	}
//
//	bootWithESX(t)
//}
//
//func TestBootServiceFloppyWithESX(t *testing.T) {
//	testBootOrder = []string{
//		provisioning.BootDeviceFloppy,
//	}
//
//	testDisks = []*provisioning.DiskState{
//		{
//			DiskType:             provisioning.DiskTypeFloppy,
//			SourceImageReference: "file:///floppybird.img",
//		},
//	}
//
//	bootWithESX(t)
//}
//
//func TestBootWithMock(t *testing.T) {
//	th := test.NewServiceHost(t)
//	defer th.Stop()
//
//	if err := th.StartServiceSync(provisioning.BootServiceEsx, NewBootService()); err != nil {
//		glog.Fatalf("Error starting service: %s\n", err)
//	}
//
//	mockReq := provisioning.ComputeBootRequest{
//		ComputeReference: uri.Extend(uri.Empty(), provisioning.Resources+"/compute-hosts/bogus"),
//		BootDeviceOrder: []string{
//			provisioning.BootDeviceNetwork,
//			provisioning.BootDeviceCdrom,
//			provisioning.BootDeviceDisk,
//		},
//		IsMockRequest: true,
//	}
//
//	ctx := context.Background()
//	op := operation.NewPatch(ctx, uri.Extend(th.URI(), provisioning.BootServiceEsx), nil)
//	op.SetExpiration(time.Now().Add(5 * time.Second))
//	op.SetBody(&mockReq)
//	if err := client.Send(op).Wait(); err != nil {
//		t.Error(err)
//	}
//}
