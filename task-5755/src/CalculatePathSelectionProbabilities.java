import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;
import org.torproject.descriptor.ServerDescriptor;

/*
 * Calculate five path-selection probabilities for relays based on
 * consensus weights and advertised bandwidths:
 *
 *  - advertised_bandwidth_fraction: Relative advertised bandwidth of
 *    this relay compared to the total advertised bandwidth in the
 *    network.  If there were no bandwidth authorities, this fraction
 *    would be the probability of this relay to be selected by clients.
 *
 *  - consensus_weight_fraction: Fraction of this relay's consensus weight
 *    compared to the sum of all consensus weights in the network.  This
 *    fraction is a very rough approximation of the probability of this
 *    relay to be selected by clients.
 *
 *  - guard_probability: Probability of this relay to be selected for the
 *    guard position.  This probability is calculated based on consensus
 *    weights, relay flags, directory ports, and bandwidth weights in the
 *    consensus.  Path selection depends on more factors, so that this
 *    probability can only be an approximation.
 *
 *  - middle_probability: Probability of this relay to be selected for the
 *    middle position.  This probability is calculated based on consensus
 *    weights, relay flags, directory ports, and bandwidth weights in the
 *    consensus.  Path selection depends on more factors, so that this
 *    probability can only be an approximation.
 *
 *  - exit_probability: Probability of this relay to be selected for the
 *    exit position.  This probability is calculated based on consensus
 *    weights, relay flags, directory ports, and bandwidth weights in the
 *    consensus.  Path selection depends on more factors, so that this
 *    probability can only be an approximation.
 */
public class CalculatePathSelectionProbabilities {
  public static void main(String[] args) throws Exception {

    /* Read advertised bandwidths of all server descriptors in
     * in/server-descriptors/ to memory.  This is a rather brute-force
     * approach, but it's fine for running this analysis. */
    DescriptorReader descriptorReader =
        DescriptorSourceFactory.createDescriptorReader();
    descriptorReader.addDirectory(new File("in/server-descriptors"));
    Iterator<DescriptorFile> descriptorFiles =
        descriptorReader.readDescriptors();
    Map<String, Integer> serverDescriptors =
        new HashMap<String, Integer>();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      for (Descriptor descriptor : descriptorFile.getDescriptors()) {
        if (!(descriptor instanceof ServerDescriptor)) {
          continue;
        }
        ServerDescriptor serverDescriptor = (ServerDescriptor) descriptor;
        String digest = serverDescriptor.getServerDescriptorDigest();
        int advertisedBandwidth = Math.min(Math.min(
            serverDescriptor.getBandwidthBurst(),
            serverDescriptor.getBandwidthObserved()),
            serverDescriptor.getBandwidthRate());
        serverDescriptors.put(digest.toUpperCase(), advertisedBandwidth);
      }
    }

