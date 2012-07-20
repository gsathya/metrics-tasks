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
from optparse import OptionParser, OptionGroup

class RelayStats(object):
    def __init__(self):
        self._data = None

    @property
    def data(self):
        if not self._data:
            self._data = json.load(file('details.json'))
        return self._data

    def get_relays(self, countries=[], as_sets=[], exits_only=False, guards_only=False):
        relays = []
        if countries:
            countries = [x.lower() for x in countries]
        for relay in self.data['relays']:
            if not relay['running']:
                continue
            if countries and not relay.get('country', ' ') in countries:
                continue
            if as_sets and not relay.get('as_number', ' ') in as_sets:
                continue
            if exits_only and not relay.get('exit_probability', -1) > 0.0:
                continue
            if guards_only and not relay.get('guard_probability', -1) > 0.0:
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

if '__main__' == __name__:
    parser = OptionParser()
    group = OptionGroup(parser, "Filtering options")
    group.add_option("-a", "--as", dest="ases", action="append",
                     help="select only relays from autonomous system number AS",
                     metavar="AS")
    group.add_option("-c", "--country", action="append",
                     help="select only relays from country with code CC", metavar="CC")
    group.add_option("-e", "--exits-only", action="store_true",
                     help="select only relays suitable for exit position")
    group.add_option("-g", "--guards-only", action="store_true",
                     help="select only relays suitable for guard position")
    parser.add_option_group(group)
    group = OptionGroup(parser, "Grouping options")
    group.add_option("-A", "--by-as", action="store_true", default=False,
                     help="group relays by AS")
    group.add_option("-C", "--by-country", action="store_true", default=False,
                     help="group relays by country")
    parser.add_option_group(group)
    group = OptionGroup(parser, "Display options")
    group.add_option("-t", "--top", type="int", default=10, metavar="NUM",
                     help="display only the top results (default: %default)")
    parser.add_option_group(group)
    (options, args) = parser.parse_args()
    if len(args) > 0:
        parser.error("Did not understand positional argument(s), use options instead.")
    if not os.path.exists('details.json'):
        parser.error("Did not find details.json.  Please download this file using the following command:\ncurl -o details.json 'https://onionoo.torproject.org/details?type=relay&running=true'")

    stats = RelayStats()
    relays = stats.get_relays(countries=options.country,
                              as_sets=options.ases,
                              exits_only=options.exits_only,
                              guards_only=options.guards_only)
    grouped_relays = stats.group_relays(relays,
                     by_country=options.by_country,
                     by_as_number=options.by_as)
    sorted_groups = stats.format_and_sort_groups(grouped_relays,
                    by_country=options.by_country,
                    by_as_number=options.by_as)
    stats.print_groups(sorted_groups, options.top,
                       by_country=options.by_country,
                       by_as_number=options.by_as)
