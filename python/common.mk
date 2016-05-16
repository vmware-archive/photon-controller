DEVELOP?=develop
BOLD=$(shell tput bold)
HEADER=$(shell tput setaf 5)$(BOLD)
WARNING=$(shell tput setaf 1)$(BOLD)
NORMAL=$(shell tput sgr0)
ESX_VERSION?=6.5.0

# If build target contains dist, use python 2.7. This is only because on the build
# machine, we only have python 2.7 installed.
ifeq (dist,$(findstring dist,$(MAKECMDGOALS)))
	PYTHON_VERSION?=2.7
else
	PYTHON_VERSION?=2.6
endif
