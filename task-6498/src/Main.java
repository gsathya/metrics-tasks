import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;

import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;
import org.torproject.descriptor.ServerDescriptor;

public class Main {
  public static void main(String[] args) throws IOException {

    /* Parse server descriptors in in/server-descriptors/, not keeping a
     * parse history, and memorize bandwidth rate, burst, and observed
     * bandwidth for every server descriptor. */
    DescriptorReader descriptorReader =
        DescriptorSourceFactory.createDescriptorReader();
    descriptorReader.addDirectory(new File("in/server-descriptors"));
    Iterator<DescriptorFile> descriptorFiles =
        descriptorReader.readDescriptors();
    Map<String, int[]> serverDescriptors = new HashMap<String, int[]>();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      for (Descriptor descriptor : descriptorFile.getDescriptors()) {
        if (!(descriptor instanceof ServerDescriptor)) {
          continue;
        }
        ServerDescriptor serverDescriptor = (ServerDescriptor) descriptor;
        String digest = serverDescriptor.getServerDescriptorDigest();
        int[] bandwidths = new int[] {
            serverDescriptor.getBandwidthRate() / 1024,
            serverDescriptor.getBandwidthBurst() / 1024,
            serverDescriptor.getBandwidthObserved() / 1024 };
        serverDescriptors.put(digest.toUpperCase(), bandwidths);
      }
    }

