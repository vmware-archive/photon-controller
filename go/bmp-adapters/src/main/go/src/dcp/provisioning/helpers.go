package provisioning

import (
	"strings"

	"dcp"
	"dcp/client"
	"dcp/common"
	"dcp/operation"
	"dcp/uri"
	"errors"
	"fmt"

	"github.com/golang/glog"
	"golang.org/x/net/context"
)

// GetComputeState retrieves the compute host state.
func GetComputeState(ctx context.Context, u uri.URI, s *ComputeState) error {
	op := operation.NewGet(ctx, u)
	err := client.Send(op).Wait()
	if err != nil {
		return err
	}

	if err := op.DecodeBody(s); err != nil {
		return err
	}

	return nil
}

func GetSnapshotState(ctx context.Context, u uri.URI, s *SnapshotState) error {
	op := operation.NewGet(ctx, u)
	err := client.Send(op).Wait()
	if err != nil {
		return err
	}

	if err := op.DecodeBody(s); err != nil {
		return err
	}

	return nil
}

// GetComputeDescription retrieves the compute host description.
func GetComputeDescription(ctx context.Context, u uri.URI, s *ComputeDescription) error {
	op := operation.NewGet(ctx, u)
	err := client.Send(op).Wait()
	if err != nil {
		return err
	}

	if err := op.DecodeBody(s); err != nil {
		return err
	}

	return nil
}

// GetComputeStateWithDescription retrieves the compute host state.
func GetComputeStateWithDescription(ctx context.Context, u uri.URI, s *ComputeStateWithDescription) error {
	u = uri.ExtendQuery(u, QueryExpand, QueryComputeDescriptionLink)
	op := operation.NewGet(ctx, u)
	err := client.Send(op).Wait()
	if err != nil {
		return err
	}

	if err := op.DecodeBody(s); err != nil {
		return err
	}

	return nil
}

func GetInheritedParentComputeState(ctx context.Context, u uri.URI, s *ComputeStateWithDescription) error {
	if s.ParentLink == "" {
		return errors.New("parentLink is not set")
	}

	parent := &ComputeStateWithDescription{}
	err := GetComputeStateWithDescription(ctx, uri.Extend(uri.Local(), s.ParentLink), parent)
	if err != nil {
		return err
	}

	if s.AdapterManagementReference == "" {
		s.AdapterManagementReference = parent.AdapterManagementReference
	}

	if s.Description.ZoneID == "" {
		s.Description.ZoneID = parent.Description.ZoneID
	}

	return nil
}

// GetChildComputeStateWithDescription retrieves the compute host state for the given uri,
// and sets the AdapterManagementReference and ZoneID to that of the parent's.
func GetChildComputeStateWithDescription(ctx context.Context, u uri.URI, s *ComputeStateWithDescription) error {
	err := GetComputeStateWithDescription(ctx, u, s)
	if err != nil {
		return err
	}

	return GetInheritedParentComputeState(ctx, u, s)
}

// PatchComputeState to given DCP uri
func PatchComputeState(ctx context.Context, u uri.URI, s *ComputeState) error {
	op := operation.NewPatch(ctx, u, nil).SetBody(s)
	err := client.Send(op).Wait()
	if err != nil {
		return err
	}

	return nil
}

// PostComputeState POSTS the given ComputeState to the ComputeState factory
func PostComputeState(ctx context.Context, states []*ComputeState) error {
	u := uri.Extend(uri.Local(), Compute)

	var ops []*operation.Operation

	for _, s := range states {
		if glog.V(dcp.Debug) {
			glog.Infof("Posting compute %s", s.ID)
		}
		op := operation.NewPost(ctx, u, nil).SetBody(s)
		ops = append(ops, op)
		go client.Send(op)
	}

	_, err := operation.Join(ops)
	return err
}

// DeleteComputeState deletes the ComputeState and its DiskServices
func DeleteComputeState(ctx context.Context, s *ComputeState) error {
	var ops []*operation.Operation

	links := append(s.DiskLinks, s.SelfLink)

	for _, link := range links {
		u := uri.Extend(uri.Local(), link)
		body := &common.ServiceDocument{SelfLink: link}
		op := operation.NewDelete(ctx, u).SetBody(body)
		ops = append(ops, op)
		go client.Send(op)
	}

	_, err := operation.Join(ops)
	return err
}

func HandleMockRequest(ctx context.Context, req *ComputeInstanceRequest, ts *TaskStateWrapper) {
	// clean up the compute state in photon
	if req.RequestType == InstanceRequestTypeDelete {
		state := &ComputeStateWithDescription{}
		err := GetComputeStateWithDescription(ctx, req.ComputeReference, state)
		if err != nil {
			ts.PatchFailure("Error obtaining ComputeState", err)
			return
		}
		DeleteComputeState(ctx, &state.ComputeState)
	}
	// Simply PATCH task to completion and short-circuit the workflow.
	ts.PatchStage(common.TaskStageFinished)
	return
}

