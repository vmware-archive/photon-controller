#!/bin/bash
# Removes all Docker containers
docker ps -a | sed 1d | awk '{print $1}' | xargs docker rm -f
