import os
import datetime
import sys

from math import floor

UNALLOC_POOL = "unallocated"

class Bridge(object):
    def __init__(self, fp, pool = "", params = {}):
        object.__init__(self)
        self._fp = fp
        self._pool = pool
        self._params = params
        self._from = None
        self._to = None
        self._usage_per_country = {}
        self._usage_per_day = {}

    def pprint(self):
        print self._fp, self._pool, self._params

    # <40 bytes fp> <pool> <key>=<val> ...
    @staticmethod
    def fromLine(line):
        fp = line[:40]
        pool = ""
        params = {}
        
        rest = line[41:]
        next_spc = rest.find(" ")
        if next_spc == -1:
            pool = rest
            return Bridge(fp, pool)

        pool = rest[:next_spc]
        rest = rest[next_spc+1:]

        key = val = ""
        next_spc = rest.find(" ")
        while next_spc != -1:
            key, val = Bridge.parseKeyVal(rest[:next_spc])
            params[key] = val
            rest = rest[next_spc+1:]
            next_spc = rest.find(" ")

        return Bridge(fp, pool, params)

    @staticmethod
    def parseKeyVal(line):
        [key, val] = line.split("=")
        return key, val

class BridgePool(object):
    def __init__(self, path, bridges, year, month, day, hour, minute, seconds):
        object.__init__(self)
        self._path = path
        self._date = (year, month, day, hour, minute, seconds)
        self._bridges = bridges
        self.gatherBridges()

    def pprint(self):
        print "%s/%s/%s :: %s:%s:%s" % self._date
        for b in self._bridges:
            b.pprint()

    def gatherBridges(self):
        f = open(self._path, "r")
        contents = f.read().split("\n")[1:]
        for line in contents:
            if len(line.strip()) == 0:
                continue
            if line[:40] in self._bridges.keys():
                self._bridges[line[:40]]._to = self._date
            else:
                b = Bridge.fromLine(line)
                b._from = self._date
                self._bridges[b._fp] = b

def listHas(l, comp):
    for e in l:
        if e.startswith(comp):
            return True
    return False

class BridgeUsageAnalyzer(object):
    def __init__(self, fromPath = ".", bridges = {}):
        object.__init__(self)
        self._bridges = bridges
        self._root = fromPath
        self.gatherUsage()

    def gatherUsage(self):
        print "Walking dirs to gather bridge usage (you might want to go do something else for a while)..."
        for root, dirs, files in os.walk(self._root):
            comps = root.split(os.sep)
            # bridge-descriptors-<year>-<month>/fp[0]/fp[1]/fp
            if not listHas(comps, "bridge-descriptors-"):
                continue
            if not listHas(comps, "extra-info"):
                continue
            print root

            for f in files:
                f_value = os.path.join(root, f)
                try:
                    bfp, ips, day = self.parseExtraInfo(f_value)
                    if not bfp in self._bridges.keys():
                        #print "WARNING: %s is not in the bridge pool" % (f,)
                        continue
                    daily_usage = 0
                    for c in ips.keys():
                        if not c in self._bridges[bfp]._usage_per_country.keys():
                            self._bridges[bfp]._usage_per_country[c] = 0
                        self._bridges[bfp]._usage_per_country[c] += ips[c]
                        daily_usage += ips[c]
                    if not day in self._bridges[bfp]._usage_per_day.keys():
                        self._bridges[bfp]._usage_per_day[day] = 0
                    self._bridges[bfp]._usage_per_day[day] += daily_usage
                except KeyboardInterrupt, e:
                    sys.exit(1)
                except Exception, e:
                    print e
                    print "ERROR: Something was wrong while processing %s" % (f_value,)

    def parseExtraInfo(self, path):
        contents = open(path, "r").read()
        bfp = ""
        ips = {}
        day = ""
        for l in contents.split("\n"):
            if l.startswith("extra-info Unnamed"):
                bfp = l.split(" ")[-1].lower()
                continue
            if l.startswith("published"):
                day = l.split(" ")[1]
            if l.startswith("bridge-ips"):
                bridgeips = l.split(",")[1:]
                try:
                    for ci in bridgeips:
                        parts = ci.split("=")
                        ips[parts[0]] = int(parts[1])
                except:
                    break

        return bfp, ips, day

    # Question: is there any correlation between the usage by country
    # and the pool the bridge is from?
    def analyzeUsage(self):
        alloc_users = 0
        unalloc_users = 0

        alloc_usage = {}
        unalloc_usage = {}
        for bfp in self._bridges.keys():
            acc = 0
            for c in self._bridges[bfp]._usage_per_country.keys():
                acc = self._bridges[bfp]._usage_per_country[c]
                if self._bridges[bfp]._pool == UNALLOC_POOL:
                    unalloc_users += acc
                    if not c in unalloc_usage.keys():
                        unalloc_usage[c] = 0
                    unalloc_usage[c] += acc
                else:
                    alloc_users += acc
                    if not c in alloc_usage.keys():
                        alloc_usage[c] = 0
                    alloc_usage[c] += acc

        print "Total allocated pool users: %d" % (alloc_users,)
        print "Total unallocated pool users: %d" % (unalloc_users,)

        alloc_items = alloc_usage.items()
        alloc_sorted_items = sorted(alloc_items, key=lambda use: use[1])
        print "Country usage for allocated pool:"
        for country, count in alloc_sorted_items:
            print "  %s = %d" % (country, count)

        unalloc_items = unalloc_usage.items()
        unalloc_sorted_items = sorted(unalloc_items, key=lambda use: use[1])
        print "Country usage for unallocated pool:"
        for country, count in unalloc_sorted_items:
            print "  %s = %d" % (country, count)

        total_avg_unalloc = []
        total_avg_alloc = []
        for bfp in self._bridges.keys():
            avg = 0.0
            for day in self._bridges[bfp]._usage_per_day.keys():
                avg += self._bridges[bfp]._usage_per_day[day]
            if avg == 0.0:
                continue
            avg = float(avg)/len(self._bridges[bfp]._usage_per_day.keys())
            if self._bridges[bfp]._pool == UNALLOC_POOL:
                total_avg_unalloc.append(avg)
            else:
                total_avg_alloc.append(avg)
            #print "Average for %s: %d" % (bfp, avg)
        total_avg_unalloc.sort()
        total_avg_alloc.sort()

        print "Unallocated pool:"
        print "  Average clients per day: %d" % (float(sum(total_avg_unalloc))/len(total_avg_unalloc),)
        print "  Median: %d" % (total_avg_unalloc[int(round(len(total_avg_unalloc)/2.0))],)
        print "  75th percentile: %d" % (total_avg_unalloc[int(round(len(total_avg_unalloc)*0.75))],)
        print "  90th percentile: %d" % (total_avg_unalloc[int(round(len(total_avg_unalloc)*0.9))],)

        print "Allocated pool:"
        print "  Average clients per day: %d" % (float(sum(total_avg_alloc))/len(total_avg_alloc),)
        print "  Median: %d" % (total_avg_alloc[int(round(len(total_avg_alloc)/2.0))],)
        print "  75th percentile: %d" % (total_avg_alloc[int(round(len(total_avg_alloc)*0.75))],)
        print "  90th percentile: %d" % (total_avg_alloc[int(round(len(total_avg_alloc)*0.9))],)