// Retrieve compute with description by host selflink and VM ID
func GetComputeStateByID(ctx context.Context, parentLink string, vmIDFieldName string, vmID string,
	s *ComputeStateWithDescription) error {
	computeLinks := getComputeLinksByParentID(ctx, parentLink, vmIDFieldName, vmID)
	if len(computeLinks) == 0 {
		return fmt.Errorf("Can't find compute by parentLink %v, vmID %v", parentLink, vmID)
	}

	u := uri.Extend(uri.Local(), computeLinks[0])
	return GetComputeStateWithDescription(ctx, u, s)
}

// Retrieve compute links for a given parent link
func getComputeLinksByParentID(ctx context.Context, parentLink string, vmIDFieldName string, vmID string) []string {
	var queryString string
	if len(vmID) > 0 {
		queryString = "(%s eq '%s' and %s eq '%s')"
		queryString = fmt.Sprintf(queryString, FieldNameParentLink, parentLink, vmIDFieldName, vmID)
	} else {
		queryString = "(%s eq '%s')"
		queryString = fmt.Sprintf(queryString, FieldNameParentLink, parentLink)
	}

	results := ExecuteODataQuery(ctx, queryString)
	return results.DocumentLinks
}

// Execute a odata query and return results
func ExecuteODataQuery(ctx context.Context, queryString string) *common.ServiceDocumentQueryResult {
	u := uri.Extend(uri.Local(), common.ODataQuery)
	u, _ = extenURIWithQueryString(u, queryString)
	op := operation.NewGet(ctx, u)
	if err := client.Send(op).Wait(); err != nil {
		glog.Infof("Failed executing query %s, detail error: %s", u.String(), err)
		return nil
	}

	task := &common.QueryTask{}
	if err := op.DecodeBody(task); err != nil {
		glog.Infof("Failed decode query result, detail error: %s", err)
		return nil
	}

	return &task.Results
}

// Get all compute states for a given parent link
func GetComputeStatesByParentLink(ctx context.Context, parentLink string) (map[string]ComputeState, error) {
	u := uri.Extend(uri.Local(), Compute)
	u = uri.ExtendQuery(u, QueryExpand, QueryDocumentSelfLinkLink)

	queryString := fmt.Sprintf("(%s eq '%s')", FieldNameParentLink, parentLink)
	u, _ = extenURIWithQueryString(u, queryString)

	op := operation.NewGet(ctx, u)
	if err := client.Send(op).Wait(); err != nil {
		return nil, err
	}

	type computeStateQueryResult struct {
		common.ServiceDocumentQueryResult
		Documents map[string]ComputeState `json:"documents,omitempty"`
	}

	var s computeStateQueryResult
	if err := op.DecodeBody(&s); err != nil {
		return nil, err
	}

	return s.Documents, nil
}

// Extend URI with query string
// We have to do this is since in NettyClientRequestHanlder, we call targetUri.getQuery(), which
// is using URL path decoder to decode query, so it won't decode + to space
// More on this topic http://stackoverflow.com/questions/1634271/url-encoding-the-space-character-or-20
func extenURIWithQueryString(u uri.URI, queryString string) (uri.URI, error) {
	u = uri.ExtendQuery(u, "$filter", queryString)
	uStr := strings.Replace(u.String(), "+", "%20", -1)
	return uri.Parse(uStr)
}

// Post a new compute description
func PostComputeDescription(ctx context.Context, computeDesc *ComputeDescription) error {
	computeDescFactoryURI := uri.Extend(uri.Local(), ComputeDescriptions)
	op := operation.NewPost(ctx, computeDescFactoryURI, nil).SetBody(&computeDesc)
	err := client.Send(op).Wait()
	if err != nil && !strings.Contains(err.Error(), "Service already started") {
		glog.Errorf("Error posting compute description, detail error: %v", err)
		return err
	}

	if err = op.DecodeBody(&computeDesc); err != nil {
		glog.Errorf("Error retrieving compute description after post, detail error: %v", err)
		return err
	}
	return nil
}

// PostOneComputeState POSTS the given ComputeState to the ComputeState factory
// and return the posted ComputeState
// TODO, should do a refactor to rename other post to PostComputeStates, will get
func PostOneComputeState(ctx context.Context, state *ComputeState) error {
	u := uri.Extend(uri.Local(), Compute)

	if glog.V(dcp.Debug) {
		glog.Infof("Posting compute %v", state)
	}
	op := operation.NewPost(ctx, u, nil).SetBody(state)

	err := client.Send(op).Wait()
	if err != nil && !strings.Contains(err.Error(), "Service already started") {
		glog.Errorf("Error posting compute, detail error: %v", err)
		return err
	}

	if err = op.DecodeBody(&state); err != nil {
		glog.Errorf("Error retrieving compute after post, detail error: %v", err)
		return err
	}
	return nil
}
