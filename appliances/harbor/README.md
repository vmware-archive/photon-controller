# Harbor Stem Cell

This builds the Harbor stem cell for use by the Cluster Manager. It can be used to bring up the harbor registry. To 
build the Harbor stem cell run,

```
./build-harbor-stem-cell.sh
```

The stem cell has docker-compose and Harbor installed. To change the hostname, password, etc. edit the harbor.cfg 
file in /root/harbor directory. To bring up the Harbor registry run the following commands from within the 
/root/harbor directory:

```
./prepare
docker-compose up -d
```
