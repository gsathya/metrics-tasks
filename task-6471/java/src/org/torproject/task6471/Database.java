/* Copyright 2012 The Tor Project */
package org.torproject.task6471;

/**
 * Database storing multiple GeoIP databases and supporting efficient
 * ip-to-country lookups in the most recent of those databases for any
 * given date.
 *
 * A typical query for this GeoIP database is: "to which country was IPv4
 * address 1.2.3.4 assigned on date 20120912?"  This query is answered by
 * looking at the entries from the most recent database published on or
 * before 20120912.  If the earliest known database was published after
 * 20120912, the earliest known database is used to resolve the request.
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
   * Query the database for the country code assigned to an IPv4 address
   * and date.
   *
   * @param address IPv4 address in dotted-quad notation.
   * @param date Assignment date in format yyyymmdd.
   * @return Assigned country code, or null if no assignment could be
   *         found.
   */
  public String lookupCountryCodeFromIpv4AddressAndDate(String address,
      String date);

  /**
   * Resolve the country code to the corresponding country name.
   *
   * @param countryCode Country code to be resolved.
   * @return Country name.
   */
  public String getCountryNameForCountryCode(String countryCode);

  /**
   * Query the database for the country name assigned to an IPv4 address
   * and date.  This is a convenience method that first looks up the
   * country code and then resolves it to the country name.
   *
   * @param address IPv4 address in dotted-quad notation.
   * @param date Assignment date in format yyyymmdd.
   * @return Assigned country name, or null if no assignment could be
   *         found.
   */
  public String lookupCountryNameFromIpv4AddressAndDate(String address,
      String date);
}
