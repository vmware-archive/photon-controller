#!/bin/bash
# Removes all Docker images
docker images | sed 1d | awk '{print $3}' | xargs docker rmi -f
