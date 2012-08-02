#!/bin/bash
rsync -arz --delete metrics.torproject.org::metrics-recent/relay-descriptors/consensuses in
rsync -arz --delete metrics.torproject.org::metrics-recent/relay-descriptors/server-descriptors in

