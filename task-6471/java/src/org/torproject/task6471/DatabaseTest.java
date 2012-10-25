/* Copyright 2012 The Tor Project */
package org.torproject.task6471;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Test the multi-GeoIP database implementation.
 */
public class DatabaseTest {

  @Test()
  public void testSingleIpRangeSingleDatebase() {
    DatabaseImporterImpl database = new DatabaseImporterImpl();
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.repairTree();
    assertEquals(1, database.getNumberOfElements());
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "2.255.255.255", "19920901"));
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "2.255.255.255", "20020901"));
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "2.255.255.255", "20120901"));
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "2.255.255.255", "20220901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.0.0.0", "19920901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.0.0.0", "20020901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.0.0.0", "20120901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.0.0.0", "20220901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "19920901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20020901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20120901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20220901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.255.255.255", "19920901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.255.255.255", "20020901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.255.255.255", "20120901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.255.255.255", "20220901"));
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "4.0.0.0", "19920901"));
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "4.0.0.0", "20020901"));
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "4.0.0.0", "20120901"));
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "4.0.0.0", "20220901"));
  }

  @Test()
  public void testTwoAdjacentIpRangesSingleDatabase() {
    DatabaseImporterImpl database = new DatabaseImporterImpl();
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.addRange("20120901", "ca", "4.0.0.0", 16777216);
    database.repairTree();
    assertEquals(2, database.getNumberOfElements());
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "2.255.255.255", "20120901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20120901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20120901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20120901"));
    assertEquals("ca", database.lookupCountryCodeFromIpv4AddressAndDate(
        "4.127.0.0", "20120901"));
    assertEquals("ca", database.lookupCountryCodeFromIpv4AddressAndDate(
        "4.127.0.0", "20120901"));
    assertEquals("ca", database.lookupCountryCodeFromIpv4AddressAndDate(
        "4.127.0.0", "20120901"));
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "5.0.0.0", "20120901"));
  }

  @Test()
  public void testTwoNonAdjacentIpDateRangesSingleDatabase() {
    DatabaseImporterImpl database = new DatabaseImporterImpl();
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.addRange("20120901", "ca", "6.0.0.0", 16777216);
    database.repairTree();
    assertEquals(2, database.getNumberOfElements());
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "2.255.255.255", "20120901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20120901"));
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "4.255.255.255", "20120901"));
    assertEquals("ca", database.lookupCountryCodeFromIpv4AddressAndDate(
        "6.127.0.0", "20120901"));
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "7.0.0.0", "20120901"));
  }

  @Test()
  public void testDuplicateImport() {
    DatabaseImporterImpl database = new DatabaseImporterImpl();
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.repairTree();
    assertEquals(1, database.getNumberOfElements());
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "2.255.255.255", "20120901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20120901"));
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "4.0.0.0", "20120901"));
  }

  @Test()
  public void testDuplicateImportDifferentCountryCode() {
    DatabaseImporterImpl database = new DatabaseImporterImpl();
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.addRange("20120901", "ca", "3.0.0.0", 16777216);
    database.repairTree();
    assertEquals(1, database.getNumberOfElements());
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20120901"));
  }

  @Test()
  public void testLeaveIpChangeUnchanged() {
    DatabaseImporterImpl database = new DatabaseImporterImpl();
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.repairTree();
    database.addRange("20121001", "us", "3.0.0.0", 16777216);
    database.repairTree();
    assertEquals(1, database.getNumberOfElements());
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20120801"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20120901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20121001"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20121101"));
  }

  @Test()
  public void testLeaveIpChangeUnchangedReverseOrder() {
    DatabaseImporterImpl database = new DatabaseImporterImpl();
    database.addRange("20121001", "us", "3.0.0.0", 16777216);
    database.repairTree();
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.repairTree();
    assertEquals(1, database.getNumberOfElements());
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20120801"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20120901"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20121001"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20121101"));
  }

  @Test()
  public void testMissingIpRange() {
    DatabaseImporterImpl database = new DatabaseImporterImpl();
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.repairTree();
    database.addRange("20121101", "us", "3.0.0.0", 16777216);
    database.repairTree();
    database.addRange("20121001", "us", "6.0.0.0", 16777216);
    database.repairTree();
    assertEquals(3, database.getNumberOfElements());
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20120801"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20120901"));
    assertEquals(null, database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20121001"));
    assertEquals("us", database.lookupCountryCodeFromIpv4AddressAndDate(
        "3.127.0.0", "20121101"));
  }
}
