"""
Usage - python pyentropy.py -h
Output - A CSV file of the format (without newlines):
         <valid-after>,
         <entropy for all nodes>,
         <max entropy for all nodes>,
         <entropy for exit nodes>,
         <max entropy for exit nodes>,
         <entropy for guard nodes>,
         <max entropy for guard nodes>,
         <entropy for countries>,
         <max entropy for countries>,
         <entropy for AS>,
         <max entropy for AS>
rsync -arz --delete metrics.torproject.org::metrics-recent/relay-descriptors/consensuses in
"""

import sys
import math
import os
import pygeoip
import StringIO
import stem.descriptor
from stem.descriptor.server_descriptor import RelayDescriptor, BridgeDescriptor
from binascii import b2a_hex, a2b_base64, a2b_hex
from optparse import OptionParser

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
        self.as_no = None
        self.as_name = None
        self.is_exit = None
        self.is_guard = None

    def add(self, key, values):
        if key == 'r':
           self.nick = values[0]
           self.ip = values[5]
           self.country = gi_db.country_name_by_addr(self.ip)
           self.as_no, self.as_name = self.get_as_details()
        if key == 'w':
           self.bandwidth = int(values[0].split('=')[1])
        if key == 's':
           self.flags = values
           if "Exit" in self.flags:
               self.is_exit = True
           if "Guard" in self.flags:
               self.is_guard = True

    def get_as_details(self):
        try:
            value = as_db.org_by_addr(str(self.ip)).split()
            return value[0], value[1]
        except:
            return None, None
        
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
    bw_countries, bw_as = {}, {}
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
        if router.as_no:
            if bw_as.has_key(router.as_no):
                bw_as[router.as_no] += router.bandwidth
            else:
                bw_as[router.as_no] = router.bandwidth

    if len(routers) <= 0:
        return
    
    entropy, entropy_exit, entropy_guard, entropy_country, entropy_as = 0.0, 0.0, 0.0, 0.0, 0.0
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
    
    for as_no in bw_as.iterkeys():
        p = float(bw_as[as_no]) / float(totalBW)
        if p !=0:
            entropy_as += -(p * math.log(p, 2))
    
    # Entropy of uniform distribution of 'n' possible values: log(n)
    max_entropy = math.log(len(routers), 2)
    max_entropy_guard = math.log(guards_n, 2)
    max_entropy_exit = math.log(exits_n, 2)
    max_entropy_country = math.log(len(bw_countries), 2)
    max_entropy_as = math.log(len(bw_as), 2)
    
    return ",".join([valid_after,
                     str(entropy),
                     str(max_entropy),
                     str(entropy_exit),
                     str(max_entropy_exit),
                     str(entropy_guard),
                     str(max_entropy_guard),
                     str(entropy_country),
                     str(max_entropy_country),
                     str(entropy_as),
                     str(max_entropy_as)])

def parse_args():
    usage = "Usage - python pyentropy.py [options]"
    parser = OptionParser(usage)

    parser.add_option("-g", "--geoip", dest="gi_db", default="GeoIP.dat", help="Input GeoIP database")
    parser.add_option("-a", "--as", dest="as_db", default="GeoIPASNum.dat", help="Input AS GeoIP database")
    parser.add_option("-o", "--output", dest="output", default="entropy.csv", help="Output filename")
    parser.add_option("-c", "--consensus", dest="consensus", default="in/consensus", help="Input consensus dir")

    (options, args) = parser.parse_args()

    return options

if __name__ == "__main__":

    options = parse_args()
    gi_db = pygeoip.GeoIP(options.gi_db)
    as_db = pygeoip.GeoIP(options.as_db)

    with open(options.output, 'w') as f:
        for file_name in os.listdir(options.consensus):
            string = run(os.path.join(options.consensus, file_name))
            if string:
                f.write("%s\n" % (string))
