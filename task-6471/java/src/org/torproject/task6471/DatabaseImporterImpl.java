package org.torproject.task6471;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.Stack;
import java.util.TreeMap;

public class DatabaseImporterImpl extends DatabaseImpl
    implements DatabaseImporter {

  /**
   * Parse one or more stats files.
   */
  public boolean importRegionalRegistryStatsFileOrDirectory(String path) {
    boolean allImportsSuccessful = true;
    Stack<File> stackedFiles = new Stack<File>();
    stackedFiles.add(new File(path));
    List<File> allFiles = new ArrayList<File>();
    while (!stackedFiles.isEmpty()) {
      File file = stackedFiles.pop();
      if (file.isDirectory()) {
        stackedFiles.addAll(Arrays.asList(file.listFiles()));
      } else if (file.getName().endsWith(".md5") ||
          file.getName().endsWith(".md5.gz") ||
          file.getName().endsWith(".asc") ||
          file.getName().endsWith(".asc.gz")) {
        System.err.println("Signature and digest files are not supported "
            + "yet: '" + file.getAbsolutePath() + "'.  Skipping.");
        /* TODO Implement checking signatures/digests. */
      } else if (file.getName().endsWith(".gz") ||
          file.getName().endsWith(".bz2")) {
        System.err.println("Parsing compressed files is not supported "
            + "yet: '" + file.getAbsolutePath() + "'.  Skipping.");
        /* TODO Implement parsing compressed files. */
      } else {
        /* TODO Make sure that we're not importing files for a date if we
         * have less than all five of them. */
        allFiles.add(file);
      }
    }
    Collections.sort(allFiles, Collections.reverseOrder());
    for (File file : allFiles) {
      if (!this.importRegionalRegistryStatsFile(file)) {
        allImportsSuccessful = false;
      }
    }
    return allImportsSuccessful;
  }

  /**
   * Simple and not very robust implementation of an RIR stats file
   * parser.
   */
  private boolean importRegionalRegistryStatsFile(File file) {
    try {
      BufferedReader br = new BufferedReader(new FileReader(file));
      String line;
      String databaseFileName = file.getName();
      while ((line = br.readLine()) != null) {
        if (line.startsWith("#") || line.length() == 0) {
          /* Skip comment or empty line. */
          continue;
        }
        String[] parts = line.split("\\|");
        if (parts[0].equals("2")) {
          continue;
        }
        if (parts[1].equals("*")) {
          /* Skip summary line. */
          continue;
        }
        String type = parts[2];
        if (type.equals("asn")) {
          /* TODO Add support for ASNs, but probably not using registry
           * stats files. */
          continue;
        } else if (type.equals("ipv6")) {
          /* TODO Add support for IPv6 addresses. */
          continue;
        }
        String code = parts[1].toLowerCase();
        if (code.length() != 2) {
          /* Don't import illegal range. */
          continue;
        }
        String startAddressString = parts[3];
        long addresses = Long.parseLong(parts[4]);
        this.addRegionalRegistryStatsFileRange(databaseFileName, code,
            startAddressString, addresses);
      }
      br.close();
      this.repairTree();
    } catch (IOException e) {
      return false;
    }
    return true;
  }

  void addRegionalRegistryStatsFileRange(String databaseFileName,
      String code, String startAddressString, long addresses) {
    String databaseDateString =
        databaseFileName.substring(databaseFileName.length() - 8);
    int databaseDate = convertDateStringToNumber(databaseDateString);
    long startAddress = convertAddressStringToNumber(startAddressString);
    long endAddress = startAddress + addresses - 1L;
    this.addDatabase(databaseFileName, databaseDate);
    this.addRange(databaseDate, startAddress, endAddress, code);
  }

  public boolean importGeoLiteCityFileOrDirectory(String path) {
    boolean allImportsSuccessful = true;
    Stack<File> stackedFiles = new Stack<File>();
    stackedFiles.add(new File(path));
    SortedMap<File, Set<File>> filesByDirectory =
        new TreeMap<File, Set<File>>();
    while (!stackedFiles.isEmpty()) {
      File file = stackedFiles.pop();
      if (file.isDirectory()) {
        stackedFiles.addAll(Arrays.asList(file.listFiles()));
      } else if (!file.getName().endsWith(".csv")) {
        System.err.println("Parsing other files than .csv is not "
            + "supported: '" + file.getAbsolutePath() + "'.  Skipping.");
      } else {
        if (!filesByDirectory.containsKey(file.getParentFile())) {
          filesByDirectory.put(file.getParentFile(), new HashSet<File>());
        }
        filesByDirectory.get(file.getParentFile()).add(file);
      }
    }
    for (Set<File> files : filesByDirectory.values()) {
      File blocksFile = null, locationFile = null;
      for (File file : files) {
        if (file.getName().equals("GeoLiteCity-Blocks.csv")) {
          blocksFile = file;
        } else if (file.getName().equals("GeoLiteCity-Location.csv")) {
          locationFile = file;
        }
      }
      if (blocksFile != null && locationFile != null) {
        if (!this.importGeoLiteCityBlocksAndLocationFiles(blocksFile,
            locationFile)) {
          allImportsSuccessful = false;
        }
        files.remove(blocksFile);
        files.remove(locationFile);
        if (!files.isEmpty()) {
          System.err.println("Did not recognize the following files, or "
              + "did not find the blocks/location equivalent:");
          for (File file : files) {
            System.err.println(file.getAbsolutePath());
          }
        }
      }
    }
    return allImportsSuccessful;
  }

  boolean importGeoLiteCityBlocksAndLocationFiles(File blocksFile,
      File locationFile) {
    long lastModifiedMillis = blocksFile.lastModified();
    String databaseFileName = blocksFile.getName() + "+"
        + locationFile.getName();
    int databaseDate = (int) (lastModifiedMillis / 86400000);
    this.addDatabase(databaseFileName, databaseDate);
    try {
      /* Parse location file first and remember country codes for given
       * locations. */
      Map<Integer, String> locations = new HashMap<Integer, String>();
      BufferedReader br = new BufferedReader(new FileReader(
          locationFile));
      String line;
      while ((line = br.readLine()) != null) {
        if (line.startsWith("Copyright") || line.startsWith("locId")) {
          /* Skip copyright notice and column headers. */
          continue;
        }
        String[] parts = line.split(",");
        int location = Integer.parseInt(parts[0]);
        String code = parts[1].replaceAll("\"", "").toLowerCase();
        locations.put(location, code);
      }
      br.close();
      /* Parse blocks file and add ranges to the database. */
      br = new BufferedReader(new FileReader(blocksFile));
      while ((line = br.readLine()) != null) {
        if (!line.startsWith("\"")) {
          /* Skip copyright notice and column headers. */
          continue;
        }
        String[] parts = line.replaceAll("\"", "").split(",");
        long startAddress = Long.parseLong(parts[0]),
            endAddress = Long.parseLong(parts[1]);
        int location = Integer.parseInt(parts[2]);
        if (!locations.containsKey(location)) {
          System.err.println(blocksFile.getAbsolutePath() + " contains "
              + "line '" + line + "' that doesn't match any line in "
              + locationFile.getAbsolutePath() + ".  Aborting.");
          break;
        }
        String code = locations.get(location);
        this.addRange(databaseDate, startAddress, endAddress, code);
      }
      br.close();
    } catch (IOException e) {
      return false;
    }
    return true;
  }

  public boolean importGeoIPASNum2FileOrDirectory(String path) {
    boolean allImportsSuccessful = true;
    Stack<File> stackedFiles = new Stack<File>();
    stackedFiles.add(new File(path));
    List<File> allFiles = new ArrayList<File>();
    while (!stackedFiles.isEmpty()) {
      File file = stackedFiles.pop();
      if (file.isDirectory()) {
        stackedFiles.addAll(Arrays.asList(file.listFiles()));
      } else if (!file.getName().endsWith(".csv")) {
        System.err.println("Parsing other files than .csv is not "
            + "supported: '" + file.getAbsolutePath() + "'.  Skipping.");
      } else {
        allFiles.add(file);
      }
    }
    Collections.sort(allFiles, Collections.reverseOrder());
    for (File file : allFiles) {
      if (!this.importGeoIPASNum2File(file)) {
        allImportsSuccessful = false;
      }
    }
    return allImportsSuccessful;
  }

  private boolean importGeoIPASNum2File(File file) {
    long lastModifiedMillis = file.lastModified();
    String databaseFileName = file.getName();
    int databaseDate = (int) (lastModifiedMillis / 86400000);
    this.addDatabase(databaseFileName, databaseDate);
    try {
      BufferedReader br = new BufferedReader(new FileReader(file));
      String line;
      while ((line = br.readLine()) != null) {
        String[] parts = line.split(",");
        long startAddress = Long.parseLong(parts[0]),
            endAddress = Long.parseLong(parts[1]);
        String code = parts[2].split(" ")[0].replaceAll("\"", "");
        if (!code.startsWith("AS")) {
          /* Don't import illegal range. */
          continue;
        }
        this.addRange(databaseDate, startAddress, endAddress, code);
      }
      br.close();
      this.repairTree();
    } catch (IOException e) {
      return false;
    }
    return true;
  }

  /**
   * Internal counters for import statistics.
   */
  private int rangeImports = 0, rangeImportsKeyLookups = 0;

  /**
   * Add new database date and file name if we didn't know them yet. */
  void addDatabase(String databaseFileName, int databaseDate) {
    if (!this.databaseDates.contains(databaseDate)) {
      this.databaseDates.add(databaseDate);
      this.addedDatabaseDate = databaseDate;
    }
    this.databaseFileNames.add(convertDateNumberToString(databaseDate)
        + "!" + databaseFileName);
  }

  /**
   * Add a single address and date range to the tree, which may require
   * splitting up existing ranges.
   *
   * This method has default visibility and is not specified in the
   * interface, because the caller needs to make sure that repairTree()
   * is called prior to any lookupAddress() calls.  No further checks are
   * performed that the tree is repaired before looking up an address.
   */
  void addRange(int databaseDate, long startAddress, long endAddress,
      String code) {
    this.rangeImports++;

    /* We might have to split existing ranges or the new range before
     * adding it to the tree, and we might have to remove existing ranges.
     * We shouldn't mess with the tree directly while iterating  over it,
     * so let's for now only calculate what changes we want to make. */
    SortedMap<Long, TreeElement> updateElements =
        this.getUpdatesForAddingRange(databaseDate, code, startAddress,
        endAddress);

    /* Apply updates.  Elements with non-null values are added, elements
     * with null values are removed. */
    for (Map.Entry<Long, TreeElement> e : updateElements.entrySet()) {
      if (e.getValue() == null) {
        this.ranges.remove(e.getKey());
      } else {
        this.ranges.put(e.getKey(), e.getValue());
      }
    }
  }

  /**
   * Calculate necessary changes to the tree to add a range.
   */
  private SortedMap<Long, TreeElement> getUpdatesForAddingRange(
      int databaseDate, String code, long startAddress, long endAddress) {

    /* Keep updates in a single tree where non-null values will later be
     * added, possibly replacing existing elements, and null values will
     * be removed from the tree. */
    SortedMap<Long, TreeElement> updateElements =
        new TreeMap<Long, TreeElement>();

    /* Find out previous and next database, so that we can possibly merge
     * ranges. */
    int previousDatabaseDate =
        this.databaseDates.headSet(databaseDate).isEmpty() ? -1 :
        this.databaseDates.headSet(databaseDate).last();
    int nextDatabaseDate =
        this.databaseDates.tailSet(databaseDate + 1).isEmpty() ? -1 :
        this.databaseDates.tailSet(databaseDate + 1).first();

    /* Remember the address boundaries of the next (partial) range to be
     * added. */
    long nextStartAddress = startAddress, nextEndAddress = endAddress;
    int nextFirstDbDate = databaseDate, nextLastDbDate = databaseDate;

    /* Iterate backwards over the existing ranges, starting at the end
     * address of the range to be added and at the last conceivable
     * database publication date. */
    for (Map.Entry<Long, TreeElement> e : this.ranges.tailMap(
        convertAddressAndDateToKey(endAddress + 1L, 0) - 1L).entrySet()) {
      this.rangeImportsKeyLookups++;

      /* Extract everything we need to know from the next existing range
       * we're looking at. */
      long eStartAddress = convertKeyToAddress(e.getKey());
      long eEndAddress = e.getValue().endAddress;
      int eFirstDbDate = convertKeyToDate(e.getKey());
      int eLastDbDate = e.getValue().lastDbDate;
      String eCode = e.getValue().code;

      /* If the next (partial) range starts after the current element
       * ends, add the new range. */
      if (nextStartAddress > eEndAddress &&
          nextEndAddress >= startAddress) {
        updateElements.put(convertAddressAndDateToKey(nextStartAddress,
            nextFirstDbDate), new TreeElement(nextEndAddress,
            nextLastDbDate, code));
        nextEndAddress = nextStartAddress - 1L;
        nextStartAddress = startAddress;
        nextFirstDbDate = databaseDate;
        nextLastDbDate = databaseDate;
      }

      /* If the next (partial) range still ends after the current element
       * ends, add the new range. */
      if (nextEndAddress > eEndAddress &&
          nextEndAddress >= startAddress) {
        updateElements.put(convertAddressAndDateToKey(eEndAddress + 1L,
            databaseDate), new TreeElement(nextEndAddress, databaseDate,
            code));
        nextEndAddress = eEndAddress;
        nextStartAddress = startAddress;
        nextFirstDbDate = databaseDate;
        nextLastDbDate = databaseDate;
      }

      /* If the existing range ends before the new range starts, we don't
       * have to look at any more existing ranges. */
      if (eEndAddress < startAddress) {
        break;
      }

      /* Cut off any address range parts of the existing element that are
       * not contained in the currently added range.  First check whether
       * the existing range ends after the newly added range.  In that
       * case cut off the overlapping part and store it as a new
       * element.*/
      if (eStartAddress <= endAddress && eEndAddress > endAddress) {
        updateElements.put(convertAddressAndDateToKey(endAddress + 1L,
            eFirstDbDate), new TreeElement(eEndAddress, eLastDbDate,
            eCode));
        updateElements.put(convertAddressAndDateToKey(eStartAddress,
            eFirstDbDate), new TreeElement(endAddress, eLastDbDate,
            eCode));
        eEndAddress = endAddress;
      }

      /* Similarly, check whether the existing range starts before the
       * newly added one.  If so, cut off the overlapping part and store
       * it as new element. */
      if (eStartAddress < startAddress && eEndAddress >= startAddress) {
        updateElements.put(convertAddressAndDateToKey(eStartAddress,
            eFirstDbDate), new TreeElement(startAddress - 1L, eLastDbDate,
            eCode));
        updateElements.put(convertAddressAndDateToKey(startAddress,
            eFirstDbDate), new TreeElement(eEndAddress, eLastDbDate,
            eCode));
        eStartAddress = startAddress;
      }

      /* Now we're sure the existing element doesn't exceed the newly
       * added element, address-wise. */
      nextStartAddress = eStartAddress;
      nextEndAddress = eEndAddress;

      /* If the range is already contained and has the same code, mark it
       * as updated.  If it's contained with a different code, ignore the
       * update. */
      if (eFirstDbDate <= databaseDate && eLastDbDate >= databaseDate) {
        if (eCode.equals(code)) {
          nextFirstDbDate = eFirstDbDate;
          nextLastDbDate = eLastDbDate;
        } else {
          updateElements.clear();
          return updateElements;
        }
      }

      /* See if we can merge the new range with the previous or next
       * range.  If so, extend our database range and mark the existing
       * element for deletion. */
      if (eCode.equals(code)) {
        if (eLastDbDate == previousDatabaseDate) {
          nextFirstDbDate = eFirstDbDate;
          updateElements.put(convertAddressAndDateToKey(eStartAddress,
              eFirstDbDate), null);
        } else if (eFirstDbDate == nextDatabaseDate) {
          nextLastDbDate = eLastDbDate;
          updateElements.put(convertAddressAndDateToKey(eStartAddress,
              eFirstDbDate), null);
        }
      }
    }

    /* If there's still some part (or the whole?) address range left to
     * add, add it now. */
    while (nextEndAddress >= startAddress) {
      updateElements.put(convertAddressAndDateToKey(nextStartAddress,
          nextFirstDbDate), new TreeElement(nextEndAddress,
          nextLastDbDate, code));
      nextEndAddress = nextStartAddress - 1L;
      nextStartAddress = startAddress;
      nextFirstDbDate = databaseDate;
      nextLastDbDate = databaseDate;
    }

    /* Return the tree updates that will add the given range. */
    return updateElements;
  }

  /**
   * Internal counter for millis spent on repairing the tree.
   */
  private long treeRepairMillis = 0L;

  /* Newly added database date, or -1 if a database from an already known
   * date was imported. */
  private int addedDatabaseDate = -1;

  /**
   * Repair tree by making sure that any range from a given database date
   * to another is still valid when considering any other database that
   * was imported later.
   *
   * It's okay to split a date range when importing a database from
   * another registry that doesn't contain the given address range.  We'll
   * merge the two date ranges when parsing that registry's file.
   *
   * This method has default visibility and is not specified in the
   * interface, because the caller needs to make sure that repairTree()
   * is called prior to any lookupAddress() calls.  No further checks are
   * performed that the tree is repaired before look up an address.
   *
   * TODO While repairing the tree, we might also optimize it by merging
   * adjacent address ranges with the same database date ranges.
   */
  void repairTree() {
    if (this.addedDatabaseDate < 0) {
      return;
    }
    long startedRepairingTree = System.currentTimeMillis();
    SortedMap<Long, TreeElement> updateElements =
        new TreeMap<Long, TreeElement>();
    for (Map.Entry<Long, TreeElement> e : this.ranges.entrySet()) {
      if (e.getValue().modifiedInLastImport) {
        e.getValue().modifiedInLastImport = false;
      } else {
        int eFirstDbDate = convertKeyToDate(e.getKey());
        int eLastDbDate = e.getValue().lastDbDate;
        long eStartAddress = convertKeyToAddress(e.getKey());
        long eEndAddress = e.getValue().endAddress;
        String eCode = e.getValue().code;
        int start = eFirstDbDate, end = eFirstDbDate;
        for (int cur : this.databaseDates.tailSet(eFirstDbDate)) {
          if (cur > eLastDbDate) {
            break;
          }
          if (cur == addedDatabaseDate) {
            if (start >= 0 && end >= 0) {
              updateElements.put(convertAddressAndDateToKey(eStartAddress,
                  start), new TreeElement(eEndAddress, end, eCode));
              start = end = -1;
            }
          } else if (start < 0) {
            start = end = cur;
          } else {
            end = cur;
          }
        }
        if (start >= 0 && end >= 0) {
          updateElements.put(convertAddressAndDateToKey(eStartAddress,
              start), new TreeElement(eEndAddress, end, eCode));
        }
      }
    }
    for (Map.Entry<Long, TreeElement> e : updateElements.entrySet()) {
      this.ranges.put(e.getKey(), e.getValue());
    }
    this.addedDatabaseDate = -1;
    this.treeRepairMillis += (System.currentTimeMillis()
        - startedRepairingTree);
  }

  /**
   * Return number of contained ranges.
   */
  int getNumberOfElements() {
    return this.ranges.size();
  }

  /**
   * Save the combined databases to disk.
   */
  public boolean saveCombinedDatabases(String path) {
    try {

      /* Create parent directories if necessary. */
      File file = new File(path);
      if (file.getParentFile() != null) {
        file.getParentFile().mkdirs();
      }

      /* Start with writing all contained database file names to the file
       * header. */
      BufferedWriter bw = new BufferedWriter(new FileWriter(file));
      for (String databaseFileName : this.databaseFileNames) {
        bw.write("!" + databaseFileName + "\n");
      }

      /* Next write all database ranges in the same order as they are
       * currently contained in memory.  The only information we can drop
       * is the last known database index of each range, because we assume
       * the tree is already in repaired state. */
      for (Map.Entry<Long, TreeElement> e : this.ranges.entrySet()) {
        bw.write(String.format("%s,%s,%s,%s,%s%n",
            convertKeyToAddressString(e.getKey()),
            convertAddressNumberToString(e.getValue().endAddress),
            e.getValue().code,
            convertKeyToDateString(e.getKey()),
            convertDateNumberToString(e.getValue().lastDbDate)));
      }
      bw.close();
    } catch (IOException e) {
      return false;
    }
    return true;
  }

  /* Return a nicely formatted string summarizing database contents and
   * usage statistics. */
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Tree contains %d databases and %d combined "
        + "address ranges.\n"
        + "Performed %d address range imports requiring %d lookups.\n"
        + "Performed %d address lookups requiring %d lookups.\n"
        + "Spent %d millis on repairing tree.\n"
        + "First 10 entries, in reverse order, are:",
        this.databaseDates.size(), this.ranges.size(), this.rangeImports,
        this.rangeImportsKeyLookups, this.addressLookups,
        this.addressLookupsKeyLookups, this.treeRepairMillis));
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
}
