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

from optparse import OptionParser
from binascii import b2a_hex, a2b_base64, a2b_hex
from stem.descriptor.server_descriptor import RelayDescriptor, BridgeDescriptor

class Router:
    def __init__(self):
        self.lines = []
        self.nick = None
        self.digest = None
        self.hex_digest = None
        self.bandwidth = None
        self.advertised_bw = None
        self.flags = None
        self.probability = None
        self.ip = None
        self.country = None
        self.as_no = None
        self.as_name = None
        self.is_exit = None
        self.is_guard = None
    
    def add_router_info(self, values):
           self.nick = values[0]
           self.digest = values[2]
           self.hex_digest = b2a_hex(a2b_base64(self.digest+"="))
           self.ip = values[5]
           self.country = gi_db.country_name_by_addr(self.ip)
           self.as_no, self.as_name = self.get_as_details()

    def add_weights(self, values):
           self.advertised_bw = self.get_advertised_bw()
           if self.advertised_bw:
               self.bandwidth = self.advertised_bw
           else:
               self.bandwidth = int(values[0].split('=')[1])

    def add_flags(self, values):
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
    
    def get_advertised_bw(self):
        try:
            with open(options.server_desc+self.hex_digest) as f:
                data = f.read()
                
            desc_iter = stem.descriptor.server_descriptor.parse_file(StringIO.StringIO(data))
            desc_entries = list(desc_iter)
            desc = desc_entries[0]
            return min(desc.average_bandwidth, desc.burst_bandwidth, desc.observed_bandwidth)
        except:
            return None

def parse_bw_weights(values):
    data = {}
    try:
        for value in values:
            key, value = value.split("=")
            data[key] = float(value) / 10000
        return data
    except:
        return None

def run(file_name):
    routers = []
    router = None
    Wed, Wee, Wgd, Wgg = 1, 1, 1, 1
    # parse consensus
    with open(file_name, 'r') as f:
        for line in f.readlines():
            key = line.split()[0]
            values = line.split()[1:]
            if key =='r':
                router = Router()
                routers.append(router)
                router.add_router_info(values)
            elif key == 's':
                router.add_flags(values)
            elif key == 'w':
                router.add_weights(values)
            elif key == 'valid-after':
                valid_after = ' '.join(values)
            elif key == 'bandwidth-weights':
                data = parse_bw_weights(values)
                try: 
                    Wed = data['Wed']
                    Wee = data['Wee']
                    Wgd = data['Wgd']
                    Wgg = data['Wgg']
                except:
                    pass
    
    total_bw, total_exit_bw, total_guard_bw = 0, 0, 0
    guards_no, exits_no = 0, 0
    bw_countries, bw_as = {}, {}
    for router in routers:
        total_bw += router.bandwidth
        if router.is_guard and router.is_exit:
            total_guard_bw += Wgd*router.bandwidth
            total_exit_bw += Wed*router.bandwidth
        elif router.is_guard:
            total_guard_bw += Wgg*router.bandwidth
            guards_no += 1
        elif router.is_exit:
            total_exit_bw += Wee*router.bandwidth
            exits_no += 1
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
        p = float(router.bandwidth) / float(total_bw)
        if p != 0:
            entropy += -(p * math.log(p, 2))
        if router.is_guard and router.is_exit:
            p = float(Wgd*router.bandwidth) / float(total_guard_bw)
            if p != 0:
                entropy_guard += -(p * math.log(p, 2))
            p = float(Wed*router.bandwidth) / float(total_exit_bw)
            if p != 0:
                entropy_exit += -(p * math.log(p, 2))
        elif router.is_guard:
            p = float(Wgg*router.bandwidth) / float(total_guard_bw)
            if p != 0:
                entropy_guard += -(p * math.log(p, 2))
        elif router.is_exit:
            p = float(Wee*router.bandwidth) / float(total_exit_bw)
            if p != 0:
                entropy_exit += -(p * math.log(p, 2))
    
    for country in bw_countries.iterkeys():
        p = float(bw_countries[country]) / float(total_bw)
        if p != 0:
            entropy_country += -(p * math.log(p, 2))
    
    for as_no in bw_as.iterkeys():
        p = float(bw_as[as_no]) / float(total_bw)
        if p !=0:
            entropy_as += -(p * math.log(p, 2))
    
    # Entropy of uniform distribution of 'n' possible values: log(n)
    max_entropy = math.log(len(routers), 2)
    max_entropy_guard = math.log(guards_no, 2)
    max_entropy_exit = math.log(exits_no, 2)
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
    
    parser.add_option("-g", "--geoip", dest="gi_db", default="GeoIP.dat", 
                      help="Input GeoIP database")
    parser.add_option("-a", "--as", dest="as_db", default="GeoIPASNum.dat",
                      help="Input AS GeoIP database")
    parser.add_option("-s", "--server_desc", dest="server_desc",
                      default="data/relay-descriptors/server-descriptors/", help="Server descriptors directory")
    parser.add_option("-o", "--output", dest="output", default="entropy.csv",
                      help="Output filename")
    parser.add_option("-c", "--consensus", dest="consensus", default="in/consensus",
                      help="Input consensus dir")
    
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
