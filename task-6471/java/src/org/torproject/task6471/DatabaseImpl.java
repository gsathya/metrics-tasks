/* Copyright 2012 The Tor Project */
package org.torproject.task6471;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Implementation of database holding multiple GeoIP or ASN databases with
 * special focus on lookup performance, import performance, and memory
 * consumption (in that order).
 *
 * This implementation uses a single tree to store IP address and date
 * ranges.  Each tree element is stored under a long integer consisting of
 * the start IPv4 address in the higher bits and the first database
 * publication date containing that range in the lower bits.  The tree
 * element itself contains the end IPv4 address, last database publication
 * date, and country code or AS number.
 *
 * Lookups for a given address and random date only require iterating
 * backwards over ranges with start address smaller than or equaling the
 * requested address and can terminate as soon as a range with a smaller
 * end address is encountered.
 *
 * As a requirement for lookups to work correctly, address ranges may
 * never overlap for different assignment periods.  This makes the import
 * process somewhat more complex and time-consuming, which is a trade-off
 * for faster lookup performance.
 */
public class DatabaseImpl implements Database {

  /**
   * Tree element containing an end IPv4 address, last database date,
   * last database index, and country code or AS number.  Start IPv4
   * address and first database date are encoded in the key under which
   * the element is stored.
   */
  protected static class TreeElement {
    protected long endAddress;
    protected int lastDbDate;
    protected String code;
    protected boolean modifiedInLastImport;
    protected TreeElement(long endAddress, int lastDbDate, String code) {
      this.endAddress = endAddress;
      this.lastDbDate = lastDbDate;
      this.code = code;
      this.modifiedInLastImport = true;
    }
  }

  /**
   * IPv4 address and date ranges, ordered backwards by start address and
   * first database date.
   */
  protected SortedMap<Long, TreeElement> ranges =
      new TreeMap<Long, TreeElement>(Collections.reverseOrder());

  /**
   * Database dates ordered from oldest to youngest.
   */
  protected SortedSet<Integer> databaseDates = new TreeSet<Integer>();

  /**
   * Database dates and file names, formatted as yyyymmdd!filename.
   */
  protected SortedSet<String> databaseFileNames = new TreeSet<String>();

  /**
   * Internal counters for lookup statistics.
   */
  protected int addressLookups = 0, addressLookupsKeyLookups = 0;

  /**
   * Look up address and date by iterating backwards over possibly
   * matching ranges.
   */
  public String lookupIpv4AddressAndDate(
      String addressString, String dateString) {
    this.addressLookups++;

    long address = convertAddressStringToNumber(addressString);
    int date = convertDateStringToNumber(dateString);

    if (this.databaseDates.isEmpty()) {
      return null;
    }

    /* Look up which database we want. */
    int databaseDate = this.databaseDates.headSet(date + 1).isEmpty() ?
        this.databaseDates.first() :
        this.databaseDates.headSet(date + 1).last();

    /* Iterate backwards over the existing ranges, starting at the last
     * possible date of the address to be found. */
    for (Map.Entry<Long, TreeElement> e : this.ranges.tailMap(
        convertAddressAndDateToKey(address + 1L, 0) - 1L).entrySet()) {
      this.addressLookupsKeyLookups++;

      /* If either the end address or end date of the range we're looking
       * at is smaller than the values we're looking for, we can be sure
       * not to find it anymore. */
      if (e.getValue().endAddress < address ||
          e.getValue().lastDbDate < databaseDate) {
        return null;
      }

      /* If the range starts at a later date, skip it and look at the next
       * one. */
      long startDate = convertKeyToDate(e.getKey());
      if (startDate > databaseDate) {
        continue;
      }

      /* Both address and date ranges match, so return the assigned
       * code. */
      return e.getValue().code;
    }

    /* No ranges (left) to look at.  We don't have what we were looking
     * for. */
    return null;
  }

  /* Helper: convert a dotted-quad formatted address string to its
   * corresponding long integer number. */
  static long convertAddressStringToNumber(String addressString) {
    long address = 0;
    String[] addressParts = addressString.split("\\.");
    for (int i = 0; i < 4; i++) {
      address += Long.parseLong(addressParts[i]) << ((3 - i) * 8);
    }
    return address;
  }

