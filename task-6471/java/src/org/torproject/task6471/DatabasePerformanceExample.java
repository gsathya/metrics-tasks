package org.torproject.task6471;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;

public class DatabasePerformanceExample {
  public static void main(String[] args) throws IOException {

    File testCasesCsvFile = new File("test-cases.csv");
    if (!testCasesCsvFile.exists()) {
      System.out.print("Generating test cases... ");
      long startMillis = System.currentTimeMillis();
      List<Long> tests = new ArrayList<Long>();
      Random rnd = new Random(1L);
      int startDate = DatabaseImpl.convertDateStringToNumber("20071001");
      int endDate = DatabaseImpl.convertDateStringToNumber("20120930");
      /* Skipping Dec 1--3, 2009, because the first available database
       * from December 2009 was published on the 4th, and generating test
       * cases was just too confusing when taking that into account. */
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
      Map<Long, String> results = new HashMap<Long, String>();
      for (long test : tests) {
        results.put(test, "??");
      }
      for (File file : files) {
        String dbMonth = file.getName().substring(
            file.getName().length() - 8);
        dbMonth = dbMonth.substring(0, 6);
        DatabaseImporter temp = new DatabaseImporterImpl();
        temp.importRegionalRegistryStatsFileOrDirectory(
            file.getAbsolutePath());
        for (long test : tests) {
          String testDateString = DatabaseImpl.
              convertKeyToDateString(test);
          String testMonth = testDateString.substring(0, 6);
          if (testMonth.equals(dbMonth)) {
            String testAddressString = DatabaseImpl.
                convertKeyToAddressString(test);
            String code = temp.lookupIpv4AddressAndDate(
                testAddressString, testDateString);
            if (code != null) {
              results.put(test, code);
            }
          }
        }
      }
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          testCasesCsvFile));
      for (Map.Entry<Long, String> e : results.entrySet()) {
        String testAddressString = DatabaseImpl.
            convertKeyToAddressString(e.getKey());
        String testDateString = DatabaseImpl.
            convertKeyToDateString(e.getKey());
        String code = e.getValue();
        bw.write(testAddressString + "," + testDateString + "," + code
            + "\n");
      }
      bw.close();
      long endMillis = System.currentTimeMillis();
      System.out.println((endMillis - startMillis) + " millis.");
    }

    System.out.print("Importing files... ");
    long startMillis = System.currentTimeMillis();
    DatabaseImporter combinedDatabase = new DatabaseImporterImpl();
    combinedDatabase.importRegionalRegistryStatsFileOrDirectory(
        "../data");
    long endMillis = System.currentTimeMillis();
    System.out.println((endMillis - startMillis) + " millis.");

    System.out.print("Making test requests... ");
    startMillis = endMillis;
    BufferedReader br = new BufferedReader(new FileReader(
        testCasesCsvFile));
    String line;
    int tests = 0, failures = 0;
    while ((line = br.readLine()) != null) {
      String[] parts = line.split(",");
      String testAddress = parts[0];
      String testDate = parts[1];
      if (parts.length != 3) {
        System.out.println(line);
        System.exit(1);
      }
      String expected = "??".equals(parts[2]) ? null : parts[2];
      String result =
          combinedDatabase.lookupIpv4AddressAndDate(
          testAddress, testDate);
      tests++;
      if ((expected == null && result != null) ||
          (expected != null && !expected.equals(result))) {
        //System.out.println("Expected " + expected + " for "
        //    + testAddress + " " + testDate + ", but got " + result);
        failures++;
      }
    }
    br.close();
    endMillis = System.currentTimeMillis();
    System.out.println((endMillis - startMillis) + " millis, " + failures
        + " out of " + tests + " tests failed.");

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
    br = new BufferedReader(new FileReader(testCasesCsvFile));
    tests = failures = 0;
    while ((line = br.readLine()) != null) {
      String[] parts = line.split(",");
      String testAddress = parts[0];
      String testDate = parts[1];
      String expected = parts[2].equals("??") ? null : parts[2];
      String result =
          combinedDatabase.lookupIpv4AddressAndDate(
          testAddress, testDate);
      tests++;
      if ((expected == null && result != null) ||
          (expected != null && !expected.equals(result))) {
        //System.out.println("Expected " + expected + " for "
        //    + testAddress + " " + testDate + ", but got " + result);
        failures++;
      }
    }
    br.close();
    endMillis = System.currentTimeMillis();
    System.out.println((endMillis - startMillis) + " millis, " + failures
        + " out of " + tests + " tests failed.");
  }
}
