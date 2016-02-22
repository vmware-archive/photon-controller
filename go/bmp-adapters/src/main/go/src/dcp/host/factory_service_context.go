package host

import (
	"dcp/common"
	"dcp/errors"
	"dcp/operation"
	"dcp/uri"
	"io"
	"path"

	"golang.org/x/net/context"

	"github.com/pborman/uuid"
)

type ServiceDocumentGetter interface {
	GetServiceDocument() *common.ServiceDocument
}

type FactoryServiceContextHandler interface {
	CreateDocument() ServiceDocumentGetter
	CreateService() Service
}

type FactoryServiceContext struct {
	MinimalService

	h FactoryServiceContextHandler
}

func NewFactoryServiceContext(h FactoryServiceContextHandler) Service {
	f := FactoryServiceContext{
		h: h,
	}

	return &f
}

func (f *FactoryServiceContext) HandleStart(ctx context.Context, op *operation.Operation) {
	op.Complete()
}

func (f *FactoryServiceContext) HandleRequest(ctx context.Context, op *operation.Operation) {
	// TODO(PN): Support GET (need a way to get elements) and DELETE
	switch op.GetRequest().Method {
	case "POST":
	default:
		err := errors.MethodNotAllowed{Allowed: []string{"POST"}}
		op.Fail(err)
		return
	}

	f.handlePost(ctx, op)
}

// handlePost calls out to the factory service implementation's POST handler,
// if it exists, and waits for completion. If this runs and completes without
// error, the returned body is passed to the start operation for the service
// created by this factory.
func (f *FactoryServiceContext) handlePost(ctx context.Context, op *operation.Operation) {
	var err error
	var sd *common.ServiceDocument

	if h, ok := f.h.(PostHandler); ok {
		// Run the factory service's POST handler and wait for completion.
		err = op.CreateChild(ctx).Go(ctx, h.HandlePost).Wait()
		if err != nil {
			op.Fail(err)
			return
		}
	}

	doc := f.h.CreateDocument()
	err = op.DecodeBody(doc)
	if err != nil && err != io.EOF {
		op.Fail(err)
		return
	}

	sd = doc.GetServiceDocument()
	sd.SelfLink = path.Join(f.SelfLink(), uuid.New())
	op.SetBody(doc)

	buf, err := op.EncodeBodyAsBuffer()
	if err != nil {
		op.Fail(err)
		return
	}

	// Start child service at service document's selflink
	startOp := op.NewPost(ctx, uri.Extend(uri.Local(), sd.SelfLink), buf)
	f.Host().StartService(startOp, f.h.CreateService())
	err = startOp.Wait()
	if err != nil {
		op.Fail(err)
		return
	}

	op.Complete()
}
