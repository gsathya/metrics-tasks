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

    def group_relays(self, relays, by_country=False, by_as_number=False):
        grouped_relays = {}
        for relay in relays:
            if by_country and by_as_number:
                key = (relay.get('country', None), relay.get('as_number', None))
            elif by_country:
                key = relay.get('country', None)
            elif by_as_number:
                key = relay.get('as_number', None)
            else:
                key = relay.get('fingerprint')
            if key not in grouped_relays:
                grouped_relays[key] = []
            grouped_relays[key].append(relay)
        return grouped_relays

    def format_and_sort_groups(self, grouped_relays, by_country=False, by_as_number=False):
        formatted_groups = {}
        for group in grouped_relays.viewvalues():
            group_weights = (0, 0, 0, 0, 0)
            relays_in_group = 0
            for relay in group:
                weights = (relay.get('consensus_weight_fraction', 0),
                           relay.get('advertised_bandwidth_fraction', 0),
                           relay.get('guard_probability', 0),
                           relay.get('middle_probability', 0),
                           relay.get('exit_probability', 0))
                group_weights = tuple(sum(x) for x in zip(group_weights, weights))
                nickname = relay['nickname']
                fingerprint = relay['fingerprint']
                exit = 'Exit' if 'Exit' in set(relay['flags']) else ''
                guard = 'Guard' if 'Guard' in set(relay['flags']) else ''
                country = relay.get('country', '')
                as_number = relay.get('as_number', '')
                as_name = relay.get('as_name', '')
                relays_in_group += 1
            if by_country or by_as_number:
                nickname = "(%d relays)" % relays_in_group
                fingerprint = "*"
                exit = "*"
                guard = "*"
            if by_country and not by_as_number:
                as_number = "*"
                as_name = "*"
            if by_as_number and not by_country:
                country = "*"
            formatted_group = "%8.4f%% %8.4f%% %8.4f%% %8.4f%% %8.4f%% %-19s %-40s %-4s %-5s %-2s %-9s %s" % (
                              group_weights[0] * 100.0,
                              group_weights[1] * 100.0,
                              group_weights[2] * 100.0,
                              group_weights[3] * 100.0,
                              group_weights[4] * 100.0,
                              nickname, fingerprint,
                              exit, guard, country, as_number, as_name)
            formatted_groups[formatted_group] = group_weights
        sorted_groups = sorted(formatted_groups.iteritems(), key=operator.itemgetter(1))
        sorted_groups.reverse()
        return sorted_groups

    def print_groups(self, sorted_groups, count=10, by_country=False, by_as_number=False):
        print "       CW    adv_bw   P_guard  P_middle    P_exit Nickname            Fingerprint                              Exit Guard CC AS_num    AS_name"

        for formatted_group, weight in sorted_groups[:count]:
            print formatted_group
        if len(sorted_groups) > count:
            if by_country and by_as_number:
                type = "countries and ASes"
            elif by_country:
                type = "countries"
            elif by_as_number:
                type = "ASes"
            else:
                type = "relays"
            other_weights = (0, 0, 0, 0, 0)
            for _, weights in sorted_groups[count:]:
                other_weights = tuple(sum(x) for x in zip(other_weights, weights))
            print "%8.4f%% %8.4f%% %8.4f%% %8.4f%% %8.4f%% (%d other %s)" % (
                  other_weights[0] * 100.0, other_weights[1] * 100.0,
                  other_weights[2] * 100.0, other_weights[3] * 100.0,
                  other_weights[4] * 100.0, len(sorted_groups) - count, type)
        selection_weights = (0, 0, 0, 0, 0)
        for _, weights in sorted_groups:
            selection_weights = tuple(sum(x) for x in zip(selection_weights, weights))
        if selection_weights[0] < 0.999:
            print "%8.4f%% %8.4f%% %8.4f%% %8.4f%% %8.4f%% (total in selection)" % (
                  selection_weights[0] * 100.0, selection_weights[1] * 100.0,
                  selection_weights[2] * 100.0, selection_weights[3] * 100.0,
                  selection_weights[4] * 100.0)

    def output_countries(self, count='10', flags=''):
        count = int(count)
        flags = flags.split()
        relays = self.get_relays(flags)
        grouped_relays = self.group_relays(relays, by_country=True)
        sorted_groups = self.format_and_sort_groups(grouped_relays, by_country=True)
        self.print_groups(sorted_groups, count, by_country=True)

    def output_as_sets(self, count='10', flags='', countries=''):
        count = int(count)
        flags = flags.split()
        relays = self.get_relays(flags, countries)
        grouped_relays = self.group_relays(relays, by_as_number=True)
        sorted_groups = self.format_and_sort_groups(grouped_relays, by_as_number=True)
        self.print_groups(sorted_groups, count, by_as_number=True)

    def output_relays(self, count='10', flags='', countries='', as_sets=''):
        count = int(count)
        flags = flags.split()
        as_sets = as_sets.split()
        relays = self.get_relays(flags, countries, as_sets)
        grouped_relays = self.group_relays(relays)
        sorted_groups = self.format_and_sort_groups(grouped_relays)
        self.print_groups(sorted_groups, count)

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
