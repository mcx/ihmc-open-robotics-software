#!/bin/bash
# Uncomment for debugging this script
set -o xtrace

# Make sure it works one way or the other to reduce possible errors
if (( EUID == 0 )); then
    echo "Run with sudo." 1>&2
    exit 1
fi

sudo -u root docker run \
    --tty \
    --interactive \
    --rm \
    --network host \
    --privileged \
    --gpus all \
    --device /dev/dri:/dev/dri \
    --env DISPLAY \
    --volume /tmp/.X11-unix:/tmp/.X11-unix:rw \
    ihmcrobotics/nvidia-ros:0.1 rviz