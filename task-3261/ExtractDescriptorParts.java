import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.torproject.descriptor.BridgeNetworkStatus;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;
import org.torproject.descriptor.ExtraInfoDescriptor;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;
import org.torproject.descriptor.ServerDescriptor;

/* Extract the relevant parts from bridge descriptors and consensuses that
 * are required to answer what fraction of bridges are not reporting
 * bridge usage statistics. */
public class ExtractDescriptorParts {
  public static void main(String[] args) throws Exception {

    /* Define paths: we parse descriptor (tarballs) from in/, store the
     * parse history to parse-history, write relevant parts per bridge to
     * temp/, and write publication times of bridge network statuses to
     * bridge-network-statuses. */
    File inDirectory = new File("in");
    File parseHistoryFile = new File("parse-history");
    File tempDirectory = new File("temp");
    File statusFile = new File("bridge-network-statuses");

    /* Read descriptors. */
    SimpleDateFormat dateTimeFormat =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    DescriptorReader reader =
        DescriptorSourceFactory.createDescriptorReader();
    reader.addDirectory(inDirectory);
    reader.setExcludeFiles(parseHistoryFile);
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getDescriptors() != null) {
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {

          /* Extract bridge-stats and geoip-stats from bridge extra-info
           * descriptors. */
          if (descriptor instanceof ExtraInfoDescriptor) {
            System.out.print("e");
            SortedSet<String> lines = new TreeSet<String>();
            ExtraInfoDescriptor extraInfoDescriptor =
                (ExtraInfoDescriptor) descriptor;
            if (extraInfoDescriptor.getBridgeStatsEndMillis() > 0) {
              lines.add("bridge-stats " + dateTimeFormat.format(
                  extraInfoDescriptor.getBridgeStatsEndMillis()) + " "
                  + extraInfoDescriptor.getBridgeStatsIntervalLength()
                  + " " + (extraInfoDescriptor.getGeoipDbDigest() == null
                  ? "NA" : extraInfoDescriptor.getGeoipDbDigest()));
            }
            if (extraInfoDescriptor.getGeoipStartTimeMillis() > 0) {
              long intervalLength =
                  (extraInfoDescriptor.getPublishedMillis()
                  - extraInfoDescriptor.getGeoipStartTimeMillis())
                  / 1000L;
              String geoipStatsEnd = dateTimeFormat.format(
                  extraInfoDescriptor.getPublishedMillis());
              lines.add("geoip-stats " + geoipStatsEnd + " "
                  + intervalLength + " "
                  + (extraInfoDescriptor.getGeoipDbDigest() == null
                  ? "NA" : extraInfoDescriptor.getGeoipDbDigest()));
            }
            if (!lines.isEmpty()) {
              File outputFile = new File(tempDirectory,
                  extraInfoDescriptor.getFingerprint().toUpperCase());
              outputFile.getParentFile().mkdirs();
              BufferedWriter bw = new BufferedWriter(new FileWriter(
                  outputFile, true));
              for (String l : lines) {
                bw.write(l + "\n");
              }
              bw.close();
            }

          /* Extract all bridges with the Running flag from bridge network
           * statuses.  Also extract the status publication time. */
          } else if (descriptor instanceof BridgeNetworkStatus) {
            System.out.print("n");
            BridgeNetworkStatus status = (BridgeNetworkStatus) descriptor;
            String published = dateTimeFormat.format(
                status.getPublishedMillis());
            if (status.getStatusEntries() != null) {
              for (NetworkStatusEntry entry :
                  status.getStatusEntries().values()) {
                if (entry.getFlags().contains("Running")) {
                  File outputFile = new File(tempDirectory,
                      entry.getFingerprint().toUpperCase());
                  outputFile.getParentFile().mkdirs();
                  BufferedWriter bw = new BufferedWriter(new FileWriter(
                      outputFile, true));
                  String digest = entry.getDescriptor().toUpperCase();
                  bw.write("running-bridge " + published + " " + digest
                      + "\n");
                  bw.close();
                }
              }
              BufferedWriter bw = new BufferedWriter(new FileWriter(
                  statusFile, true));
              bw.write(published + "\n");
              bw.close();
            }

          /* Extract publication time, digest, uptime, and platform string
           * from bridge server descriptors. */
          } else if (descriptor instanceof ServerDescriptor) {
            System.out.print("s");
            ServerDescriptor serverDescriptor =
                (ServerDescriptor) descriptor;
            String published = dateTimeFormat.format(
                serverDescriptor.getPublishedMillis());
            String digest = descriptorFile.getFileName().substring(
                descriptorFile.getFileName().lastIndexOf("/") + 1).
                toUpperCase();
            String uptime = serverDescriptor.getUptime() == null ? "-1"
                : String.valueOf(serverDescriptor.getUptime());
            String platform = serverDescriptor.getPlatform() == null
                ? "NA" : serverDescriptor.getPlatform();
            File outputFile = new File(tempDirectory,
                serverDescriptor.getFingerprint().toUpperCase());
            outputFile.getParentFile().mkdirs();
            BufferedWriter bw = new BufferedWriter(new FileWriter(
                outputFile, true));
            bw.write("server-descriptor " + published + " "
                + digest + " " + uptime + " " + platform + "\n");
            bw.close();

          /* Extract hashed fingerprints of all relays with the Running
           * flag from relay network status consensuses. */
          } else if (descriptor instanceof RelayNetworkStatusConsensus) {
            System.out.print("r");
            RelayNetworkStatusConsensus status =
                (RelayNetworkStatusConsensus) descriptor;
            if (status.getStatusEntries() != null) {
              for (NetworkStatusEntry entry :
                  status.getStatusEntries().values()) {
                if (entry.getFlags().contains("Running")) {
                  String hashedFingerprint = Hex.encodeHexString(
                      DigestUtils.sha(Hex.decodeHex(
                      entry.getFingerprint().toCharArray()))).
                      toUpperCase();
                  File outputFile = new File(tempDirectory,
                      hashedFingerprint);
                  outputFile.getParentFile().mkdirs();
                  BufferedWriter bw = new BufferedWriter(new FileWriter(
                      outputFile, true));
                  bw.write("running-relay " + dateTimeFormat.format(
                      status.getValidAfterMillis()) + "\n");
                  bw.close();
                }
              }
            }
          }
        }
      }
    }
  }
}

