package data

type V1 struct {
	Host        string
	Path        string
	MAC         string
	IP          string
	Netmask     string
	Routers     []string
	NameServers []string
	NextFile    string
	ComputeStateReference   string
	DiskStateReference string
}
