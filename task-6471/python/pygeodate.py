import bisect
import datetime

class Range:
  def __init__(self, line):
    parts = line.split(',')
    # TODO Extend to IPv6.  The cool thing in Python is that ints have
    # variable size, so we can use 48 bit keys for IPv4 (32 bit for the
    # IPv4 address and 16 bit for the database date) and 144 bit keys for
    # IPv6.
    self.start_address = Database.address_string_to_number(parts[0])
    self.end_address = Database.address_string_to_number(parts[1])
    self.code = parts[2]
    self.start_date = Database.date_string_to_number(parts[3])
    self.end_date = Database.date_string_to_number(parts[4])
    self.key = Database.create_key(self.start_address, self.start_date)

class Database:
  def __init__(self):
    # TODO Replace with crit-bit tree if performance becomes a problem;
    # alternatively, use an OrderedDict which is the equivalent of
    # LinkedHashMap in Java.
    self.data = []
    self.dates = []
    self.keys = []

  @staticmethod
  def address_string_to_number(address_string):
    octets = address_string.split('.')
    return long(''.join(["%02X" % long(octet) for octet in octets]), 16)

  @staticmethod
  def date_string_to_number(date_string):
    date_datetime = datetime.datetime.strptime(date_string, '%Y%m%d')
    # Divide seconds by 86400=24*60*60 for number of days since 19700101
    return int(date_datetime.strftime('%s')) / 86400

  @staticmethod
  def create_key(address, date):
    return (address << 16) + date

  def load_combined_databases(self, path):
    with open(path) as input_file:
      for line in input_file:
        line = line.strip()
        if line.startswith('!'):
          date = line.split("!")[1]
          if date not in self.dates:
            bisect.insort(self.dates, date)
        else:
          r = Range(line)
          self.data.append((r.key, r))
    self.data.sort()
    self.keys = [r[0] for r in self.data]

  def lookup_address_and_date(self, address_string, date_string):
    if len(self.data) == 0:
      return '??'
    dates_pos = max(0, bisect.bisect(self.dates, date_string) - 1)
    address = Database.address_string_to_number(address_string)
    date = Database.date_string_to_number(self.dates[dates_pos])
    key = Database.create_key(address, date)
    pos = bisect.bisect(self.keys, key + 1)
    # Look up address and date by iterating backwards over possibly
    # matching ranges.
    while pos:
      pos = pos - 1
      r = self.data[pos][1]
      # If either the end address or end date of the range we're looking
      # at is smaller than the values we're looking for, we can be sure
      # not to find it anymore.
      if r.end_address < address or r.end_date < date:
        return '??'
      # If the range starts at a later date, skip it and look at the next
      # one.
      if r.start_date > date:
        continue
      # Both address and date ranges match, so return the assigned
      # code.
      return r.code
    # No ranges (left) to look at.  We don't have what we were looking
    # for. */
    return '??';

if __name__ == "__main__":
  db = Database()
  db.load_combined_databases('geoip-2007-10-2012-09.csv')
  with open('test-cases-2007-10-2012-09.csv') as input_file:
    for line in input_file:
      line = line.strip()
      parts = line.split(',')
      address_string = parts[0]
      date_string = parts[1]
      expected = parts[2]
      result = db.lookup_address_and_date(address_string, date_string)
      if (expected != result):
        print "! %s -> %s" % (line, result)
        break

