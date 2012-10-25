package org.torproject.task6471;

public interface DatabaseImporter extends Database {

  /**
   * Import the contents of one or more IP address assignments files
   * published by the Regional Internet Registries.  The file or files
   * are expected to conform to the RIR Statistics Exchange Format.
   * Only IPv4 address ranges are imported, whereas ASN and IPv6 lines are
   * ignored.  Only the country code, start address, and address range
   * length fields are imported.  (TODO Extend to IPv6 and find similar
   * data source for ASN.)
   *
   * A typical entry from a RIR file is:
   *   "ripencc|FR|ipv4|2.0.0.0|1048576|20100712|allocated".
   *
   * It is important to note that all five registry files (AfriNIC, APNIC,
   * ARIN, LACNIC, and RIPE NCC) published on a given day should be
   * imported, or the missing address ranges will be considered as
   * unassigned from that day until the next database publication day.
   * (TODO We could be smarter here by checking that less than five
   * registry files have been imported for the same day, or something.)
   *
   * @param path Path to a stats file or directory.
   * @return True if importing the file or directory was successful,
   *         false otherwise.
   */
  public boolean importRegionalRegistryStatsFileOrDirectory(String path);

  /**
   * Save the combined databases in a format that can later be loaded much
   * more efficiently than importing the original RIR files again.
   *
   * @param path Path to the combined database file.
   * @return True if saving the combined database file was successful,
   *         false otherwise.
   */
  public boolean saveCombinedDatabases(String path);

}
