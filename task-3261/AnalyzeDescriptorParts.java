import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

/* Analyze descriptors parts bridge by bridge and determine whether a
 * bridge reported usage statistics at a given time, and if not, find out
 * why not. */
public class AnalyzeDescriptorParts {
  public static void main(String[] args) throws Exception {

    /* Define paths: we read descriptor part files from temp/ and append
     * statistics on half hour detail to eval-out.csv. */
    File tempDirectory = new File("temp");
    File evalOutFile = new File("eval-out.csv");

    /* Parse descriptor part files bridge by bridge. */
    SimpleDateFormat dateTimeFormat =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    final long HALF_HOUR = 30L * 60L * 1000L;
    BufferedWriter ebw = new BufferedWriter(new FileWriter(evalOutFile));
    for (File tempFile : tempDirectory.listFiles()) {
      String fingerprint = tempFile.getName();
      BufferedReader br = new BufferedReader(new FileReader(tempFile));
      String line;

      /* For each bridge, determine when it was first seen as relay.  All
       * timestamps are in half hours since 1970-01-01 00:00:00 UTC. */
      long firstRunningRelay = Long.MAX_VALUE;

      /* For each time the bridge was listed in a bridge network status as
       * Running, remember the status publication time and referenced
       * descriptor digest. */
      SortedMap<Long, String> runningBridgeHalfHours =
          new TreeMap<Long, String>();

      /* For each descriptor published by the bridge, remember seven
       * timestamps in an array:
       * 0: when the bridge was started due to the descriptor publication
       *    time and reported uptime,
       * 1: when the descriptor was published,
       * 2: when the descriptor was first referenced in a status,
       * 3: when the descriptor was last referenced in status,
       * 4: when the first descriptor in the same uptime session was first
       *    referenced in a status,
       * 5: when the last descriptor in the same uptime session was last
       *    referenced in a status, and
       * 6: when the last descriptor in the same uptime session was
       *    published. */
      Map<String, long[]> descriptorSessions =
          new HashMap<String, long[]>();

      /* For each descriptor, remember the platform string. */
      Map<String, String> descriptorPlatforms =
          new HashMap<String, String>();

      /* For each bridge-stats or geoip-stats line, remember a long[] with
       * two timestamps and a boolean:
       * 0: when the statistics interval started,
       * 1: when the statistics interval ended,
       * 2: whether the bridge reported its geoip file digest (only
       *    0.2.3.x or higher). */
      SortedMap<Long, long[]> bridgeStats = new TreeMap<Long, long[]>(),
          geoipStats = new TreeMap<Long, long[]>();

      /* Parse the file in temp/ line by line. */
      while ((line = br.readLine()) != null) {

        /* Remember when a descriptor was published and which platform
         * string it contained. */
        if (line.startsWith("server-descriptor ")) {
          String[] parts = line.split(" ");
          long publishedMillis = dateTimeFormat.parse(parts[1] + " "
              + parts[2]).getTime();
          long publishedHalfHour = publishedMillis / HALF_HOUR + 1L;
          String descriptor = parts[3];
          long startedHalfHour = (publishedMillis
              - Long.parseLong(parts[4]) * 1000L) / HALF_HOUR + 1L;
          long[] descriptorSession;
          if (descriptorSessions.containsKey(descriptor)) {
            descriptorSession = descriptorSessions.get(descriptor);
          } else {
            descriptorSession = new long[7];
            descriptorSessions.put(descriptor, descriptorSession);
          }
          if (descriptorSession[0] == 0) {
            descriptorSession[0] = startedHalfHour;
            descriptorSession[1] = publishedHalfHour;
          }
          String platform = line.substring(line.indexOf("Tor "));
          descriptorPlatforms.put(descriptor, platform);

        /* Remember when a descriptor was first and last referenced from a
         * bridge network status. */
        } else if (line.startsWith("running-bridge ")) {
          String[] parts = line.split(" ");
          long publishedMillis = dateTimeFormat.parse(parts[1] + " "
              + parts[2]).getTime();
          long publishedHalfHour = publishedMillis / HALF_HOUR;
          String descriptor = parts[3];
          long[] descriptorSession;
          if (descriptorSessions.containsKey(descriptor)) {
            descriptorSession = descriptorSessions.get(descriptor);
            if (descriptorSession[2] == 0 ||
                publishedHalfHour < descriptorSession[2]) {
              descriptorSession[2] = publishedHalfHour;
            }
            if (publishedHalfHour > descriptorSession[3]) {
              descriptorSession[3] = publishedHalfHour;
            }
          } else {
            descriptorSession = new long[7];
            descriptorSession[2] = publishedHalfHour;
            descriptorSession[3] = publishedHalfHour;
            descriptorSessions.put(descriptor, descriptorSession);
          }
          runningBridgeHalfHours.put(publishedHalfHour, descriptor);

        /* Remember the start and end of a bridge-stats or geoip-stats
         * interval, and remember whether the extra-info descriptor
         * contained a geoip-db-digest line. */
        } else if (line.startsWith("bridge-stats ") ||
            line.startsWith("geoip-stats ")) {
          String parts[] = line.split(" ");
          long statsEndMillis = dateTimeFormat.parse(parts[1] + " "
              + parts[2]).getTime();
          long statsEnd = statsEndMillis / HALF_HOUR;
          long statsStart = (statsEndMillis - Long.parseLong(parts[3])
              * 1000L) / HALF_HOUR;
          boolean hasGeoipFile = !parts[4].equals("NA");
          long[] stats = new long[3];
          stats[0] = statsStart;
          stats[1] = statsEnd;
          stats[2] = hasGeoipFile ? 1L : 0L;
          if (line.startsWith("bridge-stats ")) {
            bridgeStats.put(statsStart, stats);
          } else {
            geoipStats.put(statsStart, stats);
          }

        /* Remember when this bridge was first seen as a relay in the
         * consensus. */
        } else if (line.startsWith("running-relay ")) {
          long runningRelayMillis = dateTimeFormat.parse(line.substring(
              "running-relay ".length())).getTime() / HALF_HOUR;
          firstRunningRelay = Math.min(firstRunningRelay,
              runningRelayMillis);
        }
      }
      br.close();

      /* Sort descriptors by their first reference in a bridge network
       * status. */
      SortedMap<Long, String> descriptorsByFirstReferenced =
          new TreeMap<Long, String>();
      for (Map.Entry<String, long[]> e : descriptorSessions.entrySet()) {
        if (e.getValue()[2] == 0) {
          continue;
        }
        descriptorsByFirstReferenced.put(e.getValue()[2], e.getKey());
      }
      if (descriptorsByFirstReferenced.isEmpty()) {
        continue;
      }

      /* Go through list of descriptors and see if two or more of them
       * belong to the same bridge uptime session.  Two descriptors are
       * considered as part of the same uptime session if a) they are
       * referenced from two subsequent statuses and b) the start time in
       * the second descriptor lies before the publication time of the
       * first descriptor.  First make a list of all descriptors of a
       * session and then update their long[] values to contain session
       * information. */
      long[] previousDescriptorTimestamps = null;
      long firstStatusInSession = Long.MAX_VALUE,
          lastStatusInSession = -1L, lastDescriptorPublished = -1L;
      Set<String> descriptorsInSession = new HashSet<String>();
      for (String descriptor : descriptorsByFirstReferenced.values()) {
        long[] currentDescriptorTimestamps =
            descriptorSessions.get(descriptor);
        String currentDescriptor = descriptor;
        if (previousDescriptorTimestamps != null) {
          boolean sameSession =
              previousDescriptorTimestamps[3] + 1L ==
              currentDescriptorTimestamps[2] &&
              currentDescriptorTimestamps[0] <=
              previousDescriptorTimestamps[1];
          if (!sameSession) {
            for (String descriptorInSession : descriptorsInSession) {
              long[] descriptorTimestamps = descriptorSessions.get(
                  descriptorInSession);
              descriptorTimestamps[4] = firstStatusInSession;
              descriptorTimestamps[5] = lastStatusInSession;
              descriptorTimestamps[6] = lastDescriptorPublished;
            }
            firstStatusInSession = Long.MAX_VALUE;
            lastStatusInSession = lastDescriptorPublished = -1L;
            descriptorsInSession.clear();
          }
        }
        firstStatusInSession = Math.min(firstStatusInSession,
            currentDescriptorTimestamps[2]);
        lastStatusInSession = Math.max(lastStatusInSession,
            currentDescriptorTimestamps[3]);
        lastDescriptorPublished = Math.max(lastDescriptorPublished,
            currentDescriptorTimestamps[1]);
        descriptorsInSession.add(currentDescriptor);
        previousDescriptorTimestamps = currentDescriptorTimestamps;
      }
      for (String descriptorInSession : descriptorsInSession) {
        long[] descriptorTimestamps = descriptorSessions.get(
            descriptorInSession);
        descriptorTimestamps[4] = firstStatusInSession;
        descriptorTimestamps[5] = lastStatusInSession;
        descriptorTimestamps[6] = lastDescriptorPublished;
      }

      /* Go through all statuses listing this bridge as Running, determine
       * if it reported usage statistics and if they were considered for
       * aggregation, and find out possible reasons for the bridge not
       * reporting usage statistics. */
      for (Map.Entry<Long, String> e :
          runningBridgeHalfHours.entrySet()) {
        long statusPublished = e.getKey();
        String descriptor = e.getValue();
        String platform = descriptorPlatforms.get(descriptor);
        boolean reported = false, discarded = false;
        String reason = "none";
        if (firstRunningRelay <= statusPublished) {
          /* The bridge was running as a relay before. */
          discarded = true;
          reason = "runasrelay";
        }
        if (!geoipStats.headMap(statusPublished + 1).isEmpty()) {
          long[] stats = geoipStats.get(geoipStats.headMap(statusPublished
              + 1).lastKey());
          if (stats[0] <= statusPublished && stats[1] > statusPublished) {
            /* Status publication time falls into stats interval. */
            reported = true;
            if (platform != null && platform.compareTo("Tor 0.2.2") > 0) {
              /* geoip stats published by versions 0.2.2.x or higher are
               * buggy and therefore discarded. */
              discarded = true;
              reason = "geoip022";
            }
          }
        }
        if (!bridgeStats.headMap(statusPublished + 1).isEmpty()) {
          long[] stats = bridgeStats.get(bridgeStats.headMap(
              statusPublished + 1).lastKey());
          if (stats[0] <= statusPublished && stats[1] > statusPublished) {
            /* Status publication time falls into stats interval. */
            reported = true;
            if (platform != null && platform.compareTo("Tor 0.2.3") > 0 &&
                stats[2] == 0) {
              /* The bridge running version 0.2.3.x did not have a geoip
               * file and therefore published bad bridge statistics. */
              discarded = true;
              reason = "nogeoipfile";
            }
          }
        }
        if (!reported) {
          /* The bridge didn't report statistics, so it doesn't matter
           * whether we'd have discarded them. */
          discarded = false;
          if (!descriptorSessions.containsKey(descriptor)) {
            /* The descriptor referenced in the bridge network status is
             * unavailable, which means we cannot make any statement why the
             * bridge did not report usage statistics. */
            reason = "noserverdesc";
          } else {
            long[] descriptorTimestamps = descriptorSessions.get(descriptor);
            long sessionStart = descriptorTimestamps[4],
                sessionEnd = descriptorTimestamps[5],
                lastDescPubl = descriptorTimestamps[6];
            long currentStatsEnd = sessionStart
                + 48 * ((statusPublished - sessionStart) / 48 + 1);
            if (sessionEnd <= currentStatsEnd) {
              /* The current uptime session ends before the 24-hour statistics
               * interval. */
              reason = "lessthan24h";
            } else if (currentStatsEnd > lastDescPubl) {
              /* The current uptime session ended after the 24-hour statistics
               * interval, but the bridge didn't publish a descriptor
               * containing the statistics. */
              reason = "publdelay";
            } else {
              /* There is some other reason why the bridge did not report
               * statistics. */
              reason = "other";
            }
          }
        }
        ebw.write(dateTimeFormat.format(statusPublished * HALF_HOUR) + ","
            + fingerprint + "," + reported + "," + discarded + ","
            + reason + "\n");
      }
    }
    ebw.close();
  }
}

