package types

import (
	"encoding/json"
	"time"
)

type Time struct {
	time.Time
}

func (t Time) MarshalJSON() ([]byte, error) {
	var epocMicroS int64
	epocMicroS = t.Unix() * int64(time.Microsecond)
	return json.Marshal(epocMicroS)
}

func (t *Time) UnmarshalJSON(b []byte) error {
	var epocMicroS int64

	err := json.Unmarshal(b, &epocMicroS)
	if err != nil {
		return err
	}
	t.Time = time.Unix(epocMicroS/int64(time.Microsecond), 0)
	return nil
}

func (t *Time) Add(d time.Duration) Time {
	return Time{t.Time.Add(d)}
}

func Now() Time {
	return Time{time.Now()}
}
