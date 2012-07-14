#!/usr/bin/env python
#
# This program is free software. It comes without any warranty, to
# the extent permitted by applicable law. You can redistribute it
# and/or modify it under the terms of the Do What The Fuck You Want
# To Public License, Version 2, as published by Sam Hocevar. See
# http://sam.zoy.org/wtfpl/COPYING for more details.

import json
import operator
import sys

class RelayStats(object):
    def __init__(self):
        self._data = None

    @property
    def data(self):
        if not self._data:
            self._data = json.load(file('details.json'))
        return self._data

    def get_total_consensus_weight(self, relays=None):
        if relays is None:
            relays = self.get_relays()
        total_consensus_weight = 0
        for relay in relays:
            total_consensus_weight += relay['consensus_weight']
        return total_consensus_weight

    def get_relays(self, flags=[], countries=''):
        relays = []
        for relay in self.data['relays']:
            if not relay['running']:
                continue
            if set(flags) & set(relay['flags']) != set(flags):
                continue
            if countries and not relay.get('country', '') in countries:
                continue
            relays.append(relay)
        return relays

    def output_countries(self, count='10', flags=''):
        count = int(count)
        flags = flags.split()
        relays = self.get_relays(flags)
        countries = {}
        for relay in relays:
            country = relay.get('country', None)
            if country not in countries:
              countries[country] = 0
            countries[country] += relay['consensus_weight']

        ranking = sorted(countries.iteritems(), key=operator.itemgetter(1))
        ranking.reverse()
        total_consensus_weight = self.get_total_consensus_weight(relays)
        for country, weight in ranking[:count]:
            print "%3.2f%% %s" % (weight * 100.0 / total_consensus_weight, country)

    def output_as_sets(self, count='10', flags='', countries=''):
        count = int(count)
        flags = flags.split()
        relays = self.get_relays(flags, countries)
        as_sets = {}
        for relay in relays:
            as_set = relay.get('as_name', 'Unknown')
            if as_set not in as_sets:
                as_sets[as_set] = 0
            as_sets[as_set] += relay['consensus_weight']

        total_consensus_weight = self.get_total_consensus_weight(relays)
        ranking = sorted(as_sets.iteritems(), key=operator.itemgetter(1))
        ranking.reverse()
        for as_set, weight in ranking[:count]:
            print "%3.4f%% %s" % (weight * 100.0 / total_consensus_weight, as_set)

    def output_relays(self, count='10', flags='', countries=''):
        count = int(count)
        flags = flags.split()
        relays = self.get_relays(flags, countries)

        total_consensus_weight = self.get_total_consensus_weight()
        ranking = sorted(relays, key=operator.itemgetter('consensus_weight'))
        ranking.reverse()
        for relay in ranking[:count]:
            print "%3.4f%% %-20s %s" % (relay['consensus_weight'] * 100.0 / total_consensus_weight, relay['nickname'], relay['fingerprint'])

OUTPUTS = {
  'countries': 'output_countries',
  'as-sets': 'output_as_sets',
  'relays': 'output_relays',
}

def usage():
    print >>sys.stderr, """Usage: %(progname)s <output> [args ...]

Where <output> is one of:
 - countries [COUNT] [FLAGS]
   relative percentage of the consensus in each countries
 - as-sets [COUNT] [FLAGS] [COUNTRIES]
   relative percentage of the consensus in each AS sets
 - relays [COUNT] [FLAGS] [COUNTRIES]
   list relays ranked by their place in the whole consensus

Examples:

 - To get the top five exit nodes in France:
   %(progname)s top 5 Exit fr
 - To get weights of the top ten AS of all relays in Germany:
   %(progname)s as-sets 10 Running de

This script expect to have a file called 'details.json' in the
current directory. In order to retrieve the needed data, one
can issue the following command:

    curl -o details.json 'https://onionoo.torproject.org/details?type=relay&running=true'
""" % { 'progname': sys.argv[0] }
    sys.exit(1)

if '__main__' == __name__:
    if len(sys.argv) == 1:
        usage()
    func = OUTPUTS.get(sys.argv[1], None)
    if not func:
        usage()
    stats = RelayStats()
    getattr(stats, func)(*sys.argv[2:])
