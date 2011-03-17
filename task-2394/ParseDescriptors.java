import java.io.*;
import java.util.*;
import org.apache.commons.codec.binary.*;

public class ParseDescriptors {
  public static void main(String[] args) throws IOException {

    /* Find all files that we should parse and distinguish between
     * consensuses, votes, and server descriptors. */
    SortedMap<String, File> consensuses = new TreeMap<String, File>();
    SortedMap<String, File> descriptors = new TreeMap<String, File>();
    SortedMap<String, File> votes = new TreeMap<String, File>();
    Stack<File> files = new Stack<File>();
    files.add(new File("descriptors"));
    while (!files.isEmpty()) {
      File file = files.pop();
      String filename = file.getName();
      if (file.isDirectory()) {
        files.addAll(Arrays.asList(file.listFiles()));
      } else if (filename.endsWith("-consensus")) {
        consensuses.put(filename, file);
      } else if (filename.contains("-vote-")) {
        votes.put(filename, file);
      } else if (filename.length() == 40) {
        descriptors.put(filename, file);
      }
    }
    System.out.println("We found " + consensuses.size()
        + " consensuses, " + votes.size() + " votes, and "
        + descriptors.size() + " server descriptors.");

    /* Parse consensuses in an outer loop and the referenced votes and
     * descriptors in inner loops.  Write the results to disk as soon as
     * we can to avoid keeping many things in memory. */
    SortedMap<String, String> bandwidthAuthorities =
        new TreeMap<String, String>();
    bandwidthAuthorities.put("27B6B5996C426270A5C95488AA5BCEB6BCC86956",
        "ides");
    bandwidthAuthorities.put("80550987E1D626E3EBA5E5E75A458DE0626D088C",
        "urras");
    bandwidthAuthorities.put("D586D18309DED4CD6D57C18FDB97EFA96D330566",
        "moria1");
    bandwidthAuthorities.put("ED03BB616EB2F60BEC80151114BB25CEF515B226",
        "gabelmoo");
    BufferedWriter bw = new BufferedWriter(new FileWriter(
        "bandwidth-comparison.csv"));
    bw.write("validafter,fingerprint,nickname,category,"
        + "descriptorbandwidth,consensusbandwidth");
    for (String bandwidthAuthority : bandwidthAuthorities.values()) {
      bw.write("," + bandwidthAuthority + "bandwidth");
    }
    bw.write("\n");
    Map<String, String> parsedDescriptors = new HashMap<String, String>();
    for (File consensusFile : consensuses.values()) {
      System.out.println("Parsing consensus " + consensusFile.getName());
      BufferedReader brC = new BufferedReader(new FileReader(
          consensusFile));
      String lineC, validAfter = null, lastDirSource = null,
          lastRLine = null, lastSLine = null;
      String consensusTimestamp = consensusFile.getName().substring(0,
              "YYYY-MM-DD-hh-mm-ss".length());
      Map<String, Map<String, String>> measuredBandwidthsByDirSource =
          new HashMap<String, Map<String, String>>();
      while ((lineC = brC.readLine()) != null) {

        /* Start with parsing a consensus to find out which votes it
         * contains. */
        if (lineC.startsWith("valid-after ")) {
          validAfter = lineC.substring("valid-after ".length());
        } else if (lineC.startsWith("dir-source ")) {
          lastDirSource = lineC.split(" ")[2];
        } else if (lineC.startsWith("vote-digest ") &&
            bandwidthAuthorities.containsKey(lastDirSource)) {
          String voteDigest = lineC.substring("vote-digest ".length());
          String voteFilename = consensusTimestamp + "-vote-"
              + lastDirSource + "-" + voteDigest;
          if (votes.containsKey(voteFilename)) {

            /* Parse votes first and extract measured bandwidths. */
            Map<String, String> measuredBandwidths =
                new HashMap<String, String>();
            measuredBandwidthsByDirSource.put(lastDirSource,
                measuredBandwidths);
            BufferedReader brV = new BufferedReader(new FileReader(
                votes.get(voteFilename)));
            String lineV;
            while ((lineV = brV.readLine()) != null) {
              if (lineV.startsWith("r ")) {
                lastRLine = lineV;
              } else if (lineV.startsWith("w ") &&
                  lineV.contains(" Measured=")) {
                String fingerprint = Hex.encodeHexString(Base64.
                    decodeBase64(lastRLine.split(" ")[2] + "="));
                String measuredBandwidth = lineV.substring(lineV.indexOf(
                    " Measured=") + " Measured=".length()).split(" ")[0];
                measuredBandwidths.put(fingerprint, measuredBandwidth);
              }
            }
            brV.close();
          }

        /* Parse r, s, and w lines from the consensus. */
        } else if (lineC.startsWith("r ")) {
          lastRLine = lineC;
        } else if (lineC.startsWith("s ")) {
          lastSLine = lineC;
        } else if (lineC.startsWith("w ")) {
          String[] parts = lastRLine.split(" ");
          String nickname = parts[1];
          String fingerprint = Hex.encodeHexString(Base64.decodeBase64(
              parts[2] + "="));
          String descriptor = Hex.encodeHexString(Base64.decodeBase64(
              parts[3] + "="));
          boolean exitFlag = lastSLine.contains(" Exit");
          boolean guardFlag = lastSLine.contains(" Guard");
          String consensusBandwidth = lineC.substring(lineC.indexOf(
              " Bandwidth=") + " Bandwidth=".length()).split(" ")[0];

          /* Parse the referenced server descriptor (if we haven't done so
           * before) to learn about the relay's exit policy and reported
           * bandwidth. */
          boolean parsedDescriptor = false, defaultPolicy = false;
          String descriptorBandwidth = null;
          if (parsedDescriptors.containsKey(descriptor)) {
            String parseResults = parsedDescriptors.get(descriptor);
            parsedDescriptor = true;
            defaultPolicy = parseResults.endsWith("1");
            descriptorBandwidth = parseResults.split(",")[0];
          } else if (descriptors.containsKey(descriptor)) {
            parsedDescriptor = true;
            BufferedReader brD = new BufferedReader(new FileReader(
                descriptors.get(descriptor)));
            Set<String> defaultRejects = new HashSet<String>(
                Arrays.asList(("0.0.0.0/8:*,169.254.0.0/16:*,"
                + "127.0.0.0/8:*,192.168.0.0/16:*,10.0.0.0/8:*,"
                + "172.16.0.0/12:*,$IP:*,*:25,*:119,*:135-139,*:445,"
                + "*:563,*:1214,*:4661-4666,*:6346-6429,*:6699,"
                + "*:6881-6999").split(",")));
            /* Starting with 0.2.1.6-alpha, ports 465 and 587 were allowed
             * in the default exit policy again (and therefore removed
             * from the default reject lines). */
            Set<String> optionalRejects = new HashSet<String>(
                Arrays.asList("*:465,*:587".split(",")));
            String lineD, address = null;
            while ((lineD = brD.readLine()) != null) {
              if (lineD.startsWith("router ")) {
                address = lineD.split(" ")[2];
              } else if (lineD.startsWith("bandwidth ")) {
                descriptorBandwidth = lineD.split(" ")[3];
              } else if (lineD.startsWith("reject ")) {
                String rejectPattern = lineD.substring("reject ".
                    length());
                if (defaultRejects.contains(rejectPattern)) {
                  defaultRejects.remove(rejectPattern);
                } else if (optionalRejects.contains(rejectPattern)) {
                  optionalRejects.remove(rejectPattern);
                } else if (rejectPattern.equals(address + ":*")) {
                  defaultRejects.remove("$IP:*");
                } else {
                  break;
                }
              } else if (lineD.startsWith("accept ")) {
                if (defaultRejects.isEmpty() &&
                    lineD.equals("accept *:*")) {
                  defaultPolicy = true;
                }
                break;
              }
            }
            brD.close();
            parsedDescriptors.put(descriptor, descriptorBandwidth + ","
                + (defaultPolicy ? "1" : "0"));
          } else {
            System.out.println("We're missing descriptor " + descriptor
                + ".  Please make sure that all referenced server "
                + "descriptors are available.  Continuing anyway.");
          }

          /* Write everything we know about this relay to disk. */
          String category = null;
          if (guardFlag && exitFlag && defaultPolicy) {
            category = "Guard & Exit (default policy)";
          } else if (!guardFlag && exitFlag && defaultPolicy) {
            category = "Exit (default policy)";
          } else if (guardFlag && exitFlag && !defaultPolicy) {
            category = "Guard & Exit (non-default policy)";
          } else if (!guardFlag && exitFlag && !defaultPolicy) {
            category = "Exit (non-default policy)";
          } else if (guardFlag && !exitFlag) {
            category = "Guard";
          } else if (!guardFlag && !exitFlag) {
            category = "Middle";
          }
          bw.write(validAfter + "," + fingerprint + "," + nickname + ","
              + category + "," + (parsedDescriptor ? descriptorBandwidth
              : "NA") + "," + consensusBandwidth);
          for (String bandwidthAuthority :
              bandwidthAuthorities.keySet()) {
            if (measuredBandwidthsByDirSource.containsKey(
                bandwidthAuthority) && measuredBandwidthsByDirSource.get(
                bandwidthAuthority).containsKey(fingerprint)) {
              bw.write("," + measuredBandwidthsByDirSource.get(
                  bandwidthAuthority).get(fingerprint));
            } else {
              bw.write(",NA");
            }
          }
          bw.write("\n");
        }
      }
      brC.close();
    }
    bw.close();
  }
}

