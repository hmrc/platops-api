#!/bin/bash

os=$(uname -s)
mongoContainerName="platops-api-it"
cwd=$(pwd)
tmpDir="$cwd/tmp"
mongodDataDir="$tmpDir/$mongoContainerName/db"
mongoVersion="6.0"
mongoDockerRepositoryPath="percona/percona-server-mongodb"
platform="multi"
mongoImage="$mongoDockerRepositoryPath:$mongoVersion-$platform"

is_listening() {
    local port=$1
    if sudo netstat -an -p tcp |grep $port | grep LISTEN > /dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

is_mongo_cmd_available() {
  command -v mongo &> /dev/null;
}

is_mongosh_cmd_available() {
  command -v mongosh &> /dev/null;
}

wait_for_check_for_container_status() {
    local containerName=$1
    local state=${2:-true}
    local service=$3
    local port=$4

    if $state; then
      message="Waiting for $service to start on $port"
    else
      message="Waiting for $service to stop listening on $port"
    fi

  check() {
    if $state; then
      case "$containerName" in
         "$mongoContainerName")
             $quiet || echo "Checking if the $mongoContainerName dockerised MongoDB is ready"
             if docker exec $mongoContainerName mongod --version > /dev/null 2>&1 && is_listening $port; then
               return 0
             else
               return 1
             fi
             ;;
         *)
             echo "checking if $service is listening"
             is_listening $port
             ;;
       esac
    else
      ! is_listening $port
    fi
  }

  timeout=60

  $quiet || echo "$message"
  start_time=$(date +%s)
  while ! check; do
    $quiet || echo  "$message"
    current_time=$(date +%s)
    elapsed_time=$((current_time - start_time))
    if [ $elapsed_time -ge $timeout ]; then
        echo "$message timed out after $timeout seconds"
        exit 1
    fi
    sleep 1
  done
  echo "Success [$message]"

}

clean_tmpdir() {
  echo "Preparing $mongodDataDir as the data directory for mongo"
  # These platform specific steps are required only remove if you understand the side effects
  # Docker desktop on Mac will hold open file handles even after stopping the container.
  if [ $os == "Linux" ];then
    $quiet || echo "Deleting tmp directory as root with sudo on Linux"
    sudo rm -rf "$tmpDir"
    mkdir -p "$mongodDataDir"
    $quiet || echo "Adding rwx for ugo for Linux on the data directory"
    chmod -R 777 $tmpDir
  else
    $quiet || echo "Deleting tmp directory on Mac"
    rm -rf "$mongodDataDir"
    mkdir -p "$mongodDataDir"
  fi

}

setup_private_mongo() {
  if is_listening 27017; then
      echo "Mongo is running, trying to gracefully shutdown MongoDB"
      if is_mongo_cmd_available; then
        echo "Using local mongo shell to shutdown server"
        mongo --port 27017 --eval "db.getSiblingDB('admin').shutdownServer()"
      elif is_mongosh_cmd_available; then
        echo "Using local mongosh shell to shutdown server"
        mongosh --port 27017 --eval "db.getSiblingDB('admin').shutdownServer()"
      else
        echo "Looking for mongo running in docker"
        local containerInfo=$(docker ps --format '{{.Names}} {{.Ports}}' | grep '27017')
        if [ -n "$containerInfo" ]; then
          containerName=$(echo "$containerInfo" | awk '{print $1}')
          echo "Found docker running mongo with the container name '$containerName'"
          docker update --restart no $(docker ps -q |grep -v $containerName) &> /dev/null
          docker exec "$containerName" mongosh --eval "db.getSiblingDB('admin').shutdownServer()"
          sleep 5 #race conditions are hard to prevent
          wait_for_check_for_container_status $containerName false "MongoDB" 27017
        else
          echo "No docker container is listening on 27017, exiting with error"
          exit 1
        fi
    fi
  fi

  clean_tmpdir
  echo "Starting docker with data directory $mongodDataDir"

  containerId=$(docker run --name $mongoContainerName -p 27017:27017 -d --rm -v $mongodDataDir:/data/db:rw $mongoImage --replSet rs0)
  sleep 20 #race conditions are hard to prevent
  echo "Container id $containerId"
  docker ps

  if [ -n $containerId ];then
    echo "mongodb started in container with $containerId"
  else
    echo "failed to start mongodb, please retry this script!"
    exit 1
  fi

  $quiet || docker ps -a --filter "id=$containerId"

  wait_for_check_for_container_status $mongoContainerName true "MongoDB" 27017
  echo "Initialising the replica set"

  docker exec $mongoContainerName mongosh admin --eval "rs.initiate()"
  docker exec $mongoContainerName mongosh admin --eval "disableTelemetry()"

}

setup_private_mongo
