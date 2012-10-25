package org.torproject.task6471;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;

public class DatabasePerformanceExample {
  public static void main(String[] args) {

    System.out.print("Generating test cases... ");
    long startMillis = System.currentTimeMillis();
    List<Long> tests = new ArrayList<Long>();
    SortedMap<Long, String> results = new TreeMap<Long, String>();
    Random rnd = new Random(1L);
    int startDate = DatabaseImpl.convertDateStringToNumber("20071001");
    int endDate = DatabaseImpl.convertDateStringToNumber("20120930");
    /* Skipping Dec 1--3, 2009, because the first available database from
     * December 2009 was published on the 4th, and generating test cases
     * was just too confusing when taking that into account. */
    List<Integer> skipDates = new ArrayList<Integer>();
    skipDates.add(DatabaseImpl.convertDateStringToNumber("20091201"));
    skipDates.add(DatabaseImpl.convertDateStringToNumber("20091202"));
    skipDates.add(DatabaseImpl.convertDateStringToNumber("20091203"));
    for (int i = 0; i < 100000; i++) {
      long testAddress = rnd.nextLong() & ((1L << 32) - 1L);
      int testDate = startDate + rnd.nextInt(endDate - startDate);
      if (skipDates.contains(testDate)) {
        i--;
      } else {
        tests.add((testAddress << 16) + testDate);
      }
    }
    Stack<File> stackedFiles = new Stack<File>();
    stackedFiles.add(new File("../data"));
    SortedSet<File> files = new TreeSet<File>();
    while (!stackedFiles.isEmpty()) {
      File file = stackedFiles.pop();
      if (file.isDirectory()) {
        stackedFiles.addAll(Arrays.asList(file.listFiles()));
      } else if (!file.getName().endsWith(".md5") &&
          !file.getName().endsWith(".md5.gz") &&
          !file.getName().endsWith(".asc") &&
          !file.getName().endsWith(".asc.gz")) {
        files.add(file);
      }
    }
    for (File file : files) {
      String dbMonth = file.getName().substring(
          file.getName().length() - 8);
      dbMonth = dbMonth.substring(0, 6);
      DatabaseImporter temp = new DatabaseImporterImpl();
      temp.importRegionalRegistryStatsFileOrDirectory(
          file.getAbsolutePath());
      for (long test : tests) {
        int testDate = (int) (test & ((1 << 16) - 1));
        String testMonth = DatabaseImpl.convertDateNumberToString(
            testDate).substring(0, 6);
        if (testMonth.equals(dbMonth)) {
          String testAddressString = DatabaseImpl.
              convertAddressNumberToString(test >> 16);
          String testDateString = DatabaseImpl.convertDateNumberToString(
              testDate);
          String countryCode =
              temp.lookupCountryCodeFromIpv4AddressAndDate(
              testAddressString, testDateString);
          if (countryCode != null) {
            results.put(test, countryCode);
          }
        }
      }
    }
    long endMillis = System.currentTimeMillis();
    System.out.println((endMillis - startMillis) + " millis.");

    System.out.print("Importing files... ");
    startMillis = endMillis;
    DatabaseImporter combinedDatabase = new DatabaseImporterImpl();
    combinedDatabase.importRegionalRegistryStatsFileOrDirectory(
        "../data");
    endMillis = System.currentTimeMillis();
    System.out.println((endMillis - startMillis) + " millis.");

    System.out.print("Making test requests... ");
    startMillis = endMillis;
    int failures = 0;
    for (long test : tests) {
      String testAddress = DatabaseImpl.convertAddressNumberToString(
          test >> 16);
      String testDate = DatabaseImpl.convertDateNumberToString(
          (int) (test & ((1 << 16) - 1)));
      String expected = results.get(test);
      String result =
          combinedDatabase.lookupCountryCodeFromIpv4AddressAndDate(
          testAddress, testDate);
      if ((expected == null && result != null) ||
          (expected != null && !expected.equals(result))) {
        //System.out.println("Expected " + expected + " for "
        //    + testAddress + " " + testDate + ", but got " + result);
        failures++;
      }
    }
    endMillis = System.currentTimeMillis();
    System.out.println((endMillis - startMillis) + " millis, " + failures
        + " out of " + tests.size() + " tests failed.");

    System.out.println(combinedDatabase);

    System.out.print("Saving combined databases to disk... ");
    startMillis = endMillis;
    combinedDatabase.saveCombinedDatabases("geoip-2007-10-2012-09.csv");
    endMillis = System.currentTimeMillis();
    System.out.println((endMillis - startMillis) + " millis.");
    startMillis = endMillis;

    System.out.print("Loading combined databases from disk... ");
    startMillis = endMillis;
    Database database = new DatabaseImpl();
    database.loadCombinedDatabases("geoip-2007-10-2012-09.csv");
    endMillis = System.currentTimeMillis();
    System.out.println((endMillis - startMillis) + " millis.");

    System.out.print("Making a second round of test requests... ");
    startMillis = endMillis;
    failures = 0;
    for (long test : tests) {
      String testAddress = DatabaseImpl.convertAddressNumberToString(
          test >> 16);
      String testDate = DatabaseImpl.convertDateNumberToString(
          (int) (test & ((1 << 16) - 1)));
      String expected = results.get(test);
      String result = database.lookupCountryCodeFromIpv4AddressAndDate(
          testAddress, testDate);
      if ((expected == null && result != null) ||
          (expected != null && !expected.equals(result))) {
        //System.out.println("Expected " + expected + " for "
        //    + testAddress + " " + testDate + ", but got " + result);
        failures++;
      }
    }
    endMillis = System.currentTimeMillis();
    System.out.println((endMillis - startMillis) + " millis, " + failures
        + " out of " + tests.size() + " tests failed.");
  }
}