class BridgePoolAnalyzer(object):
    year = 0
    month = 1
    day = 2
    hour = 3
    minute = 4
    seconds = 5

    def __init__(self, fromPath = "."):
        object.__init__(self)
        self._root = fromPath
        self._pools = []
        self._bridges = {}
        self.gatherBridgePools()

    def pprint(self):
        for bp in self._pools:
            bp.pprint()
    
    def gatherBridgePools(self):
        print "Walking dirs to gather bridge pools (you might want to go do something else for a while)..."
        for root, dirs, files in os.walk(self._root):
            comps = root.split(os.sep)
            if not listHas(comps,"bridge-pool-assignments"):
                continue
            print root

            for f in files:
                f_value = os.path.join(root, f)
                date = f.split("-")
                try:
                    self._pools.append(BridgePool(f_value, self._bridges,
                                                  date[self.year], date[self.month], date[self.day],
                                                  date[self.hour], date[self.minute], date[self.seconds]))
                except KeyboardInterrupt, e:
                    sys.exit(1)
                except Exception, e:
                    print e
                    print "ERROR: Something was wrong while processing %s" % (f_value,)

    # Question: how much do bridges last when they are unassigned?
    def bridgeAvailability(self):
        print "Analyzing the data..."
        availability_unalloc = {}
        availability_else = {}

        for bfp in self._bridges.keys():
            b = self._bridges[bfp]
            if b._to is None:
                b._to = self._pools[-1]._date
            if b._pool == UNALLOC_POOL:
                availability_unalloc[b._fp] = (datetime.datetime(int(b._to[0]),   int(b._to[1]),   int(b._to[2]),
                                                                 int(b._to[3]),   int(b._to[4]),   int(b._to[5])) - 
                                               datetime.datetime(int(b._from[0]), int(b._from[1]), int(b._from[2]),
                                                                 int(b._from[3]), int(b._from[4]), int(b._from[5]))).total_seconds() / (60.0 * 60.0 * 24.0)
            else:
                availability_else[b._fp] = (datetime.datetime(int(b._to[0]),   int(b._to[1]),   int(b._to[2]),
                                                              int(b._to[3]),   int(b._to[4]),   int(b._to[5])) - 
                                            datetime.datetime(int(b._from[0]), int(b._from[1]), int(b._from[2]),
                                                              int(b._from[3]), int(b._from[4]), int(b._from[5]))).total_seconds() / (60.0 * 60.0 * 24.0)
        
        print "Allocated bridges:"
        doall(availability_else)

        print "Unallocated bridges:"
        doall(availability_unalloc)

def calc_average(array):
    avg = 0.0
    for k in array.keys():
        avg += float(array[k])
    avg /= len(array.keys())
    return avg

def calc_median(values):
    return values[int(floor(len(values)/2.0))]

def calc_percentile(values, perc):
    pos = int(round(perc/100.0 * float(len(values))))
    return values[pos]

def doall(array):
    print "  Average availability:", calc_average(array)
    values = array.values()
    values.sort()
    print "  Median availability:", calc_median(values)
    print "  75th percentile availability:", calc_percentile(values, 75)
    print "  90th percentile availability:", calc_percentile(values, 90)

if __name__ == "__main__":
    path = "."
    if len(sys.argv) > 1:
        path = sys.argv[1]
    bpa = BridgePoolAnalyzer(path)
    #bpa.pprint()
    bpa.bridgeAvailability()

    bua = BridgeUsageAnalyzer(path, bpa._bridges)
    bua.analyzeUsage()
