"""
Usage - python pyentropy.py <consensus-dir> <output-file>
Output - A CSV file of the format <valid-after>,<entropy for all nodes>,<entropy for exitnodes>,<entropy for guardnodes>
rsync -arz --delete metrics.torproject.org::metrics-recent/relay-descriptors/consensuses in
"""

import sys
import math
import os
from decimal import *

RESULTS = []
KEYS = ['r','s','v','w','p','m']

class Router:
    def __init__(self):
        self.lines = []
        self.nick = None
        self.bandwidth = None
        self.flags = None
        self.probability = None
        self.is_exit = None
        self.is_guard = None

    def add(self, key, values):
        if key == 'r':
           self.nick = values[0]
        if key == 'w':
           self.bandwidth = int(values[0].split('=')[1])
        if key == 's':
           self.flags = values
           if "Exit" in self.flags:
               self.is_exit = True
           if "Guard" in self.flags:
               self.is_guard = True

def run(file_name):
    routers = []
        # parse consensus
    with open(file_name, 'r') as f:
        for line in f.readlines():
            key = line.split()[0]
            values = line.split()[1:]
            if key =='r':
                router = Router()
                router.add(key, values)
            elif key == 'p':
                router.add(key, values)
                routers.append(router)
            elif key == 'valid-after':
                valid_after = ' '.join(values)
            elif key in KEYS:
                router.add(key, values)

    totalBW, totalExitBW, totalGuardBW = 0, 0, 0
    for router in routers:
        totalBW += router.bandwidth
        if router.is_guard:
            totalGuardBW += router.bandwidth
        if router.is_exit:
            totalExitBW += router.bandwidth

    if len(routers) <= 0:
        return

    entropy, entropy_exit, entropy_guard = 0.0, 0.0, 0.0
    for router in routers:
        p = float(router.bandwidth) / float(totalBW)
        if p != 0:
            entropy += -(p * math.log(p, 2))

        if router.is_guard:
            p = float(router.bandwidth) / float(totalGuardBW)
            if p != 0:
                entropy_guard += -(p * math.log(p, 2))

        if router.is_exit:
            p = float(router.bandwidth) / float(totalExitBW)
            if p != 0:
                entropy_exit += -(p * math.log(p, 2))

    return ",".join([valid_after, str(entropy), str(entropy_exit), str(entropy_guard)])

def usage():
    print "Usage - python pyentropy.py <consensus-dir> <output-file>"

if __name__ == "__main__":
    if len(sys.argv) != 3:
        usage()
    else:
        with open(sys.argv[2], 'w') as f:
            for file_name in os.listdir(sys.argv[1]):
                string = run(os.path.join(sys.argv[1], file_name))
                if string:
                    f.write("%s\n" % (string))
