import java.io.*;
import java.text.*;
import java.util.*;
public class AggregatePerDay {
  public static void main(String[] args) throws Exception {
    SortedMap<String, long[]> byteHistory = new TreeMap<String, long[]>();
    BufferedReader br = new BufferedReader(new FileReader(
        "bridge-bandwidth-histories-sorted.txt"));
    String line;
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    SimpleDateFormat dateFormat = new SimpleDateFormat(
        "yyyy-MM-dd");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    SimpleDateFormat timeFormat = new SimpleDateFormat(
        "HH:mm:ss");
    timeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    while ((line = br.readLine()) != null) {
      String[] parts = line.split(" ");
      if (parts.length < 3) {
        continue;
      }
      String fingerprint = parts[2];
      while (line.contains("-history")) {
        line = line.substring(line.indexOf("-history") - 4);
        boolean isReadHistory = line.startsWith("read");
        line = line.substring(5);
        parts = line.split(" ");
        if (parts.length >= 6 && parts[5].length() > 0 && !parts[5].contains("history")) {
          String[] bytes = parts[5].split(",");
          long intervalEnd = dateTimeFormat.parse(parts[1] + " " + parts[2]).getTime();
          for (int i = bytes.length - 1; i >= 0; i--) {
            String key = fingerprint + ","
                + dateFormat.format(intervalEnd)
                + (isReadHistory ? ",read" : ",write");
            long timeIndex = timeFormat.parse(
                dateTimeFormat.format(intervalEnd).split(" ")[1]).getTime()
                / (15L * 60L * 1000L);
            long value = Long.parseLong(bytes[i]);
            if (!byteHistory.containsKey(key)) {
              byteHistory.put(key, new long[96]);
            }
            byteHistory.get(key)[(int) timeIndex] = value + 1L;
            intervalEnd -= 15L * 60L * 1000L;
          }
        }
      }
    }
    br.close();
    BufferedWriter bw = new BufferedWriter(new FileWriter(
        "bridge-bandwidth-per-day.csv"));
    for (Map.Entry<String, long[]> e : byteHistory.entrySet()) {
      long total = 0L, count = 0L;
      for (long val : e.getValue()) {
        if (val > 0L) {
          total += val - 1L;
          count += 1L;
        }
      }
      bw.write(e.getKey() + "," + total + "," + count + "\n");
    }
    bw.close();
  }
}

