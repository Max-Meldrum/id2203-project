#!/bin/bash

# https://stackoverflow.com/questions/15557857/how-to-kill-all-processes-that-were-opened-by-a-shell-script-upon-ctrl-c
# Way to kill all nodes we have started through this script..
intexit() {
    kill -HUP -$$
}

hupexit() {
    echo
    echo "Killing cluster"
    exit
}

trap hupexit HUP
trap intexit INT

# Credit to https://github.com/Limmen/Distributed-KV-store/blob/master/cluster.sh
echo "Starting cluster of $1 servers"
./bootstrap_server.sh &
{
sleep 2
BOOTCLIENTS=$(($1-1))
for i in `seq 1 $BOOTCLIENTS`;
       do
                 sleep 1
                 PORT=$(($i+3000))
                (./normal_server.sh $PORT &)
        done
} &> /dev/null
wait
