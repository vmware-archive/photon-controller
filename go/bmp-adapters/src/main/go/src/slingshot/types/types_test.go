package types

import (
	"encoding/json"
	"testing"

	"github.com/stretchr/testify/assert"
)

// An empty configuration should be valid.
func TestEmptyConfiguration(t *testing.T) {
	var x, y Configuration

	b, err := json.Marshal(x)
	if assert.NoError(t, err) {
		err = json.Unmarshal(b, &y)
		assert.NoError(t, err)
	}
}
