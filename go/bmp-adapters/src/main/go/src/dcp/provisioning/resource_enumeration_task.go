package provisioning

import (
	"dcp/common"
)

type ResourceEnumerationTaskState struct {
	common.ServiceDocument

	TaskInfo *common.TaskState `json:"taskInfo,omitempty"`
}

func NewResourceEnumerationTaskState() *ResourceEnumerationTaskState {
	return &ResourceEnumerationTaskState{
		TaskInfo: common.NewTaskState(),
	}
}

func (t *ResourceEnumerationTaskState) SetStage(stage common.TaskStage) {
	t.TaskInfo.SetStage(stage)
}

func (t *ResourceEnumerationTaskState) SetFailure(err error) {
	t.TaskInfo.SetFailure(err)
}
