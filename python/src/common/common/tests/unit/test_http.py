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
import mock
import unittest
import json

from common.http import ClientError
from common.http import DatastoreService
from common.http import HostService
from common.http import HttpClient
from common.http import ServiceAPI
from common.http import ServerError


class TestHttpClient(unittest.TestCase):

    def setUp(self):
        self.headers = {'Content-Type': 'application/json',
                        'User-Agent': 'agent-python-client'}

    @mock.patch("common.http.httplib.HTTPConnection")
    def test_http_operations(self, http_mock):
        conn = mock.MagicMock()
        http_mock.return_value = conn

        http_client = HttpClient("localhost", 12345)
        url = "method1"
        params = {"data": {"a": "val1"}}

        # Verify post
        http_client.post(url, params)
        http_mock.assert_called_with("localhost", 12345)
        body = json.dumps(params["data"])
        conn.request.assert_called_with("POST", url, body, self.headers)
        # Verify put
        http_client.put(url, params)
        conn.request.assert_called_with("PUT", url, body, self.headers)
        # Verify patch
        http_client.patch(url, params)
        conn.request.assert_called_with("PATCH", url, body, self.headers)


class TestServiceAPI(unittest.TestCase):

    def setUp(self):
        self.http_client = mock.MagicMock()
        resp = mock.MagicMock()
        resp.status = httplib.OK
        self.http_client.patch.return_value = resp
        self.http_client.put.return_value = resp
        self.http_client.post.return_value = resp

    def test_helpers(self):
        url = "service1"
        res = "resource"
        base = ServiceAPI(None, url)

        # verify response checker
        resp = mock.MagicMock()
        resp.status = 500
        resp.reason = "internal error"
        self.assertRaises(ServerError, base.check_resp, resp)

        # verify the url string builder
        res_url = "%s/%s" % (url, res)
        self.assertEqual(base.get_path(res), res_url)


class TestHostService(TestServiceAPI):

    def test_update(self):
        host_service = HostService(self.http_client)
        host_id = "host1"
        fields = {"dataStores": ["datastore1"]}
        host_service.update(host_id, fields)
        uri = "%s/%s" % (HostService.PATH, host_id)
        self.http_client.patch.assert_called_once_with(uri, {'data': fields})


class TestDatastoreService(TestServiceAPI):

    def test_create(self):
        datastore_service = DatastoreService(self.http_client)
        ds_id = "datastore1"
        uri = "%s/%s" % (DatastoreService.PATH, ds_id)
        fields = {"id": ds_id, "name": "datastore", "type": "NFS",
                  "documentSelfLink": uri}
        # Test create when the document doesn't exists
        datastore_service.create(ds_id, fields)
        self.http_client.post.assert_called_once_with(DatastoreService.PATH,
                                                      {'data': fields})
        self.http_client.reset_mock()

        # Test create when the document exists
        resp = mock.MagicMock()
        resp.status = httplib.CONFLICT
        resp.reason = "document already exists"
        self.http_client.post.return_value = resp
        self.assertRaises(ClientError, datastore_service.create, ds_id, fields)

    def test_update(self):
        datastore_service = DatastoreService(self.http_client)
        ds_id = "datastore1"
        uri = "%s/%s" % (DatastoreService.PATH, ds_id)
        fields = {"id": ds_id, "name": "datastore", "type": "NFS",
                  "documentSelfLink": uri}
        # Test create when the document doesn't exists
        datastore_service.update(ds_id, fields)
        selfLink = fields["documentSelfLink"]
        self.http_client.put.assert_called_once_with(selfLink,
                                                     {'data': fields})
