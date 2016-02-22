package client

import (
	"crypto/tls"
	"dcp/operation"
	"fmt"
	"io/ioutil"
	"net"
	"net/http"
	"net/url"
	"time"
)

type Client interface {
	Send(o *operation.Operation) *operation.Operation
}

var DefaultTransport = &http.Transport{
	Proxy: http.ProxyFromEnvironment,
	Dial: NewDialer(&net.Dialer{
		Timeout: 10 * time.Second,
	}, MaximumConnections).Dial,
	DisableKeepAlives:   true,
	MaxIdleConnsPerHost: MaximumConnections,
	TLSClientConfig: &tls.Config{
		InsecureSkipVerify: true, // TODO: config option
	},
}

var defaultClient = &http.Client{Transport: DefaultTransport}

// responseToError checks the status code of a response for errors.
func responseToError(req *http.Request, res *http.Response) error {
	if res.StatusCode >= http.StatusOK && res.StatusCode < http.StatusBadRequest {
		return nil
	}

	b, _ := ioutil.ReadAll(res.Body)
	res.Body.Close()

	return fmt.Errorf("dcp client %s %s: %d (%s) %s",
		req.Method, req.URL, res.StatusCode, res.Status, string(b))
}

func doSend(req *http.Request) (*http.Response, error) {
	res, err := defaultClient.Do(req)
	if err != nil {
		return nil, err
	}

	// Figure out if the response should be interpreted as error.
	err = responseToError(req, res)
	if err != nil {
		return nil, err
	}

	return res, nil
}

func isNetError(err error) bool {
	if uerr, ok := err.(*url.Error); ok {
		if _, ok := uerr.Err.(*net.OpError); ok {
			return true
		}
	}

	return false
}

// Send executes the specified operation, while observing context cancellation.
func Send(o *operation.Operation) *operation.Operation {
	req, err := o.CreateRequest()
	if err != nil {
		o.Fail(err)
		return o
	}

	if !DefaultTransport.DisableKeepAlives {
		req.Header.Set("Connection", "keep-alive")
	}

	resc := make(chan *http.Response, 1)
	errc := make(chan error, 1)
	o.Start()

	for {
		// Execute operation in separate routine.
		go func() {
			res, err := doSend(req)
			resc <- res
			errc <- err
		}()

		// Wait for the context to be cancelled or the operation to be done.
		select {
		case <-o.Done():
			// Timeout
			DefaultTransport.CancelRequest(req)
			<-errc // Wait for goroutine to return.
			<-resc
			return o
		case err = <-errc:
			res := <-resc
			if err == nil {
				o.SetResponse(res)
				o.Complete()
				return o
			}

			shouldRetry := isNetError(err)
			if !shouldRetry || o.DecrementRetriesRemaining() <= 0 {
				o.Fail(err)
				return o
			}

			o.WaitForRetryDelay(err)
		}
	}
}
