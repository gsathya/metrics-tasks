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
    DatabaseImpl database = new DatabaseImpl();
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.repairIndex();
    assertEquals(1, ((DatabaseImpl) database).getNumberOfElements());
    assertEquals(null, database.lookupAddress("2.255.255.255",
        "19920901"));
    assertEquals(null, database.lookupAddress("2.255.255.255",
        "20020901"));
    assertEquals(null, database.lookupAddress("2.255.255.255",
        "20120901"));
    assertEquals(null, database.lookupAddress("2.255.255.255",
        "20220901"));
    assertEquals("us", database.lookupAddress("3.0.0.0", "19920901"));
    assertEquals("us", database.lookupAddress("3.0.0.0", "20020901"));
    assertEquals("us", database.lookupAddress("3.0.0.0", "20120901"));
    assertEquals("us", database.lookupAddress("3.0.0.0", "20220901"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "19920901"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20020901"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20120901"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20220901"));
    assertEquals("us", database.lookupAddress("3.255.255.255",
        "19920901"));
    assertEquals("us", database.lookupAddress("3.255.255.255",
        "20020901"));
    assertEquals("us", database.lookupAddress("3.255.255.255",
        "20120901"));
    assertEquals("us", database.lookupAddress("3.255.255.255",
        "20220901"));
    assertEquals(null, database.lookupAddress("4.0.0.0", "19920901"));
    assertEquals(null, database.lookupAddress("4.0.0.0", "20020901"));
    assertEquals(null, database.lookupAddress("4.0.0.0", "20120901"));
    assertEquals(null, database.lookupAddress("4.0.0.0", "20220901"));
  }

  @Test()
  public void testTwoAdjacentIpRangesSingleDatabase() {
    DatabaseImpl database = new DatabaseImpl();
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.addRange("20120901", "ca", "4.0.0.0", 16777216);
    database.repairIndex();
    assertEquals(2, ((DatabaseImpl) database).getNumberOfElements());
    assertEquals(null, database.lookupAddress("2.255.255.255",
        "20120901"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20120901"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20120901"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20120901"));
    assertEquals("ca", database.lookupAddress("4.127.0.0", "20120901"));
    assertEquals("ca", database.lookupAddress("4.127.0.0", "20120901"));
    assertEquals("ca", database.lookupAddress("4.127.0.0", "20120901"));
    assertEquals(null, database.lookupAddress("5.0.0.0", "20120901"));
  }

  @Test()
  public void testTwoNonAdjacentIpDateRangesSingleDatabase() {
    DatabaseImpl database = new DatabaseImpl();
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.addRange("20120901", "ca", "6.0.0.0", 16777216);
    database.repairIndex();
    assertEquals(2, ((DatabaseImpl) database).getNumberOfElements());
    assertEquals(null, database.lookupAddress("2.255.255.255", "20120901"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20120901"));
    assertEquals(null, database.lookupAddress("4.255.255.255", "20120901"));
    assertEquals("ca", database.lookupAddress("6.127.0.0", "20120901"));
    assertEquals(null, database.lookupAddress("7.0.0.0", "20120901"));
  }

  @Test()
  public void testDuplicateImport() {
    DatabaseImpl database = new DatabaseImpl();
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.repairIndex();
    assertEquals(1, ((DatabaseImpl) database).getNumberOfElements());
    assertEquals(null, database.lookupAddress("2.255.255.255", "20120901"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20120901"));
    assertEquals(null, database.lookupAddress("4.0.0.0", "20120901"));
  }

  @Test()
  public void testDuplicateImportDifferentCountryCode() {
    DatabaseImpl database = new DatabaseImpl();
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.addRange("20120901", "ca", "3.0.0.0", 16777216);
    database.repairIndex();
    assertEquals(1, ((DatabaseImpl) database).getNumberOfElements());
    assertEquals("us", database.lookupAddress("3.127.0.0", "20120901"));
  }

  @Test()
  public void testLeaveIpChangeUnchanged() {
    DatabaseImpl database = new DatabaseImpl();
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.addRange("20121001", "us", "3.0.0.0", 16777216);
    database.repairIndex();
    assertEquals(1, ((DatabaseImpl) database).getNumberOfElements());
    assertEquals("us", database.lookupAddress("3.127.0.0", "20120801"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20120901"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20121001"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20121101"));
  }

  @Test()
  public void testLeaveIpChangeUnchangedReverseOrder() {
    DatabaseImpl database = new DatabaseImpl();
    database.addRange("20121001", "us", "3.0.0.0", 16777216);
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.repairIndex();
    assertEquals(1, ((DatabaseImpl) database).getNumberOfElements());
    assertEquals("us", database.lookupAddress("3.127.0.0", "20120801"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20120901"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20121001"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20121101"));
  }

  @Test()
  public void testMissingIpRange() {
    DatabaseImpl database = new DatabaseImpl();
    database.addRange("20120901", "us", "3.0.0.0", 16777216);
    database.addRange("20121101", "us", "3.0.0.0", 16777216);
    database.addRange("20121001", "us", "6.0.0.0", 16777216);
    database.repairIndex();
    assertEquals(3, ((DatabaseImpl) database).getNumberOfElements());
    assertEquals("us", database.lookupAddress("3.127.0.0", "20120801"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20120901"));
    assertEquals(null, database.lookupAddress("3.127.0.0", "20121001"));
    assertEquals("us", database.lookupAddress("3.127.0.0", "20121101"));
  }
}
