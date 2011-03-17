import java.io.*;
import java.text.*;
import java.util.*;
public class Eval {
  public static void main(String[] args) throws Exception {
    BufferedReader br = new BufferedReader(new FileReader(
        "consensus-params"));
    String line = null;
    SortedMap<String, String> consensusParams =
        new TreeMap<String, String>();
    SortedSet<String> params = new TreeSet<String>(Arrays.asList((
        "CircPriorityHalflifeMsec,CircuitPriorityHalflifeMsec,"
        + "CircuitPriorityHalflife,bwconnburst,bwconnrate,"
        + "circwindow,cbtquantile,refuseunknownexits,cbtnummodes").
        split(",")));
    while ((line = br.readLine()) != null) {
      String date = line.substring(23, 33);
      StringBuilder sb = new StringBuilder();
      for (String param : params) {
        if (line.contains(param + "=")) {
          sb.append("," + line.substring(line.indexOf(param + "=")).
              split(" ")[0].split("=")[1]);
        } else {
          sb.append(",NA");
        }
      }
      consensusParams.put(date, sb.toString());
      String[] parts = line.split(" ");
      for (int i = 1; i < parts.length; i++) {
        if (!params.contains(parts[i].split("=")[0])) {
          System.out.println("Unknown consensus param '"
              + parts[i].split("=")[0] + "' in " + line);
        }
      }
    }
    br.close();
    br = new BufferedReader(new FileReader("votes-measured"));
    SortedMap<String, String> votesMeasured =
        new TreeMap<String, String>();
    long bwscannerStart = 0L, bwscannerEnd = 0L;
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    BufferedWriter bw = new BufferedWriter(new FileWriter(
        "bwscanner-failures.csv"));
    bw.write("start,end\n");
    while ((line = br.readLine()) != null) {
      String date = line.substring(5, 15);
      String votes = line.substring(0, 5).trim();
      votesMeasured.put(date, votes);
      String dateTime = line.substring(5, 15) + " "
          + line.substring(16, 18) + ":" + line.substring(19, 21) + ":"
          + line.substring(22, 24);
      long dateTimeSeconds = dateTimeFormat.parse(dateTime).getTime();
      if (Integer.parseInt(votes) >= 3) {
        if (bwscannerStart == 0L) {
          bwscannerStart = bwscannerEnd = dateTimeSeconds;
        } else if (bwscannerEnd + 12L * 60L * 60L * 1000L >=
            dateTimeSeconds) {
          bwscannerEnd = dateTimeSeconds;
        } else {
          //bw.write(dateFormat.format(bwscannerStart) + ","
          //    + dateFormat.format(bwscannerEnd) + "\n");
          bw.write(dateFormat.format(bwscannerEnd) + ","
              + dateFormat.format(dateTimeSeconds
              + 24L * 60L * 60L * 1000L) + "\n");
          bwscannerStart = bwscannerEnd = dateTimeSeconds;
        }
      }
    }
    /*if (bwscannerStart > 0L) {
      bw.write(dateFormat.format(bwscannerStart) + ","
          + dateFormat.format(bwscannerEnd) + "\n");
    }*/
    bw.close();
    br.close();
    br = new BufferedReader(new FileReader("torperf.csv"));
    br.readLine();
    bw = new BufferedWriter(new FileWriter("torperf-stats.csv"));
    bw.write("date,source,md");
    for (String param : params) {
      bw.write("," + param);
    }
    bw.write(",votesMeasured\n");
    long lastDateSeconds = 0L;
    String lastSource = null;
    while ((line = br.readLine()) != null) {
      String[] parts = line.split(",");
      String date = parts[1];
      long dateSeconds = dateFormat.parse(date).getTime();
      String source = parts[0];
      while (source.equals(lastSource) &&
          lastDateSeconds + 24L * 60L * 60L * 1000L < dateSeconds) {
        lastDateSeconds += 24L * 60L * 60L * 1000L;
        bw.write(dateFormat.format(lastDateSeconds) + "," + source
            + ",NA");
        for (String param : params) {
          bw.write(",NA");
        }
        bw.write(",NA\n");
      }
      lastDateSeconds = dateSeconds;
      lastSource = source;
      String md = parts[3];
      bw.write(date + "," + source + "," + md);
      if (consensusParams.containsKey(date)) {
        bw.write(consensusParams.get(date));
      } else {
        for (String param : params) {
          bw.write(",NA");
        }
      }
      if (votesMeasured.containsKey(date)) {
        bw.write("," + votesMeasured.get(date) + "\n");
      } else {
        bw.write(",NA\n");
      }
    }
    bw.close();
    br.close();
  }
}