    /* Go through consensuses in in/consensuses/ in arbitrary order and
     * calculate the five path-selection probabilities for each of them.
     * Write results for a given consensuses to a single new line per
     * relay to out.csv. */
    descriptorReader = DescriptorSourceFactory.createDescriptorReader();
    descriptorReader.addDirectory(new File("in/consensuses"));
    descriptorFiles = descriptorReader.readDescriptors();
    BufferedWriter bw = new BufferedWriter(new FileWriter("out.csv"));
    bw.write("validafter,fingerprint,advertised_bandwidth_fraction,"
        + "consensus_weight_fraction,guard_probability,"
        + "middle_probability,exit_probability\n");
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      for (Descriptor descriptor : descriptorFile.getDescriptors()) {
        if (!(descriptor instanceof RelayNetworkStatusConsensus)) {
          continue;
        }
        RelayNetworkStatusConsensus consensus =
            (RelayNetworkStatusConsensus) descriptor;

        /* Extract valid-after time and bandwidth weights from the parsed
         * consensus. */
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss");
        dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String validAfter = dateTimeFormat.format(
            consensus.getValidAfterMillis());
        SortedMap<String, Integer> bandwidthWeights =
            consensus.getBandwidthWeights();
        SortedSet<String> weightKeys = new TreeSet<String>(Arrays.asList((
            "Wgg,Wgm,Wgd,Wmg,Wmm,Wme,Wmd,Weg,Wem,Wee,Wed,Wgb,Wmb,Web,Wdb,"
            + "Wbg,Wbm,Wbe,Wbd").split(",")));
        weightKeys.removeAll(bandwidthWeights.keySet());
        if (!weightKeys.isEmpty()) {
          System.err.print("Consensus is missing bandwidth-weights:");
          for (String weightKey : weightKeys) {
            System.err.print(" " + weightKey);
          }
          System.err.println();
          continue;
        }
        double wgg = ((double) bandwidthWeights.get("Wgg")) / 10000.0,
            wgd = ((double) bandwidthWeights.get("Wgd")) / 10000.0,
            wmg = ((double) bandwidthWeights.get("Wmg")) / 10000.0,
            wmm = ((double) bandwidthWeights.get("Wmm")) / 10000.0,
            wme = ((double) bandwidthWeights.get("Wme")) / 10000.0,
            wmd = ((double) bandwidthWeights.get("Wmd")) / 10000.0,
            wee = ((double) bandwidthWeights.get("Wee")) / 10000.0,
            wed = ((double) bandwidthWeights.get("Wed")) / 10000.0;

        /* Go through network statuses and calculate the five weights for
         * each of them.  Also sum up totals to calculate probabilities
         * later. */
        SortedMap<String, Double>
            advertisedBandwidths = new TreeMap<String, Double>(),
            consensusWeights = new TreeMap<String, Double>(),
            guardWeights = new TreeMap<String, Double>(),
            middleWeights = new TreeMap<String, Double>(),
            exitWeights = new TreeMap<String, Double>();
        double totalAdvertisedBandwidth = 0.0;
        double totalConsensusWeight = 0.0;
        double totalGuardWeight = 0.0;
        double totalMiddleWeight = 0.0;
        double totalExitWeight = 0.0;
        for (NetworkStatusEntry relay :
            consensus.getStatusEntries().values()) {
          String fingerprint = relay.getFingerprint();
          if (!relay.getFlags().contains("Running")) {
            continue;
          }
          boolean isExit = relay.getFlags().contains("Exit") &&
              !relay.getFlags().contains("BadExit");
          boolean isGuard = relay.getFlags().contains("Guard");
          String serverDescriptorDigest = relay.getDescriptor().
              toUpperCase();
          double advertisedBandwidth;
          if (!serverDescriptors.containsKey(serverDescriptorDigest)) {
            System.err.println("Missing server descriptor "
                + serverDescriptorDigest + " while processing consensus "
                + validAfter + ".  Setting advertised bandwidth to 0.");
            advertisedBandwidth = 0.0;
          } else {
            advertisedBandwidth = (double) serverDescriptors.get(
                serverDescriptorDigest);
          }
          double consensusWeight = (double) relay.getBandwidth();
          double guardWeight = (double) relay.getBandwidth();
          double middleWeight = (double) relay.getBandwidth();
          double exitWeight = (double) relay.getBandwidth();

          /* Case 1: relay has both Guard and Exit flag and could be
           * selected for either guard, middle, or exit position.  Apply
           * bandwidth weights W?d. */
          if (isGuard && isExit) {
            guardWeight *= wgd;
            middleWeight *= wmd;
            exitWeight *= wed;

          /* Case 2: relay only has the Guard flag, not the Exit flag.
           * While, in theory, the relay could also be picked for the exit
           * position (if it has a weird exit policy), Weg is hard-coded
           * to 0 here.  Otherwise, relays with exit policy reject *:*
           * would show up as possible exits, which makes no sense.  Maybe
           * this is too much of an oversimplification?  For the other
           * positions, apply bandwidth weights W?g. */
          } else if (isGuard) {
            guardWeight *= wgg;
            middleWeight *= wmg;
            exitWeight = 0.0;

          /* Case 3: relay only has the Exit flag, not the Guard flag.  It
           * cannot be picked for the guard position, so set Wge to 0.
           * For the other positions, apply bandwidth weights W?e. */
          } else if (isExit) {
            guardWeight = 0.0;
            middleWeight *= wme;
            exitWeight *= wee;

          /* Case 4: relay has neither Exit nor Guard flag.  Similar to
           * case 2, this relay *could* have a weird exit policy and be
           * picked in the exit position.  Same rationale applies, so Wme
           * is set to 0.  The relay cannot be a guard, so Wgm is 0, too.
           * For middle position, apply bandwidth weight Wmm. */
          } else {
            guardWeight = 0.0;
            middleWeight *= wmm;
            exitWeight = 0.0;
          }

          /* Store calculated weights and update totals. */
          advertisedBandwidths.put(fingerprint, advertisedBandwidth);
          consensusWeights.put(fingerprint, consensusWeight);
          guardWeights.put(fingerprint, guardWeight);
          middleWeights.put(fingerprint, middleWeight);
          exitWeights.put(fingerprint, exitWeight);
          totalAdvertisedBandwidth += advertisedBandwidth;
          totalConsensusWeight += consensusWeight;
          totalGuardWeight += guardWeight;
          totalMiddleWeight += middleWeight;
          totalExitWeight += exitWeight;
        }

        /* Write calculated path-selection probabilities to the output
         * file. */
        for (NetworkStatusEntry relay :
            consensus.getStatusEntries().values()) {
          String fingerprint = relay.getFingerprint();
          bw.write(String.format("%s,%s,%.9f,%.9f,%.9f,%.9f,%.9f%n",
              validAfter,
              fingerprint,
              advertisedBandwidths.get(fingerprint)
                  / totalAdvertisedBandwidth,
              consensusWeights.get(fingerprint) / totalConsensusWeight,
              guardWeights.get(fingerprint) / totalGuardWeight,
              middleWeights.get(fingerprint) / totalMiddleWeight,
              exitWeights.get(fingerprint) / totalExitWeight));
        }
      }
    }
    bw.close();
  }
}

