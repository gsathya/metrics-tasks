import java.io.*;
import java.text.*;
import java.util.*;
public class ExtractOnionKeyLifetimes {
  public static void main(String[] args) throws Exception {
    BufferedReader br = new BufferedReader(new FileReader(
        "sorted-2011-05.csv"));
    String line, lastFingerprint = null, firstPublished = null,
        lastPublished = null, lastOnionKey = null;
    SimpleDateFormat formatter = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    while ((line = br.readLine()) != null) {
      String[] parts = line.split(",");
      String fingerprint = parts[0];
      String published = parts[1];
      String onionKey = parts[2];
      if (lastFingerprint == null ||
          !lastFingerprint.equals(fingerprint) ||
          !lastOnionKey.equals(onionKey)) {
        if (firstPublished != null &&
            !firstPublished.equals(lastPublished)) {
          long interval = formatter.parse(lastPublished).getTime()
              - formatter.parse(firstPublished).getTime();
          System.out.println(lastFingerprint + "," + firstPublished + ","
              + lastPublished + "," + (interval / 1000L));
        }
        firstPublished = published;
      }
      lastFingerprint = fingerprint;
      lastPublished = published;
      lastOnionKey = onionKey;
    }
    br.close();
  }
}

