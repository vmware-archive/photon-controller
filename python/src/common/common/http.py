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

import httplib
import json


class ServerError(Exception):
    def __init__(self, status, reason):
        self.status = status
        self.reason = reason
        msg = "Server Error : %s , % s" % (status, reason)
        super(self.__class__, self).__init__(msg)


class ClientError(Exception):
    def __init__(self, status, reason):
        self.status = status
        self.reason = reason
        msg = "Client Error : %s , % s" % (status, reason)
        super(self.__class__, self).__init__(msg)


class ServiceAPI(object):

    def __init__(self, http_client, path):
        self.http_client = http_client
        self.service_path = path

    def check_resp(self, resp):
        code = resp.status
        if code != httplib.OK:
            if code < 500:
                raise ClientError(resp.status, resp.reason)
            else:
                raise ServerError(resp.status, resp.reason)

    def get_path(self, suffix):
        return "%s/%s" % (self.service_path, suffix)


class HostService(ServiceAPI):

    PATH = "/photon/cloudstore/hosts"

    def __init__(self, http_client):
        super(self.__class__, self).__init__(http_client,
                                             self.PATH)

    def update(self, host_id, fields):
        """
        host_id: str
                Host id
        fields: dict
               A dict that can have the following (key, value) updated:
               "networks": set of str
               "dataStores": set of str
               "datastoreServiceLinks": map of str
        """
        path = self.get_path(host_id)
        req = {"data": fields}
        resp = self.http_client.patch(path, req)
        self.check_resp(resp)


class DatastoreService(ServiceAPI):

    PATH = "/photon/cloudstore/datastores"

    def __init__(self, http_client):
        super(self.__class__, self).__init__(http_client,
                                             self.PATH)

    def create(self, ds_id, fields):
        """
        ds_id: str
              Datastore id
        fields: dict
               A dict representation of a datastore object, it can
               be created with the following (key, value):
               "id":  str
               "name": str
               "type": str
               "tags": set of str
               "documentSelfLink": str
        """
        documentSelfLink = self.get_path(ds_id)
        fields["documentSelfLink"] = documentSelfLink
        req = {"data": fields}
        resp = self.http_client.post(self.service_path, req)
        self.check_resp(resp)

    def update(self, ds_id, fields):
        """
        ds_id: str
              Datastore id
        fields: dict
               A dict that can have the following (key, value) updated:
               "tags": set of str
        """
        documentSelfLink = self.get_path(ds_id)
        fields["documentSelfLink"] = documentSelfLink
        req = {"data": fields}
        resp = self.http_client.put(documentSelfLink, req)
        self.check_resp(resp)


class HttpClient(object):
    """
    An http client that is able to execute RESTful requests.
    """
    UserAgent = "agent-python-client"
    DEFAULT_CONTENT_TYPE = "application/json"
    POST_OP = "POST"
    PUT_OP = "PUT"
    PATCH_OP = "PATCH"

    def __init__(self, host, port):
        self.host = host
        self.port = port
        self.conn = httplib.HTTPConnection(self.host, self.port)

    def _make_call(self, method, url, req_props):
        headers = {}
        headers['Content-Type'] = self.DEFAULT_CONTENT_TYPE
        headers['User-Agent'] = self.UserAgent
        body = None
        if req_props.get("data", None):
            body = json.dumps(req_props["data"])

        self.conn.request(method, url, body, headers)
        resp = self.conn.getresponse()
        # For now we don't care about the returned body, but we need
        # to read it anyways, because we can't use self.conn.request
        # again if there is an unread message
        resp.read()
        return resp

    def post(self, url, req_prop=None):
        return self._make_call(self.POST_OP, url, req_prop)

    def put(self, url, req_prop=None):
        return self._make_call(self.PUT_OP, url, req_prop)

    def patch(self, url, req_prop=None):
        return self._make_call(self.PATCH_OP, url, req_prop)


class CloudStoreClient(object):
    """
    Cloud store client.
    """

    def __init__(self, host, port):
        self._http = HttpClient(host, port)
        self._host_service = HostService(self._http)
        self._datastore_service = DatastoreService(self._http)

    @property
    def host_service(self):
        return self._host_service

    @property
    def datastore_service(self):
        return self._datastore_service
