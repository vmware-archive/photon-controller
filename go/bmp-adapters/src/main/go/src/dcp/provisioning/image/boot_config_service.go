package image

import (
	"bytes"
	"dcp/client"
	"dcp/common"
	"dcp/host"
	"dcp/operation"
	"dcp/provisioning"
	"errors"
	"fmt"
	"io"
	"path"

	"golang.org/x/net/context"
)

// BootConfigService is a disk customization service.
// It accepts a POST request with a DiskCustomizationRequest and streams back
// the resulting artifact.
type BootConfigService struct {
	ctx    context.Context
	cancel func()

	service host.Service
}

func NewBootConfigService() host.Service {
	ctx, cancel := context.WithCancel(context.Background())
	s := &BootConfigService{
		ctx:    ctx,
		cancel: cancel,
	}

	s.service = host.NewServiceContext(s)
	return s.service
}

func (s *BootConfigService) GetState() interface{} {
	return &common.ServiceDocument{}
}

func (s *BootConfigService) HandlePost(ctx context.Context, op *operation.Operation) {
	var dcr = new(provisioning.DiskCustomizationRequest)

	err := op.DecodeBody(dcr)
	if err != nil {
		op.Fail(err)
		return
	}

	_, disk, err := s.loadStates(ctx, op, dcr)
	if err != nil {
		op.Fail(err)
		return
	}

	if disk.BootConfig == nil {
		op.Fail(errors.New("expected bootConfig field to be set"))
		return
	}

	bootConfig, err := NewBootConfig(disk.BootConfig)
	if err != nil {
		op.Fail(err)
		return
	}

	// Always clean up config image upon returning
	defer bootConfig.Cleanup()

	err = bootConfig.Process()
	if err != nil {
		op.Fail(err)
		return
	}

	var method func() (io.Reader, error)

	switch path.Base(s.service.SelfLink()) {
	case provisioning.ImageFormatIso:
		method = bootConfig.ISO
	case provisioning.ImageFormatTar:
		method = bootConfig.Tar
	case provisioning.ImageFormatFat:
		fallthrough // TODO: bootConfig.FAT
	default:
		op.Fail(fmt.Errorf("unsupported image type"))
		return
	}

	// Get io.Reader for ISO/FAT boot config generator
	img, err := method()
	if err != nil {
		op.Fail(err)
		return
	}

	// Buffer config drive in memory.
	//
	// The ISO is small (~300KB). Since cleanup is run upon returning from this
	// function, the ISO generation command must have completed before then, or
	// we risk not including all files in the resulting image.
	//
	// The config drive code supports streaming the output, so this can be bolted
	// on later if necessary without modifying the config drive code.
	//
	buf := &bytes.Buffer{}
	_, err = io.Copy(buf, img)
	if err != nil {
		op.Fail(err)
		return
	}

	op.SetBody(buf).Complete()
}

func (s *BootConfigService) loadStates(ctx context.Context, op *operation.Operation, dcr *provisioning.DiskCustomizationRequest) (*provisioning.ComputeState, *provisioning.DiskState, error) {
	// Send GETs in parallel
	computeOp := client.Send(op.NewGet(ctx, dcr.ComputeReference))
	diskOp := client.Send(op.NewGet(ctx, dcr.DiskReference))

	// Both states are needed so we can wait for them sequentially
	if err := computeOp.Wait(); err != nil {
		return nil, nil, err
	}

	if err := diskOp.Wait(); err != nil {
		return nil, nil, err
	}

	var compute provisioning.ComputeState
	if err := computeOp.DecodeBody(&compute); err != nil {
		return nil, nil, err
	}

	var disk provisioning.DiskState
	if err := diskOp.DecodeBody(&disk); err != nil {
		return nil, nil, err
	}

	return &compute, &disk, nil
}
