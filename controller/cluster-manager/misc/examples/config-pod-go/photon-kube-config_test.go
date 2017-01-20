/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package main

import (
	"testing"
	"strconv"
	"fmt"
)

func TestGetKeyAndUpdateConfig(t *testing.T) {
	config := "a = test\n"
	value := "!@#$%^&*()_+=-`~"
	data := map[string][]byte{ "p": []byte(value) }
	expected := fmt.Sprintf("%sp = %s\n", config, value)

	result := getKeyAndUpdateConfig(data, "p", config)
	if result != expected {
		t.Fatalf("Expected: %s, got: %s", strconv.QuoteToASCII(expected), strconv.QuoteToASCII(result))
	}

	value = "$$$$$$$$$"
	data = map[string][]byte{ "p": []byte(value) }
	expected = fmt.Sprintf("%sp = %s\n", config, value)

	result = getKeyAndUpdateConfig(data, "p", config)
	if result != expected {
		t.Fatalf("Expected: %s, got: %s", strconv.QuoteToASCII(expected), strconv.QuoteToASCII(result))
	}

	delete(data, "p")
	expected = fmt.Sprintf("%s", config)

	result = getKeyAndUpdateConfig(data, "p", config)
	if result != expected {
		t.Fatalf("Expected: %s, got: %s", strconv.QuoteToASCII(expected), strconv.QuoteToASCII(result))
	}

}
