import java.io.*;
import java.text.*;
import java.util.*;
public class EvaluateHsDirs {
  public static void main(String[] args) throws Exception {
    BufferedWriter bw = new BufferedWriter(new FileWriter(
        "hsdir-sessions.csv"));
    bw.write("fingerprint,firstseen,lastseen,duration\n");
    BufferedReader br = new BufferedReader(new FileReader("hsdir.csv"));
    String line = br.readLine(), firstValidAfter = null,
        lastValidAfter = null, lastButOneValidAfter = null;
    Map<String, String> last = new HashMap<String, String>();
    Map<String, String> current = new HashMap<String, String>();
    SimpleDateFormat formatter = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    while ((line = br.readLine()) != null) {
      String[] parts = line.split(",");
      String validAfter = parts[0];
      if (firstValidAfter == null) {
        firstValidAfter = validAfter;
        lastValidAfter = validAfter;
      }
      if (!line.startsWith("20") || !lastValidAfter.equals(validAfter)) {
        for (Map.Entry<String, String> e : last.entrySet()) {
          if (!current.containsKey(e.getKey()) &&
              !e.getValue().equals(firstValidAfter)) {
            long duration =
                (formatter.parse(lastButOneValidAfter).getTime()
                - formatter.parse(e.getValue()).getTime()) / 1000L;
            bw.write(e.getKey() + "," + e.getValue() + ","
                + lastButOneValidAfter + "," + duration + "\n");
          }
        }
        if (!line.startsWith("20")) {
          break;
        }
        last = current;
        current = new HashMap<String, String>();
        lastButOneValidAfter = lastValidAfter;
      } else if (last.containsKey(parts[1])) {
        current.put(parts[1], last.remove(parts[1]));
      } else {
        current.put(parts[1], validAfter);
      }
      lastValidAfter = validAfter;
    }
    br.close();
    bw.close();
  }
}

