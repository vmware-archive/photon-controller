DEPS=common host stats
TESTS=agent/tests/unit
EXTRA_CLEANUP_TARGETS=$(BUILD)/$(NAME).log

ifdef INTEGRATION
ifdef REMOTE_SERVER
	TEST_OPTS += --tc=agent_remote_test.server:$(REMOTE_SERVER) -s \
                     --tc agent_remote_stress_test.host:$(REMOTE_SERVER)
endif
ifdef DATASTORES
	TEST_OPTS += --tc=agent_remote_test.datastores:$(DATASTORES)
endif
ifdef REMOTE_ISO
	TEST_OPTS += --tc=agent_remote_test.iso_file:"$(REMOTE_ISO)"
endif
ifdef AGENT_IN_UWSIM
	TEST_OPTS += --tc=agent_remote_test.agent_in_uwsim:1
endif
ifdef ZOOKEEPER
	TEST_OPTS += --tc=agent_remote_test.zookeeper:$(ZOOKEEPER)
endif
ifdef SANITY
	TESTS += agent/tests/integration/test_remote_agent.py:TestRemoteAgent.test_power_ops
else
	TESTS += agent/tests/integration
endif
endif

include ../python.mk
