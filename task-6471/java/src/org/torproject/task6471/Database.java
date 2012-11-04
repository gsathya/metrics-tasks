/* Copyright 2012 The Tor Project */
package org.torproject.task6471;

/**
 * Database storing multiple GeoIP or ASN databases and supporting
 * efficient ip-to-country-code or ip-to-AS-number lookups in the most
 * recent of those databases for any given date.
 *
 * A typical query for this GeoIP database is: "to which country code was
 * IPv4 address 1.2.3.4 assigned on date 20120912?"  This query is
 * answered by looking at the entries from the most recent database
 * published on or before 20120912.  If the earliest known database was
 * published after 20120912, the earliest known database is used to
 * resolve the request.
 */
public interface Database {

  /**
   * Load a combined databases file.
   *
   * @param path Path to the combined database file.
   * @return True if loading the combined database file was successful,
   *         false otherwise.
   */
  public boolean loadCombinedDatabases(String path);

  /**
   * Query the database for the country code or AS number assigned to an
   * IPv4 address on a given date.
   *
   * @param address IPv4 address in dotted-quad notation.
   * @param date Assignment date in format yyyymmdd.
   * @return Assigned country code or AS number, or null if no assignment
   * could be found.
   */
  public String lookupIpv4AddressAndDate(String address, String date);
}
