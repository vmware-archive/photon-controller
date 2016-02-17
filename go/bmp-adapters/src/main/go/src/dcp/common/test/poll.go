package test

import (
	"dcp/client"
	"dcp/common"
	"dcp/operation"
	"dcp/provisioning"
	"dcp/uri"
	"errors"
	"fmt"
	"time"

	"golang.org/x/net/context"
)

type pollFunc func() (bool, error)

func poll(ctx context.Context, fn pollFunc) error {
	ticker := time.NewTicker(time.Millisecond * 100)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
			ok, err := fn()
			if err != nil {
				return err
			}
			if ok {
				return nil
			}
		}
	}
}

func WaitForTaskCompletion(expirationTime time.Time, taskURI uri.URI) error {
	ctx, _ := context.WithTimeout(context.Background(), expirationTime.Sub(time.Now()))
	return poll(ctx, func() (bool, error) {
		op := operation.NewGet(ctx, taskURI)
		if err := client.Send(op).Wait(); err != nil {
			return false, fmt.Errorf("error issuing GET: %s", err)
		}

		var state provisioning.ComputeSubTaskState
		if err := op.DecodeBody(&state); err != nil {
			return false, fmt.Errorf("error decoding body: %s", err)
		}

		switch state.TaskInfo.Stage {
		case common.TaskStageFinished:
			return true, nil
		case common.TaskStageFailed:
			return false, errors.New(state.TaskInfo.Failure.Message)
		default:
			return false, nil
		}
	})
}

func WaitForNonEmptyBody(ctx context.Context, u uri.URI) error {
	return poll(ctx, func() (bool, error) {
		op := operation.NewGet(ctx, u)
		if err := client.Send(op).Wait(); err != nil {
			return false, fmt.Errorf("Error issuing GET: %s", err)
		}

		var m map[string]interface{}
		if err := op.DecodeBody(&m); err != nil {
			return false, fmt.Errorf("Error decoding body: %s", err)
		}

		return len(m) > 0, nil
	})
}
