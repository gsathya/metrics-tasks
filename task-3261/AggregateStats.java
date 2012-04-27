import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

/* Aggregate half-hourly per-bridge data to daily statistics. */
public class AggregateStats {
  public static void main(String[] args) throws Exception {

    /* Read file containing publication times of bridge statuses and count
     * statuses per day. */
    SortedMap<String, Long> publishedStatuses =
        new TreeMap<String, Long>();
    File statusFile = new File("bridge-network-statuses");
    if (!statusFile.exists()) {
      System.err.println(statusFile.getAbsolutePath() + " does not "
          + "exist.  Exiting.");
      System.exit(1);
    } else {
      BufferedReader br = new BufferedReader(new FileReader(statusFile));
      String line;
      while ((line = br.readLine()) != null) {
        String date = line.split(" ")[0];
        if (publishedStatuses.containsKey(date)) {
          publishedStatuses.put(date, publishedStatuses.get(date) + 1L);
        } else {
          publishedStatuses.put(date, 1L);
        }
      }
    }

    /* Aggregate single observations in memory. */
    SortedMap<String, Map<String, Long>> aggregatedStats =
        new TreeMap<String, Map<String, Long>>();
    SortedSet<String> allKeys = new TreeSet<String>();
    File evalOutFile = new File("eval-out.csv");
    if (!evalOutFile.exists()) {
      System.err.println(evalOutFile.getAbsolutePath() + " does not "
          + "exist.  Exiting.");
      System.exit(1);
    } else {
      BufferedReader ebr = new BufferedReader(new FileReader(evalOutFile));
      String line;
      while ((line = ebr.readLine()) != null) {
        String[] parts = line.split(",");
        String date = parts[0].split(" ")[0];
        String key = parts[2] + "," + parts[3] + "," + parts[4];
        allKeys.add(key);
        Map<String, Long> stats = null;
        if (aggregatedStats.containsKey(date)) {
          stats = aggregatedStats.get(date);
        } else {
          stats = new HashMap<String, Long>();
          aggregatedStats.put(date, stats);
        }
        if (stats.containsKey(key)) {
          stats.put(key, stats.get(key) + 1L);
        } else {
          stats.put(key, 1L);
        }
      }
      ebr.close();
    }

    /* Write aggregated statistics to aggregated.csv. */
    File aggregatedFile = new File("aggregated.csv");
    BufferedWriter abw = new BufferedWriter(new FileWriter(
        aggregatedFile));
    abw.write("date,reported,discarded,reason,bridges,statuses\n");
    long previousDateMillis = -1L;
    final long DAY = 24L * 60L * 60L * 1000L;
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    for (Map.Entry<String, Map<String, Long>> e :
        aggregatedStats.entrySet()) {
      String date = e.getKey();
      long currentDateMillis = dateFormat.parse(date).getTime();
      while (previousDateMillis > -1L &&
          currentDateMillis - previousDateMillis > DAY) {
        previousDateMillis += DAY;
        String tempDate = dateFormat.format(previousDateMillis);
        for (String key : allKeys) {
          abw.write(tempDate + "," + key + ",NA,0\n");
        }
      }
      previousDateMillis = currentDateMillis;
      String nextDate = dateFormat.format(currentDateMillis + DAY);
      String nextPlusOneDate = dateFormat.format(currentDateMillis
          + 2 * DAY);
      long statuses = publishedStatuses.containsKey(date) ?
          publishedStatuses.get(date) : 0L;
      Map<String, Long> stats = e.getValue();
      if (!aggregatedStats.containsKey(nextDate) ||
          !aggregatedStats.containsKey(nextPlusOneDate) ||
          statuses < 40) {
        for (String key : allKeys) {
          abw.write(date + "," + key + ",NA," + statuses + "\n");
        }
      } else {
        for (String key : allKeys) {
          if (stats.containsKey(key)) {
            abw.write(date + "," + key + "," + (stats.get(key) / statuses)
                + "," + statuses + "\n");
          } else {
            abw.write(date + "," + key + ",0," + statuses + "\n");
          }
        }
      }
    }
    abw.close();
  }
}

