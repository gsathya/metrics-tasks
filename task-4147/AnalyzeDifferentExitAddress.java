import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.torproject.descriptor.BandwidthHistory;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;
import org.torproject.descriptor.ExitList;
import org.torproject.descriptor.ExitListEntry;
import org.torproject.descriptor.ExtraInfoDescriptor;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;

/* Answer the question what fraction of bytes written by relays with the
 * Exit flag could have used a different address for exiting than the
 * relay used for registering in the Tor network. */
public class AnalyzeDifferentExitAddress {
  public static void main(String[] args) throws Exception {

    System.out.println(new Date() + " Starting.");

    /* Iterate over extra-info descriptors to learn about bandwidth
     * histories.  Append 15-minute intervals of written bytes to
     * status/written-bytes/$fingerprint. */
    System.out.println(new Date() + " Reading in/extra-infos/* ...");
    DescriptorReader extraInfoReader = DescriptorSourceFactory
        .createDescriptorReader();
    extraInfoReader.addDirectory(new File("in/extra-infos"));
    extraInfoReader.setExcludeFiles(new File(
        "status/extra-info-history"));
    Iterator<DescriptorFile> extraInfoFiles =
        extraInfoReader.readDescriptors();
    while (extraInfoFiles.hasNext()) {
      DescriptorFile extraInfoFile = extraInfoFiles.next();
      if (extraInfoFile.getDescriptors() != null) {
        for (Descriptor descriptor : extraInfoFile.getDescriptors()) {
          ExtraInfoDescriptor extraInfoDescriptor =
              (ExtraInfoDescriptor) descriptor;
          BandwidthHistory writeHistory = extraInfoDescriptor.
              getWriteHistory();
          if (writeHistory == null) {
            continue;
          }
          String fingerprint = extraInfoDescriptor.getFingerprint();
          File writtenBytesFile = new File("status/written-bytes/"
              + fingerprint);
          writtenBytesFile.getParentFile().mkdirs();
          BufferedWriter bw = new BufferedWriter(new FileWriter(
              writtenBytesFile, true));
          for (Map.Entry<Long, Long> e :
              writeHistory.getBandwidthValues().entrySet()) {
            long intervalEndMillis = e.getKey();
            long bytesWritten = e.getValue();
            bw.write(String.valueOf(intervalEndMillis) + " "
                + String.valueOf(bytesWritten) + "\n");
          }
          bw.close();
        }
      }
    }

    /* Iterate over exit lists to learn about exit IP addresses.  Append
     * lines to status/exit-addresses/$fingerprint. */
    System.out.println(new Date() + " Reading in/exit-lists/* ...");
    DescriptorReader exitListReader =
        DescriptorSourceFactory.createDescriptorReader();
    exitListReader.addDirectory(new File("in/exit-lists"));
    exitListReader.setExcludeFiles(new File("status/exit-list-history"));
    Iterator<DescriptorFile> exitListFiles =
        exitListReader.readDescriptors();
    while (exitListFiles.hasNext()) {
      DescriptorFile exitListFile = exitListFiles.next();
      if (exitListFile.getDescriptors() != null) {
        for (Descriptor descriptor : exitListFile.getDescriptors()) {
          ExitList exitList = (ExitList) descriptor;
          if (exitList.getExitListEntries() == null) {
            continue;
          }
          for (ExitListEntry exitListEntry :
                exitList.getExitListEntries()) {
            String fingerprint = exitListEntry.getFingerprint();
            File exitAddressesFile = new File("status/exit-addresses/"
                + fingerprint);
            exitAddressesFile.getParentFile().mkdirs();
            long scanMillis = exitListEntry.getScanMillis();
            String address = exitListEntry.getExitAddress();
            BufferedWriter bw = new BufferedWriter(new FileWriter(
                exitAddressesFile, true));
            bw.write(String.valueOf(scanMillis) + " " + address + "\n");
            bw.close();
          }
        }
      }
    }

    /* Iterate over consensuses to learn about OR addresses of relays with
     * the Exit flag.  Append lines to
     * status/or-addresses/$fingerprint. */
    System.out.println(new Date() + " Reading in/consensuses/* ...");
    DescriptorReader consensusReader =
        DescriptorSourceFactory.createDescriptorReader();
    consensusReader.addDirectory(new File("in/consensuses"));
    consensusReader.setExcludeFiles(new File("status/consensus-history"));
    Iterator<DescriptorFile> consensusFiles =
        consensusReader.readDescriptors();
    while (consensusFiles.hasNext()) {
      DescriptorFile consensusFile = consensusFiles.next();
      if (consensusFile.getDescriptors() != null) {
        for (Descriptor descriptor : consensusFile.getDescriptors()) {
          RelayNetworkStatusConsensus consensus =
              (RelayNetworkStatusConsensus) descriptor;
          if (consensus.getStatusEntries() == null) {
            continue;
          }
          long validAfterMillis = consensus.getValidAfterMillis();
          for (NetworkStatusEntry statusEntry :
              consensus.getStatusEntries().values()) {
            if (!statusEntry.getFlags().contains("Exit")) {
              continue;
            }
            String fingerprint = statusEntry.getFingerprint();
            File orAddressesFile = new File("status/or-addresses/"
                + fingerprint);
            orAddressesFile.getParentFile().mkdirs();
            String address = statusEntry.getAddress();
            BufferedWriter bw = new BufferedWriter(new FileWriter(
                orAddressesFile, true));
            bw.write(String.valueOf(validAfterMillis) + " " + address
                + "\n");
            bw.close();
          }
        }
      }
    }

    /* Make sure not to overwrite existing results, and prepare writing
     * results otherwise. */
    File differentExitAddressFile = new File(
        "out/different-exit-address.csv");
    if (differentExitAddressFile.exists()) {
      return;
    } else {
      differentExitAddressFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          differentExitAddressFile));
      bw.write("timestamp,differentaddress,writtenbytes\n");
      bw.close();
    }

    /* Iterate over OR addresses of relays with the Exit flag. */
    System.out.println(new Date() + " Writing "
        + "out/different-exit-address.csv ...");
    for (File orAddressesFile :
          new File("status/or-addresses").listFiles()) {
      String fingerprint = orAddressesFile.getName();

      /* For every relay, read OR addresses, bandwidth histories, and
       * exit addresses to memory. */
      SortedMap<Long, String> orAddresses = new TreeMap<Long, String>();
      SortedMap<Long, Long> writtenBytes = new TreeMap<Long, Long>();
      SortedMap<Long, String> exitAddresses = new TreeMap<Long, String>();
      String line;
      BufferedReader br = new BufferedReader(new FileReader(
          orAddressesFile));
      while ((line = br.readLine()) != null) {
        String[] parts = line.split(" ");
        long validAfterMillis = Long.parseLong(parts[0]);
        String address = parts[1];
        orAddresses.put(validAfterMillis, address);
      }
      br.close();
      File writtenBytesFile = new File("status/written-bytes/"
          + fingerprint);
      if (!writtenBytesFile.exists()) {
        continue;
      }
      br = new BufferedReader(new FileReader(writtenBytesFile));
      while ((line = br.readLine()) != null) {
        String[] parts = line.split(" ");
        long intervalEndMillis = Long.parseLong(parts[0]);
        long bytes = Long.parseLong(parts[1]);
        writtenBytes.put(intervalEndMillis, bytes);
      }
      br.close();
      File exitAddressesFile = new File("status/exit-addresses/"
          + fingerprint);
      if (exitAddressesFile.exists()) {
        br = new BufferedReader(new FileReader(exitAddressesFile));
        while ((line = br.readLine()) != null) {
          String[] parts = line.split(" ");
          long scanMillis = Long.parseLong(parts[0]);
          String address = parts[1];
          exitAddresses.put(scanMillis, address);
        }
        br.close();
      }

      /* Go through consensuses containing this relay as Exit relay in
       * chronological order, sum up written bytes in the hour after the
       * consensuses' valid-after time, and look up any exit addresses
       * found up to 23 hours before up to 1 hour after the valid-after
       * time. */
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          differentExitAddressFile, true));
      for (Map.Entry<Long, String> e : orAddresses.entrySet()) {
        long validAfterMillis = e.getKey();
        String currentOrAddress = e.getValue();
        long currentWrittenBytes = 0L;
        for (long currentBytes : writtenBytes.tailMap(validAfterMillis).
            headMap(validAfterMillis + 1L * 60L * 60L * 1000L).values()) {
          currentWrittenBytes += currentBytes;
        }
        if (currentWrittenBytes < 1L) {
          continue;
        }
        Set<String> currentExitAddresses = new HashSet<String>();
        for (String currentExitAddress : exitAddresses.tailMap(
            validAfterMillis - 23L * 60L * 60L * 1000L).headMap(
            validAfterMillis + 1L * 60L * 60L * 1000L).values()) {
          if (!currentExitAddress.equals(currentOrAddress)) {
            currentExitAddresses.add(currentExitAddress);
          }
        }
        boolean usedOtherAddress = !currentExitAddresses.isEmpty();
        bw.write(String.valueOf(validAfterMillis / 1000L) + ","
            + (usedOtherAddress ? "TRUE" : "FALSE") + ","
            + currentWrittenBytes + "\n");
      }
      bw.close();
    }

    System.out.println(new Date() + " Terminating.");
  }
}

