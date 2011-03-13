import java.io.*;
import java.text.*;
import java.util.*;

public class BridgeDBLogConverter {
  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      System.out.println("Usage: java BridgeDBLogConverter logfile year "
          + "outfile");
      System.exit(1);
    }
    File logfile = new File(args[0]), outfile = new File(args[2]);
    String year = args[1];
    SimpleDateFormat logFormat = new SimpleDateFormat(
        "yyyy MMM dd HH:mm:ss");
    SimpleDateFormat isoFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    SimpleDateFormat fileFormat = new SimpleDateFormat(
        "yyyy/MM/dd/yyyy-MM-dd-HH-mm-ss");
    logFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    fileFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    BufferedReader br = new BufferedReader(new FileReader(logfile));
    BufferedWriter bw = new BufferedWriter(new FileWriter(outfile));
    String line;
    SortedMap<String, String> entries = new TreeMap<String, String>();
    long lastTimestamp = -1L;
    String fingerprint = null, lastFingerprint = null, type = null,
        port = "", flag = "";
    while ((line = br.readLine()) != null) {
      long timestamp = logFormat.parse(year + " "
          + line.substring(0, 15)).getTime();
      if (timestamp > lastTimestamp + 10L * 60L * 1000L) {
        if (lastTimestamp > 0L) {
          bw.write("bridge-pool-assignment "
              + isoFormat.format(lastTimestamp) + "\n");
          for (String entry : entries.values()) {
            bw.write(entry + "\n");
          }
          entries.clear();
        }
      }
      lastTimestamp = timestamp;
      String[] parts = line.split(" ");
      fingerprint = parts[7];
      String assignment = line.substring(line.indexOf(parts[7]) + 41);
      if (!fingerprint.equals(lastFingerprint)) {
        if (lastFingerprint != null) {
          entries.put(lastFingerprint, lastFingerprint + " " + type
          + port + flag);
        }
        type = null;
        port = "";
        flag = "";
      }
      if (assignment.startsWith("to IP ")) {
        int ring = -1;
        if (assignment.startsWith("to IP category ring")) {
          ring = 4; // TODO This is fragile!
        } else {
          ring = Integer.parseInt(assignment.split(" ")[3]) - 1;
        }
        String newType = "https ring=" + ring;
        if (type != null && !type.equals(newType)) {
          System.out.println("type inconsistency in line '" + line + "'");
          System.exit(1);
        }
        type = newType;
        if (assignment.endsWith(" (port-443 subring)")) {
          String newPort = " port=443";
          if (port.length() > 0 && !port.equals(newPort)) {
            System.out.println("port inconsistency in line '" + line
                + "'");
            System.exit(1);
          }
          port = newPort;
        } else if (assignment.endsWith(" (stable subring)")) {
          String newFlag = " flag=stable";
          if (flag.length() > 0 && !flag.equals(newFlag)) {
            System.out.println("flag inconsistency in line '" + line
                + "'");
            System.exit(1);
          }
          flag = newFlag;
        }
      } else if (assignment.equals("to email ring")) {
        String newType = "email";
        if (type != null && !type.equals(newType)) {
          System.out.println("type inconsistency in line '" + line + "'");
          System.exit(1);
        }
        type = newType;
      } else if (assignment.startsWith("to Ring ")) {
        String newType = "email";
        if (type != null && !type.equals(newType)) {
          System.out.println("type inconsistency in line '" + line + "'");
          System.exit(1);
        }
        type = newType;
        if (assignment.equals("to Ring (port-443 subring)")) {
          String newPort = " port=443";
          if (port.length() > 0 && !port.equals(newPort)) {
            System.out.println("port inconsistency in line '" + line
                + "'");
            System.exit(1);
          }
          port = newPort;
        } else if (assignment.equals("to Ring (stable subring)")) {
          String newFlag = " flag=stable";
          if (flag.length() > 0 && !flag.equals(newFlag)) {
            System.out.println("flag inconsistency in line '" + line
                + "'");
            System.exit(1);
          }
          flag = newFlag;
        } else {
          System.out.println("type inconsistency in line '" + line
              + "'");
          System.exit(1);
        }
      } else {
        type = "unallocated";
      }
      lastFingerprint = fingerprint;
    }
    if (lastTimestamp > 0L) {
      bw.write("bridge-pool-assignment "
          + isoFormat.format(lastTimestamp) + "\n");
      for (String entry : entries.values()) {
        bw.write(entry + "\n");
      }
    }
    bw.close();
  }
}

