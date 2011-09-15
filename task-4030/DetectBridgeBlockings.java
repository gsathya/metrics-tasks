import java.io.*;
import java.text.*;
import java.util.*;

/**
 * Processes previously extracted bridge usage statistics to detect
 * possible bridge blockings in a given country.
 *
 * Before running this tool, make sure you download (sanitized) bridge
 * descriptors from https://metrics.torproject.org/data.html#bridgedesc,
 * extract them to a local directory, and run the following command:
 *
 * $ grep -Rl "^bridge-ips [a-z]" bridge-descriptors-* | \
 *   xargs -I {} grep -E "^extra-info|^bridge" {} > bridge-stats
 **/

public class DetectBridgeBlockings {
  public static void main(String[] args) throws Exception {

    /* Run the analysis for the country with this country code. */
    final String COUNTRY = "cn";

    /* Consider bridges with at most this many users as potentially
     * blocked. */
    final int BLOCKED_THRESHOLD = 36;

    /* Consider bridges blocked that report no more than BLOCKED_THRESHOLD
     * users for at least this number of days after having reported more
     * than BLOCKED_THRESHOLD users at least once before. */
    final int BLOCKED_DAYS = 7;

    /* Only include bridges in the results that have reported at least
     * this number of statistics. */
    final int MIN_REPORTS = 30;

    /* Begin of analysis interval (inclusive). */
    final String INTERVAL_BEGIN = "2010-01-01";
    
    /* End of analysis interval (inclusive). */
    final String INTERVAL_END = "2010-07-31";

    /* Check whether we have an input file. */
    File inputFile = new File("bridge-stats");
    if (!inputFile.exists()) {
      System.out.println("File " + inputFile + " not found.  Please see "
          + "the README.");
      System.exit(1);
    }

    /* Read the relevant bridge statistics parts into memory. */
    BufferedReader br = new BufferedReader(new FileReader(inputFile));
    String line, fingerprint = null, date = null;
    SortedMap<String, SortedMap<String, Integer>> usersPerBridgeAndDay =
        new TreeMap<String, SortedMap<String, Integer>>();
    while ((line = br.readLine()) != null) {
      if (line.startsWith("extra-info ")) {
        fingerprint = line.split(" ")[2];
      } else if (line.startsWith("bridge-stats-end ")) {
        date = line.substring("bridge-stats-end ".length(),
            "bridge-stats-end YYYY-MM-DD".length());
      } else if (line.startsWith("bridge-ips ")) {
        if (date.compareTo(INTERVAL_BEGIN) < 0 ||
            date.compareTo(INTERVAL_END) > 0) {
          continue;
        }
        int ipsFromCountry = 0;
        for (String part : line.split(" ")[1].split(",")) {
          String country = part.split("=")[0];
          if (country.equals(COUNTRY)) {
            ipsFromCountry = Integer.parseInt(part.split("=")[1]);
            break;
          }
        }
        if (!usersPerBridgeAndDay.containsKey(fingerprint)) {
          usersPerBridgeAndDay.put(fingerprint,
              new TreeMap<String, Integer>());
        }
        usersPerBridgeAndDay.get(fingerprint).put(date, ipsFromCountry);
      }
    }
    br.close();

    /* Write processed statistics for COUNTRY to disk including a column
     * for suspected blockings. */
    SimpleDateFormat dateFormat = new SimpleDateFormat(
        "yyyy-MM-dd");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    BufferedWriter bw = new BufferedWriter(new FileWriter(
        "bridge-blockings.csv"));
    bw.write("fingerprint,date,ips,blocked\n");
    for (Map.Entry<String, SortedMap<String, Integer>> e :
        usersPerBridgeAndDay.entrySet()) {
      fingerprint = e.getKey();
      SortedMap<String, Integer> usersPerDay = e.getValue();
      if (usersPerDay.size() < MIN_REPORTS) {
        continue;
      }
      long lastDateMillis = 0L;
      String lastExceededBlockedThreshold = null;
      SortedSet<String> datesNotExceedingBlockedThreshold =
          new TreeSet<String>();
      for (Map.Entry<String, Integer> e1 : usersPerDay.entrySet()) {
        date = e1.getKey();
        long dateMillis = dateFormat.parse(date).getTime();
        while (lastDateMillis > 0L &&
            dateMillis > lastDateMillis + 24L * 60L * 60L * 1000L) {
          lastDateMillis += 24L * 60L * 60L * 1000L;
          bw.write(fingerprint + "," + dateFormat.format(lastDateMillis)
              + ",NA,NA\n");
        }
        lastDateMillis = dateMillis;
        int ips = e1.getValue();
        String bwLinePart = fingerprint + "," + date + "," + ips;
        if (ips > BLOCKED_THRESHOLD) {
          String blocked = "FALSE";
          if (lastExceededBlockedThreshold != null &&
              dateFormat.parse(date).getTime() -
              dateFormat.parse(lastExceededBlockedThreshold).getTime()
              > BLOCKED_DAYS * 24L * 60L * 60L * 1000L) {
            blocked = "TRUE";
          }
          for (String buffered : datesNotExceedingBlockedThreshold) {
            bw.write(buffered + "," + blocked + "\n");
          }
          datesNotExceedingBlockedThreshold.clear();
          bw.write(bwLinePart + ",FALSE\n");
          lastExceededBlockedThreshold = date;
        } else {
          datesNotExceedingBlockedThreshold.add(bwLinePart);
        }
      }
      for (String buffered : datesNotExceedingBlockedThreshold) {
        bw.write(buffered + ",TRUE\n");
      }
      datesNotExceedingBlockedThreshold.clear();
    }
    bw.close();
  }
}

