/**
 * Simulate variation of mean time between failure on Stable relays.  The
 * simulation is based on the previously generated SQL results containing
 * network status entries and parts of server descriptors.  In a first
 * step, parse the SQL results that are in descending order to calculate
 * time until next failure for all relays and write them to disk as one
 * file per network status in tunf/$filename.  (Skip this step if there is
 * already a tunf/ directory.)  In a second step, parse the network
 * statuses again, but this time from first to last, calculate mean times
 * between failure for all relays, form relay subsets based on minimal
 * MTBF, look up what the time until next failure would be for a subset,
 * and write results to mtbf-sim.csv to disk. */
import java.io.*;
import java.text.*;
import java.util.*;
public class SimulateMeanTimeBetweenFailure {
  public static void main(String[] args) throws Exception {

    /* Measure how long this execution takes. */
    long started = System.currentTimeMillis();

    /* Decide whether we need to do the reverse run, or if we can use
     * previous results. */
    if (!new File("tunf").exists()) {

      /* For each relay as identified by its hex encoded fingerprint,
       * track time until next failure in seconds in a long. */
      SortedMap<String, Long> knownRelays = new TreeMap<String, Long>();

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
      while ((line = br.readLine()) != null) {
        if (!line.startsWith("20")) {
          continue;
        }
        String[] parts = line.split(",");
        String validAfter = parts[0];
        if (lastValidAfter != null &&
            !lastValidAfter.equals(validAfter)) {

          /* We just parsed all lines of a consensus.  Let's write times
           * until next failure to disk for all running relays and update
           * our internal history. */
          if (lastButOneValidAfter == null) {
            lastButOneValidAfter = lastValidAfter;
          }
          long lastValidAfterMillis = isoFormatter.parse(lastValidAfter).
              getTime();
          File tunfFile = new File("tunf",
              formatter.format(lastValidAfterMillis));
          tunfFile.getParentFile().mkdirs();
          BufferedWriter bw = new BufferedWriter(new FileWriter(
              tunfFile));
          long secondsSinceLastValidAfter =
              (isoFormatter.parse(lastButOneValidAfter).getTime()
              - lastValidAfterMillis) / 1000L;

          /* Iterate over our history first and see if these relays have
           * been running in the considered consensus.  Remember changes
           * to our history and modify it below to avoid concurrent
           * modification errors. */
          Set<String> removeFromHistory = new HashSet<String>();
          Map<String, Long> addToHistory = new HashMap<String, Long>();
          for (Map.Entry<String, Long> e : knownRelays.entrySet()) {
            String fingerprint = e.getKey();
            if (runningRelays.containsKey(fingerprint)) {

              /* This relay has been running, so write it to the output
               * file and update our history. */
              long hoursUntilFailure = e.getValue();
              bw.write(fingerprint + "," + (secondsSinceLastValidAfter
                  + hoursUntilFailure) + "\n");
              boolean restarted = runningRelays.get(fingerprint).
                  split(",")[2].equals("t");
              if (restarted) {
                removeFromHistory.add(fingerprint);
              } else {
                addToHistory.put(fingerprint, secondsSinceLastValidAfter
                    + hoursUntilFailure);
              }
              runningRelays.remove(fingerprint);
            } else {

              /* This relay has not been running, so remove it from our
               * history. */
              removeFromHistory.add(fingerprint);
            }
          }

          /* Update our history for real now.  We couldn't do this above,
           * or we'd have modified the set we've been iterating over. */
          for (String f : removeFromHistory) {
            knownRelays.remove(f);
          }
          for (Map.Entry<String, Long> e : addToHistory.entrySet()) {
            knownRelays.put(e.getKey(), e.getValue());
          }

          /* Iterate over the relays that we found in the consensus, but
           * that we didn't have in our history. */
          for (Map.Entry<String, String> e : runningRelays.entrySet()) {
            String fingerprint = e.getKey();
            bw.write(fingerprint + ",0\n");
            boolean restarted = e.getValue().split(",")[2].equals("t");
            if (!restarted) {
              knownRelays.put(fingerprint, 0L);
            }
          }
          bw.close();

          /* Prepare for next consensus. */
          runningRelays = new HashMap<String, String>();
          lastButOneValidAfter = lastValidAfter;
        }

        /* Add the running relay lines to a map that we parse once we have
         * all lines of a consensus. */
        String fingerprint = parts[1];
        runningRelays.put(fingerprint, line);
        lastValidAfter = validAfter;
      }
    }

    /* Run the simulation for the following WMTBF percentiles: */
    List<Long> requiredWMTBFs = new ArrayList<Long>();
    for (long l : new long[] { 20, 30, 40, 50, 60, 70, 80 }) {
      requiredWMTBFs.add(l);
    }
    Collections.sort(requiredWMTBFs);
    BufferedWriter bw = new BufferedWriter(new FileWriter(
        "mtbf-sim.csv"));
    bw.write("time");
    for (long requiredWMTBF : requiredWMTBFs) {
      bw.write(",mtunf" + requiredWMTBF + ",perc75tunf" + requiredWMTBF
      + ",perc80tunf" + requiredWMTBF + ",perc85tunf" + requiredWMTBF
      + ",perc90tunf" + requiredWMTBF + ",perc95tunf" + requiredWMTBF
      + ",wmtbf" + requiredWMTBF);
    }
    bw.write("\n");

    /* For each relay as identified by its base-64 encoded fingerprint,
     * track weighted run length, total run weights, and current run
     * length in a double[3]. */
    SortedMap<String, double[]> knownRelays =
        new TreeMap<String, double[]>();

    /* Parse previously exported network status entries again, but this
     * time in forward order. */
    SimpleDateFormat formatter = new SimpleDateFormat(
        "yyyy-MM-dd-HH-mm-ss");
    formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    SimpleDateFormat isoFormatter = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    isoFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    Map<String, String> runningRelays = new HashMap<String, String>(),
        lastRunningRelays = new HashMap<String, String>();
    BufferedReader br = new BufferedReader(new FileReader(
        "running-relays-forward.csv"));
    String line, lastValidAfter = null, firstValidAfter = null;
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

        /* We just parsed all lines of a consensus.  First, see if 12
         * hours have passed since we last discounted weighted run lengths
         * and total run weights.  If so, discount both variables for all
         * known relays by factor 0.95 (or 19/20 since these are long
         * integers) and remove those relays with a total run weight below
         * 1/10000. */
        long lastValidAfterMillis = isoFormatter.parse(lastValidAfter).
            getTime();
        long validAfterMillis = isoFormatter.parse(validAfter).getTime();
        long weightingInterval = validAfterMillis
            / (12L * 60L * 60L * 1000L);
        if (nextWeightingInterval < 0L) {
          nextWeightingInterval = weightingInterval;
        }
        while (weightingInterval > nextWeightingInterval) {
          Set<String> relaysToRemove = new HashSet<String>();
          for (Map.Entry<String, double[]> e : knownRelays.entrySet()) {
            double[] w = e.getValue();
            w[0] *= 0.95;
            w[1] *= 0.95;
          }
          for (String fingerprint : relaysToRemove) {
            knownRelays.remove(fingerprint);
          }
          nextWeightingInterval += 1L;
        }

        /* Update history for running relays.  Start by iterating over all
         * relays in the history, see if they're running now and whether
         * they have been restarted.  Distinguish four cases for relays in
         * the history: 1) still running, 2) still running but restarted,
         * 3) started in this consensus, 4) stopped in this consensus. */
        double secondsSinceLastValidAfter =
            (double) ((validAfterMillis - lastValidAfterMillis) / 1000L);
        Set<String> updatedRelays = new HashSet<String>();
        for (Map.Entry<String, double[]> e : knownRelays.entrySet()) {
          String fingerprint = e.getKey();
          double[] w = e.getValue();
          if (runningRelays.containsKey(fingerprint)) {
            if (w[2] > 0.1) {
              if (!runningRelays.get(fingerprint).split(",")[2].
                  equals("t")) {

                /* Case 1) still running: */
                w[2] += secondsSinceLastValidAfter;
              } else {

                /* Case 2) still running but restarted: */
                w[0] += w[2];
                w[1] += 1.0;
                w[2] = secondsSinceLastValidAfter;
              }
            } else {

              /* Case 3) started in this consensus: */
              w[2] = secondsSinceLastValidAfter;
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
            knownRelays.put(fingerprint, new double[] { 0.0, 0.0,
                secondsSinceLastValidAfter });
          }
        }

        /* Calculate WMTBFs for all running relays and put them in a list
         * that we can sort by WMTBF in descending order. */
        List<String> wmtbfs = new ArrayList<String>();
        for (String fingerprint : runningRelays.keySet()) {
          double[] w = knownRelays.get(fingerprint);
          double totalRunLength = w[0] + w[2];
          double totalWeights = w[1] + (w[2] > 0.1 ? 1.0 : 0.0);
          long wmtbf = totalWeights < 0.0001 ? 0
              : (long) (totalRunLength / totalWeights);
          wmtbfs.add(String.format("%012d %s", wmtbf, fingerprint));
        }
        Collections.sort(wmtbfs, Collections.reverseOrder());

        /* Read previously calculated TUNFs from disk. */
        Map<String, Long> tunfs = new HashMap<String, Long>();
        File tunfFile = new File("tunf",
            formatter.format(lastValidAfterMillis));
        if (!tunfFile.exists()) {
          if (!lastValidAfter.equals(firstValidAfter)) {
            System.out.println("Could not find file " + tunfFile
                + ". Skipping simulation!");
          }
        } else {
          BufferedReader tunfBr = new BufferedReader(new FileReader(
              tunfFile));
          String tunfLine;
          while ((tunfLine = tunfBr.readLine()) != null) {
            String[] tunfParts = tunfLine.split(",");
            tunfs.put(tunfParts[0], Long.parseLong(tunfParts[1]));
          }
          tunfBr.close();

          /* Run the simulation for the relays in the current consensus
           * for various required WFUs. */
          bw.write(isoFormatter.format(lastValidAfterMillis));
          long totalRelays = (long) wmtbfs.size(), selectedRelays = 0L,
              totalTunf = 0L, minimalWmtbf = 0L;
          int simulationIndex = 0;
          List<Long> tunfList = new ArrayList<Long>();
          for (String relay : wmtbfs) {
            while (simulationIndex < requiredWMTBFs.size() &&
                selectedRelays * 100L > totalRelays
                * requiredWMTBFs.get(simulationIndex)) {
              if (selectedRelays == 0L) {
                bw.write(",NA,NA,NA,NA,NA,NA");
              } else {
                Collections.sort(tunfList, Collections.reverseOrder());
                long perc75 = tunfList.get((75 * tunfList.size()) / 100);
                long perc80 = tunfList.get((80 * tunfList.size()) / 100);
                long perc85 = tunfList.get((85 * tunfList.size()) / 100);
                long perc90 = tunfList.get((90 * tunfList.size()) / 100);
                long perc95 = tunfList.get((95 * tunfList.size()) / 100);
                bw.write("," + (totalTunf / selectedRelays) + "," + perc75
                    + "," + perc80 + "," + perc85 + "," + perc90 + ","
                    + perc95);
              }
              bw.write("," + minimalWmtbf);
              simulationIndex++;
            }
            String[] wmtbfParts = relay.split(" ");
            minimalWmtbf = Long.parseLong(wmtbfParts[0]);
            String fingerprint = wmtbfParts[1];
            long tunf = tunfs.get(fingerprint);
            totalTunf += tunf;
            tunfList.add(tunf);
            selectedRelays += 1L;
          }
          bw.write("\n");
        }

        /* We're done with this consensus.  Prepare for the next. */
        lastRunningRelays = runningRelays;
        runningRelays = new HashMap<String, String>();
      }

      /* Add the running relay lines to a map that we parse once we have
       * all lines of a consensus. */
      String fingerprint = parts[1];
      runningRelays.put(fingerprint, line);
      lastValidAfter = validAfter;
    }
    bw.close();

    /* Print how long this execution took and exit. */
    System.out.println("Execution took " + ((System.currentTimeMillis()
        - started) / (60L * 1000L)) + " minutes.");
  }
}

