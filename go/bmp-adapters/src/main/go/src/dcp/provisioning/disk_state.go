package provisioning

import (
	"dcp/client"
	"dcp/common"
	"dcp/operation"
	"dcp/uri"
	"errors"
	"fmt"
	"sort"

	"golang.org/x/net/context"
)

const (
	DiskTypeSSD = "SSD"
	DiskTypeHDD = "HDD"

	// Same values as BootDevice enum
	DiskTypeCdrom   = "CDROM"
	DiskTypeFloppy  = "FLOPPY"
	DiskTypeNetwork = "NETWORK"
)

const (
	DiskStatusDetached = "DETACHED"
	DiskStatusAttached = "ATTACHED"
)

var (
	ErrUnsupportedDiskType = errors.New("dcp: unsupported disk type")
)

type DiskState struct {
	common.ServiceDocument
	ID                   string `json:"id,omitempty"`
	ZoneID               string `json:"zoneId,omitempty"`
	DataCenterID         string `json:"dataCenterId,omitempty"`
	ResourcePoolLink     string `json:"resourcePoolLink,omitempty"`
	AuthCredentialsLink  string `json:"authCredentialsLink,omitempty"`
	SourceImageReference string `json:"sourceImageReference,omitempty"`
	DiskType             string `json:"type,omitempty"`
	Name                 string `json:"name,omitempty"`
	DiskStatus           string `json:"status,omitempty"`
	CapacityMBytes       int64  `json:"capacityMBytes,omitempty"`

	BootOrder     int         `json:"bootOrder,omitempty"`
	BootArguments []string    `json:"bootArguments,omitempty"`
	BootConfig    *BootConfig `json:"bootConfig,omitempty"`

	CustomizationServiceReference string `json:"customizationServiceReference,omitempty"`

	CostPerByte  float64 `json:"costPerByte,omitempty"`
	CurrencyUnit string  `json:"currencyUnit,omitempty"`

	CustomProperties map[string]string `json:"customProperties,omitempty"`
}

type BootConfig struct {
	Label string            `json:"label,omitempty"`
	Data  map[string]string `json:"data,omitempty"`
	Files []FileEntry       `json:"files,omitempty"`
}

type FileEntry struct {
	Path              string `json:"path,omitempty"`
	Contents          string `json:"contents,omitempty"`
	ContentsReference string `json:"contentsReference,omitempty"`
}

// PostDiskState posts the given disk state to the disk service.
func PostDiskState(ctx context.Context, disk *DiskState) (*DiskState, error) {
	u := uri.Extend(uri.Local(), Disks)

	op := operation.NewPost(ctx, u, nil)
	if err := client.Send(op.SetBody(disk)).Wait(); err != nil {
		return nil, fmt.Errorf("error POSTing %s: %s", u.String(), err)
	}

	out := &DiskState{}
	if err := op.DecodeBody(out); err != nil {
		return nil, err
	}

	return out, nil
}

type diskList []*DiskState

func (d diskList) Len() int {
	return len(d)
}

func (d diskList) Less(i, j int) bool {
	return d[i].BootOrder < d[j].BootOrder
}

func (d diskList) Swap(i, j int) {
	d[i], d[j] = d[j], d[i]
}

// GetDiskState gets the DiskState for each link in diskLinks
func GetDiskState(ctx context.Context, diskLinks []string) ([]*DiskState, error) {
	var ops []*operation.Operation

	for _, diskLink := range diskLinks {
		u := uri.Extend(uri.Local(), diskLink)
		op := operation.NewGet(ctx, u)
		ops = append(ops, op)
		go client.Send(op)
	}

	_, err := operation.Join(ops)
	if err != nil {
		return nil, err
	}

	var disks []*DiskState

	for _, op := range ops {
		disk := &DiskState{}
		if err := op.DecodeBody(disk); err != nil {
			return nil, err
		}

		disks = append(disks, disk)
	}

	sort.Sort(diskList(disks))

	return disks, nil
}

// PatchDiskState patches the DiskState for each disk in disks
func PatchDiskState(ctx context.Context, disks []*DiskState) error {
	var ops []*operation.Operation

	for _, disk := range disks {
		u := uri.Extend(uri.Local(), disk.SelfLink)
		op := operation.NewPatch(ctx, u, nil).SetBody(disk)
		ops = append(ops, op)
		go client.Send(op)
	}

	_, err := operation.Join(ops)
	return err
}
