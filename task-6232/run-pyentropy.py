#!/bin/bash
#### Uncomment to use most recent data instead of extracted tarballs
###rsync -arz --delete metrics.torproject.org::metrics-recent/relay-descriptors/consensuses in
python pyentropy.py in/consensuses/ entropy.csv

