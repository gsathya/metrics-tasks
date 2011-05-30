/**
 * Simulate variation of weighted fractional uptime on Guard relays.  In
 * a first step, parse network status consensus in consensuses/ from last
 * to first, calculate future weighted fractional uptimes for all relays,
 * and write them to disk as one file per network status in
 * fwfu/$filename.  (Skip this step if there is already a fwfu/
 * directory.)  In a second step, parse the network statuse consensus
 * again, but this time from first to last, calculate past weighted
 * fractional uptimes for all relays, form relay subsets based on minimal
 * WFU, look up what the mean future WFU would be for a subset, and write
 * results to wfu-sim.csv to disk. */
import java.io.*;
import java.text.*;
import java.util.*;
public class SimulateWeightedFractionalUptime {
  public static void main(String[] args) throws Exception {

    /* Measure how long this execution takes. */
    long started = System.currentTimeMillis();

    /* Decide whether we need to do the reverse run, or if we can use
     * previous results. */
    if (!new File("fwfu").exists()) {

      /* Scan existing consensus files and sort them in reverse order. */
      SortedSet<File> allConsensuses =
          new TreeSet<File>(Collections.reverseOrder());
      Stack<File> files = new Stack<File>();
      files.add(new File("consensuses"));
      while (!files.isEmpty()) {
        File file = files.pop();
        if (file.isDirectory()) {
          files.addAll(Arrays.asList(file.listFiles()));
        } else {
          if (file.getName().endsWith("-consensus")) {
            allConsensuses.add(file);
          }
        }
      }

      /* For each relay as identified by its base-64 encoded fingerprint,
       * track weighted uptime and total weighted time in a long[2]. */
      SortedMap<String, long[]> knownRelays =
          new TreeMap<String, long[]>();

      /* Parse all consensuses in reverse order. */
      SimpleDateFormat formatter = new SimpleDateFormat(
          "yyyy-MM-dd-HH-mm-ss");
      formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
      long nextWeightingInterval = formatter.parse(allConsensuses.first().
          getName().substring(0, "yyyy-MM-dd-HH-mm-ss".length())).
          getTime() / (12L * 60L * 60L * 1000L);
      for (File consensus : allConsensuses) {

        /* Every 12 hours, weight both uptime and total time of all known
         * relays with 0.95 (or 19/20 since these are long integers) and
         * remove all with a weighted fractional uptime below 1/10000. */
        long validAfter = formatter.parse(consensus.getName().substring(0,
            "yyyy-MM-dd-HH-mm-ss".length())).getTime();
        long weightingInterval = validAfter / (12L * 60L * 60L * 1000L);
        while (weightingInterval < nextWeightingInterval) {
          Set<String> relaysToRemove = new HashSet<String>();
          for (Map.Entry<String, long[]> e : knownRelays.entrySet()) {
            long[] w = e.getValue();
            w[0] *= 19L;
            w[0] /= 20L;
            w[1] *= 19L;
            w[1] /= 20L;
            if (((10000L * w[0]) / w[1]) < 1L) {
              relaysToRemove.add(e.getKey());
            }
          }
          for (String fingerprint : relaysToRemove) {
            knownRelays.remove(fingerprint);
          }
          nextWeightingInterval -= 1L;
        }

        /* Parse all fingerprints of Running relays from the consensus. */
        Set<String> fingerprints = new HashSet<String>();
        BufferedReader br = new BufferedReader(new FileReader(consensus));
        String line, rLine = null;
        boolean reachedEnd = false;
        while ((line = br.readLine()) != null) {
          if (line.startsWith("r ")) {
            rLine = line;
          } else if (line.startsWith("s ") && line.contains(" Running")) {
            String[] parts = rLine.split(" ");
            if (parts.length < 3) {
              System.out.println("Illegal line '" + rLine + "' in "
                  + consensus + ". Skipping consensus.");
              continue;
            } else {
              String fingerprint = parts[2];
              if (fingerprint.length() !=
                  "AAAAAAAAAAAAAAAAAAAAAAAAAAA".length()) {
                System.out.println("Illegal line '" + rLine + "' in "
                    + consensus + ". Skipping consensus.");
                continue;
              }
              fingerprints.add(fingerprint);
            }
          } else if (line.startsWith("directory-signature ")) {
            reachedEnd = true;
            break;
          }
        }
        br.close();
        if (!reachedEnd) {
          System.out.println("Did not reach the consensus end of "
              + consensus + ". Skipping consensus.");
          continue;
        }

        /* Increment weighted uptime for all running relays by 3600
         * seconds. */
        /* TODO 3600 seconds is only correct if we're not missing a
         * consensus.  We could be more precise here, but it will probably
         * not affect results significantly, if at all.  The same applies
         * to the 3600 seconds constants below. */
        for (String fingerprint : fingerprints) {
          if (!knownRelays.containsKey(fingerprint)) {
            knownRelays.put(fingerprint, new long[] { 3600L, 0L });
          } else {
            knownRelays.get(fingerprint)[0] += 3600L;
          }
        }

        /* Increment total weighted time for all relays by 3600 seconds. */
        for (long[] w : knownRelays.values()) {
          w[1] += 3600L;
        }

        /* Write future WFUs for all known relays to disk. */
        File fwfuFile = new File("fwfu", consensus.getName());
        fwfuFile.getParentFile().mkdirs();
        BufferedWriter bw = new BufferedWriter(new FileWriter(fwfuFile));
        for (Map.Entry<String, long[]> e : knownRelays.entrySet()) {
          bw.write(e.getKey() + " "
              + ((10000L * e.getValue()[0]) / e.getValue()[1]) + "\n");
        }
        bw.close();
      }
    }

    /* Run the simulation for the following WFU/10000 values: */
    long[] requiredWFUs = new long[] { 9000, 9100, 9200, 9300, 9400, 9500,
        9600, 9700, 9750, 9800, 9850, 9900, 9950, 9975, 9990, 9999 };
    BufferedWriter bw = new BufferedWriter(new FileWriter("wfu-sim.csv"));
    bw.write("time");
    for (long requiredWFU : requiredWFUs) {
      bw.write(",wfu" + requiredWFU + ",perc85wfu" + requiredWFU
          + ",perc90wfu" + requiredWFU + ",perc95wfu" + requiredWFU
          + ",guards" + requiredWFU);
    }
    bw.write("\n");

    /* Scan existing consensus files and sort them in forward order. */
    SortedSet<File> allConsensuses = new TreeSet<File>();
    Stack<File> files = new Stack<File>();
    files.add(new File("consensuses"));
    while (!files.isEmpty()) {
      File file = files.pop();
      if (file.isDirectory()) {
        files.addAll(Arrays.asList(file.listFiles()));
      } else {
        if (file.getName().endsWith("-consensus")) {
          allConsensuses.add(file);
        }
      }
    }

    /* For each relay as identified by its base-64 encoded fingerprint,
     * track weighted uptime and total weighted time in a long[2]. */
    SortedMap<String, long[]> knownRelays = new TreeMap<String, long[]>();

    /* Parse all consensuses in forward order. */
    SimpleDateFormat formatter = new SimpleDateFormat(
        "yyyy-MM-dd-HH-mm-ss");
    formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    SimpleDateFormat isoFormatter = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    isoFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    long nextWeightingInterval = formatter.parse(allConsensuses.first().
        getName().substring(0, "yyyy-MM-dd-HH-mm-ss".length())).getTime()
        / (12L * 60L * 60L * 1000L);
    for (File consensus : allConsensuses) {

      /* Every 12 hours, weight both uptime and total time of all known
       * relays with 0.95 (or 19/20 since these are long integers) and
       * remove all with a weighted fractional uptime below 1/10000. */
      long validAfter = formatter.parse(consensus.getName().substring(0,
          "yyyy-MM-dd-HH-mm-ss".length())).getTime();
      long weightingInterval = validAfter / (12L * 60L * 60L * 1000L);
      while (weightingInterval > nextWeightingInterval) {
        Set<String> relaysToRemove = new HashSet<String>();
        for (Map.Entry<String, long[]> e : knownRelays.entrySet()) {
          long[] w = e.getValue();
          w[0] *= 19L;
          w[0] /= 20L;
          w[1] *= 19L;
          w[1] /= 20L;
          if (((10000L * w[0]) / w[1]) < 1L) {
            relaysToRemove.add(e.getKey());
          }
        }
        for (String fingerprint : relaysToRemove) {
          knownRelays.remove(fingerprint);
        }
        nextWeightingInterval += 1L;
      }

      /* Parse all fingerprints of Running relays from the consensus. */
      Set<String> fingerprints = new HashSet<String>();
      BufferedReader br = new BufferedReader(new FileReader(consensus));
      String line, rLine = null;
      boolean reachedEnd = false;
      while ((line = br.readLine()) != null) {
        if (line.startsWith("r ")) {
          rLine = line;
        } else if (line.startsWith("s ") && line.contains(" Running")) {
          String[] parts = rLine.split(" ");
          if (parts.length < 3) {
            System.out.println("Illegal line '" + rLine + "' in "
                + consensus + ". Skipping consensus.");
            continue;
          } else {
            String fingerprint = parts[2];
            if (fingerprint.length() !=
                "AAAAAAAAAAAAAAAAAAAAAAAAAAA".length()) {
              System.out.println("Illegal line '" + rLine + "' in "
                  + consensus + ". Skipping consensus.");
              continue;
            }
            fingerprints.add(fingerprint);
          }
        } else if (line.startsWith("directory-signature ")) {
          reachedEnd = true;
          break;
        }
      }
      br.close();
      if (!reachedEnd) {
        System.out.println("Did not reach the consensus end of "
            + consensus + ". Skipping consensus.");
        continue;
      }

      /* Increment weighted uptime for all running relays by 3600
       * seconds. */
      for (String fingerprint : fingerprints) {
        if (!knownRelays.containsKey(fingerprint)) {
          knownRelays.put(fingerprint, new long[] { 3600L, 0L });
        } else {
          knownRelays.get(fingerprint)[0] += 3600L;
        }
      }

      /* Increment total weighted time for all relays by 3600 seconds. */
      for (long[] w : knownRelays.values()) {
        w[1] += 3600L;
      }

      /* Read previously calculated future WFUs from disk. */
      Map<String, Long> fwfus = new HashMap<String, Long>();
      File fwfuFile = new File("fwfu", consensus.getName());
      if (!fwfuFile.exists()) {
        System.out.println("Could not find file " + fwfuFile
            + ". Exiting!");
        System.exit(1);
      }
      br = new BufferedReader(new FileReader(fwfuFile));
      while ((line = br.readLine()) != null) {
        String[] parts = line.split(" ");
        fwfus.put(parts[0], Long.parseLong(parts[1]));
      }

      /* Run the simulation for the relays in the current consensus for
       * various required WFUs. */
      bw.write(isoFormatter.format(validAfter));
      for (long requiredWFU : requiredWFUs) {
        long selectedRelays = 0L,
            totalRelays = (long) fingerprints.size(), totalFwfu = 0L;
        List<Long> fwfuList = new ArrayList<Long>();
        for (String fingerprint : fingerprints) {
          long[] pwfu = knownRelays.get(fingerprint);
          long wfu = (10000L * pwfu[0]) / pwfu[1];
          if (wfu >= requiredWFU) {
            selectedRelays += 1L;
            if (fwfus.containsKey(fingerprint)) {
              long fwfu = fwfus.get(fingerprint);
              totalFwfu += fwfu;
              fwfuList.add(fwfu);
            }
          }
        }
        if (selectedRelays == 0L) {
          bw.write(",NA,NA,NA,NA");
        } else {
          Collections.sort(fwfuList, Collections.reverseOrder());
          long perc85 = fwfuList.get((85 * fwfuList.size()) / 100);
          long perc90 = fwfuList.get((90 * fwfuList.size()) / 100);
          long perc95 = fwfuList.get((95 * fwfuList.size()) / 100);
          bw.write("," + (totalFwfu / selectedRelays) + "," + perc85
              + "," + perc90 + "," + perc95);
        }
        bw.write("," + (10000L * selectedRelays / totalRelays));
      }
      bw.write("\n");
    }
    bw.close();

    /* Print how long this execution took and exit. */
    System.out.println("Execution took " + ((System.currentTimeMillis()
        - started) / (60L * 1000L)) + " minutes.");
  }
}

