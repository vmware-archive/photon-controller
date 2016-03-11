package image

import (
	"dcp/client"
	"dcp/filecache"
	"dcp/operation"
	"dcp/provisioning"
	"dcp/uri"
	"io"
	"os"
	"path"

	"golang.org/x/net/context"

	"github.com/golang/glog"
)

func Path(compute *provisioning.ComputeState, u uri.URI) string {
	return filecache.Path(compute.ID + "." + path.Base(u.Path))
}

func Download(ctx context.Context, compute *provisioning.ComputeState, disk *provisioning.DiskState) (string, error) {
	if disk.CustomizationServiceReference == "" {
		return filecache.Download(disk.SourceImageReference)
	}

	glog.Infof("Generating image with: %s", disk.CustomizationServiceReference)

	customizationServiceURI, err := uri.Parse(disk.CustomizationServiceReference)
	if err != nil {
		return "", err
	}

	dcr := provisioning.DiskCustomizationRequest{
		ComputeReference: uri.Extend(uri.Local(), compute.SelfLink),
		DiskReference:    uri.Extend(uri.Local(), disk.SelfLink),
	}

	filePath := Path(compute, customizationServiceURI)
	f, err := os.Create(filePath)
	if err != nil {
		return "", err
	}

	defer f.Close()

	op := operation.NewPost(ctx, customizationServiceURI, nil).SetBody(dcr)
	op.SetIsStreamResponse(true)
	err = client.Send(op).Wait()
	if err != nil {
		return "", err
	}

	res := op.GetResponse()
	defer res.Body.Close()

	_, err = io.Copy(f, res.Body)
	if err != nil {
		return "", err
	}

	return filePath, nil
}