    /* Parse consensuses in in/consensuses/, keeping a parse history. */
    descriptorReader = DescriptorSourceFactory.createDescriptorReader();
    descriptorReader.addDirectory(new File("in/consensuses"));
    descriptorReader.setExcludeFiles(new File("parsed-consensuses"));
    descriptorFiles = descriptorReader.readDescriptors();
    File resultsFile = new File("task-6498-results.csv");
    boolean writeHeader = !resultsFile.exists();
    BufferedWriter bw = new BufferedWriter(new FileWriter(resultsFile,
        true));
    if (writeHeader) {
      bw.write("valid_after,min_rate,min_advbw,ports,same_network,relays,"
          + "exit_prob\n");
    }
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      for (Descriptor descriptor : descriptorFile.getDescriptors()) {
        if (!(descriptor instanceof RelayNetworkStatusConsensus)) {
          continue;
        }
        RelayNetworkStatusConsensus consensus =
            (RelayNetworkStatusConsensus) descriptor;
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss");
        dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String validAfter = dateTimeFormat.format(
            consensus.getValidAfterMillis());
        SortedMap<String, Integer> bandwidthWeights =
            consensus.getBandwidthWeights();
        if (bandwidthWeights == null) {
          continue;
        }
        SortedSet<String> weightKeys = new TreeSet<String>(Arrays.asList(
            "Wee,Wed".split(",")));
        weightKeys.removeAll(bandwidthWeights.keySet());
        if (!weightKeys.isEmpty()) {
          continue;
        }
        double wee = ((double) bandwidthWeights.get("Wee")) / 10000.0,
            wed = ((double) bandwidthWeights.get("Wed")) / 10000.0;
        SortedSet<String> fingerprints = new TreeSet<String>();
        Map<String, Double> exitWeights = new HashMap<String, Double>();
        double totalExitWeight = 0.0;
        Map<String, Integer> bandwidthRates =
            new HashMap<String, Integer>();
        Map<String, Integer> advertisedBandwidths =
            new HashMap<String, Integer>();
        Map<String, Set<Integer>> exitPorts =
            new HashMap<String, Set<Integer>>();
        Map<String, String> addressParts = new HashMap<String, String>();
        for (NetworkStatusEntry relay :
            consensus.getStatusEntries().values()) {
          String fingerprint = relay.getFingerprint();
          fingerprints.add(fingerprint);
          if (!relay.getFlags().contains("Running")) {
            continue;
          }
          boolean isExit = relay.getFlags().contains("Exit") &&
              !relay.getFlags().contains("BadExit");
          boolean isGuard = relay.getFlags().contains("Guard");
          String serverDescriptorDigest = relay.getDescriptor().
              toUpperCase();
          int[] descriptorBandwidths = serverDescriptors.get(
              serverDescriptorDigest);
          int bandwidthRate = 0, advertisedBandwidth = 0;
          if (descriptorBandwidths != null) {
            bandwidthRate = descriptorBandwidths[0];
            advertisedBandwidth = Math.min(Math.min(
                descriptorBandwidths[0],
                descriptorBandwidths[1]),
                descriptorBandwidths[2]);
          }
          bandwidthRates.put(fingerprint, bandwidthRate);
          advertisedBandwidths.put(fingerprint, advertisedBandwidth);
          double exitWeight = (double) relay.getBandwidth();
          if (isGuard && isExit) {
            exitWeight *= wed;
          } else if (isGuard) {
            exitWeight = 0.0;
          } else if (isExit) {
            exitWeight *= wee;
          } else {
            exitWeight = 0.0;
          }
          exitWeights.put(fingerprint, exitWeight);
          totalExitWeight += exitWeight;
          exitPorts.put(fingerprint, new HashSet<Integer>());
          if (relay.getDefaultPolicy() != null &&
              relay.getPortList() != null) {
            boolean acceptPolicy = relay.getDefaultPolicy().equals(
                "accept");
            Set<Integer> policyPorts = new HashSet<Integer>();
            List<Integer> relevantPorts = new ArrayList<Integer>();
            relevantPorts.add(80);
            relevantPorts.add(443);
            relevantPorts.add(554);
            relevantPorts.add(1755);
            for (String part : relay.getPortList().split(",")) {
              int from, to;
              if (part.contains("-")) {
                from = Integer.parseInt(part.split("-")[0]);
                to = Integer.parseInt(part.split("-")[1]);
              } else {
                from = to = Integer.parseInt(part);
              }
              while (!relevantPorts.isEmpty() &&
                  from > relevantPorts.get(0)) {
                relevantPorts.remove(0);
              }
              while (!relevantPorts.isEmpty() &&
                  from <= relevantPorts.get(0) &&
                  to >= relevantPorts.get(0)) {
                policyPorts.add(relevantPorts.remove(0));
              }
            }
            for (int port : new int[] { 80, 443, 554, 1755}) {
              if (!policyPorts.contains(port) ^ acceptPolicy) {
                exitPorts.get(fingerprint).add(port);
              }
            }
          }
          String address = relay.getAddress();
          addressParts.put(fingerprint, address.substring(0,
              address.lastIndexOf(".")));
        }

        /* For the default setting, filter relays which have a bandwidth
         * rate >= 11875 KB/s, advertised bandwidth >= 5000 KB/s, and
         * which permit exiting to ports 80, 443, 554, and 1755.  Only
         * consider the fastest 2 relays per /24 with respect to exit
         * probability.  Also vary requirements.  Overall, analyze these
         * settings:
         *  - rate >= 11875, advbw >= 5000, exit to 80, 443, 554, 1755,
         *    at most 2 relays per /24
         *  - rate >= 10000, advbw >= 2000, exit to 80, 443 */
        int[] minimumBandwidthRates = new int[] { 11875, 10000 };
        int[] minimumAdvertisedBandwidths = new int[] { 5000, 2000 };
        Set<Integer> defaultPorts = new HashSet<Integer>();
        defaultPorts.add(80);
        defaultPorts.add(443);
        defaultPorts.add(554);
        defaultPorts.add(1755);
        Set<Integer> reducedPorts = new HashSet<Integer>();
        reducedPorts.add(80);
        reducedPorts.add(443);
        List<Set<Integer>> requiredPorts = new ArrayList<Set<Integer>>();
        requiredPorts.add(defaultPorts);
        requiredPorts.add(reducedPorts);
        boolean[] sameNetworks = new boolean[] { true, false };
        for (int i = 0; i < minimumBandwidthRates.length; i++) {
          int minimumBandwidthRate = minimumBandwidthRates[i];
          int minimumAdvertisedBandwidth = minimumAdvertisedBandwidths[i];
          Set<Integer> minimumRequiredPorts = requiredPorts.get(i);
          boolean sameNetwork = sameNetworks[i];
          Map<String, List<Double>> exitWeightFractionsByAddressParts =
              new HashMap<String, List<Double>>();
          for (String fingerprint : fingerprints) {
            int bandwidthRate = bandwidthRates.get(fingerprint);
            int advertisedBandwidth = advertisedBandwidths.get(
                fingerprint);
            Set<Integer> allowedExitPorts = exitPorts.get(fingerprint);
            if (bandwidthRate < minimumBandwidthRate ||
                advertisedBandwidth < minimumAdvertisedBandwidth ||
                !allowedExitPorts.containsAll(minimumRequiredPorts)) {
              continue;
            }
            double exitWeightFraction = exitWeights.get(fingerprint)
                / totalExitWeight;
            String addressPart = addressParts.get(fingerprint);
            if (!exitWeightFractionsByAddressParts.containsKey(
                addressPart)) {
              exitWeightFractionsByAddressParts.put(addressPart,
                  new ArrayList<Double>());
            }
            exitWeightFractionsByAddressParts.get(addressPart).add(
                exitWeightFraction);
          }
          double totalExitWeightFraction = 0.0;
          int totalRelays = 0;
          for (List<Double> weightFractions :
              exitWeightFractionsByAddressParts.values()) {
            if (sameNetwork) {
              Collections.sort(weightFractions);
              while (weightFractions.size() > 2) {
                weightFractions.remove(0);
              }
            }
            for (double weightFraction : weightFractions) {
              totalExitWeightFraction += weightFraction;
              totalRelays++;
            }
          }

          /* For each setting, append results to results.csv:
           *  - valid_after: consensus valid-after time
           *  - min_rate: minimum bandwidth rate in KB/s
           *  - min_advbw: minimum advertised bandwidth in KB/s
           *  - ports: "80-443" or "80-443-554-1755"
           *  - relays: number of relays matching the requirements
           *  - exit_prob: sum of exit probabilities */
          bw.write(String.format("%s,%d,%d,%s,%s,%d,%.4f%n",
              validAfter, minimumBandwidthRate,
              minimumAdvertisedBandwidth, minimumRequiredPorts.size() <= 2
              ? "80-443" : "80-443-554-1755",
              sameNetwork ? "TRUE" : "FALSE", totalRelays,
              totalExitWeightFraction));
        }
      }
    }
    bw.close();
  }
}

