import java.io.*;
import java.util.*;
import org.apache.commons.codec.*;
import org.apache.commons.codec.digest.*;
import org.apache.commons.codec.binary.*;

/* Generate sample bridge descriptor tarball contents for metrics-db and
 * BridgeDB load tests.  Accept an extracted, non-sanitized bridge
 * descriptor tarball as input and generate sample tarball contents with
 * multiples of bridges up to a given maximum multiplier as output.
 * Descriptors are multiplied by overwriting the first four hex characters
 * of bridge fingerprints with 0000, 0001, etc., keeping references
 * between descriptors intact.
 *
 * NOTE THAT THE OUTPUT TARBALL CONTENTS ARE NOT SANITIZED!
 *
 * The changes are only sufficient to trick metrics-db and BridgeDB that
 * bridges are distinct.  Descriptors may still contain original IP
 * addresses in exit policies and other contact information.  Sanitized
 * descriptors could not be used as input, because they may have skewed
 * results too much. */
public class GenerateSampleBridgeDescriptorTarballs {
  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      System.err.println("Usage: java "
          + GenerateSampleBridgeDescriptorTarballs.class.getName()
          + " in-directory out-directory max-multiplier");
      System.exit(1);
    }
    File inDirectory = new File(args[0]);
    File outDirectory = new File(args[1]);
    int maxMultiplier = Integer.parseInt(args[2]);
    readDescriptors(inDirectory);
    for (int multiplier = 1; multiplier <= maxMultiplier;
        multiplier *= 2) {
      writeDescriptors(new File(outDirectory, String.format("%04d",
          multiplier)), multiplier);
    }
  }

  private static void readDescriptors(File inDirectory) throws Exception {
    readNetworkstatusBridges(new File(inDirectory,
        "networkstatus-bridges"));
    readBridgeDescriptors(new File(inDirectory, "bridge-descriptors"));
    readCachedExtrainfos(new File(inDirectory, "cached-extrainfo"));
    readCachedExtrainfos(new File(inDirectory, "cached-extrainfo.new"));
  }

  private static SortedMap<String, String> networkstatusEntries =
      new TreeMap<String, String>();
  private static void readNetworkstatusBridges(
      File networkstatusBridgesFile) throws Exception {
    BufferedReader br = new BufferedReader(new FileReader(
        networkstatusBridgesFile));
    String line, fingerprint = null, published = null;
    StringBuilder sb = null;
    while ((line = br.readLine()) != null) {
      if (line.startsWith("r ")) {
        if (sb != null) {
          networkstatusEntries.put(fingerprint + " " + published,
              sb.toString());
        }
        sb = new StringBuilder();
        String[] parts = line.split(" ");
        fingerprint = Hex.encodeHexString(Base64.decodeBase64(
            parts[2] + "=")).toUpperCase();
        published = parts[4] + " " + parts[5];
      }
      sb.append(line + "\n");
    }
    if (sb != null) {
      networkstatusEntries.put(fingerprint + " " + published,
          sb.toString());
    }
    br.close();
  }

  private static SortedMap<String, String> bridgeDescriptors =
      new TreeMap<String, String>();
  private static void readBridgeDescriptors(File bridgeDescriptorsFile)
      throws Exception {
    BufferedReader br = new BufferedReader(new FileReader(
        bridgeDescriptorsFile));
    String line, fingerprint = null, published = null;
    StringBuilder sb = null;
    while ((line = br.readLine()) != null) {
      if (line.startsWith("@purpose ")) {
        if (sb != null) {
          bridgeDescriptors.put(fingerprint + " " + published,
              sb.toString());
        }
        sb = new StringBuilder();
      } else if (line.startsWith("published ")) {
        published = line.substring("published ".length());
      } else if (line.startsWith("opt fingerprint ")) {
        fingerprint = line.substring("opt fingerprint ".length()).
            replaceAll(" ", "");
      }
      sb.append(line + "\n");
    }
    if (sb != null) {
      bridgeDescriptors.put(fingerprint + " " + published, sb.toString());
    }
    br.close();

  }

  private static SortedMap<String, String> cachedExtrainfos =
      new TreeMap<String, String>();
  private static void readCachedExtrainfos(File cachedExtrainfoFile)
      throws Exception {
    BufferedReader br = new BufferedReader(new FileReader(
        cachedExtrainfoFile));
    String line, fingerprint = null, published = null;
    StringBuilder sb = null;
    while ((line = br.readLine()) != null) {
      if (line.startsWith("extra-info ")) {
        if (sb != null) {
          cachedExtrainfos.put(fingerprint + " " + published,
              sb.toString());
        }
        sb = new StringBuilder();
        fingerprint = line.split(" ")[2];
      } else if (line.startsWith("published ")) {
        published = line.substring("published ".length());
      }
      sb.append(line + "\n");
    }
    if (sb != null) {
      cachedExtrainfos.put(fingerprint + " " + published, sb.toString());
    }
    br.close();
  }

  private static void writeDescriptors(File outDirectory, int multiplier)
      throws Exception {
    outDirectory.mkdirs();
    for (File file : outDirectory.listFiles()) {
      file.delete();
    }
    for (int i = 0; i < multiplier; i++) {
      String fingerprintPrefix = String.format("%04x", i);
      SortedMap<String, String> extraInfoDigests = writeCachedExtrainfos(
          outDirectory, fingerprintPrefix);
      SortedMap<String, String> descriptorDigests =
          writeBridgeDescriptors(outDirectory, extraInfoDigests,
          fingerprintPrefix);
      writeNetworkstatusBridges(outDirectory, descriptorDigests,
          fingerprintPrefix);
    }
  }

  private static SortedMap<String, String> writeCachedExtrainfos(
      File outDirectory, String fingerprintPrefix) throws Exception {
    SortedMap<String, String> extraInfoDigests =
        new TreeMap<String, String>();
    BufferedWriter bw = new BufferedWriter(new FileWriter(new File(
        outDirectory, "cached-extrainfo"), true));
    for (Map.Entry<String, String> e : cachedExtrainfos.entrySet()) {
      String fingerprintPublished = e.getKey();
      String cachedExtrainfo = e.getValue();
      BufferedReader br = new BufferedReader(new StringReader(
          cachedExtrainfo));
      String line;
      StringBuilder sb = new StringBuilder();
      while ((line = br.readLine()) != null) {
        if (line.startsWith("extra-info ")) {
          String[] parts = line.split(" ");
          sb.append(parts[0] + " " + parts[1] + " " + fingerprintPrefix
              + parts[2].substring(4) + "\n");
        } else if (line.equals("router-signature")) {
          sb.append(line + "\n");
          String digest = DigestUtils.shaHex(sb.toString()).toUpperCase();
          extraInfoDigests.put(fingerprintPublished, digest);
        } else {
          sb.append(line + "\n");
        }
      }
      bw.write(sb.toString());
    }
    bw.close();
    return extraInfoDigests;
  }

  private static SortedMap<String, String> writeBridgeDescriptors(
      File outDirectory, SortedMap<String, String> extraInfoDigests,
      String fingerprintPrefix) throws Exception {
    SortedMap<String, String> descriptorDigests =
        new TreeMap<String, String>();
    BufferedWriter bw = new BufferedWriter(new FileWriter(new File(
        outDirectory, "bridge-descriptors"), true));
    for (Map.Entry<String, String> e : bridgeDescriptors.entrySet()) {
      String fingerprintPublished = e.getKey();
      String bridgeDescriptor = e.getValue();
      BufferedReader br = new BufferedReader(new StringReader(
          bridgeDescriptor));
      String line;
      StringBuilder sb = new StringBuilder();
      while ((line = br.readLine()) != null) {
        if (line.startsWith("@purpose ")) {
        } else if (line.startsWith("opt fingerprint ")) {
          sb.append("opt fingerprint " + fingerprintPrefix
              + line.substring("opt fingerprint 0000".length()) + "\n");
        } else if (line.startsWith("opt extra-info-digest ")) {
          String extraInfoDigest = null;
          if (extraInfoDigests.containsKey(fingerprintPublished)) {
            extraInfoDigest = extraInfoDigests.get(fingerprintPublished);
          } else {
            extraInfoDigest = fingerprintPrefix
                + line.split(" ")[2].substring(4);
          }
          sb.append("opt extra-info-digest " + extraInfoDigest + "\n");
        } else if (line.equals("router-signature")) {
          sb.append(line + "\n");
          String digest = DigestUtils.shaHex(sb.toString()).toUpperCase();
          descriptorDigests.put(fingerprintPublished, digest);
        } else {
          sb.append(line + "\n");
        }
      }
      bw.write("@purpose bridge\n" + sb.toString());
    }
    bw.close();
    return descriptorDigests;
  }

  private static void writeNetworkstatusBridges(File outDirectory,
      SortedMap<String, String> descriptorDigests,
      String fingerprintPrefix) throws Exception {
    BufferedWriter bw = new BufferedWriter(new FileWriter(new File(
        outDirectory, "networkstatus-bridges"), true));
    for (Map.Entry<String, String> e : networkstatusEntries.entrySet()) {
      String fingerprintPublished = e.getKey();
      String networkstatusEntry = e.getValue();
      BufferedReader br = new BufferedReader(new StringReader(
          networkstatusEntry));
      String line;
      StringBuilder sb = new StringBuilder();
      while ((line = br.readLine()) != null) {
        if (line.startsWith("r ")) {
          String[] parts = line.split(" ");
          String fingerprint = parts[2], descriptorDigest = parts[3];
          String newFingerprint = Base64.encodeBase64String(Hex.decodeHex(
              (fingerprintPrefix + fingerprintPublished.split(" ")[0].
              substring(4)).toCharArray())).substring(0, 27);
          String newDescriptorDigest = null;
          if (descriptorDigests.containsKey(fingerprintPublished)) {
            newDescriptorDigest = Base64.encodeBase64String(Hex.decodeHex(
                descriptorDigests.get(fingerprintPublished).
                toCharArray())).substring(0, 27);
          } else {
            newDescriptorDigest = "AA" + descriptorDigest.substring(2);
          }
          sb.append("r " + parts[1] + " " + newFingerprint + " "
              + newDescriptorDigest + " " + parts[4] + " " + parts[5]
              + " " + parts[6] + " " + parts[7] + " " + parts[8] + "\n");
        } else {
          sb.append(line + "\n");
        }
      }
      bw.write(sb.toString());
    }
    bw.close();
  }
}

