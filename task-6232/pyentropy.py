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
                
    # build hash table with freq. distribution
    # key: bandwidth
    # value: number of bandwidth's observations
    
    bw_dist, bw_dist_exit, bw_dist_guard = {}, {}, {}
    for router in routers:
        if router.is_exit:
            if bw_dist_exit.has_key(router.bandwidth):
                bw_dist_exit[router.bandwidth] += 1
            else:
                bw_dist_exit[router.bandwidth] = 1
        if router.is_guard:
            if bw_dist_guard.has_key(router.bandwidth):
                bw_dist_guard[router.bandwidth] += 1
            else:
                bw_dist_guard[router.bandwidth] = 1
        if bw_dist.has_key(router.bandwidth):
            bw_dist[router.bandwidth] += 1
        else:
            bw_dist[router.bandwidth] = 1
    
    if len(routers) <= 0:
        print "Error: amount of routers must be > 0."
        return;
    
    entropy, entropy_exit, entropy_guard = 0.0, 0.0, 0.0
    for bw in bw_dist.iterkeys():
        # p = probability of one particular bandwidth
        p = float(bw_dist[bw]) / len(routers)
        entropy += -(p * math.log(p, 2))
        
    for bw in bw_dist_exit.iterkeys():
        # p = probability of one particular bandwidth
        p = float(bw_dist[bw]) / len(routers)
        entropy_exit += -(p * math.log(p, 2))
        
    for bw in bw_dist_guard.iterkeys():
        # p = probability of one particular bandwidth
        p = float(bw_dist[bw]) / len(routers)
        entropy_guard += -(p * math.log(p, 2))
    
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
                f.write("%s\n" % (string))
