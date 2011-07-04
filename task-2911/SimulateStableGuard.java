/**
 * Simulate variation of WMTBF, WFU, and advertised bandwidth requirements
 * on Stable and Guard flag assignment.  In a first step, parse network
 * status consensus entries from last to first, calculate future stability
 * metrics for all running relays, and write them to disk as one file per
 * network status in future-stability/$filename.  (Skip this step if there
 * is already a future-stability/ directory.)  In a second step, parse the
 * network statuse consensus entries again, but this time from first to
 * last, calculate past stability metrics for all relays, assign relay
 * flags to relays, look up future stability of these relays, and write
 * results to stability.csv to disk.
 *
 * Please see the README for more information! */
import java.io.*;
import java.text.*;
import java.util.*;
public class SimulateStableGuard {
  public static void main(String[] args) throws Exception {

    /* Define analysis interval. */
    String analyzeFrom = "2010-07-01 00:00:00",
        analyzeTo = "2010-12-31 23:00:00";

    /* Decide whether we need to do the reverse run, or if we can use
     * previous results. */
    if (!new File("future-stability").exists()) {

      /* Track weighted uptime and total weighted time in a long[2]. */
      SortedMap<String, long[]> wfuHistory =
          new TreeMap<String, long[]>();

      /* Track time until next failure in seconds in a long. */
      SortedMap<String, Long> tunfHistory = new TreeMap<String, Long>();

      /* Track weighted written bytes and total weighted time in a
       * long[2]. */
      SortedMap<String, long[]> wbHistory = new TreeMap<String, long[]>();

      /* Parse previously exported network status entries in reverse
       * order. */
      SimpleDateFormat formatter = new SimpleDateFormat(
          "yyyy-MM-dd-HH-mm-ss");
      formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
      SimpleDateFormat isoFormatter = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      isoFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
      Map<String, String> runningRelays = new HashMap<String, String>();
      BufferedReader br = new BufferedReader(new FileReader(
          "running-relays-reverse.csv"));
      String line, lastValidAfter = null, lastButOneValidAfter = null;
      long nextWeightingInterval = -1L;
      while ((line = br.readLine()) != null) {
        if (!line.startsWith("20")) {
          continue;
        }
        String[] parts = line.split(",");
        String validAfter = parts[0];
        if (lastValidAfter != null &&
            !lastValidAfter.equals(validAfter)) {

          /* We just parsed the very first consensus.  There's nothing to
           * do here. */
          if (lastButOneValidAfter == null) {
            runningRelays.clear();
            lastButOneValidAfter = lastValidAfter;
            continue;
          }

          /* We just parsed the first consensus outside the analysis
           * interval.  Stop parsing here. */
          if (lastValidAfter.compareTo(analyzeFrom) < 0) {
            break;
          }

          /* We just parsed all lines of a consensus.  First, see if 12
           * hours have passed since we last discounted wfu and wb history
           * values.  If so, discount variables for all known relays by
           * factor 0.95 (or 19/20 since these are long integers) and
           * remove those relays with a weighted fractional uptime below
           * 1/10000 from the history. */
          long lastValidAfterMillis = isoFormatter.parse(lastValidAfter).
              getTime();
          long secondsSinceLastValidAfter =
              (isoFormatter.parse(lastButOneValidAfter).getTime()
              - lastValidAfterMillis) / 1000L;
          long weightingInterval = lastValidAfterMillis
              / (12L * 60L * 60L * 1000L);
          if (nextWeightingInterval < 0L) {
            nextWeightingInterval = weightingInterval;
          }
          while (weightingInterval < nextWeightingInterval) {
            Set<String> relaysToRemove = new HashSet<String>();
            for (Map.Entry<String, long[]> e : wfuHistory.entrySet()) {
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
              wfuHistory.remove(fingerprint);
            }
            relaysToRemove.clear();
            for (Map.Entry<String, long[]> e : wbHistory.entrySet()) {
              long[] w = e.getValue();
              w[0] *= 19L;
              w[0] /= 20L;
              w[1] *= 19L;
              w[1] /= 20L;
              if (w[1] < 1L) {
                relaysToRemove.add(e.getKey());
              }
            }
            for (String fingerprint : relaysToRemove) {
              wbHistory.remove(fingerprint);
            }
            nextWeightingInterval -= 1L;
          }

          /* Increment weighted written bytes and total weighted time for
           * all running relays. */
          for (Map.Entry<String, String> runningRelay :
              runningRelays.entrySet()) {
            String fingerprint = runningRelay.getKey();
            String[] relayParts = runningRelay.getValue().split(",");
            long writtenSum = relayParts.length < 7 ? 0 :
                Long.parseLong(relayParts[6]);
            long[] w = wbHistory.containsKey(fingerprint) ?
                wbHistory.get(fingerprint) : new long[2];
            w[0] += writtenSum * secondsSinceLastValidAfter /
                (24L * 60L * 60L);
            w[1] += secondsSinceLastValidAfter;
            wbHistory.put(fingerprint, w);
          }

          /* Increment weighted uptime for all running relays that have
           * not been restarted by seconds since last valid-after time. */
          for (Map.Entry<String, String> runningRelay :
              runningRelays.entrySet()) {
            String fingerprint = runningRelay.getKey();
            boolean restarted = runningRelay.getValue().
                split(",")[2].equals("t");
            long seconds = restarted ? 0 : secondsSinceLastValidAfter;
            if (!wfuHistory.containsKey(fingerprint)) {
              wfuHistory.put(fingerprint, new long[] { seconds, 0L });
            } else {
              wfuHistory.get(fingerprint)[0] += seconds;
            }
          }

          /* Increment total weighted time for all relays by seconds since
           * last valid-after time. */
          for (long[] w : wfuHistory.values()) {
            w[1] += secondsSinceLastValidAfter;
          }

          /* Iterate over our time until next failure history first and
           * see if these relays have been running in the considered
           * consensus.  Remember changes to our history and modify it
           * below to avoid concurrent modification errors. */
          Set<String> removeFromHistory = new HashSet<String>(),
              foundInHistory = new HashSet<String>();
          Map<String, Long> addToHistory = new HashMap<String, Long>();
          for (Map.Entry<String, Long> e : tunfHistory.entrySet()) {
            String fingerprint = e.getKey();
            if (runningRelays.containsKey(fingerprint)) {

              /* This relay has been running, so update our history. */
              boolean restarted = runningRelays.get(fingerprint).
                  split(",")[2].equals("t");
              if (restarted) {
                removeFromHistory.add(fingerprint);
              } else {
                addToHistory.put(fingerprint, secondsSinceLastValidAfter
                    + e.getValue());
              }
              foundInHistory.add(fingerprint);
            } else {

              /* This relay has not been running, so remove it from our
               * history. */
              removeFromHistory.add(fingerprint);
            }
          }

          /* Update our history for real now.  We couldn't do this above,
           * or we'd have modified the set we've been iterating over. */
          for (String f : removeFromHistory) {
            tunfHistory.remove(f);
          }
          for (Map.Entry<String, Long> e : addToHistory.entrySet()) {
            tunfHistory.put(e.getKey(), e.getValue());
          }

          /* Iterate over the relays that we found in the consensus, but
           * that we didn't have in our history. */
          for (Map.Entry<String, String> e : runningRelays.entrySet()) {
            String fingerprint = e.getKey();
            if (!foundInHistory.contains(fingerprint)) {
              boolean restarted = e.getValue().split(",")[2].equals("t");
              if (!restarted) {
                tunfHistory.put(fingerprint, 0L);
              }
            }
          }

          /* If the consensus falls within our analysis interval, write
           * future WFUs, TUNFs, and WBs for all known relays to disk. */
          if (lastValidAfter.compareTo(analyzeFrom) >= 0) {
            File futureStabilityFile = new File("future-stability",
                formatter.format(lastValidAfterMillis));
            futureStabilityFile.getParentFile().mkdirs();
            BufferedWriter bw = new BufferedWriter(new FileWriter(
                futureStabilityFile));
            for (String fingerprint : runningRelays.keySet()) {
              long[] wfu = wfuHistory.get(fingerprint);
              long tunf = tunfHistory.containsKey(fingerprint) ?
                  tunfHistory.get(fingerprint) : 0;
              long[] wb = wbHistory.get(fingerprint);
              bw.write(fingerprint + " " + tunf + " "
                  + ((10000L * wfu[0]) / wfu[1]) + " "
                  + (wb[0] / wb[1]) + "\n");
            }
            bw.close();
          }

          /* Prepare for next consensus. */
          runningRelays.clear();
          lastButOneValidAfter = lastValidAfter;
        }

        /* Add the running relay lines to a map that we parse once we have
         * all lines of a consensus. */
        String fingerprint = parts[1];
        runningRelays.put(fingerprint, line);
        lastValidAfter = validAfter;
      }
    }

    /* Run the Guard simulation for the following WFU and advertised
     * bandwidth percentiles: */
    List<Integer> wfuPercentiles = new ArrayList<Integer>();
    for (int l : new int[] { 30, 40, 50, 60, 70 }) {
      wfuPercentiles.add(l);
    }
    List<Integer> advBwPercentiles = new ArrayList<Integer>();
    for (int l : new int[] { 30, 40, 50, 60, 70 }) {
      advBwPercentiles.add(l);
    }

    /* Run the Stable simulation for the following WMTBF percentiles: */
    List<Integer> wmtbfPercentiles = new ArrayList<Integer>();
    for (int l : new int[] { 30, 40, 50, 60, 70 }) {
      wmtbfPercentiles.add(l);
    }

    /* Add column headers to output file. */
    SortedSet<String> columns = new TreeSet<String>();
    columns.addAll(new ArrayList<String>(Arrays.asList(("running,"
        + "guard,mwfu,perc15wfu,perc10wfu,perc5wfu,"
        + "guardintersect,guardobserved,guardsimulated,"
        + "minwta,minwtb,familiar,"
        + "perc1fwb,perc2fwb,perc5fwb,perc10fwb,"
        + "exclwt,exclwfu,excladvbw").split(","))));
    for (int wfuPercentile : wfuPercentiles) {
      for (int advBwPercentile : advBwPercentiles) {
        String simulation = wfuPercentile + "wfu" + advBwPercentile
            + "advbw";
        columns.add("minwfua" + wfuPercentile + "wfu");
        columns.add("minwfub" + wfuPercentile + "wfu");
        columns.add("minadvbwa" + advBwPercentile + "advbw");
        columns.add("minadvbwb" + advBwPercentile + "advbw");
        columns.add("guard" + simulation);
        columns.add("mwfu" + simulation);
        columns.add("perc15wfu" + simulation);
        columns.add("perc10wfu" + simulation);
        columns.add("perc5wfu" + simulation);
        columns.add("perc1fwb" + simulation);
        columns.add("perc2fwb" + simulation);
        columns.add("perc5fwb" + simulation);
        columns.add("perc10fwb" + simulation);
      }
    }
    columns.addAll(new ArrayList<String>(Arrays.asList(("stable,"
        + "perc25tunf,perc20tunf,perc15tunf,perc10tunf,perc5tunf,"
        + "stableintersect,stableobserved,stablesimulated").split(","))));
    for (int wmtbfPercentile : wmtbfPercentiles) {
      columns.add("minwmtbfa" + wmtbfPercentile);
      columns.add("minwmtbfb" + wmtbfPercentile);
      columns.add("stable" + wmtbfPercentile);
      columns.add("mtunf" + wmtbfPercentile);
      columns.add("perc25tunf" + wmtbfPercentile);
      columns.add("perc20tunf" + wmtbfPercentile);
      columns.add("perc15tunf" + wmtbfPercentile);
      columns.add("perc10tunf" + wmtbfPercentile);
      columns.add("perc5tunf" + wmtbfPercentile);
    }
    BufferedWriter bw = new BufferedWriter(new FileWriter(
        "stability.csv"));
    bw.write("time");
    for (String column : columns) {
      bw.write("," + column);
    }
    bw.write("\n");

    /* Track weighted uptime and total weighted time in a long[2]. */
    SortedMap<String, long[]> wfuHistory = new TreeMap<String, long[]>();

    /* Track weighted run length, total run weights, and current run
     * length in a double[3]. */
    SortedMap<String, double[]> mtbfHistory =
        new TreeMap<String, double[]>();

    /* Parse previously exported network status entries again, but this
     * time in forward order. */
    SimpleDateFormat formatter = new SimpleDateFormat(
        "yyyy-MM-dd-HH-mm-ss");
    formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    SimpleDateFormat isoFormatter = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    isoFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    Map<String, String> runningRelays = new HashMap<String, String>();
    Set<String> guardRelays = new HashSet<String>(),
        stableRelays = new HashSet<String>();
    BufferedReader br = new BufferedReader(new FileReader(
        "running-relays-forward.csv"));
    String line, lastValidAfter = null, firstValidAfter = null,
        lastButOneValidAfter = null;
    long nextWeightingInterval = -1L;
    while ((line = br.readLine()) != null) {
      if (!line.startsWith("20")) {
        continue;
      }
      String[] parts = line.split(",");
      String validAfter = parts[0];
      if (firstValidAfter == null) {
        firstValidAfter = validAfter;
      }
      if (lastValidAfter != null &&
          !lastValidAfter.equals(validAfter)) {

        /* We just parsed the very first consensus.  There's nothing to
         * do here. */
        if (lastButOneValidAfter == null) {
          runningRelays.clear();
          guardRelays.clear();
          stableRelays.clear();
          lastButOneValidAfter = lastValidAfter;
          continue;
        }

        /* We just parsed the first consensus outside the analysis
         * interval.  Stop analysis here. */
        if (lastValidAfter.compareTo(analyzeTo) > 0) {
          break;
        }

        /* We just parsed all lines of a consensus.  First, see if 12
         * hours have passed since we last discounted uptimes and total
         * times.  If so, discount both variables for all known relays by
         * factor 0.95 (or 19/20 since these are long integers) and remove
         * those relays with a weighted fractional uptime below 1/10000.
         * Also, discount weighted run length and total run weights for
         * all known relays by factor 0.95. */
        long lastValidAfterMillis = isoFormatter.parse(lastValidAfter).
            getTime();
        long secondsSinceLastValidAfter = (lastValidAfterMillis
            - isoFormatter.parse(lastButOneValidAfter).getTime()) / 1000L;
        long weightingInterval = lastValidAfterMillis
            / (12L * 60L * 60L * 1000L);
        if (nextWeightingInterval < 0L) {
          nextWeightingInterval = weightingInterval;
        }
        while (weightingInterval > nextWeightingInterval) {
          Set<String> relaysToRemove = new HashSet<String>();
          for (Map.Entry<String, long[]> e : wfuHistory.entrySet()) {
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
            wfuHistory.remove(fingerprint);
          }
          for (Map.Entry<String, double[]> e : mtbfHistory.entrySet()) {
            double[] w = e.getValue();
            w[0] *= 0.95;
            w[1] *= 0.95;
          }
          nextWeightingInterval += 1L;
        }

        /* Increment weighted uptime for all running relays that have not
         * been restarted by seconds since last valid-after time. */
        for (Map.Entry<String, String> runningRelay :
            runningRelays.entrySet()) {
          String fingerprint = runningRelay.getKey();
          boolean restarted = runningRelay.getValue().
              split(",")[2].equals("t");
          long seconds = restarted ? 0 : secondsSinceLastValidAfter;
          if (!wfuHistory.containsKey(fingerprint)) {
            wfuHistory.put(fingerprint, new long[] { seconds, 0L });
          } else {
            wfuHistory.get(fingerprint)[0] += seconds;
          }
        }

        /* Increment total weighted time for all relays by seconds since
         * last valid-after time. */
        for (long[] w : wfuHistory.values()) {
          w[1] += secondsSinceLastValidAfter;
        }

        /* Update MTBF history for running relays.  Start by iterating
         * over all relays in the history, see if they're running now and
         * whether they have been restarted.  Distinguish four cases for
         * relays in the history: 1) still running, 2) still running but
         * restarted, 3) started in this consensus, 4) stopped in this
         * consensus. */
        Set<String> updatedRelays = new HashSet<String>();
        for (Map.Entry<String, double[]> e : mtbfHistory.entrySet()) {
          String fingerprint = e.getKey();
          double[] w = e.getValue();
          if (runningRelays.containsKey(fingerprint)) {
            if (w[2] > 0.1) {
              if (!runningRelays.get(fingerprint).split(",")[2].
                  equals("t")) {

                /* Case 1) still running: */
                w[2] += (double) secondsSinceLastValidAfter;
              } else {

                /* Case 2) still running but restarted: */
                w[0] += w[2];
                w[1] += 1.0;
                w[2] = (double) secondsSinceLastValidAfter;
              }
            } else {

              /* Case 3) started in this consensus: */
              w[2] = (double) secondsSinceLastValidAfter;
            }

            /* Mark relay as already processed, or we'd add it to the
             * history as a new relay below. */
            updatedRelays.add(fingerprint);
          } else if (w[2] > 0.1) {

            /* Case 4) stopped in this consensus: */
            w[0] += w[2];
            w[1] += 1.0;
            w[2] = 0.0;
          }
        }

        /* Iterate over the set of currently running relays and add those
         * that we haven't processed above to our history. */
        for (String fingerprint : runningRelays.keySet()) {
          if (!updatedRelays.contains(fingerprint)) {
            updatedRelays.add(fingerprint);
            mtbfHistory.put(fingerprint, new double[] { 0.0, 0.0,
                (double) secondsSinceLastValidAfter });
          }
        }

        /* Read previously calculated future WFUs, TUNFs, and WBs from
         * disk. */
        Map<String, Long> fwfus = new HashMap<String, Long>(),
            tunfs = new HashMap<String, Long>(),
            fwbs = new HashMap<String, Long>();
        File futureStabilityFile = new File("future-stability",
            formatter.format(lastValidAfterMillis));
        if (!futureStabilityFile.exists()) {
          if (!lastValidAfter.equals(firstValidAfter) &&
              lastValidAfter.compareTo(analyzeFrom) >= 0) {
            System.out.println("Could not find file "
                + futureStabilityFile + ". Skipping simulation!");
          }
        } else if (lastValidAfter.compareTo(analyzeFrom) >= 0) {
          BufferedReader fsBr = new BufferedReader(new FileReader(
              futureStabilityFile));
          String fsLine;
          while ((fsLine = fsBr.readLine()) != null) {
            String[] fsParts = fsLine.split(" ");
            tunfs.put(fsParts[0], Long.parseLong(fsParts[1]));
            fwfus.put(fsParts[0], Long.parseLong(fsParts[2]));
            fwbs.put(fsParts[0], Long.parseLong(fsParts[3]));
          }
          fsBr.close();

          /* Prepare writing results. */
          Map<String, String> results = new HashMap<String, String>();
          results.put("running", "" + runningRelays.size());
          results.put("guard", "" + guardRelays.size());
          results.put("stable", "" + stableRelays.size());

          /* Look up future WFUs and calculate advertised bandwidth
           * percentiles of relays that actually got the Guard flag
           * assigned. */
          long totalFwfu = 0L, totalFwb = 0L;
          List<Long> fwfuList = new ArrayList<Long>();
          for (String fingerprint : guardRelays) {
            long fwfu = fwfus.get(fingerprint);
            totalFwfu += fwfu;
            fwfuList.add(fwfu);
          }
          Collections.sort(fwfuList);
          results.put("mwfu", "" + (totalFwfu / guardRelays.size()));
          results.put("perc15wfu", ""
              + fwfuList.get((15 * fwfuList.size()) / 100));
          results.put("perc10wfu", ""
              + fwfuList.get((10 * fwfuList.size()) / 100));
          results.put("perc5wfu", ""
              + fwfuList.get((5 * fwfuList.size()) / 100));
          List<Long> fwbList = new ArrayList<Long>();
          for (String fingerprint : guardRelays) {
            long fwb = fwbs.get(fingerprint);
            totalFwb += fwb;
            fwbList.add(fwb);
          }
          if (fwbList.size() > 20) {
            Collections.sort(fwbList);
            results.put("perc1fwb", "" + fwbList.get(
                fwbList.size() / 100));
            results.put("perc2fwb", "" + fwbList.get(
                fwbList.size() / 50));
            results.put("perc5fwb", "" + fwbList.get(
                fwbList.size() / 20));
            results.put("perc10fwb", "" + fwbList.get(
                fwbList.size() / 10));
          }

          /* Prepare calculating thresholds for assigning the Guard flag
           * in simulations. */
          List<Long> advertisedBandwidths = new ArrayList<Long>(),
              totalWeightedTimes = new ArrayList<Long>();
          for (Map.Entry<String, String> e : runningRelays.entrySet()) {
            String[] relayParts = e.getValue().split(",");
            long advertisedBandwidth = relayParts[5].length() == 0 ? 0 :
                Long.parseLong(relayParts[5]);
            advertisedBandwidths.add(advertisedBandwidth);
            totalWeightedTimes.add(wfuHistory.get(e.getKey())[1]);
          }
          Collections.sort(advertisedBandwidths);
          Collections.sort(totalWeightedTimes);
          long minimumTotalWeightedTime = totalWeightedTimes.get((
              1 * totalWeightedTimes.size()) / 8);
          results.put("minwta", "" + minimumTotalWeightedTime);
          if (minimumTotalWeightedTime > 8L * 24L * 60L * 60L) {
            minimumTotalWeightedTime = 8L * 24L * 60L * 60L;
          }
          results.put("minwtb", "" + minimumTotalWeightedTime);
          List<Long> weightedFractionalUptimesFamiliar =
              new ArrayList<Long>();
          for (Map.Entry<String, String> e : runningRelays.entrySet()) {
            long[] wfuHistoryEntry = wfuHistory.get(e.getKey());
            long totalWeightedTime = wfuHistoryEntry[1];
            if (totalWeightedTime >= minimumTotalWeightedTime) {
              long weightedFractionalUptime =
                  (10000L * wfuHistoryEntry[0]) / wfuHistoryEntry[1];
              weightedFractionalUptimesFamiliar.add(
                  weightedFractionalUptime);
            }
          }
          Collections.sort(weightedFractionalUptimesFamiliar);
          results.put("familiar",
              "" + weightedFractionalUptimesFamiliar.size());

          /* Run Guard simulation for the relays in the current consensus
           * for various WFU percentiles. */
          for (int wfuPercentile : wfuPercentiles) {
            for (int advBwPercentile : advBwPercentiles) {
              String simulation = wfuPercentile + "wfu" + advBwPercentile
                  + "advbw";
              long minimumAdvertisedBandwidth = advertisedBandwidths.get(
                  (advBwPercentile * advertisedBandwidths.size()) / 100);
              results.put("minadvbwa" + advBwPercentile + "advbw",
                  "" + minimumAdvertisedBandwidth);
              if (minimumAdvertisedBandwidth > 250L * 1024L) {
                minimumAdvertisedBandwidth = 250L * 1024L;
              }
              results.put("minadvbwb" + advBwPercentile + "advbw",
                  "" + minimumAdvertisedBandwidth);
              long minimumWeightedFractionalUptime =
                  weightedFractionalUptimesFamiliar.get((wfuPercentile
                  * weightedFractionalUptimesFamiliar.size()) / 100);
              results.put("minwfua" + wfuPercentile + "wfu",
                  "" + minimumWeightedFractionalUptime);
              if (minimumWeightedFractionalUptime > 9800) {
                minimumWeightedFractionalUptime = 9800;
              }
              results.put("minwfub" + wfuPercentile + "wfu",
                  "" + minimumWeightedFractionalUptime);
              totalFwfu = 0L;
              totalFwb = 0L;
              fwfuList.clear();
              fwbList.clear();
              Set<String> selectedRelays = new HashSet<String>();
              int excladvbw = 0, exclwt = 0, exclwfu = 0;
              for (String relay : runningRelays.values()) {
                boolean notEnoughAdvertisedBandwidth = false,
                    notEnoughTotalWeightedTime = false,
                    notEnoughWeightedFractionalUptime = false,
                    selected = true;
                String[] relayParts = relay.split(",");
                long advertisedBandwidth = relayParts[5].length() == 0 ?
                    0 : Long.parseLong(relayParts[5]);
                if (advertisedBandwidth < minimumAdvertisedBandwidth) {
                  notEnoughAdvertisedBandwidth = true;
                  selected = false;
                }
                String fingerprint = relayParts[1];
                long[] wfuHistoryEntry = wfuHistory.get(fingerprint);
                long totalWeightedTime = wfuHistoryEntry[1];
                if (totalWeightedTime < minimumTotalWeightedTime) {
                  notEnoughTotalWeightedTime = true;
                  selected = false;
                }
                long weightedFractionalUptime =
                    (10000L * wfuHistoryEntry[0]) / wfuHistoryEntry[1];
                if (weightedFractionalUptime <
                    minimumWeightedFractionalUptime) {
                  notEnoughWeightedFractionalUptime = true;
                  selected = false;
                }
                if (selected) {
                  long fwfu = fwfus.get(fingerprint);
                  totalFwfu += fwfu;
                  fwfuList.add(fwfu);
                  long fwb = fwbs.get(fingerprint);
                  totalFwb += fwb;
                  fwbList.add(fwb);
                  selectedRelays.add(fingerprint);
                } else if (guardRelays.contains(fingerprint)) {
                  if (notEnoughWeightedFractionalUptime) {
                    exclwfu++;
                  }
                  if (notEnoughTotalWeightedTime) {
                    exclwt++;
                  }
                  if (notEnoughAdvertisedBandwidth) {
                    excladvbw++;
                  }
                }
              }

              /* Calculate percentiles of future WFU and of advertised
               * bandwidth as the simulation results. */
              results.put("guard" + simulation,
                  "" + selectedRelays.size());
              if (fwfuList.size() > 0) {
                Collections.sort(fwfuList);
                results.put("mwfu" + simulation,
                    "" + (totalFwfu / fwfuList.size()));
                results.put("perc15wfu" + simulation,
                    "" + fwfuList.get((15 * fwfuList.size()) / 100));
                results.put("perc10wfu" + simulation,
                    "" + fwfuList.get((10 * fwfuList.size()) / 100));
                results.put("perc5wfu" + simulation,
                    "" + fwfuList.get((5 * fwfuList.size()) / 100));
              }
              if (fwbList.size() > 20) {
                Collections.sort(fwbList);
                results.put("perc1fwb" + simulation,
                    "" + fwbList.get(fwbList.size() / 100));
                results.put("perc2fwb" + simulation,
                    "" + fwbList.get(fwbList.size() / 50));
                results.put("perc5fwb" + simulation,
                    "" + fwbList.get(fwbList.size() / 20));
                results.put("perc10fwb" + simulation,
                    "" + fwbList.get(fwbList.size() / 10));
              }

              /* If this is the simulation using default values, compare
               * selected Guard relays with observed Guard relays. */
              if (wfuPercentile == 50 && advBwPercentile == 50) {
                Set<String> intersection = new HashSet<String>();
                intersection.addAll(guardRelays);
                intersection.retainAll(selectedRelays);
                Set<String> observedOnly = new HashSet<String>();
                observedOnly.addAll(guardRelays);
                observedOnly.removeAll(selectedRelays);
                Set<String> simulatedOnly = new HashSet<String>();
                simulatedOnly.addAll(selectedRelays);
                simulatedOnly.removeAll(guardRelays);
                results.put("guardintersect", "" + intersection.size());
                results.put("guardobserved", "" + observedOnly.size());
                results.put("guardsimulated", "" + simulatedOnly.size());
                results.put("exclwt", "" + exclwt);
                results.put("exclwfu", "" + exclwfu);
                results.put("excladvbw", "" + excladvbw);
              }
            }
          }

          /* Look up TUNFs of relays that actually got the Stable flag
           * assigned. */
          long totalTunf = 0L;
          List<Long> tunfList = new ArrayList<Long>();
          for (String fingerprint : stableRelays) {
            long tunf = tunfs.get(fingerprint);
            totalTunf += tunf;
            tunfList.add(tunf);
          }
          Collections.sort(tunfList);
          results.put("perc25tunf", ""
              + tunfList.get((25 * tunfList.size()) / 100));
          results.put("perc20tunf", ""
              + tunfList.get((20 * tunfList.size()) / 100));
          results.put("perc15tunf", ""
              + tunfList.get((15 * tunfList.size()) / 100));
          results.put("perc10tunf", ""
              + tunfList.get((10 * tunfList.size()) / 100));
          results.put("perc5tunf", ""
              + tunfList.get((5 * tunfList.size()) / 100));

          /* Prepare calculating thresholds for assigning the Stable flag
           * in simulations. */
          List<Long> wmtbfs = new ArrayList<Long>();
          for (String fingerprint : runningRelays.keySet()) {
            double[] w = mtbfHistory.get(fingerprint);
            double totalRunLength = w[0] + w[2];
            double totalWeights = w[1] + (w[2] > 0.1 ? 1.0 : 0.0);
            long wmtbf = totalWeights < 0.0001 ? 0
                : (long) (totalRunLength / totalWeights);
            wmtbfs.add(wmtbf);
          }
          Collections.sort(wmtbfs);

          /* Run Stable simulation for the relays in the current consensus
           * for various WMTBF percentiles. */
          for (int wmtbfPercentile : wmtbfPercentiles) {
            long minimumWeightedMeanTimeBetweenFailure =
                wmtbfs.get((wmtbfPercentile * wmtbfs.size()) / 100);
            results.put("minwmtbfa" + wmtbfPercentile,
                "" + minimumWeightedMeanTimeBetweenFailure);
            if (minimumWeightedMeanTimeBetweenFailure >
                5L * 24L * 60L * 60L) {
              minimumWeightedMeanTimeBetweenFailure =
                  5L * 24L * 60L * 60L;
            }
            results.put("minwmtbfb" + wmtbfPercentile,
                "" + minimumWeightedMeanTimeBetweenFailure);
            totalTunf = 0L;
            tunfList.clear();
            Set<String> selectedRelays = new HashSet<String>();
            for (String fingerprint : runningRelays.keySet()) {
              double[] w = mtbfHistory.get(fingerprint);
              double totalRunLength = w[0] + w[2];
              double totalWeights = w[1] + (w[2] > 0.1 ? 1.0 : 0.0);
              long wmtbf = totalWeights < 0.0001 ? 0
                  : (long) (totalRunLength / totalWeights);
              if (wmtbf < minimumWeightedMeanTimeBetweenFailure) {
                continue;
              }
              long tunf = tunfs.get(fingerprint);
              totalTunf += tunf;
              tunfList.add(tunf);
              selectedRelays.add(fingerprint);
            }
            results.put("stable" + wmtbfPercentile, "" + tunfList.size());
            if (tunfList.size() > 0L) {
              Collections.sort(tunfList);
              results.put("mtunf" + wmtbfPercentile,
                  "" + (totalTunf / tunfList.size()));
              results.put("perc25tunf"
                  + wmtbfPercentile,
                  "" + tunfList.get((25 * tunfList.size()) / 100));
              results.put("perc20tunf"
                  + wmtbfPercentile,
                  "" + tunfList.get((20 * tunfList.size()) / 100));
              results.put("perc15tunf"
                  + wmtbfPercentile,
                  "" + tunfList.get((15 * tunfList.size()) / 100));
              results.put("perc10tunf"
                  + wmtbfPercentile,
                  "" + tunfList.get((10 * tunfList.size()) / 100));
              results.put("perc5tunf"
                  + wmtbfPercentile,
                  "" + tunfList.get((5 * tunfList.size()) / 100));
            }

            /* If this is the simulation using default values, compare
             * selected Stable relays with observed Stable relays. */
            if (wmtbfPercentile == 50) {
              Set<String> intersection = new HashSet<String>();
              intersection.addAll(stableRelays);
              intersection.retainAll(selectedRelays);
              Set<String> observedOnly = new HashSet<String>();
              observedOnly.addAll(stableRelays);
              observedOnly.removeAll(selectedRelays);
              Set<String> simulatedOnly = new HashSet<String>();
              simulatedOnly.addAll(selectedRelays);
              simulatedOnly.removeAll(stableRelays);
              results.put("stableintersect", "" + intersection.size());
              results.put("stableobserved", "" + observedOnly.size());
              results.put("stablesimulated", "" + simulatedOnly.size());
            }
          }

          /* Write results. */
          bw.write(lastValidAfter);
          for (String column : columns) {
            if (results.containsKey(column)) {
              bw.write("," + results.get(column));
            } else {
              bw.write(",NA");
            }
          }
          bw.write("\n");
        }

        /* We're done with this consensus.  Prepare for the next. */
        runningRelays.clear();
        guardRelays.clear();
        stableRelays.clear();
        lastButOneValidAfter = lastValidAfter;
      }

      /* Add the running relay lines to a map that we parse once we have
       * all lines of a consensus. */
      String fingerprint = parts[1];
      runningRelays.put(fingerprint, line);
      if (parts[3].equals("t")) {
        stableRelays.add(fingerprint);
      }
      if (parts[4].equals("t")) {
        guardRelays.add(fingerprint);
      }
      lastValidAfter = validAfter;
    }
    bw.close();
  }
}

