import bisect
import socket
import struct
import datetime

class Range:
  def __init__(self, line):
    parts = line.split(',')
    # TODO Extend to IPv6.  The cool thing in Python is that ints have
    # variable size, so we can use 48 bit keys for IPv4 (32 bit for the
    # IPv4 address and 16 bit for the database date) and 144 bit keys for
    # IPv6.
    self.start_address = Database.address_ston(parts[0])
    self.end_address = Database.address_ston(parts[1])
    self.code = parts[2]
    self.start_date = Database.date_ston(parts[3])
    self.end_date = Database.date_ston(parts[4])
    self.key = Database.create_key(self.start_address, self.start_date)

  def __str__(self):
    return "%s,%s,%s,%s,%s" % \
          (Database.address_ntos(self.start_address),
           Database.address_ntos(self.end_address),
           self.code,
           Database.date_ntos(self.start_date),
           Database.date_ntos(self.end_date))

class Database:
  def __init__(self):
    # TODO Replace with crit-bit tree if performance becomes a problem
    self.data = []
    self.dates = []
    self.keys = []

  @staticmethod
  def address_ston(address_string):
    try:
      address_struct = socket.inet_pton(socket.AF_INET, address_string)
    except socket.error:
        raise ValueError
    return struct.unpack('!I', address_struct)[0]

  @staticmethod
  def address_ntos(address):
    return socket.inet_ntop(socket.AF_INET, struct.pack('!I', address))

  @staticmethod
  def date_ston(date_string):
    date_datetime = datetime.datetime.strptime(date_string, '%Y%m%d')
    return int(date_datetime.strftime('%s')) / 86400

  @staticmethod
  def date_ntos(date):
    return datetime.datetime.fromtimestamp(date * 86400).strftime('%Y%m%d')

  @staticmethod
  def address_kton(key):
    return key >> 16

  @staticmethod
  def date_kton(key):
    return key & 0xffff

  @staticmethod
  def address_ktos(key):
    return Database.address_ntos(Database.address_kton(key))

  @staticmethod
  def date_ktos(key):
    return Database.date_ntos(Database.date_kton(key))

  @staticmethod
  def create_key(address, date):
    return (address << 16) + date

  def load_combined_databases(self, path):
    with open(path) as input_file:
      for line in input_file.readlines():
        line = line.strip()
        if line.startswith('!'):
          self.add_date(line)
          continue
        else:
          self.add_range(line)
    self.data.sort()
    self.keys = [r[0] for r in self.data]

  def add_date(self, line):
    date = line.split("!")[1]
    if date not in self.dates:
      bisect.insort(self.dates, date)

  def add_range(self, line):
    r = Range(line)
    self.data.append((r.key, r))

  def lookup_address_and_date(self, address_string, date_string):
    if len(self.data) == 0:
      return '??'
    dates_pos = max(0, bisect.bisect(self.dates, date_string) - 1)
    address = Database.address_ston(address_string)
    date = Database.date_ston(self.dates[dates_pos])
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
    for line in input_file.readlines():
      line = line.strip()
      parts = line.split(',')
      address_string = parts[0]
      date_string = parts[1]
      expected = parts[2]
      result = db.lookup_address_and_date(address_string, date_string)
      if (expected != result):
        print "! %s -> %s" % (line, result)
        break

