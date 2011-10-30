import java.io.*;
import java.text.*;
import java.util.*;

/**
 * Simulate different requirements for stable bridges that BridgeDB can
 * use to include at least one such stable bridge in its responses to
 * bridge users.
 *
 * The two bridge stability metrics used here are weighted mean time
 * between address changes (WMTBAC) and weighted fractional uptime (WFU).
 * Different requirements based on these two metrics are simulated by
 * comparing the time on same address (TOSA) and weighted fractional
 * uptime in the future (WFU).
 */
public class SimulateBridgeStability {

  /* Bridge history entry for the third step.  Once we teach BridgeDB
   * how to keep track of bridge stability, it's going to keep records
   * similar to this one. */
  private static class BridgeHistoryElement {
    /* Weighted uptime in seconds that is used for WFU calculation. */
    public long weightedUptime;
    /* Weighted time in seconds that is used for WFU calculation. */
    public long weightedTime;
    /* Weighted run length of previously used addresses or ports in
     * seconds. */
    public double weightedRunLength;
    /* Total run weights of previously used addresses or ports. */
    public double totalRunWeights;
    /* Currently known address. */
    public String address;
    /* Month string (YYYY-MM) that was used as input to the bridge
     * descriptor sanitizer. */
    public String month;
    /* Currently known port. */
    public int port;
    /* Timestamp in milliseconds when this bridge was last seen with a
     * different address or port.  When adding a history entry, this
     * timestamp is initialized with the publication time of the previous
     * status. */
    public long lastSeenWithDifferentAddressAndPort;
    /* Timestamp in milliseconds when this bridge was last seen with this
     * address and port. */
    public long lastSeenWithThisAddressAndPort;
  }

