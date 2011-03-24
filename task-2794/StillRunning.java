import java.io.*;
import java.util.*;
public class StillRunning {
  public static void main(String[] args) throws Exception {

    /* Parse bridge pool assignments. */
    Map<String, String> assignments = new HashMap<String, String>();
    BufferedReader br = new BufferedReader(new FileReader(
        "assignments.csv"));
    String line = br.readLine();
    while ((line = br.readLine()) != null) {
      String[] parts = line.split(",");
      String fingerprint = parts[1];
      String type = parts[2];
      assignments.put(fingerprint, type);
    }
    br.close();

    /* Parse running bridges in first status of the second day in the data
     * set. */
    br = new BufferedReader(new FileReader("statuses.csv"));
    line = br.readLine();
    if (!line.split(",")[15].equals("running")) {
      System.out.println("Column 16 should be 'running'");
      System.exit(1);
    }
    String dayOne = null, lastStatus = null;
    List<String> fingerprints = new ArrayList<String>();
    Map<String, String> addresses = new HashMap<String, String>();
    while ((line = br.readLine()) != null) {
      String[] parts = line.split(",");
      String status = parts[0];
      if (dayOne == null) {
        dayOne = status.substring(0, "yyyy-mm-dd".length());
      } else if (status.startsWith(dayOne)) {
        continue;
      }
      String running = parts[15];
      if (!running.equals("TRUE")) {
        continue;
      }
      if (lastStatus != null && !status.equals(lastStatus)) {
        break;
      }
      lastStatus = status;
      String fingerprint = parts[1];
      fingerprints.add(fingerprint);
      String address = parts[4];
      addresses.put(fingerprint, address);
    }

    /* Parse subsequent statuses and count how often these bridges
     * occur. */
    Map<String, Integer>
        fingerprintAnyCount = new HashMap<String, Integer>(),
        fingerprintSameCount = new HashMap<String, Integer>();
    for (String fingerprint : fingerprints) {
      fingerprintAnyCount.put(fingerprint, 1);
      fingerprintSameCount.put(fingerprint, 1);
    }
    do {
      String[] parts = line.split(",");
      String status = parts[0];
      String running = parts[15];
      if (!running.equals("TRUE")) {
        continue;
      }
      String fingerprint = parts[1];
      if (!fingerprints.contains(fingerprint)) {
        continue;
      }
      fingerprintAnyCount.put(fingerprint,
          fingerprintAnyCount.get(fingerprint) + 1);
      String address = parts[4];
      if (addresses.get(fingerprint).equals(address)) {
        fingerprintSameCount.put(fingerprint,
            fingerprintSameCount.get(fingerprint) + 1);
      }
    } while ((line = br.readLine()) != null);

    /* Create two lists of fingerprints, ordered by the number of
     * occurrences. */
    SortedMap<String, String>
        sortAnyFingerprints = new TreeMap<String, String>(),
        sortSameFingerprints = new TreeMap<String, String>();
    for (Map.Entry<String, Integer> e : fingerprintAnyCount.entrySet()) {
      sortAnyFingerprints.put(String.format("%05d %s", e.getValue(),
          e.getKey()), e.getKey());
    }
    List<String> sortedAnyFingerprints = new ArrayList<String>(
        sortAnyFingerprints.values());
    for (Map.Entry<String, Integer> e : fingerprintSameCount.entrySet()) {
      sortSameFingerprints.put(String.format("%05d %s", e.getValue(),
          e.getKey()), e.getKey());
    }
    List<String> sortedSameFingerprints = new ArrayList<String>(
        sortSameFingerprints.values());

    /* Write bridges of first status to disk. */
    BufferedWriter bw = new BufferedWriter(new FileWriter(
        "still-running-bridges.csv"));
    bw.write("status,anyid,sameid,type,addresschange\n");
    for (String fingerprint : sortedAnyFingerprints) {
      bw.write(lastStatus + ","
          + sortedAnyFingerprints.indexOf(fingerprint) + ","
          + sortedSameFingerprints.indexOf(fingerprint) + ","
          + assignments.get(fingerprint) + ",FALSE\n");
    }

    /* Parse statuses once again and write bridges to disk. */
    br = new BufferedReader(new FileReader("statuses.csv"));
    line = br.readLine();
    while ((line = br.readLine()) != null) {
      String[] parts = line.split(",");
      String status = parts[0];
      if (status.startsWith(dayOne)) {
        continue;
      }
      String running = parts[15];
      if (!running.equals("TRUE")) {
        continue;
      }
      String fingerprint = parts[1];
      if (!fingerprints.contains(fingerprint)) {
        continue;
      }
      String address = parts[4];
      boolean addressChange = !addresses.get(fingerprint).equals(address);
      bw.write(status + ","
          + sortedAnyFingerprints.indexOf(fingerprint) + ","
          + sortedSameFingerprints.indexOf(fingerprint) + ","
          + assignments.get(fingerprint) + ","
          + (addressChange ? "TRUE" : "FALSE") + "\n");
    }
    bw.close();
    br.close();
  }
}

