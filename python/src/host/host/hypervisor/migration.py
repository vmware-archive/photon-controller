# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy
# of the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, without
# warranties or conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the
# License for then specific language governing permissions and limitations
# under the License.

import abc as abc


class Migration(object):
    """A class that wraps migration(vmotion) specific implementation."""
    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def migrate_vm(self, request):
        """
        Start a VM migration between two ESX hosts.

        Manages the lifecycle of a migration. This RPC is called by an
        outside source, such as the scheduler.

        Args:
            request: The host.MigrateRequest() used to initiate a migration.

        Returns:
            A Host.MigrateResponse()
        """
        pass

    def fill_migrate_spec_on_dst(self, request):
        """
        For the destination to add its information to the migration spec.

        This is called by host_handler, which is called by the RPC
        coming from the migrate method in this file.

        Args:
            request: The host.FillMigrateSpecRequest() used to
            initiate a migration.

        Return:
            A Host.FillMigrateSpecResponse()
        """
        pass

    @abc.abstractmethod
    def initialize_migration_on_dst(self, request):
        """
        The destination starts a process and waits for migration to begin.

        Second step in the migration lifecycle called on the destination
        after the prepare call was successful to initialize the migration.
        (Might be a noop depending on the hypervisor.)

        Args:
            request: The host.InitializeMigrationRequest().

        Returns:
            A host.InitializeMigrationResponse()
        """
        pass

    @abc.abstractmethod
    def complete_migration_on_dst(self, request):
        """
        Clean up state on the destination after a migration.

        Args:
            request: The host.CompleteMigrationRequest().

        Returns:
            A host.CompleteMigrationResponse()
        """
        pass
