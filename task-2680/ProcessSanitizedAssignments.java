import java.io.*;
import java.util.*;

public class ProcessSanitizedAssignments {
  public static void main(String[] args) throws IOException {

    /* Validate command-line arguments. */
    if (args.length != 1 || !new File(args[0]).exists()) {
      System.out.println("Usage: java ProcessSanitizedAssignments <dir>");
      System.exit(1);
    }

    /* Find all files that we should parse. Somewhat fragile, but should
     * work. */
    System.out.println("Creating list of files we should parse.");
    SortedMap<String, File> assignments = new TreeMap<String, File>();
    Stack<File> files = new Stack<File>();
    files.add(new File(args[0]));
    while (!files.isEmpty()) {
      File file = files.pop();
      if (file.isDirectory()) {
        files.addAll(Arrays.asList(file.listFiles()));
      } else {
        assignments.put(file.getName(), file);
      }
    }
    System.out.println("We found " + assignments.size() + " bridge pool "
        + "assignment files.");

    /* Parse assignments. */
    if (!assignments.isEmpty()) {
      System.out.println("Parsing bridge pool assignment files.");
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          "assignments.csv"));
      bw.write("assignment,fingerprint,type,ring,port,flag,bucket\n");
      int parsedAssignments = 0, totalAssignments = assignments.size(),
          writtenOutputLines = 1;
      long started = System.currentTimeMillis();
      for (File file : assignments.values()) {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line, assignmentTime = null;
        while ((line = br.readLine()) != null) {
          if (line.startsWith("bridge-pool-assignment ")) {
            assignmentTime = line.substring("bridge-pool-assignment ".
                length());
          } else {
            String[] parts = line.split(" ");
            String fingerprint = parts[0];
            String type = parts[1];
            String ring = null, port = null, flag = null, bucket = null;
            for (int i = 2; i < parts.length; i++) {
              String[] parts2 = parts[i].split("=");
              String key = parts2[0];
              String value = parts2[1];
              if (key.equals("ring")) {
              } else if (key.equals("ring")) {
                ring = value;
              } else if (key.equals("port")) {
                port = value;
              } else if (key.equals("flag")) {
                flag = value;
              } else if (key.equals("bucket")) {
                bucket = value;
              } else {
                System.out.println("Unknown keyword in line '" + line
                    + "'. Please check. Exiting.");
                System.exit(1);
              }
            }
            bw.write(assignmentTime + "," + fingerprint + "," + type + ","
                + (ring != null ? ring : "NA") + ","
                + (port != null ? port : "NA") + ","
                + (flag != null ? flag : "NA") + ","
                + (bucket != null ? bucket : "NA") + "\n");
            writtenOutputLines++;
          }
        }
        br.close();
        parsedAssignments++;
        if (parsedAssignments % (totalAssignments / 10) == 0) {
          double fractionDone = (double) (parsedAssignments) /
              (double) totalAssignments;
          double fractionLeft = 1.0D - fractionDone;
          long now = System.currentTimeMillis();
          double millisLeft = ((double) (now - started)) * fractionLeft /
              fractionDone;
          long secondsLeft = (long) millisLeft / 1000L;
          System.out.println("  " + (parsedAssignments / (totalAssignments
              / 10)) + "0% done, " + secondsLeft + " seconds left.");
        }
      }
      bw.close();
      System.out.println("Parsed " + parsedAssignments + " bridge pool "
          + "assignment files and wrote " + writtenOutputLines + " lines "
          + "to assignments.csv.");
    }

    /* This is it. */
    System.out.println("Terminating.");
  }
}

