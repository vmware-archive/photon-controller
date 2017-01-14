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
	"fmt"
	"io/ioutil"
	"log"
	"os"
	"regexp"
	"strings"
	"time"

	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/pkg/runtime"
	"k8s.io/client-go/pkg/api/v1"
	"k8s.io/client-go/pkg/watch"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/cache"
)

const PhotonSecretName = "photon-keys"

const UsernameKey = "username"

const PasswordKey = "password"

const PhotonConfigDir = "/etc/photon"

const PhotonConfigFile = PhotonConfigDir + "/photon.cfg"

const PhotonBaseConfig = `[Global]
target = 0.0.0.0
ignoreCertificate = true
tenant = tenant
project = project
overrideIP = false
`

func handleUpdate(obj interface{}, remove bool) {
	secret := obj.(*v1.Secret)
	if (secret.ObjectMeta.Name == PhotonSecretName) {
		data := secret.Data
		if remove {
			delete(data, UsernameKey)
			delete(data, PasswordKey)
		}
		config := readOldConfig()
		newConfig := updateWithNewConfig(data, config)
		writeToFile(newConfig)
	}
}

func getKeyAndUpdateConfig(data map[string][]byte, key string, config string) string {
	rePattern := fmt.Sprintf("(?m)[\r\n]+^.*%s.*$", key)
	re := regexp.MustCompile(rePattern)
	config = re.ReplaceAllString(config, "")
	if _, exists := data[key]; exists {
		log.Print("Key Found....................")
		config += fmt.Sprintf("%s = %s\n",
			key, strings.TrimSpace(string(data[key])))
	}
	return config
}

func updateWithNewConfig(data map[string][]byte, config string) string {
	config = getKeyAndUpdateConfig(data, UsernameKey, config)
	config = getKeyAndUpdateConfig(data, PasswordKey, config)
	return config
}

func readOldConfig() string {
	config := ""
	if _, err := os.Stat(PhotonConfigDir); os.IsNotExist(err) {
		log.Printf("Error: Config directory (%s) does not exists. " +
               "That means the mount from host wast not set properly. "+
               "Creating this directory anyway now.", PhotonConfigDir)
		os.Mkdir(PhotonConfigDir, 0711)
	}

	if _, err := os.Stat(PhotonConfigFile); os.IsNotExist(err) {
		log.Print("Info: No old config file exists, will create new config file.")
		config += PhotonBaseConfig
	} else {
		input, err := ioutil.ReadFile(PhotonConfigFile)
		if err != nil {
			config += PhotonBaseConfig
			log.Print("Error: Could not read old config file.")

		} else {
			config += string(input)
		}
	}
	return config
}

func writeToFile(newConfig string) {
	f, err := os.OpenFile(PhotonConfigFile, os.O_RDWR|os.O_CREATE, 0660);
	if err != nil {
		panic(err)
	}

	defer f.Close()

	if _, err = f.WriteString(newConfig); err != nil {
		panic(err)
	}

	log.Printf("Info: Updated %s config file.", PhotonConfigFile)
}

func main() {
	config, err := rest.InClusterConfig()
	if err != nil {
		panic(err.Error())
	}

	clientset, err := kubernetes.NewForConfig(config)
	if err != nil {
		panic(err.Error())
	}

	watchlist := cache.ListWatch{
		ListFunc: func(options v1.ListOptions) (runtime.Object, error) {
			return clientset.Core().Secrets(v1.NamespaceAll).List(options)
		},
		WatchFunc: func(options v1.ListOptions) (watch.Interface, error) {
			return clientset.Core().Secrets(v1.NamespaceAll).Watch(options)
		},
	}

	_, controller := cache.NewInformer(
		&watchlist,
		&v1.Secret{},
		time.Second * 0,
		cache.ResourceEventHandlerFuncs{
			AddFunc: func(obj interface{}) {
				handleUpdate(obj, false);
			},
			DeleteFunc: func(obj interface{}) {
				log.Print("Info: Secret deleted.")
				handleUpdate(obj, true);
			},
			UpdateFunc:func(oldObj, newObj interface{}) {
				handleUpdate(newObj, false);
			},
		},
	)
	stop := make(chan struct{})
	go controller.Run(stop)

	log.Print("Info: Work started.")

	for {
		time.Sleep(time.Second)
	}
}
