from host.hypervisor.esx.logging_wrappers import ConnWrapper

ConnWrapper.extra_headers = {"User-Agent": "vmware"}
