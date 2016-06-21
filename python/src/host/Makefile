DEPS=common
TESTS=host/tests/unit
EXTRAS=[test,esx]

CHECK_PRUNE='pyVmomiExtra'

ifdef INTEGRATION
ifdef REMOTE_SERVER
	TEST_OPTS += --tc=host_remote_test.server:$(REMOTE_SERVER)
endif
ifdef ESX_PWD
	TEST_OPTS += --tc=host_remote_test.esx_pwd:'$(ESX_PWD)'
endif
ifdef NOCAPTURE
	TEST_OPTS += --nocapture
endif
TESTS += host/tests/integration
endif

include ../python.mk
