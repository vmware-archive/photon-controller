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
//	"code.google.com/p/go-uuid/uuid"
//
//	"golang.org/x/net/context"
//)
//
//func snapshotServiceWithESX(t *testing.T) {
//	ctx := context.Background()
//
//	createWithESX(t, func(th *test.ServiceHost, chsURI uri.URI) {
//		if err := th.StartServiceSync(provisioning.SnapshotServiceEsx, NewSnapshotService()); err != nil {
//			t.Fatal(err)
//		}
//		taskState := provisioning.NewComputeSubTaskState()
//		taskState.TaskInfo.Stage = common.TaskStageCreated
//		taskURI := th.StartMock(taskState)
//		snapshot := provisioning.SnapshotState{
//			ID:          uuid.New(),
//			ComputeLink: chsURI.Path,
//			Name:        "testSnapshot",
//			Description: "This is a test snapshot",
//		}
//		snapshotURI := th.StartMock(&snapshot)
//		req := provisioning.ComputeSnapshotRequest{
//			SnapshotReference:     snapshotURI,
//			SnapshotTaskReference: taskURI,
//		}
//
//		op := operation.NewPatch(ctx, uri.Extend(th.URI(), provisioning.SnapshotServiceEsx), nil)
//		if err := client.Send(op.SetBody(&req)).Wait(); err != nil {
//			t.Errorf("Error issuing PATCH: %s", err)
//		}
//
//		err := test.WaitForTaskCompletion(time.Now().Add(operation.DefaultTimeout), taskURI)
//		if err != nil {
//			t.Error(err)
//		}
//
//		snapshotState := &provisioning.SnapshotState{}
//		if err := provisioning.GetSnapshotState(ctx, req.SnapshotReference, snapshotState); err != nil {
//			t.Error(err)
//		}
//
//		if snapshotState.Name != snapshot.Name {
//			t.Errorf("Snapshot Name does not match =%s (%s)", snapshot.Name, snapshotState.Name)
//		}
//	})
//}
//
//func TestSnapshotServiceWithESX(t *testing.T) {
//	snapshotServiceWithESX(t)
//}
