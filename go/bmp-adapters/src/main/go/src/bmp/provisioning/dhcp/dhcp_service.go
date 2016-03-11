package dhcp

import (
	"dcp/common"
	"dcp/host"
	"dcp/operation"
	"slingshot"

	"golang.org/x/net/context"
	"github.com/golang/glog"
)

type Service struct {
	host.MinimalService
	Server *slingshot.Server
}

type ServiceState struct {
	common.ServiceDocument
	// ID is the ServiceHostState ID.  We use this to identify if this node is
	// the owner of PARTITIONED documents.
	ID string
}

func NewService() host.Service {
	return &Service{}
}

func (s *Service) HandleStart(ctx context.Context, op *operation.Operation) {
	glog.Infof("HandleStart dhcp_service")
	state := &ServiceState{}
	op.DecodeBody(state)

	var err error
	s.Server, err = slingshot.NewServer(state.ID)
	if err != nil {
		op.Fail(err)
		return
	}

	if err := s.Server.Run(); err != nil {
		op.Fail(err)
		return
	}
	op.Complete()
}

// Stop the SlingshotService
func (s *Service) HandleDelete(ctx context.Context, op *operation.Operation) error {
	s.Server.Stop()
	op.Complete()
	return nil
}