  /* Run the analysis in three steps:
   *
   * In the first step, we parse sanitized bridge network statuses from
   * first to last to determine stable addresses that have changed by the
   * sanitizing process only.  In the second step, we parse statuses from
   * last to first to calculate TOSA and future WFU, and write future
   * stability metrics to disk as one file per network status in
   * future-stability/$filename.  In the third step, we parse the statuses
   * again from first to last, calculate past stability metrics for all
   * bridges, select stable bridges, look up future stability of these
   * bridges, and write results to stability.csv.
   */
  public static void main(String[] args) throws Exception {

    /* Measure how long this execution takes. */
    long started = System.currentTimeMillis();

    /* Prepare timestamp parsing. */
    SimpleDateFormat isoFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    SimpleDateFormat statusFileNameFormat = new SimpleDateFormat(
        "yyyyMMdd-HHmmss");
    statusFileNameFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    SimpleDateFormat futureStabilityFileNameFormat = new SimpleDateFormat(
        "yyyy-MM-dd-HH-mm-ss");
    futureStabilityFileNameFormat.setTimeZone(TimeZone.getTimeZone(
        "UTC"));

    /* Define analysis interval. */
    String analyzeFrom = "2010-07-01 00:00:00",
        analyzeTo = "2011-06-30 23:00:00";
    long analyzeFromMillis = isoFormat.parse(analyzeFrom).getTime(),
        analyzeToMillis = isoFormat.parse(analyzeTo).getTime();

    /* Scan existing status files. */
    SortedSet<File> allStatuses = new TreeSet<File>();
    Stack<File> files = new Stack<File>();
    files.add(new File("bridge-statuses"));
    while (!files.isEmpty()) {
      File file = files.pop();
      if (file.isDirectory()) {
        files.addAll(Arrays.asList(file.listFiles()));
      } else {
        if (file.getName().endsWith(
            "-4A0CCD2DDC7995083D73F5D667100C8A5831F16D")) {
          allStatuses.add(file);
        }
      }
    }
    System.out.println("Scanning " + allStatuses.size() + " bridge "
        + "network statuses.");

    /* Parse statuses in forward order to detect stable fingerprint/
     * address combinations to correct some of the IP address changes from
     * one month to the next. */
    SortedSet<String> stableFingerprintsAndAddresses =
        new TreeSet<String>();
    File stableFingerprintsAndAddressesFile =
        new File("stable-fingerprints-and-addresses");
    if (stableFingerprintsAndAddressesFile.exists()) {
      System.out.println("Reading stable fingerprints and addresses from "
          + "disk...");
      BufferedReader br = new BufferedReader(new FileReader(
          stableFingerprintsAndAddressesFile));
      String line;
      while ((line = br.readLine()) != null) {
        stableFingerprintsAndAddresses.add(line);
      }
      br.close();
    } else {
      System.out.println("Parsing bridge network statuses and writing "
          + "stable fingerprints and addresses to disk...");
      Map<String, Long> firstSeenFingerprintAndAddress =
          new HashMap<String, Long>();
      for (File status : allStatuses) {
        Set<String> fingerprints = new HashSet<String>();
        BufferedReader br = new BufferedReader(new FileReader(status));
        String line, rLine = null;
        while ((line = br.readLine()) != null) {
          if (line.startsWith("r ")) {
            String[] parts = line.split(" ");
            if (parts.length < 8) {
              System.out.println("Illegal line '" + rLine + "' in "
                  + status + ". Skipping status.");
              break;
            }
            String fingerprint = parts[2];
            String address = parts[6];
            String fingerprintAndAddress = fingerprint + " " + address;
            if (stableFingerprintsAndAddresses.contains(
                fingerprintAndAddress)) {
              continue;
            } else {
              String date = parts[4];
              String time = parts[5];
              long published = isoFormat.parse(date + " " + time).
                  getTime();
              if (!firstSeenFingerprintAndAddress.containsKey(
                  fingerprintAndAddress)) {
                firstSeenFingerprintAndAddress.put(fingerprintAndAddress,
                    published);
              } else if (published - firstSeenFingerprintAndAddress.get(
                  fingerprintAndAddress) > 36L * 60L * 60L * 1000L) {
                stableFingerprintsAndAddresses.add(
                    fingerprintAndAddress);
              }
            }
          }
        }
        br.close();
      }
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          stableFingerprintsAndAddressesFile));
      for (String stableFingerprintAndAddress :
          stableFingerprintsAndAddresses) {
        bw.write(stableFingerprintAndAddress + "\n");
      }
      bw.close();
    }
    System.out.println("We know about "
        + stableFingerprintsAndAddresses.size() + " stable fingerprints "
        + "and addresses.");

    /* Now parse statuses in reverse direction to calculate time until
     * next address change and weighted fractional uptime for all bridges.
     * Whenever we find a bridge published in a month for the first time,
     * we look if we identified the bridge fingerprint and address (either
     * new or old) as stable before.  If we did, ignore this address
     * change. */
    File futureStabilityDirectory = new File("future-stability");
    if (futureStabilityDirectory.exists()) {
      System.out.println("Not overwriting files in "
          + futureStabilityDirectory.getAbsolutePath());
    } else {

      /* Track weighted uptime and total weighted time in a long[2]. */
      SortedMap<String, long[]> wfuHistory =
          new TreeMap<String, long[]>();

      /* Track timestamps of next address changes in a long. */
      SortedMap<String, Long> nacHistory = new TreeMap<String, Long>();

      /* Store the last known r lines by fingerprint to be able to decide
       * whether a bridge has changed its IP address. */
      Map<String, String> lastKnownRLines = new HashMap<String, String>();

      /* Parse bridge network statuses in reverse order. */
      SortedSet<File> allStatusesReverseOrder =
          new TreeSet<File>(Collections.reverseOrder());
      allStatusesReverseOrder.addAll(allStatuses);
      long nextWeightingInterval = -1L, lastStatusPublicationMillis = -1L;
      for (File status : allStatusesReverseOrder) {

        /* Parse status publication time from file name. */
        long statusPublicationMillis = statusFileNameFormat.parse(
            status.getName().substring(0, "yyyyMMdd-HHmmss".length())).
            getTime();

        /* We're just looking at the first status outside the analysis
         * interval.  Stop parsing here. */
        if (statusPublicationMillis < analyzeFromMillis) {
          break;
        }

        /* Calculate the seconds since the last parsed status.  If this is
         * the first status or we haven't seen a status for more than 60
         * minutes, assume 60 minutes. */
        long secondsSinceLastStatusPublication =
            lastStatusPublicationMillis < 0L ||
            lastStatusPublicationMillis - statusPublicationMillis
            > 60L * 60L * 1000L ? 60L * 60L
            : (lastStatusPublicationMillis - statusPublicationMillis)
            / 1000L;
        lastStatusPublicationMillis = statusPublicationMillis;

        /* Before parsing the next bridge network status, see if 12 hours
         * have passed since we last discounted wfu history values.  If
         * so, discount variables for all known bridges by factor 0.95 (or
         * 19/20 since these are long integers) and remove those bridges
         * with a weighted fractional uptime below 1/10000 from the
         * history. */
        long weightingInterval = statusPublicationMillis
            / (12L * 60L * 60L * 1000L);
        if (nextWeightingInterval < 0L) {
          nextWeightingInterval = weightingInterval;
        }
        while (weightingInterval < nextWeightingInterval) {
          Set<String> bridgesToRemove = new HashSet<String>();
          for (Map.Entry<String, long[]> e : wfuHistory.entrySet()) {
            long[] w = e.getValue();
            w[0] *= 19L;
            w[0] /= 20L;
            w[1] *= 19L;
            w[1] /= 20L;
            if (((10000L * w[0]) / w[1]) < 1L) {
              bridgesToRemove.add(e.getKey());
            }
          }
          for (String fingerprint : bridgesToRemove) {
            wfuHistory.remove(fingerprint);
          }
          nextWeightingInterval -= 1L;
        }

        /* Increment total weighted time for all bridges by seconds
         * since the last status was published. */
        for (long[] w : wfuHistory.values()) {
          w[1] += secondsSinceLastStatusPublication;
        }

        /* If the status falls within our analysis interval, write future
         * WFUs and TOSAs for all running bridges to disk. */
        BufferedWriter bw = null;
        if (statusPublicationMillis <= analyzeToMillis) {
          File futureStabilityFile = new File("future-stability",
              futureStabilityFileNameFormat.format(
              statusPublicationMillis));
          futureStabilityFile.getParentFile().mkdirs();
          bw = new BufferedWriter(new FileWriter(futureStabilityFile));
        }


        /* Parse r lines of all bridges with the Running flag from the
         * current bridge network status. */
        BufferedReader br = new BufferedReader(new FileReader(status));
        String line, rLine = null;
        SortedMap<String, String> runningBridges =
            new TreeMap<String, String>();
        while ((line = br.readLine()) != null) {
          if (line.startsWith("r ")) {
            rLine = line;
          } else if (line.startsWith("s ") && line.contains(" Running")) {
            String[] parts = rLine.split(" ");
            if (parts.length < 8) {
              System.out.println("Illegal line '" + rLine + "' in "
                  + status + ". Skipping line.");
              continue;
            }
            String fingerprint = parts[2];
            runningBridges.put(fingerprint, rLine);
          }
        }
        br.close();

        /* If this status doesn't contain a single bridge with the Running
         * flag, ignore it.  This is a problem with the bridge authority
         * and doesn't mean we should consider all bridges as down. */
        if (runningBridges.isEmpty()) {
          continue;
        }

        /* Find out if a bridge changed its IP address or port. */
        for (Map.Entry<String, String> e : runningBridges.entrySet()) {
          String fingerprint = e.getKey();
          String brLine = e.getValue();
          String[] brParts = brLine.split(" ");

          /* Increment weighted uptime by seconds since last status
           * publication time. */
          if (!wfuHistory.containsKey(fingerprint)) {
            wfuHistory.put(fingerprint, new long[] {
                secondsSinceLastStatusPublication,
                secondsSinceLastStatusPublication });
          } else {
            wfuHistory.get(fingerprint)[0] +=
                secondsSinceLastStatusPublication;
          }

          /* Check for address or port change. */
          String address = brParts[6];
          String port = brParts[7];
          boolean sameAddressAndPort = false;
          if (lastKnownRLines.containsKey(fingerprint)) {
            String[] lastKnownRLineParts =
                lastKnownRLines.get(fingerprint).split(" ");
            String lastAddress = lastKnownRLineParts[6];
            String lastPort = lastKnownRLineParts[7];
            if (!port.equals(lastPort)) {
              /* The port changed.  It doesn't matter whether the
               * address changed or not. */
            } else if (address.equals(lastAddress)) {
              /* The bridge's address and port are still the same. */
              sameAddressAndPort = true;
            } else {
              String month = brParts[4].substring(0, "yyyy-MM".length());
              String lastMonth = lastKnownRLineParts[4].substring(0,
                  "yyyy-MM".length());
              if (!lastMonth.equals(month) &&
                  stableFingerprintsAndAddresses.contains(
                  fingerprint + " " + address) &&
                  stableFingerprintsAndAddresses.contains(
                  fingerprint + " " + lastAddress)) {
                /* The last time we saw this bridge was in a different
                 * month.  This bridge was seen with both addresses in
                 * intervals of 36 hours or more.  Consider this
                 * address change an artifact from the sanitizing
                 * process. */
                sameAddressAndPort = true;
              } else {
                /* Either the month did not change or the address or
                 * port did change. */
              }
            }
          } else {
            /* We're seeing this bridge for the first time. */
          }
          if (!sameAddressAndPort) {
            nacHistory.put(fingerprint, statusPublicationMillis);
          }
          lastKnownRLines.put(fingerprint, brLine);

          /* Write WFU and TOSA to disk. */
          if (bw != null) {
            long[] wfu = wfuHistory.get(fingerprint);
            long tosa = (nacHistory.get(fingerprint)
                - statusPublicationMillis) / 1000L;
            bw.write(fingerprint + " " + tosa + " "
                + ((10000L * wfu[0]) / wfu[1]) + " " + "\n");
          }
        }
        br.close();
        if (bw != null) {
          bw.close();
        }
      }
    }

    /* Finally, parse statuses in forward order to calculate weighted mean
     * time between address change (WMTBAC) and weighted fractional uptime
     * (WFU) and simulate how stable bridges meeting given requirements
     * are. */
    File stabilityFile = new File("stability.csv");
    if (stabilityFile.exists()) {
      System.out.println("Not overwriting output file "
          + stabilityFile.getAbsolutePath());
    } else {

      /* Run the simulation for the following WFU and WMTBAC
       * percentiles: */
      List<Integer> wfuPercentiles = new ArrayList<Integer>();
      for (int l : new int[] { 0, 30, 40, 50, 60, 70 }) {
        wfuPercentiles.add(l);
      }
      List<Integer> wmtbacPercentiles = new ArrayList<Integer>();
      for (int l : new int[] { 0, 30, 40, 50, 60, 70 }) {
        wmtbacPercentiles.add(l);
      }

      /* Add column headers to output file. */
      SortedSet<String> columns = new TreeSet<String>();
      columns.add("running");
      columns.add("minwta");
      columns.add("minwtb");
      for (int wfuPercentile : wfuPercentiles) {
        columns.add("minwfua" + wfuPercentile + "wfu");
        columns.add("minwfub" + wfuPercentile + "wfu");
        for (int wmtbacPercentile : wmtbacPercentiles) {
          String simulation = wfuPercentile + "wfu" + wmtbacPercentile
              + "wmtbac";
          columns.add("stablebridge" + simulation);
          columns.add("perc15wfu" + simulation);
          columns.add("perc10wfu" + simulation);
          columns.add("perc5wfu" + simulation);
          columns.add("perc15tosa" + simulation);
          columns.add("perc10tosa" + simulation);
          columns.add("perc5tosa" + simulation);
        }
      }
      for (int wmtbacPercentile : wmtbacPercentiles) {
        columns.add("minwmtbaca" + wmtbacPercentile + "wmtbac");
        columns.add("minwmtbacb" + wmtbacPercentile + "wmtbac");
      }
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          stabilityFile));
      bw.write("time");
      for (String column : columns) {
        bw.write("," + column);
      }
      bw.write("\n");

      SortedMap<String, BridgeHistoryElement> history =
          new TreeMap<String, BridgeHistoryElement>();

      /* Parse previously exported network status entries again, but this
       * time in forward order. */
      long nextWeightingInterval = -1L, lastStatusPublicationMillis = -1L;
      for (File status : allStatuses) {

        /* Parse status publication time from file name. */
        long statusPublicationMillis = statusFileNameFormat.parse(
            status.getName().substring(0, "yyyyMMdd-HHmmss".length())).
            getTime();

        /* Calculate the seconds since the last parsed status.  If this is
         * the first status or we haven't seen a status for more than 60
         * minutes, assume 60 minutes. */
        long secondsSinceLastStatusPublication =
            lastStatusPublicationMillis < 0L ||
            statusPublicationMillis - lastStatusPublicationMillis
            > 60L * 60L * 1000L ? 60L * 60L
            : (statusPublicationMillis - lastStatusPublicationMillis)
            / 1000L;

        /* Before parsing the next bridge network status, see if 12 hours
         * have passed since we last discounted wfu and wmtbac history
         * values.  If so, discount variables for all known bridges by
         * factor 0.95 (or 19/20 since these are long integers) and remove
         * those bridges with a weighted fractional uptime below 1/10000
         * from the history.  Also, discount weighted run length and total
         * run weights for all known relays by factor 0.95. */
        long weightingInterval = statusPublicationMillis
            / (12L * 60L * 60L * 1000L);
        if (nextWeightingInterval < 0L) {
          nextWeightingInterval = weightingInterval;
        }
        while (weightingInterval > nextWeightingInterval) {
          Set<String> bridgesToRemove = new HashSet<String>();
          for (Map.Entry<String, BridgeHistoryElement> e :
              history.entrySet()) {
            BridgeHistoryElement historyElement = e.getValue();
            historyElement.weightedUptime =
                (historyElement.weightedUptime * 19L) / 20L;
            historyElement.weightedTime =
                (historyElement.weightedTime * 19L) / 20L;
            if (((10000L * historyElement.weightedUptime)
                / historyElement.weightedTime) < 1L) {
              String fingerprint = e.getKey();
              bridgesToRemove.add(fingerprint);
            }
            historyElement.weightedRunLength *= 0.95;
            historyElement.totalRunWeights *= 0.95;
          }
          for (String fingerprint : bridgesToRemove) {
            history.remove(fingerprint);
          }
          nextWeightingInterval += 1L;
        }

        /* Parse r lines of all bridges with the Running flag from the
         * current bridge network status. */
        BufferedReader br = new BufferedReader(new FileReader(status));
        String line, rLine = null;
        SortedMap<String, String> runningBridges =
            new TreeMap<String, String>();
        while ((line = br.readLine()) != null) {
          if (line.startsWith("r ")) {
            rLine = line;
          } else if (line.startsWith("s ") && line.contains(" Running")) {
            String[] parts = rLine.split(" ");
            if (parts.length < 8) {
              System.out.println("Illegal line '" + rLine + "' in "
                  + status + ". Skipping line.");
              continue;
            }
            String fingerprint = parts[2];
            runningBridges.put(fingerprint, rLine);
          }
        }
        br.close();

        /* If this status doesn't contain a single bridge with the Running
         * flag, ignore it.  This is a problem with the bridge authority
         * and doesn't mean we should consider all bridges as down. */
        if (runningBridges.isEmpty()) {
          continue;
        }

        /* Add new bridges to history or update history if it already
         * contains a bridge. */
        for (Map.Entry<String, String> e : runningBridges.entrySet()) {
          String fingerprint = e.getKey();
          String brLine = e.getValue();
          String[] brParts = brLine.split(" ");
          String address = brParts[6];
          int port = Integer.parseInt(brParts[7]);
          String month = brParts[4].substring(0, "yyyy-MM".length());
          BridgeHistoryElement bhe = null;
          if (!history.containsKey(fingerprint)) {

            /* Add new bridge to history. */
            bhe = new BridgeHistoryElement();
            bhe.lastSeenWithDifferentAddressAndPort =
                lastStatusPublicationMillis;
            history.put(fingerprint, bhe);
          } else {

            /* Update bridge in history. */
            bhe = history.get(fingerprint);

            /* If the port changed, ... */
            if (port != bhe.port ||

                /* ... or the address changed and ... */
                (!address.equals(bhe.address) &&

                /* ... either the month is the same ... */
                (month.equals(bhe.month) ||

                /* ... or this address is not stable ... */
                !stableFingerprintsAndAddresses.contains(
                fingerprint + " " + address) ||

                /* ... or the last address is not stable, ... */
                !stableFingerprintsAndAddresses.contains(
                fingerprint + " " + bhe.address)))) {

              /* ... assume that the bridge changed its address or
               * port. */
              bhe.weightedRunLength += (double)
                  ((bhe.lastSeenWithThisAddressAndPort -
                  bhe.lastSeenWithDifferentAddressAndPort) / 1000L);
              bhe.totalRunWeights += 1.0;
              bhe.lastSeenWithDifferentAddressAndPort =
                  bhe.lastSeenWithThisAddressAndPort;
            }
          }

          /* Regardless of whether the bridge is new, kept or changed
           * its address and port, raise its WFU times and note its
           * current address, month, and port, and that we saw it using
           * them. */
          bhe.weightedUptime += secondsSinceLastStatusPublication;
          bhe.weightedTime += secondsSinceLastStatusPublication;
          bhe.lastSeenWithThisAddressAndPort = statusPublicationMillis;
          bhe.address = address;
          bhe.month = month;
          bhe.port = port;
        }

        /* Update weighted times (not uptimes) of non-running bridges. */
        for (Map.Entry<String, BridgeHistoryElement> e :
            history.entrySet()) {
          String fingerprint = e.getKey();
          if (!runningBridges.containsKey(fingerprint)) {
            BridgeHistoryElement bhe = e.getValue();
            bhe.weightedTime += secondsSinceLastStatusPublication;
          }
        }

        /* Prepare writing results. */
        Map<String, String> results = new HashMap<String, String>();
        results.put("running", "" + runningBridges.size());

        /* If we reached the analysis interval, read previously calculated
         * future WFUs and TOSAs from disk and run the simulations. */
        Map<String, Long> fwfus = new HashMap<String, Long>(),
            tosas = new HashMap<String, Long>();
        File futureStabilityFile = new File("future-stability",
            futureStabilityFileNameFormat.format(
            statusPublicationMillis));
        if (statusPublicationMillis < analyzeFromMillis ||
            statusPublicationMillis > analyzeToMillis) {
          /* Outside of our analysis interval.  Skip simulation. */
        } else if (!futureStabilityFile.exists()) {
          System.out.println("Could not find file "
              + futureStabilityFile + ". Skipping simulation!");
        } else {
          BufferedReader fsBr = new BufferedReader(new FileReader(
              futureStabilityFile));
          String fsLine;
          while ((fsLine = fsBr.readLine()) != null) {
            String[] fsParts = fsLine.split(" ");
            tosas.put(fsParts[0], Long.parseLong(fsParts[1]));
            fwfus.put(fsParts[0], Long.parseLong(fsParts[2]));
          }
          fsBr.close();

          /* Prepare calculating thresholds for selecting stable bridges
           * in simulations. */
          List<Long> totalWeightedTimes = new ArrayList<Long>();
          for (String fingerprint : runningBridges.keySet()) {
            totalWeightedTimes.add(history.get(fingerprint).weightedTime);
          }
          Collections.sort(totalWeightedTimes);
          long minimumTotalWeightedTime = totalWeightedTimes.get(
              totalWeightedTimes.size() / 8);
          results.put("minwta", String.valueOf(minimumTotalWeightedTime));
          if (minimumTotalWeightedTime > 8L * 24L * 60L * 60L) {
            minimumTotalWeightedTime = 8L * 24L * 60L * 60L;
          }
          results.put("minwtb", String.valueOf(minimumTotalWeightedTime));
          List<Long> weightedFractionalUptimesFamiliar =
              new ArrayList<Long>();
          for (String fingerprint : runningBridges.keySet()) {
            BridgeHistoryElement bhe = history.get(fingerprint);
            if (bhe.weightedTime >= minimumTotalWeightedTime) {
              long weightedFractionalUptime =
                  (10000L * bhe.weightedUptime) / bhe.weightedTime;
              weightedFractionalUptimesFamiliar.add(
                  weightedFractionalUptime);
            }
          }
          Collections.sort(weightedFractionalUptimesFamiliar);
          List<Long> wmtbacs = new ArrayList<Long>();
          for (String fingerprint : runningBridges.keySet()) {
            BridgeHistoryElement bhe = history.get(fingerprint);
            double totalRunLength = bhe.weightedRunLength + (double)
                ((bhe.lastSeenWithThisAddressAndPort -
                bhe.lastSeenWithDifferentAddressAndPort) / 1000L);
            double totalWeights = bhe.totalRunWeights + 1.0;
            long wmtbac = totalWeights < 0.0001 ? 0L
                : (long) (totalRunLength / totalWeights);
            wmtbacs.add(wmtbac);
          }
          Collections.sort(wmtbacs);

          /* Run simulation for the bridges in the current status for
           * various WFU and WMTBAC percentiles. */
          for (int wmtbacPercentile : wmtbacPercentiles) {
            for (int wfuPercentile : wfuPercentiles) {
              String simulation = wfuPercentile + "wfu" + wmtbacPercentile
                  + "wmtbac";
              long minimumWeightedMeanTimeBetweenAddressChange =
                  wmtbacs.get((wmtbacPercentile * wmtbacs.size()) / 100);
              results.put("minwmtbaca" + wmtbacPercentile + "wmtbac",
                  String.valueOf(
                  minimumWeightedMeanTimeBetweenAddressChange));
              if (minimumWeightedMeanTimeBetweenAddressChange >
                  30L * 24L * 60L * 60L) {
                minimumWeightedMeanTimeBetweenAddressChange =
                    30L * 24L * 60L * 60L;
              }
              results.put("minwmtbacb" + wmtbacPercentile + "wmtbac",
                  String.valueOf(
                  minimumWeightedMeanTimeBetweenAddressChange));
              long minimumWeightedFractionalUptime =
                  weightedFractionalUptimesFamiliar.get((wfuPercentile
                  * weightedFractionalUptimesFamiliar.size()) / 100);
              results.put("minwfua" + wfuPercentile + "wfu",
                  String.valueOf(minimumWeightedFractionalUptime));
              if (minimumWeightedFractionalUptime > 9800) {
                minimumWeightedFractionalUptime = 9800;
              }
              results.put("minwfub" + wfuPercentile + "wfu",
                  String.valueOf(minimumWeightedFractionalUptime));
              List<Long> fwfuList = new ArrayList<Long>(),
                  tosaList = new ArrayList<Long>();
              Set<String> selectedBridges = new HashSet<String>();
              for (String fingerprint : runningBridges.keySet()) {
                BridgeHistoryElement bhe = history.get(fingerprint);
                double totalRunLength = bhe.weightedRunLength + (double)
                    ((bhe.lastSeenWithThisAddressAndPort -
                    bhe.lastSeenWithDifferentAddressAndPort) / 1000L);
                double totalWeights = bhe.totalRunWeights + 1.0;
                long wmtbac = totalWeights < 0.0001 ? 0L
                    : (long) (totalRunLength / totalWeights);
                if (wmtbac < minimumWeightedMeanTimeBetweenAddressChange) {
                  continue;
                }
                if (wfuPercentile > 0 &&
                    bhe.weightedTime < minimumTotalWeightedTime) {
                  continue;
                }
                long weightedFractionalUptime =
                    (10000L * bhe.weightedUptime) / bhe.weightedTime;
                if (weightedFractionalUptime <
                    minimumWeightedFractionalUptime) {
                  continue;
                }
                long fwfu = fwfus.get(fingerprint);
                fwfuList.add(fwfu);
                long tosa = tosas.get(fingerprint);
                tosaList.add(tosa);
                selectedBridges.add(fingerprint);
              }
              /* Calculate percentiles of future WFU and of TOSA as the
               * simulation results. */
              results.put("stablebridge" + simulation,
                  String.valueOf(selectedBridges.size()));
              if (tosaList.size() > 0L) {
                Collections.sort(tosaList);
                results.put("perc15tosa" + simulation,
                    "" + tosaList.get((15 * tosaList.size()) / 100));
                results.put("perc10tosa" + simulation,
                    "" + tosaList.get((10 * tosaList.size()) / 100));
                results.put("perc5tosa" + simulation,
                    "" + tosaList.get((5 * tosaList.size()) / 100));
              }
              if (fwfuList.size() > 0) {
                Collections.sort(fwfuList);
                results.put("perc15wfu" + simulation,
                    "" + fwfuList.get((15 * fwfuList.size()) / 100));
                results.put("perc10wfu" + simulation,
                    "" + fwfuList.get((10 * fwfuList.size()) / 100));
                results.put("perc5wfu" + simulation,
                    "" + fwfuList.get((5 * fwfuList.size()) / 100));
              }
            }
          }
        }

        /* Write results, regardless of whether we ran simulations or
         * not. */
        SortedSet<String> missingColumns = new TreeSet<String>();
        for (String resultColumn : results.keySet()) {
          if (!columns.contains(resultColumn)) {
            missingColumns.add(resultColumn);
          }
        }
        if (!missingColumns.isEmpty()) {
          System.out.println("We are missing the following columns:");
          for (String missingColumn : missingColumns) {
            System.out.print(" " + missingColumn);
          }
          System.out.println(".  Ignoring.");
        }
        bw.write(isoFormat.format(statusPublicationMillis));
        for (String column : columns) {
          if (results.containsKey(column)) {
            bw.write("," + results.get(column));
          } else {
            bw.write(",NA");
          }
        }
        bw.write("\n");
        lastStatusPublicationMillis = statusPublicationMillis;
      }
      bw.close();
    }
  }
}

