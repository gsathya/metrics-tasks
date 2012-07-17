"""
Usage - python pyentropy.py <consensus-dir> <output-file>
Output - A CSV file of the format <valid-after>,<entropy for all nodes>,<entropy for exitnodes>,<entropy for guardnodes>,<entropy for countries>
rsync -arz --delete metrics.torproject.org::metrics-recent/relay-descriptors/consensuses in
"""

import sys
import math
import os
import pygeoip
import getopt

KEYS = ['r','s','v','w','p','m']

class Router:
    def __init__(self):
        self.lines = []
        self.nick = None
        self.bandwidth = None
        self.flags = None
        self.probability = None
        self.ip = None
        self.country = None
        self.is_exit = None
        self.is_guard = None

    def add(self, key, values):
        if key == 'r':
           self.nick = values[0]
           self.ip = values[5]
           self.country = gi.country_name_by_addr(self.ip)
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
    guards_n, exits_n = 0, 0
    bw_countries = {}
    for router in routers:
        totalBW += router.bandwidth
        if router.is_guard:
            totalGuardBW += router.bandwidth
            guards_n += 1
        if router.is_exit:
            totalExitBW += router.bandwidth
            exits_n += 1
        if bw_countries.has_key(router.country):
            bw_countries[router.country] += router.bandwidth
        else:
            bw_countries[router.country] = router.bandwidth

    if len(routers) <= 0:
        return

    entropy, entropy_exit, entropy_guard, entropy_country = 0.0, 0.0, 0.0, 0.0
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

    for country in bw_countries.iterkeys():
        p = float(bw_countries[country]) / float(totalBW)
        if p != 0:
            entropy_country += -(p * math.log(p, 2))

    # Entropy of uniform distribution of 'n' possible values: log(n)
    max_entropy = math.log(len(routers), 2)
    max_entropy_guard = math.log(guards_n, 2)
    max_entropy_exit = math.log(exits_n, 2)
    max_entropy_country = math.log(len(bw_countries), 2)

    return ",".join([valid_after,
                     str(entropy/max_entropy),
                     str(entropy_exit/max_entropy_exit),
                     str(entropy_guard/max_entropy_guard),
                     str(entropy_country/max_entropy_country)])

def usage():
    print "Usage - python pyentropy.py <consensus-dir> <output-file>"

if __name__ == "__main__":
    if len(sys.argv) != 3:
        usage()
        sys.exit()

    gi = pygeoip.GeoIP(os.path.join(os.path.dirname(__file__), 'GeoIP.dat'))
    with open(sys.argv[2], 'w') as f:
        for file_name in os.listdir(sys.argv[1]):
            string = run(os.path.join(sys.argv[1], file_name))
            if string:
                f.write("%s\n" % (string))
