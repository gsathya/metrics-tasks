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
import os.path

class RelayStats(object):
    def __init__(self):
        self._data = None

    @property
    def data(self):
        if not self._data:
            self._data = json.load(file('details.json'))
        return self._data

    def get_relays(self, flags=[], countries='', as_sets=[]):
        relays = []
        for relay in self.data['relays']:
            if not relay['running']:
                continue
            if set(flags) & set(relay['flags']) != set(flags):
                continue
            if countries and not relay.get('country', ' ') in countries:
                continue
            if as_sets and not relay.get('as_number', ' ') in as_sets:
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
            countries[country] += relay['consensus_weight_fraction']

        ranking = sorted(countries.iteritems(), key=operator.itemgetter(1))
        ranking.reverse()
        for country, weight in ranking[:count]:
            print "%8.4f%% %s" % (weight * 100.0, country)
        if len(ranking) > count:
            other_consensus_weight_fraction = 0
            for as_set, weight in ranking[count:]:
                other_consensus_weight_fraction += weight
            print "%8.4f%% (%d others)" % (other_consensus_weight_fraction * 100.0, len(ranking) - count)
        selection_consensus_weight_fraction = 0
        for as_set, weight in ranking:
            selection_consensus_weight_fraction += weight
        if selection_consensus_weight_fraction < 0.999:
            print "%8.4f%% (total in selection)" % (selection_consensus_weight_fraction * 100.0)

    def output_as_sets(self, count='10', flags='', countries=''):
        count = int(count)
        flags = flags.split()
        relays = self.get_relays(flags, countries)
        as_sets = {}
        for relay in relays:
            as_set = relay.get('as_name', 'Unknown')
            if as_set not in as_sets:
                as_sets[as_set] = 0
            as_sets[as_set] += relay['consensus_weight_fraction']

        ranking = sorted(as_sets.iteritems(), key=operator.itemgetter(1))
        ranking.reverse()
        for as_set, weight in ranking[:count]:
            print "%8.4f%% %s" % (weight * 100.0, as_set)
        if len(ranking) > count:
            other_consensus_weight_fraction = 0
            for as_set, weight in ranking[count:]:
                other_consensus_weight_fraction += weight
            print "%8.4f%% (%d others)" % (other_consensus_weight_fraction * 100.0, len(ranking) - count)
        selection_consensus_weight_fraction = 0
        for as_set, weight in ranking:
            selection_consensus_weight_fraction += weight
        if selection_consensus_weight_fraction < 0.999:
            print "%8.4f%% (total in selection)" % (selection_consensus_weight_fraction * 100.0)

    def output_relays(self, count='10', flags='', countries='', as_sets=''):
        count = int(count)
        flags = flags.split()
        as_sets = as_sets.split()
        relays = self.get_relays(flags, countries, as_sets)

        ranking = sorted(relays, key=operator.itemgetter('consensus_weight_fraction'))
        ranking.reverse()
        selection_consensus_weight_fraction = 0
        for relay in ranking[:count]:
            selection_consensus_weight_fraction += relay['consensus_weight_fraction']
            print "%8.4f%% %-19s %-2s %-4s %-5s %s %-9s %s" % (relay['consensus_weight_fraction'] * 100.0, relay['nickname'], relay['fingerprint'], 'Exit' if 'Exit' in set(relay['flags']) else '', 'Guard' if 'Guard' in set(relay['flags']) else '', relay.get('country', '  '), relay.get('as_number', ''), relay.get('as_name', ''))
        if len(ranking) > count:
            other_consensus_weight_fraction = 0
            for relay in ranking[count:]:
                other_consensus_weight_fraction += relay['consensus_weight_fraction']
                selection_consensus_weight_fraction += relay['consensus_weight_fraction']
            print "%8.4f%% (%d others)" % (other_consensus_weight_fraction * 100.0, len(ranking) - count)
        if selection_consensus_weight_fraction < 0.999:
            print "%8.4f%% (total in selection)" % (selection_consensus_weight_fraction * 100.0)

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
 - relays [COUNT] [FLAGS] [COUNTRIES] [AS_SETS]
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
    if not os.path.exists('details.json'):
        usage()
    if len(sys.argv) == 1:
        usage()
    func = OUTPUTS.get(sys.argv[1], None)
    if not func:
        usage()
    stats = RelayStats()
    getattr(stats, func)(*sys.argv[2:])
