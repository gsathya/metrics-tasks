import java.io.*;
import java.text.*;
import java.util.*;

public class Merge {
  public static void main(String[] args) throws Exception {

    System.out.println("Reading guard node bandwidths...");
    SortedMap<String, String> bandwidthRanks =
        new TreeMap<String, String>();
    BufferedReader br = new BufferedReader(new FileReader(
        "bandwidths-sql.csv"));
    String line = br.readLine(), lastDateTime = null;
    List<String> currentRelays = new ArrayList<String>();
    while ((line = br.readLine()) != null) {
      if (line.startsWith("fingerprint") || line.startsWith("(")) {
        continue;
      }
      String[] parts = line.split(",");
      String fingerprint = parts[0], dateTime = parts[1],
          bandwidth = parts[2];
      if (lastDateTime != null && !dateTime.equals(lastDateTime)) {
        Collections.sort(currentRelays, new Comparator<String>() {
          public int compare(String a, String b) {
            return Integer.parseInt(a.split(",")[2]) -
                Integer.parseInt(b.split(",")[2]);
          }
        });
        for (int i = 0; i < currentRelays.size(); i++) {
          String relay = currentRelays.get(i);
          String relayParts[] = currentRelays.get(i).split(",");
          String relayFingerprint = relayParts[0];
          String relayBandwidth = relayParts[2];
          bandwidthRanks.put(relayFingerprint + "," + lastDateTime,
              String.format("%s,%.6f", relayBandwidth, (double) i /
              (double) (currentRelays.size() - 1)));
        }
        currentRelays.clear();
      }
      lastDateTime = dateTime;
      currentRelays.add(line);
    }
    br.close();

    System.out.println("Reading .mergedata file and writing completion "
       + "time, guard bandwidth, and rank to disk...");
    SortedMap<Integer, List<Long>> aggregatedResults =
        new TreeMap<Integer, List<Long>>();
    BufferedWriter bw = new BufferedWriter(new FileWriter(
        "torperf-guard-bandwidths-ranks.csv"));
    bw.write("bandwidth,rank,completiontime,guards,filesize\n");
    SimpleDateFormat dateTimeFormat =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    for (File mergedataFile : new File(".").listFiles()) {
      String filename = mergedataFile.getName();
      if (!filename.endsWith(".mergedata")) {
        continue;
      }
      String guards = filename.substring(0, filename.indexOf("80cbt"));
      String filesize = filename.split("-")[1].split("\\.")[0];
      br = new BufferedReader(new FileReader(mergedataFile));
      while ((line = br.readLine()) != null) {
        String path = null;
        long started = 0L, completed = 0L;
        for (String part : line.split(" ")) {
          String key = part.substring(0, part.indexOf("="));
          String value = part.substring(part.indexOf("=") + 1);
          if (key.equals("PATH")) {
            path = value;
          } else if (key.equals("STARTSEC")) {
            started += Long.parseLong(value) * 1000L;
          } else if (key.equals("STARTUSEC")) {
            started += Long.parseLong(value) / 1000L;
          } else if (key.equals("DATACOMPLETESEC")) {
            completed += Long.parseLong(value) * 1000L;
          } else if (key.equals("DATACOMPLETEUSEC")) {
            completed += Long.parseLong(value) / 1000L;
          } else if (key.equals("DIDTIMEOUT")) {
            if (value.equals("1")) {
              continue;
            }
          }
        }
        if (path == null || started == 0L || completed == 0L) {
          continue;
        }
        String dateTime = dateTimeFormat.format(started);
        String fingerprint = path.split(",")[0].substring(1).toLowerCase();
        String guardKey = fingerprint + "," + dateTime;
        String previousGuardKey = bandwidthRanks.headMap(guardKey).lastKey();
        if (previousGuardKey.startsWith(fingerprint)) {
          String bandwidthRank = bandwidthRanks.get(previousGuardKey);
          long completionTime = completed - started;
          bw.write(bandwidthRank + "," + completionTime + "," + guards
              + "," + filesize + "\n");
        }
      }
    }
    br.close();
    bw.close();
  }
}

