import java.io.*;
import java.util.*;
import org.apache.commons.codec.binary.*;
import org.apache.commons.codec.digest.*;

public class ProcessRelayConsensuses {
  public static void main(String[] args) throws IOException {

    /* Validate command-line arguments. */
    if (args.length != 1 || !new File(args[0]).exists()) {
      System.out.println("Usage: java ProcessRelayConsensuses <dir>");
      System.exit(1);
    }

    /* Find all files that we should parse. Somewhat fragile, but should
     * work. */
    System.out.println("Creating list of files we should parse.");
    SortedMap<String, File> consensuses = new TreeMap<String, File>();
    Stack<File> files = new Stack<File>();
    files.add(new File(args[0]));
    while (!files.isEmpty()) {
      File file = files.pop();
      String filename = file.getName();
      if (file.isDirectory()) {
        files.addAll(Arrays.asList(file.listFiles()));
      } else if (filename.endsWith("-consensus")) {
        consensuses.put(filename, file);
      }
    }
    System.out.println("We found " + consensuses.size()
        + " consensuses.");

    /* Parse consensuses. */
    if (!consensuses.isEmpty()) {
      System.out.println("Parsing consensuses.");
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          "relays.csv"));
      bw.write("consensus,fingerprint\n");
      int parsedConsensuses = 0, totalConsensuses = consensuses.size(),
          writtenOutputLines = 1;
      long started = System.currentTimeMillis();
      for (File file : consensuses.values()) {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line, validAfter = null;
        while ((line = br.readLine()) != null) {
          if (line.startsWith("valid-after ")) {
            validAfter = line.substring("valid-after ".length());
          } else if (line.startsWith("r ")) {
            if (validAfter == null) {
              System.out.println("Found an r line before the valid-after "
                  + "line in " + file.getName() + ". Please check. "
                  + "Exiting.");
              System.exit(1);
            }
            String fingerprint = DigestUtils.shaHex(Base64.decodeBase64(
                line.split(" ")[2] + "="));
            bw.write(validAfter + "," + fingerprint + "\n");
            writtenOutputLines++;
          }
        }
        br.close();
        parsedConsensuses++;
        if (parsedConsensuses % (totalConsensuses / 10) == 0) {
          double fractionDone = (double) (parsedConsensuses) /
              (double) totalConsensuses;
          double fractionLeft = 1.0D - fractionDone;
          long now = System.currentTimeMillis();
          double millisLeft = ((double) (now - started)) * fractionLeft /
              fractionDone;
          long secondsLeft = (long) millisLeft / 1000L;
          System.out.println("  " + (parsedConsensuses / (totalConsensuses
              / 10)) + "0% done, " + secondsLeft + " seconds left.");
        }
      }
      bw.close();
      System.out.println("Parsed " + parsedConsensuses + " consensuses "
          + "and wrote " + writtenOutputLines + " lines to relays.csv.");
    }

    /* This is it. */
    System.out.println("Terminating.");
  }
}


