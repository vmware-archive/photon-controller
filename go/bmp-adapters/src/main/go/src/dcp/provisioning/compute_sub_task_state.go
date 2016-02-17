package provisioning

import "dcp/common"

type ComputeSubTaskState struct {
	common.ServiceDocument

	TaskInfo                 *common.TaskState `json:"taskInfo,omitempty"`
	ParentTaskLink           string            `json:"parentTaskLink,omitempty"`
	ParentNextStageOnSuccess string            `json:"parentNextStageOnSuccess,omitempty"`
}

func NewComputeSubTaskState() *ComputeSubTaskState {
	return &ComputeSubTaskState{
		TaskInfo: &common.TaskState{},
	}
}

func (s *ComputeSubTaskState) SetStage(stage common.TaskStage) {
	s.TaskInfo.Stage = stage
}

func (s *ComputeSubTaskState) SetFailure(err error) {
	s.TaskInfo.Stage = common.TaskStageFailed
	s.TaskInfo.Failure = common.ToServerErrorResponse(err)
}
