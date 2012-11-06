package org.torproject.task6471;

public class ConvertExample {
  public static void main(String[] args) {
    System.out.print("Importing ASN database files... ");
    long startMillis = System.currentTimeMillis();
    DatabaseImporter combinedDatabase = new DatabaseImporterImpl();
    combinedDatabase.importGeoIPASNum2FileOrDirectory("../asn");
    long endMillis = System.currentTimeMillis();
    System.out.println((endMillis - startMillis) + " millis.");

    System.out.print("Saving combined ASN database to disk... ");
    startMillis = endMillis;
    combinedDatabase.saveCombinedDatabases("asn-2005-09-2012-11.csv");
    endMillis = System.currentTimeMillis();
    System.out.println((endMillis - startMillis) + " millis.");
    startMillis = endMillis;

    System.out.print("Importing city database files... ");
    startMillis = System.currentTimeMillis();
    combinedDatabase = new DatabaseImporterImpl();
    combinedDatabase.importGeoLiteCityFileOrDirectory("../city");
    endMillis = System.currentTimeMillis();
    System.out.println((endMillis - startMillis) + " millis.");

    System.out.print("Saving combined city database to disk... ");
    startMillis = endMillis;
    combinedDatabase.saveCombinedDatabases("city-2009-06-2012-10.csv");
    endMillis = System.currentTimeMillis();
    System.out.println((endMillis - startMillis) + " millis.");
    startMillis = endMillis;

  }
}