  /* Helper: convert a long integer address number to its corresponding
   * dotted-quad formatted string. */
  static String convertAddressNumberToString(long address) {
    return "" + (address / 256 / 256 / 256) + "."
        + ((address / 256 / 256) % 256) + "."
        + ((address / 256) % 256) + "." + (address % 256);
  }

  /* Helper: date format parser/formatter. */
  private static SimpleDateFormat dateFormat;
  static {
    dateFormat = new SimpleDateFormat("yyyyMMdd");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  /* Helper: convert date string in format yyyymmdd to integer containing
   * days passed since 1970-01-01. */
  static int convertDateStringToNumber(String dateString)
      throws IllegalArgumentException {
    try {
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      return (int) (dateFormat.parse(dateString).getTime() / 86400000);
    } catch (ParseException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /* Helper: convert integer containing days passed since 1970-01-01 to
   * date string in format yyyymmdd. */
  static String convertDateNumberToString(int date) {
    return dateFormat.format(((long) date) * 86400000);
  }

  /* Helper: convert the address part of a key to the long integer
   * number format. */
  static long convertKeyToAddress(long key) {
    return key >> 16;
  }

  /* Helper: convert the date part of a key to an integer containing days
   * passed since 1970-01-1. */
  static int convertKeyToDate(long key) {
    return (int) (key & ((1L << 16) - 1L));
  }

  /* Helper: convert the address part of a key to the dotted-quad string
   * format. */
  static String convertKeyToAddressString(long key) {
    return convertAddressNumberToString(convertKeyToAddress(key));
  }

  /* Helper: convert date part of a key to string in format yyyymmdd */
  static String convertKeyToDateString(long key) {
    return convertDateNumberToString(convertKeyToDate(key));
  }

  /* Helper: convert an address in long integer number format and a date
   * in integer format to a key. */
  static long convertAddressAndDateToKey(long address, int date) {
    return (address << 16) + date;
  }

  /* Return a nicely formatted string summarizing database contents and
   * usage statistics. */
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Tree contains %d databases and %d combined "
        + "address ranges.\n"
        + "Performed %d address lookups requiring %d lookups.\n"
        + "First 10 entries, in reverse order, are:",
        this.databaseDates.size(), this.ranges.size(),
        this.addressLookups, this.addressLookupsKeyLookups));
    int entries = 10;
    for (Map.Entry<Long, TreeElement> e : this.ranges.entrySet()) {
      sb.append(String.format("%n  %s %s %s %s %s",
          convertKeyToAddressString(e.getKey()),
          convertAddressNumberToString(e.getValue().endAddress),
          e.getValue().code,
          convertKeyToDateString(e.getKey()),
          convertDateNumberToString(e.getValue().lastDbDate)));
      if (--entries <= 0) {
        break;
      }
    }
    return sb.toString();
  }

  /**
   * Load previously saved combined databases from disk.  This code is not
   * at all robust against external changes of the combined database file.
   */
  public boolean loadCombinedDatabases(String path) {
    try {
      File file = new File(path);
      BufferedReader br = new BufferedReader(new FileReader(file));
      String line;
      while ((line = br.readLine()) != null) {
        if (line.startsWith("!")) {

          /* First read file header containing database dates. */
          String[] parts = line.substring(1).split("!");
          this.databaseFileNames.add(line.substring(1));
          String databaseDateString = parts[0];
          int dbDate = convertDateStringToNumber(databaseDateString);
          this.databaseDates.add(dbDate);
        } else {

          /* Next read all ranges.  Set last known database index for each
           * range to the last database we read from the header, because
           * the tree will immediately be in repaired state. */
          String[] parts = line.split(",");
          long startAddress = convertAddressStringToNumber(parts[0]);
          long endAddress = convertAddressStringToNumber(parts[1]);
          String code = parts[2];
          int firstDbDate = convertDateStringToNumber(parts[3]);
          int lastDbDate = convertDateStringToNumber(parts[4]);
          this.ranges.put(convertAddressAndDateToKey(startAddress,
              firstDbDate), new TreeElement(endAddress, lastDbDate,
              code));
        }
      }
      br.close();
    } catch (IOException e) {
      return false;
    }
    return true;
  }
}
