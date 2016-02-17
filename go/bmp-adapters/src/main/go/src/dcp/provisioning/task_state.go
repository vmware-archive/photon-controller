package provisioning

import (
	"dcp/client"
	"dcp/common"
	"dcp/operation"
	"dcp/uri"
	"fmt"
	"path/filepath"
	"runtime"

	"golang.org/x/net/context"

	"github.com/golang/glog"
)

type TaskState interface {
	SetStage(stage common.TaskStage)
	SetFailure(err error)
}

type TaskStateWrapper struct {
	ctx       context.Context
	reference uri.URI
	backing   TaskState
}

func NewTaskStateWrapper(ctx context.Context, reference uri.URI, backing TaskState) *TaskStateWrapper {
	t := TaskStateWrapper{
		ctx:       ctx,
		reference: reference,
		backing:   backing,
	}

	return &t
}

// patch sends an HTTP PATCH with updated state to the state's URI.
// As this is a best effort, the PATCH is executed asynchronously and doesn't
// return a result.
func (t *TaskStateWrapper) patch() {
	op := operation.NewPatch(t.ctx, t.reference, nil).SetBody(t.backing)

	// Kick off request synchronously so that any future mutations to the backing
	// state are not reflected in the body of this request.
	client.Send(op)

	go func() {
		if err := op.Wait(); err != nil {
			glog.Errorf("Error patching %s: %s", t.reference.String(), err)
		}
	}()
}

func (t *TaskStateWrapper) PatchStage(stage common.TaskStage) {
	t.backing.SetStage(stage)
	t.patch()
}

func (t *TaskStateWrapper) PatchFailure(msg string, err error) {
	// Include location of caller in log message.
	if _, file, line, ok := runtime.Caller(1); ok {
		file = filepath.Base(file)
		glog.Errorf(fmt.Sprintf("[%s:%d] %s: %s", file, line, msg, err))
	}

	t.backing.SetFailure(fmt.Errorf("%s: %s", msg, err))
	t.patch()
}
