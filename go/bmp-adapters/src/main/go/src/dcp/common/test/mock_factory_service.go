package test

import (
	"reflect"

	"dcp/common"
	"dcp/host"
	"dcp/operation"

	"github.com/pborman/uuid"
	"golang.org/x/net/context"
)

// MockFactoryService to mock a factory service for go test
// ServiceMap allows us to track what has been posted
type MockFactoryService struct {
	factoryURI   string
	th           *ServiceHost
	createStatef func() interface{}
	ServiceMap   map[string]interface{}
}

// Create a new mock factory service
// th            Service host
// factoryURI    Service factory URI
// createStatef  Function to create a service state for this mock factory
// Note: GO doesn't support generic, so we use a pass-in function to create state, new idea is welcome
func NewMockFactoryService(th *ServiceHost, factoryURI string,
	createStatef func() interface{}) (MockFactoryService, host.Service) {
	s := MockFactoryService{
		factoryURI:   factoryURI,
		th:           th,
		createStatef: createStatef,
		ServiceMap:   make(map[string]interface{}),
	}

	return s, host.NewServiceContext(&s)
}

func (s *MockFactoryService) GetState() interface{} {
	return nil
}

func (s *MockFactoryService) HandlePost(ctx context.Context, op *operation.Operation) {
	state := s.createStatef()
	op.DecodeBody(&state)

	val := reflect.ValueOf(state).Elem()
	f := val.FieldByName("SelfLink")
	var selfLink string
	if f.Kind() == reflect.String && f.Len() > 0 {
		selfLink = f.String()
	}

	if len(selfLink) == 0 {
		selfLink = s.factoryURI + "/" + uuid.New()
		if f.Len() == 0 {
			f.SetString(selfLink)
		}
	}
	_ = s.th.StartMockWithSelfLink(state, selfLink)
	s.ServiceMap[selfLink] = state
	op.SetBody(state).Complete()
}

// Return the QueryResult to simulate factory get behavior
func (s *MockFactoryService) HandleGet(ctx context.Context, op *operation.Operation) {
	type factoryQueryResult struct {
		common.ServiceDocumentQueryResult
		Documents map[string]interface{} `json:"documents,omitempty"`
	}
	result := factoryQueryResult{}
	result.Documents = s.ServiceMap
	op.SetBody(result).Complete()
}
