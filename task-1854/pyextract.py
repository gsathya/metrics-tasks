import os
import sys

def main():
    out_file = open('extracted.csv', 'w')
    last_validafter = ''
    last_lines = []
    for line in open('entropy.csv'):
        parts = line.strip().split(',')
        validafter = parts[0]
        min_adv_bw = int(parts[1])
        relays = parts[2]
        linf = parts[3]
        if last_validafter != validafter:
            last_lines = []
            next_cutoffs = [0, 10000, 20000, 30000, 40000, 50000, 75000,
                    100000, 100000000000000000000]
        while min_adv_bw >= next_cutoffs[0]:
            out_file.write("%s,%d,%s,%s,history\n" % (validafter,
                    next_cutoffs[0], relays, linf, ))
            next_cutoffs.pop(0)
        last_lines.append(line.strip())
        last_validafter = validafter
    for line in last_lines:
        out_file.write(line + ",last\n")

if __name__ == '__main__':
    main()

