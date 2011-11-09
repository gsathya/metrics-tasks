#!/bin/bash
COMMONS="commons-codec-1.4.jar"
CURRENT=`date -u +%Y-%m-%d-%H-00-00`

if [ ! -f commons-codec-1.4.jar ]; then
  echo "$COMMONS not found.  Please download Apache Commons Codec 1.4."
  exit 1
fi

if [ ! -d descriptors ]; then
  mkdir descriptors
else
  rm descriptors/*
fi

echo "Downloading descriptors from the metrics website..."
curl https://metrics.torproject.org/votes?valid-after=$CURRENT \
  > descriptors/$CURRENT-votes
curl https://metrics.torproject.org/consensus?valid-after=$CURRENT \
  > descriptors/$CURRENT-consensus
curl https://metrics.torproject.org/serverdesc?valid-after=$CURRENT \
  > descriptors/$CURRENT-serverdesc

if [ ! -f ParseDescriptors.class ]; then
  echo "Compiling..."
  javac -cp commons-codec-1.4.jar ParseDescriptors.java
fi

echo "Parsing descriptors..."
java -cp .:commons-codec-1.4.jar ParseDescriptors

echo "Plotting..."
R --slave -f bandwidth-comparison.R

echo "Terminating.  Please find the .png files in this directory."

