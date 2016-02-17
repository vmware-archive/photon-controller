package types

import (
	"encoding/json"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestTime(t *testing.T) {
	inTime := Now()

	b, err := json.Marshal(inTime)
	assert.NoError(t, err)

	var outTime Time
	err = json.Unmarshal(b, &outTime)
	assert.NoError(t, err)
	assert.Equal(t, inTime.Unix(), outTime.Unix())
}
