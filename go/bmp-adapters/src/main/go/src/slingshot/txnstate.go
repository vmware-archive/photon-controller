package slingshot

import (
	"dcp/provisioning/image"
	"errors"
	"fmt"
	"net"
	"net/url"
	"path"
	"slingshot/bmp"
	"slingshot/dhcp"
	"slingshot/ipxe"
	"slingshot/pxe"
	"slingshot/types"
	"time"

	"golang.org/x/net/context"

	dhcpv4 "rfc-impl.vmware.com/rfc-impl/godhcpv4"

	"github.com/golang/glog"
)

type txnState struct {
	server *Server
	ip     net.IP

	subnet        *types.Subnet
	lease         *types.Lease
	configuration *types.Configuration

	pxeReq *pxe.Options

	ipxeReq *ipxe.Options
	ipxeRep *ipxe.Options

	bootFileName string
}

func newTxn(s *Server, req dhcpv4.Request) (*txnState, error) {
	i := req.InterfaceIndex()
	if i <= 0 {
		return nil, errors.New("no such interface")
	}

	m, err := s.managerFactory.LeaseManager(i)
	if err != nil {
		return nil, err
	}

	var mac *types.MAC
	mac, err = types.NewMACFromBytes(req.GetCHAddr())
	if err != nil {
		return nil, err
	}

	// Acquire lease for this request.
	n, l, c, err := m.Get(mac)
	if err != nil {
		return nil, err
	}
	t := txnState{
		server: s,
		ip:     m.MyIP(),

		subnet:        n,
		lease:         l,
		configuration: c,
	}

	return &t, nil
}

func (t *txnState) loadPXE(req dhcpv4.Request) {
	o, err := pxe.LoadOptions(req)
	if err != nil {
		// Ignore non-PXE clients
		return
	}

	if o.SystemArchitectureType != pxe.SystemArchitectureIntelX86PC {
		// Ignore non-x86 clients
		return
	}

	t.pxeReq = &o
}

func (t *txnState) loadIPXE(req dhcpv4.Request) {
	if t.pxeReq == nil {
		return
	}

	o, err := ipxe.LoadOptions(req)
	if err != nil {
		// Ignore non-iPXE clients
		return
	}

	t.ipxeReq = o
	t.ipxeRep = &ipxe.Options{}

	// Don't wait for proxied DHCP replies (specific to iPXE)
	t.ipxeRep.NoPXEDHCP = 1
}

func (t *txnState) assignLease(req dhcpv4.Request, rep dhcpv4.Reply) {
	rep.SetOption(dhcpv4.OptionSubnetMask, []byte(t.subnet.Subnet.Mask))

	// TODO(PN): Use real lease timeout
	rep.SetDuration(dhcpv4.OptionAddressTime, 20*time.Minute)

	// Assign IP
	rep.SetYIAddr(t.lease.IP.To4())
}

func (t *txnState) assignBootfile(req dhcpv4.Request, rep dhcpv4.Reply) {
	if t.pxeReq == nil {
		// No bootfile if this is not a PXE client
		return
	}

	// Determine what we should boot
	if t.bootFileName == "" {
		c := bmp.NewClient()
		cs, ds, err := c.LoadStatesFromConfiguration(t.configuration)
		if err != nil {
			glog.Infof("%s", err)
			return
		}

		ctx := context.TODO()
		img, err := image.Download(ctx, cs, ds)
		if err != nil {
			glog.Infof("%s", err)
			return
		}

		d, err := t.server.imageHandler.AddTar(t.ip, t.subnet, t.lease, t.configuration, img)
		if err != nil {
			glog.Infof("%s", err)
			return
		}

		u := url.URL{
			Scheme: "tftp",
			Host:   d.Host,
		}

		if t.ipxeReq != nil {
			u.Path = path.Join(d.Path, d.NextFile)
			t.bootFileName = u.String()
		} else {
			u.Path = path.Join(d.Path, "ipxe/undionly.kpxe")
			t.bootFileName = u.Path[1:]
		}
	}

	rep.SetString(dhcpv4.OptionServerName, t.ip.String())
	rep.SetString(dhcpv4.OptionBootfileName, t.bootFileName)
}

func IPListToOption(l []types.IP) []byte {
	var o []byte

	// Build concatenation of IPv4 addresses for DHCP option
	for _, r := range l {
		s := r.To4()
		if s == nil || s.IsUnspecified() {
			continue
		}

		o = append(o, s...)
	}

	return o
}

func (t *txnState) assignRouter(req dhcpv4.Request, rep dhcpv4.Reply) {
	o := IPListToOption(t.configuration.Routers)
	if len(o) > 0 {
		rep.SetOption(dhcpv4.OptionRouter, o)
	}
}

func (t *txnState) assignNameServer(req dhcpv4.Request, rep dhcpv4.Reply) {
	o := IPListToOption(t.configuration.NameServers)
	if len(o) > 0 {
		rep.SetOption(dhcpv4.OptionDomainServer, o)
	}
}

func (t *txnState) clientID(req dhcpv4.Request) string {
	// Append lease IP
	return fmt.Sprintf("%s-%s", dhcp.ClientID(req), t.lease.IP.String())
}

func (t *txnState) WriteReply(req dhcpv4.Request, rep dhcpv4.Reply) error {
	t.loadPXE(req)
	t.loadIPXE(req)
	t.assignRouter(req, rep)
	t.assignLease(req, rep)
	t.assignBootfile(req, rep)
	t.assignNameServer(req, rep)

	if t.ipxeRep != nil {
		ipxeOptionMap := make(dhcpv4.OptionMap)
		ipxeOptionMap.Encode(t.ipxeRep)

		// Assign serialized option map to iPXE vendor options
		rep.SetOption(ipxe.OptionIPXEEncapsulation, ipxeOptionMap.Serialize())
	}

	if rw, ok := req.(dhcpv4.ReplyWriter); ok {
		return rw.WriteReply(rep)
	}


	return nil
}
