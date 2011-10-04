import java.io.*;
import java.util.*;
public class Eval {
  public static void main(String[] args) throws Exception {
    BufferedReader br = new BufferedReader(new FileReader("delay"));
    String line, fingerprint = null, published = null;
    Map<String, String> firstPublished = new HashMap<String, String>();
    while ((line = br.readLine()) != null) {
      if (line.startsWith("extra-info ")) {
        fingerprint = line.split(" ")[2];
        published = null;
      } else if (line.startsWith("published ")) {
        published = line.substring("published ".length());
      } else if (line.startsWith("dirreq-stats-end ")) {
        String[] parts = line.split(" ");
        String dirreqStatsEnd = parts[1] + " " + parts[2];
        String key = fingerprint + " " + dirreqStatsEnd;
        if (!firstPublished.containsKey(key) ||
            firstPublished.get(key).compareTo(published) > 0) {
          firstPublished.put(key, published);
        }
      }
    }
    br.close();
    BufferedWriter bw = new BufferedWriter(new FileWriter("delay.csv"));
    bw.write("fingerprint,dirreqstatsend,published\n");
    for (Map.Entry<String, String> e : firstPublished.entrySet()) {
      String[] parts = e.getKey().split(" ");
      fingerprint = parts[0];
      String dirreqStatsEnd = parts[1] + " " + parts[2];
      published = e.getValue();
      bw.write(fingerprint + "," + dirreqStatsEnd + "," + published
          + "\n");
    }
    bw.close();
  }
}

