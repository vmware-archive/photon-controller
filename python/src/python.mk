include ../../common.mk

.NOTPARALLEL:

PYTHON := python

# Use python from virtualenv
BIN := ../../$(DEVELOP)/bin
# Output directory for *.log, *.xml, etc.
BUILD := ../../$(DEVELOP)
MISC_BIN := ../../misc

# Can't use $(BIN)/python below because it might not be available
NAME := $(shell python setup.py --name)
VERSION := $(shell python setup.py --version)
PACKAGE := $(NAME)-$(VERSION)

PY_SRC := $(shell find . -type f -name \*.py)

ifdef CHECK_PRUNE
	CHECK_CMD := find . -name $(CHECK_PRUNE) -prune -a -type f -o -name '*.py' -exec $(BIN)/flake8 --max-line-length=120 {} +;
	CHECK_COPYRIGHT := find . -name $(CHECK_PRUNE) -prune -a -type f -o -name '*.py' | xargs -n1 -L1 $(MISC_BIN)/check_copyright
else
	CHECK_CMD := find . -name '*.py' -exec $(BIN)/flake8 --max-line-length=120 {} +;
	CHECK_COPYRIGHT := find . -name '*.py' | xargs -n1 -L1 $(MISC_BIN)/check_copyright
endif

CHECK_PYTHON3_COMPATIBILITY := find . -iname "*.py" | xargs pylint -r n  --py3k -d no-absolute-import -d old-division -d long-suffix -d long-builtin

ifdef PIP_INDEX_URL
	PIP_INSTALL := $(BIN)/pip install -i $(PIP_INDEX_URL) -q
else
	PIP_INSTALL := $(BIN)/pip install -q
endif

EXTRAS ?= [test]

ifdef XUNIT
	XUNIT_FILE := $(BUILD)/$(NAME)-nosetests.xml
	TEST_OPTS += --with-xunit --xunit-file $(XUNIT_FILE)
endif

ifdef VERBOSE_REPORT
	TEST_OPTS += -v --logging-format='%(asctime)s %(message)s'
else
	TEST_OPTS += --nologcapture
endif

add_dep_target = \
	$(if $(wildcard ../$(1)/DISABLE_FAST_MAKE), \
		$(call add_slow_dep_target,$(1)), \
		$(call add_fast_dep_target,$(1),$(2)))

get_package_name = $(shell python ../$(1)/setup.py --name)

define add_fast_dep_target
	$(eval dep-develop-$(1): ../../$(DEVELOP)/site-packages/$(2).egg-link);
	$(eval ../../$(DEVELOP)/site-packages/$(2).egg-link: ../$(1)/setup.py
		@$(MAKE) -C ../$(1) develop
	);
endef

define add_slow_dep_target
	$(eval dep-develop-$(1):
		@$(MAKE) -C ../$(1) develop
	);
endef

$(foreach dep,$(DEPS),$(call add_dep_target,$(dep),$(call get_package_name,$(dep))))

DIST := dist

all: test

$(BIN)/python:
	@echo "$(HEADER)Creating virtualenv$(NORMAL)"
	virtualenv --python=python$(PYTHON_VERSION) ../../$(DEVELOP)
	. ../../$(DEVELOP)/bin/activate;

../../$(DEVELOP)/site-packages: $(BIN)/python
	@echo "$(HEADER)Symlinking site packages$(NORMAL)"
	@SITE_PACKAGES=`$(BIN)/python ../../misc/get_site_packages_path`; \
	ln -sf $$SITE_PACKAGES ../../$(DEVELOP)/site-packages

clean:
	rm -rf dist $(XUNIT_FILE) $(XCOVER_FILE) $(NAME).egg-info ${EXTRA_CLEANUP_TARGETS} htmlcov
	find . -name '*.pyc' -exec rm -rf {} \;

$(DIST)/$(PACKAGE).tar.gz: $(PY_SRC) $(EXTRA_PY_TARGETS) $(BIN)/python
	$(BIN)/python setup.py -q sdist -d $(DIST)

dist: $(DIST)/$(PACKAGE).tar.gz

DEP_DEVELOP_TARGETS := $(patsubst %,dep-develop-%,$(DEPS))

develop: ../../$(DEVELOP)/site-packages $(DEP_DEVELOP_TARGETS) ../../$(DEVELOP)/site-packages/$(NAME).egg-link

../../$(DEVELOP)/site-packages/$(NAME).egg-link: setup.py $(EXTRA_PY_TARGETS)
	@echo "$(HEADER)Installing $(DEVELOP) $(NAME) $(NORMAL)"
	$(PIP_INSTALL) -e .$(EXTRAS)

.PHONY: develop dep-develop-%

$(BIN)/nosetests: $(BIN)/python
	$(PIP_INSTALL) nose

test: develop $(BIN)/nosetests
ifdef TESTS
	. ../../$(DEVELOP)/bin/activate; $(BIN)/nosetests -s $(TEST_OPTS) $(TESTS)
endif

coverage: develop $(BIN)/nosetests
ifdef TESTS
	. ../../$(DEVELOP)/bin/activate; coverage run $(BIN)/nosetests -s $(TEST_OPTS) $(TESTS); coverage html
endif

$(BIN)/flake8: $(BIN)/python
	$(PIP_INSTALL) flake8==2.4.1

check: develop $(BIN)/flake8
	$(CHECK_CMD)
	$(CHECK_COPYRIGHT)
	$(CHECK_PYTHON3_COMPATIBILTY)
