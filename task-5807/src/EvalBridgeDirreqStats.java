import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.torproject.descriptor.BridgeNetworkStatus;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;
import org.torproject.descriptor.ExtraInfoDescriptor;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;

/* Extract relevant pieces of information from relay consensuses and
 * bridge descriptors to estimate daily bridge users.  See README for
 * usage instructions. */
public class EvalBridgeDirreqStats {
  public static void main(String[] args) throws Exception {

    /* Parse relay consensuses from in/relay-descriptors/.  Skip this step
     * if in/relay-descriptors/ does not exist. */
    File consensusesDirectory = new File("in/relay-descriptors");
    File hashedFingerprintsFile = new File("out/hashed-fingerprints");
    File consensusesPerDayFile = new File("out/consensuses-per-day");
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    if (consensusesDirectory.exists()) {
      SortedSet<String> hashedFingerprints = new TreeSet<String>();
      SortedMap<String, Integer> consensusesPerDay =
          new TreeMap<String, Integer>();
      DescriptorReader descriptorReader =
          DescriptorSourceFactory.createDescriptorReader();
      descriptorReader.addDirectory(consensusesDirectory);
      Iterator<DescriptorFile> descriptorFiles =
          descriptorReader.readDescriptors();
      while (descriptorFiles.hasNext()) {
        DescriptorFile descriptorFile = descriptorFiles.next();
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {
          if (!(descriptor instanceof RelayNetworkStatusConsensus)) {
            continue;
          }
          RelayNetworkStatusConsensus consensus =
              (RelayNetworkStatusConsensus) descriptor;

          /* Extract hashed fingerprints of all known relays to remove
           * those fingerprints from bridge usage statistics later on. */
          for (NetworkStatusEntry statusEntry :
              consensus.getStatusEntries().values()) {
            hashedFingerprints.add(Hex.encodeHexString(DigestUtils.sha(
                Hex.decodeHex(statusEntry.getFingerprint().
                toCharArray()))).toUpperCase());
          }

          /* Count the number of consensuses per day. */
          String date = dateFormat.format(
              consensus.getValidAfterMillis());
          int consensuses = 1;
          if (consensusesPerDay.containsKey(date)) {
            consensuses += consensusesPerDay.get(date);
          }
          consensusesPerDay.put(date, consensuses);
        }
      }
      hashedFingerprintsFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          hashedFingerprintsFile));
      for (String hashedFingerprint : hashedFingerprints) {
        bw.write(hashedFingerprint + "\n");
      }
      bw.close();
      consensusesPerDayFile.getParentFile().mkdirs();
      bw = new BufferedWriter(new FileWriter(consensusesPerDayFile));
      for (Map.Entry<String, Integer> e : consensusesPerDay.entrySet()) {
        bw.write(e.getKey() + "," + e.getValue() + "\n");
      }
      bw.close();
    }

    /* Parse bridge network statuses from in/bridge-descriptors/.  Skip
     * this step if in/bridge-descriptors/ does not exist. */
    File bridgeDescriptorsDirectory = new File("in/bridge-descriptors");
    File bridgesPerDayFile = new File("out/bridges-per-day");
    File dirreqResponsesFile = new File("out/dirreq-responses");
    File dirreqWriteHistoryFile = new File("out/dirreq-write-history");
    File bridgeStatsUsersFile = new File("out/bridge-stats-users");
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    if (bridgeDescriptorsDirectory.exists()) {

      /* Read hashed fingerprints from disk, so that we can include in the
       * intermediate files whether a bridge was running as non-bridge
       * relay before. */
      SortedSet<String> hashedFingerprints = new TreeSet<String>();
      String line;
      BufferedReader br = new BufferedReader(new FileReader(
          hashedFingerprintsFile));
      while ((line = br.readLine()) != null) {
        hashedFingerprints.add(line.toUpperCase());
      }
      br.close();

      /* Prepare data structures for first collecting everything we parse.
       * There may be duplicates which we can best remove in memory. */
      SortedMap<String, List<Integer>> bridgesPerDay =
          new TreeMap<String, List<Integer>>();
      SortedSet<String> dirreqResponses = new TreeSet<String>();
      SortedMap<String, SortedMap<Long, Long>> dirreqWriteHistory =
          new TreeMap<String, SortedMap<Long, Long>>();
      SortedSet<String> bridgeIps = new TreeSet<String>();

      /* Parse everything in in/bridge-descriptors/. */
      DescriptorReader descriptorReader =
          DescriptorSourceFactory.createDescriptorReader();
      descriptorReader.addDirectory(bridgeDescriptorsDirectory);
      Iterator<DescriptorFile> descriptorFiles =
          descriptorReader.readDescriptors();
      while (descriptorFiles.hasNext()) {
        DescriptorFile descriptorFile = descriptorFiles.next();
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {
          if (descriptor instanceof BridgeNetworkStatus) {
            BridgeNetworkStatus status = (BridgeNetworkStatus) descriptor;

            /* Extract number of running bridges to calculate daily means.
             * Skip network statuses where less than 1% of bridges have
             * the Running flag. */
            String date = dateFormat.format(status.getPublishedMillis());
            int totalBridges = 0, runningBridges = 0;
            for (NetworkStatusEntry statusEntry :
                status.getStatusEntries().values()) {
              totalBridges++;
              if (statusEntry.getFlags().contains("Running")) {
                runningBridges++;
              }
            }
            if (runningBridges * 100 > totalBridges) {
              if (!bridgesPerDay.containsKey(date)) {
                bridgesPerDay.put(date, new ArrayList<Integer>());
              }
              bridgesPerDay.get(date).add(runningBridges);
            }
          } else if (descriptor instanceof ExtraInfoDescriptor) {
            ExtraInfoDescriptor extraInfoDescriptor =
                (ExtraInfoDescriptor) descriptor;
            String fingerprint = extraInfoDescriptor.getFingerprint().
                toUpperCase();
            String wasSeenAsRelay = hashedFingerprints.contains(
                fingerprint) ? "TRUE" : "FALSE";

            /* Extract v3 directory request response numbers from dirreq
             * stats, if available. */
            if (extraInfoDescriptor.getDirreqStatsEndMillis() >= 0 &&
                extraInfoDescriptor.getDirreqStatsIntervalLength()
                  == 86400 &&
                extraInfoDescriptor.getDirreqV3Resp() != null &&
                extraInfoDescriptor.getDirreqV3Resp().containsKey("ok")) {
              String dirreqStatsEnd = dateTimeFormat.format(
                  extraInfoDescriptor.getDirreqStatsEndMillis());
              SortedMap<String, Integer> resp =
                  extraInfoDescriptor.getDirreqV3Resp();
              String ok = String.valueOf(resp.get("ok"));
              String notEnoughSigs = resp.containsKey("not-enough-sigs")
                  ? String.valueOf(resp.get("not-enough-sigs")) : "NA";
              String unavailable = resp.containsKey("unavailable")
                  ? String.valueOf(resp.get("unavailable")) : "NA";
              String notFound = resp.containsKey("not-found")
                  ? String.valueOf(resp.get("not-found")) : "NA";
              String notModified = resp.containsKey("not-modified")
                  ? String.valueOf(resp.get("not-modified")) : "NA";
              String busy = resp.containsKey("busy")
                  ? String.valueOf(resp.get("busy")) : "NA";
              dirreqResponses.add(String.format(
                  "%s,%s,%s,%s,%s,%s,%s,%s%n", dirreqStatsEnd,
                  fingerprint, wasSeenAsRelay, ok, notEnoughSigs,
                  unavailable, notFound, notModified, busy));
            }

            /* Extract written directory bytes, if available. */
            if (extraInfoDescriptor.getDirreqWriteHistory() != null &&
                extraInfoDescriptor.getDirreqWriteHistory().
                getIntervalLength() == 900) {
              if (!dirreqWriteHistory.containsKey(fingerprint)) {
                dirreqWriteHistory.put(fingerprint,
                    new TreeMap<Long, Long>());
              }
              dirreqWriteHistory.get(fingerprint).putAll(
                  extraInfoDescriptor.getDirreqWriteHistory().
                  getBandwidthValues());
            }

            /* Sum up unique IP address counts from .sy and from all
             * countries from bridge stats, if available. */
            if (extraInfoDescriptor.getBridgeStatsEndMillis() >= 0 &&
                extraInfoDescriptor.getBridgeStatsIntervalLength()
                  == 86400 &&
                extraInfoDescriptor.getBridgeIps() != null) {
              String bridgeStatsEnd = dateTimeFormat.format(
                  extraInfoDescriptor.getBridgeStatsEndMillis());
              int sy = 0, all = 0;
              for (Map.Entry<String, Integer> e :
                  extraInfoDescriptor.getBridgeIps().entrySet()) {
                String country = e.getKey();
                int adjustedIps = e.getValue() - 4;
                if (country.equals("sy")) {
                  sy = adjustedIps;
                }
                all += adjustedIps;
              }
              bridgeIps.add(String.format("%s,%s,%s,%d,%d%n",
                  bridgeStatsEnd, fingerprint, wasSeenAsRelay, sy, all));
            }
          }
        }
      }

      /* Write to disk what we learned while parsing bridge extra-info
       * descriptors. */
      bridgesPerDayFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          bridgesPerDayFile));
      for (Map.Entry<String, List<Integer>> e :
          bridgesPerDay.entrySet()) {
        String date = e.getKey();
        List<Integer> bridges = e.getValue();
        int sum = 0;
        for (int b : bridges) {
          sum += b;
        }
        bw.write(String.format("%s,%d%n", date, sum / bridges.size()));
      }
      bw.close();
      dirreqResponsesFile.getParentFile().mkdirs();
      bw = new BufferedWriter(new FileWriter(dirreqResponsesFile));
      for (String resp : dirreqResponses) {
        bw.write(resp);
      }
      bw.close();
      bridgeStatsUsersFile.getParentFile().mkdirs();
      bw = new BufferedWriter(new FileWriter(bridgeStatsUsersFile));
      for (String ips : bridgeIps) {
        bw.write(ips);
      }
      bw.close();
      bw = new BufferedWriter(new FileWriter(dirreqWriteHistoryFile));
      for (Map.Entry<String, SortedMap<Long, Long>> e :
          dirreqWriteHistory.entrySet()) {
        String fingerprint = e.getKey();
        String wasSeenAsRelay = hashedFingerprints.contains(
            fingerprint) ? "TRUE" : "FALSE";
        for (Map.Entry<Long, Long> f : e.getValue().entrySet()) {
          String historyIntervalEnd = dateTimeFormat.format(f.getKey());
          bw.write(String.format("%s,%s,%d,%s%n", fingerprint,
              historyIntervalEnd, f.getValue(), wasSeenAsRelay));
        }
      }
      bw.close();
    }

    /* Aggregate the parse results from above and write relevant data for
     * estimating daily bridge users to disk.  Write results to
     * out/bridge-dirreq-stats.  This step is distinct from the parsing
     * steps, so that the parsing only has to be done once, whereas the
     * aggregation can be tweaked and re-run easily. */
    File bridgeDirreqStatsNoRelaysFile =
        new File("out/bridge-dirreq-stats-no-relays");
    File bridgeDirreqStatsAllBridgesFile =
        new File("out/bridge-dirreq-stats-all-bridges");
    if (bridgesPerDayFile.exists() &&
        dirreqResponsesFile.exists() &&
        bridgeStatsUsersFile.exists() &&
        dirreqWriteHistoryFile.exists() &&
        consensusesPerDayFile.exists()) {

      /* Run the aggregation twice, once for all bridges and once for only
       * bridges which haven't been seen as non-bridge relays before. */
      boolean[] exclude = new boolean[] { true, false };
      File[] outFiles = new File[] { bridgeDirreqStatsNoRelaysFile,
          bridgeDirreqStatsAllBridgesFile };
      for (int r = 0; r < 2; r++) {
        boolean excludeHashedFingerprints = exclude[r];
        File outFile = outFiles[r];

        /* Read parse results back to memory. */
        SortedMap<String, Integer> bridgesPerDay =
            new TreeMap<String, Integer>();
        BufferedReader br = new BufferedReader(new FileReader(
            bridgesPerDayFile));
        String line;
        while ((line = br.readLine()) != null) {
          String[] parts = line.split(",");
          bridgesPerDay.put(parts[0], Integer.parseInt(parts[1]));
        }
        br.close();
        SortedMap<String, SortedMap<Long, Long>> dirreqOkResponses =
            new TreeMap<String, SortedMap<Long, Long>>();
        br = new BufferedReader(new FileReader(dirreqResponsesFile));
        while ((line = br.readLine()) != null) {
          String[] parts = line.split(",");
          if (excludeHashedFingerprints && parts[2].equals("TRUE")) {
            /* Skip, because this bridge has been seen as relay before. */
            continue;
          }
          String fingerprint = parts[1].toUpperCase();
          long dirreqStatsEndMillis = dateTimeFormat.parse(parts[0]).
              getTime();
          long ok = Long.parseLong(parts[3]);
          if (!dirreqOkResponses.containsKey(fingerprint)) {
            dirreqOkResponses.put(fingerprint, new TreeMap<Long, Long>());
          }
          dirreqOkResponses.get(fingerprint).put(dirreqStatsEndMillis,
              ok);
        }
        br.close();
        SortedMap<String, long[]> ipsPerDay =
            new TreeMap<String, long[]>();
        br = new BufferedReader(new FileReader(bridgeStatsUsersFile));
        while ((line = br.readLine()) != null) {
          String[] parts = line.split(",");
          if (excludeHashedFingerprints && parts[2].equals("TRUE")) {
            /* Skip, because this bridge has been seen as relay before. */
            continue;
          }
          long bridgeStatsEndMillis = dateTimeFormat.parse(parts[0]).
              getTime();
          long bridgeStatsStartMillis = bridgeStatsEndMillis - 86400000L;
          long currentStartMillis = bridgeStatsStartMillis;

          /* Find UTC date break in the interval and make sure that we
           * distribute IPs to the two days correctly. */
          String[] dates = new String[] {
            dateFormat.format(bridgeStatsStartMillis),
            dateFormat.format(bridgeStatsEndMillis) };
          long[] seconds = new long[2];
          if (!dates[0].equals(dates[1])) {
            long dateBreakMillis = (bridgeStatsEndMillis / 86400000L)
                * 86400000L;
            seconds[0] = (dateBreakMillis - bridgeStatsStartMillis)
                / 1000L;
            bridgeStatsStartMillis = dateBreakMillis;
          }
          seconds[1] = (bridgeStatsEndMillis - bridgeStatsStartMillis)
              / 1000L;

          /* Update per-day counters. */
          for (int i = 0; i < dates.length; i++) {
            String date = dates[i];
            long sy = seconds[i] * Long.parseLong(parts[3]);
            long all = seconds[i] * Long.parseLong(parts[4]);
            if (!ipsPerDay.containsKey(date)) {
              ipsPerDay.put(date, new long[] { 0L, 0L });
            }
            ipsPerDay.get(date)[0] += sy;
            ipsPerDay.get(date)[1] += all;
          }
        }
        br.close();
        SortedMap<String, Integer> consensusesPerDay =
            new TreeMap<String, Integer>();
        br = new BufferedReader(new FileReader(consensusesPerDayFile));
        while ((line = br.readLine()) != null) {
          String[] parts = line.split(",");
          consensusesPerDay.put(parts[0], Integer.parseInt(parts[1]));
        }
        br.close();
        br = new BufferedReader(new FileReader(dirreqWriteHistoryFile));
        SortedMap<String, SortedMap<Long, Long>> dirreqWriteHistory =
            new TreeMap<String, SortedMap<Long, Long>>();
        while ((line = br.readLine()) != null) {
          String[] parts = line.split(",");
          if (excludeHashedFingerprints && parts[3].equals("TRUE")) {
            /* Skip, because this bridge has been seen as relay before. */
            continue;
          }
          String fingerprint = parts[0].toUpperCase();
          long historyIntervalEndMillis = dateTimeFormat.parse(parts[1]).
              getTime();
          long writtenBytes = Long.parseLong(parts[2]);
          if (!dirreqWriteHistory.containsKey(fingerprint)) {
            dirreqWriteHistory.put(fingerprint, new TreeMap<Long, Long>());
          }
          dirreqWriteHistory.get(fingerprint).put(historyIntervalEndMillis,
              writtenBytes);
        }
        br.close();

        /* For every day, count reported v3 directory request responses,
         * reported written directory bytes, and reporting bridges.
         * Distinguish between bridges reporting both responses and bytes,
         * bridges reporting only responses, and bridges reporting.  Map
         * keys are dates, map values are the number of responses, bytes,
         * or bridges. */
        SortedMap<String, Long>
            responsesReportingBoth = new TreeMap<String, Long>(),
            responsesNotReportingBytes = new TreeMap<String, Long>(),
            bytesReportingBoth = new TreeMap<String, Long>(),
            bytesNotReportingResponses = new TreeMap<String, Long>(),
            bridgesReportingBoth = new TreeMap<String, Long>(),
            bridgesNotReportingBytes = new TreeMap<String, Long>(),
            bridgesNotReportingResponses = new TreeMap<String, Long>();

        /* Consider each bridge separately. */
        SortedSet<String> allFingerprints = new TreeSet<String>();
        allFingerprints.addAll(dirreqOkResponses.keySet());
        allFingerprints.addAll(dirreqWriteHistory.keySet());
        for (String fingerprint : allFingerprints) {

          /* Obtain iterators over dirreq stats intervals and dirreq write
           * history intervals, from oldest to newest.  Either iterator
           * may contain zero elements if the bridge did not report any
           * values, but not both. */
          SortedMap<Long, Long> bridgeDirreqOkResponses =
              dirreqOkResponses.containsKey(fingerprint) ?
              dirreqOkResponses.get(fingerprint) :
              new TreeMap<Long, Long>();
          SortedMap<Long, Long> bridgeDirreqWriteHistory =
              dirreqWriteHistory.containsKey(fingerprint) ?
              dirreqWriteHistory.get(fingerprint) :
              new TreeMap<Long, Long>();
          Iterator<Long> responsesIterator =
              bridgeDirreqOkResponses.keySet().iterator();
          Iterator<Long> historyIterator =
              bridgeDirreqWriteHistory.keySet().iterator();

          /* Keep references to the currently considered intervals. */
          long responseEndMillis = responsesIterator.hasNext() ?
              responsesIterator.next() : Long.MAX_VALUE;
          long historyEndMillis = historyIterator.hasNext() ?
              historyIterator.next() : Long.MAX_VALUE;

          /* Keep the time until when we have processed statistics. */
          long currentStartMillis = 0L;

          /* Iterate over both responses and byte histories until we set
           * both to Long.MAX_VALUE, indicating that there are no further
           * values. */
          while (responseEndMillis < Long.MAX_VALUE ||
              historyEndMillis < Long.MAX_VALUE) {

            /* Dirreq-stats intervals are guaranteed to be 24 hours long,
             * and dirreq-write-history intervals are 15 minutes long.
             * This is guaranteed in the parsing code above.  It allows us
             * to calculate interval starts.  Also, if we have already
             * processed part of an interval, move the considered interval
             * start accordingly. */
            long historyStartMillis = Math.max(currentStartMillis,
                historyEndMillis - 900000L);
            long responseStartMillis = Math.max(currentStartMillis,
                responseEndMillis - 86400000L);

            /* Determine start and end time of the next interval, and
             * whether the bridge reported dirreq-stats in that interval,
             * or dirreq histories, or both. */
            long currentEndMillis;
            boolean addHistory = false, addResponses = false;
            if (historyStartMillis < responseStartMillis) {
              currentStartMillis = historyStartMillis;
              currentEndMillis = Math.min(historyEndMillis,
                  responseStartMillis);
              addHistory = true;
            } else if (responseStartMillis < historyStartMillis) {
              currentStartMillis = responseStartMillis;
              currentEndMillis = Math.min(historyStartMillis,
                  responseEndMillis);
              addResponses = true;
            } else {
              currentStartMillis = historyStartMillis;
              currentEndMillis = Math.min(historyEndMillis,
                  responseEndMillis);
              addHistory = true;
              addResponses = true;
            }

            /* Depending on which statistics the bridge reported in the
             * determined interval, obtain the number of bytes or
             * responses to add. */
            long bytesInInterval = 0L, responsesInInterval = 0L;
            if (addHistory) {
              bytesInInterval = bridgeDirreqWriteHistory.
                  get(historyEndMillis);
            }
            if (addResponses) {
              responsesInInterval = bridgeDirreqOkResponses.
                  get(responseEndMillis);
            }

            /* Find out if there is a UTC date break in the interval to be
             * added.  If there is, make sure that we distribute responses
             * and bytes to the two days correctly. */
            String[] dates = new String[] {
              dateFormat.format(currentStartMillis),
              dateFormat.format(currentEndMillis) };
            long[] seconds = new long[2];
            if (!dates[0].equals(dates[1])) {
              long dateBreakMillis = (currentEndMillis / 86400000L)
                  * 86400000L;
              seconds[0] = (dateBreakMillis - currentStartMillis) / 1000L;
              currentStartMillis = dateBreakMillis;
            }
            seconds[1] = (currentEndMillis - currentStartMillis) / 1000L;

            /* Update per-day counters. */
            for (int i = 0; i < dates.length; i++) {
              String date = dates[i];
              long bytes = seconds[i] * bytesInInterval;
              long responses = seconds[i] * responsesInInterval;
              if (!bytesReportingBoth.containsKey(date)) {
                bytesReportingBoth.put(date, 0L);
                bytesNotReportingResponses.put(date, 0L);
                responsesReportingBoth.put(date, 0L);
                responsesNotReportingBytes.put(date, 0L);
                bridgesReportingBoth.put(date, 0L);
                bridgesNotReportingBytes.put(date, 0L);
                bridgesNotReportingResponses.put(date, 0L);
              }
              if (addHistory) {
                if (addResponses) {
                  bytesReportingBoth.put(date,
                      bytesReportingBoth.get(date) + bytes);
                  responsesReportingBoth.put(date,
                      responsesReportingBoth.get(date) + responses);
                  bridgesReportingBoth.put(date,
                      bridgesReportingBoth.get(date) + seconds[i]);
                } else {
                  bytesNotReportingResponses.put(date,
                      bytesNotReportingResponses.get(date) + bytes);
                  bridgesNotReportingResponses.put(date,
                      bridgesNotReportingResponses.get(date)
                      + seconds[i]);
                }
              } else if (addResponses) {
                responsesNotReportingBytes.put(date,
                    responsesNotReportingBytes.get(date) + responses);
                bridgesNotReportingBytes.put(date,
                    bridgesNotReportingBytes.get(date) + seconds[i]);
              }
            }

            /* Move next interval start to the current interval end, and
             * possibly move to the next stats intervals.  If we have run
             * out of intervals in either or both of the sets, change the
             * reference to Long.MAX_VALUE to add the other intervals and
             * finally exit the loop. */
            currentStartMillis = currentEndMillis;
            if (historyEndMillis <= currentStartMillis) {
              historyEndMillis = historyIterator.hasNext() ?
                  historyIterator.next() : Long.MAX_VALUE;
            }
            if (responseEndMillis <= currentStartMillis) {
              responseEndMillis = responsesIterator.hasNext() ?
                  responsesIterator.next() : Long.MAX_VALUE;
            }
          }
        }

        /* Put together what we learned about bridge usage per day. */
        outFile.getParentFile().mkdirs();
        BufferedWriter bw = new BufferedWriter(new FileWriter(outFile));
        bw.write("date,nabcd,sy,consensuses,ha,hc,ra,rb,na,nb,nc\n");
        for (String date : bytesReportingBoth.keySet()) {
          String bridges = "NA";
          if (bridgesPerDay.containsKey(date)) {
            bridges = String.valueOf(bridgesPerDay.get(date) * 86400L);
          }
          String sy = "NA";
          if (ipsPerDay.containsKey(date)) {
            long[] ips = ipsPerDay.get(date);
            sy = String.format("%.5f", ((double) ips[0])
                / ((double) ips[1]));
          }
          String consensuses = "NA";
          if (consensusesPerDay.containsKey(date)) {
            consensuses = String.valueOf(consensusesPerDay.get(date));
          }
          bw.write(String.format("%s,%s,%s,%s,%d,%d,%d,%d,%d,%d,%d%n",
              date, bridges, sy, consensuses,
              bytesReportingBoth.get(date),
              bytesNotReportingResponses.get(date),
              responsesReportingBoth.get(date),
              responsesNotReportingBytes.get(date),
              bridgesReportingBoth.get(date),
              bridgesNotReportingBytes.get(date),
              bridgesNotReportingResponses.get(date)));
        }
        bw.close();
      }
    }
  }
}

