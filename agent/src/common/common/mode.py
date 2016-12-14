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
import threading
import enum
from enum import Enum
from common.lock import locked


@enum.unique
class MODE(Enum):
    NORMAL = 1
    ENTERING_MAINTENANCE = 2
    MAINTENANCE = 3
    DEPROVISIONED = 4


class ModeTransitionError(Exception):

    def __init__(self, msg, from_mode):
        Exception.__init__(self, msg)
        self.from_mode = from_mode


class Mode(object):
    """Mode is responsible to maintain the host maintenance mode status
    defined in MODE. The mode is persistent in state file, and this class
    also manages the atomic mode transition, and supports callback for
    entering and exiting certain mode.
    """

    MODE_KEY = "mode"

    def __init__(self, state):
        self._state = state
        self.lock = threading.RLock()
        self._enter_callbacks = []
        self._exit_callbacks = []
        self._change_callbacks = []

    @locked
    def get_mode(self):
        """Get host maintenance mode

        :rtype [MODE] maintenance mode
        """
        mode = self._state.get(self.MODE_KEY)
        if mode:
            return MODE(mode)
        else:
            return MODE.NORMAL

    @locked
    def set_mode(self, mode, from_modes=None):
        """Set host maintenance mode

        :param mode [MODE]: the maintenance mode
        :param from_modes [iterable]: List of modes that allowed to be
                                      changed from
        """
        assert isinstance(mode, MODE)
        current_mode = self.get_mode()
        if current_mode == mode:
            return
        if from_modes and current_mode not in from_modes:
            raise ModeTransitionError(
                "mode %s->%s not allowed" % (self.get_mode(), mode),
                current_mode)
        self._state.set(self.MODE_KEY, mode.value)

        # Trigger callbacks
        exit_callbacks = [c for m, c in self._exit_callbacks
                          if m == current_mode]
        enter_callbacks = [c for m, c in self._enter_callbacks
                           if m == mode]
        callbacks = exit_callbacks + enter_callbacks + self._change_callbacks
        for callback in callbacks:
            callback()

    def on_change(self, callback):
        """Register callback which will be triggered after mode changes
        :param callback: [callable] with no parameter
        """
        self._change_callbacks.append(callback)

    def on_enter_mode(self, mode, callback):
        """Register callback which will be triggered after entering certain
        mode
        :param mode: [MODE] the mode that is about to enter
        :param callback: [callable] with no parameter
        """
        self._enter_callbacks.append((mode, callback))

    def on_exit_mode(self, mode, callback):
        """Register callback which will be triggered after exiting certain
        mode
        :param mode: [MODE] the mode that is about to exit
        :param callback: [callable] with no parameter
        """
        self._exit_callbacks.append((mode, callback))
