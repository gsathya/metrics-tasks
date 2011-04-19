import java.io.*;
import java.util.*;

public class Aggregate {
  public static void main(String[] args) throws Exception {
    BufferedReader br = new BufferedReader(new FileReader(
        "torperf-guard-bandwidths-ranks.csv"));
    String line = br.readLine();
    List<String> sortedByBandwidth = new ArrayList<String>(),
        sortedByRank = new ArrayList<String>();
    while ((line = br.readLine()) != null) {
      sortedByBandwidth.add(line);
      sortedByRank.add(line);
    }
    br.close();
    Collections.sort(sortedByBandwidth, new Comparator<String>() {
      public int compare(String a, String b) {
        return Integer.parseInt(a.split(",")[0]) -
            Integer.parseInt(b.split(",")[0]);
      }
    });
    Collections.sort(sortedByRank, new Comparator<String>() {
      public int compare(String a, String b) {
        return (int) (100.0 * (Double.parseDouble(a.split(",")[1]) -
            Double.parseDouble(b.split(",")[1])));
      }
    });

    List<Integer> percentiles = new ArrayList<Integer>();
    for (int percentile = 1; percentile < 100; percentile += 1) {
      percentiles.add(percentile);
    }

    BufferedWriter bw = new BufferedWriter(new FileWriter(
        "torperf-guard-rank-quantiles.csv"));
    bw.write("filesize,rank");
    for (int percentile : percentiles) {
      bw.write(",p" + percentile);
    }
    bw.write(",len\n");
    for (String filesize : Arrays.asList("50kb,1mb,5mb".split(","))) {
      double rankPercentInterval = 0.02;
      double rankPercent = rankPercentInterval;
      List<Integer> times = new ArrayList<Integer>();
      for (int i = 0; i <= sortedByRank.size(); i++) {
        double rank = -1.0;
        int completionTime = -1;
        if (i < sortedByRank.size()) {
          String[] parts = sortedByRank.get(i).split(",");
          rank = Double.parseDouble(parts[1]);
          completionTime = Integer.parseInt(parts[2]);
          if (!parts[4].equals(filesize)) {
            continue;
          }
        }
        if (i == sortedByRank.size() || rank > rankPercent) {
          String rankString = String.format("%.3f",
              rankPercent - 0.5 * rankPercentInterval);
          bw.write(filesize + "," + rankString);
          if (times.size() > 1) {
            Collections.sort(times);
            for (int percentile : percentiles) {
              bw.write("," + times.get(times.size() * percentile / 100));
            }
          } else {
            for (int percentile : percentiles) {
              bw.write(",NA");
            }
          }
          bw.write("," + times.size() + "\n");
          times.clear();
          rankPercent += rankPercentInterval;
        }
        if (i == sortedByRank.size()) {
          break;
        }
        times.add(completionTime);
      }
    }
    bw.close();

    bw = new BufferedWriter(new FileWriter(
        "torperf-guard-bandwidth-quantiles.csv"));
    bw.write("filesize,bandwidth");
    for (int percentile : percentiles) {
      bw.write(",p" + percentile);
    }
    bw.write(",len\n");
    for (String filesize : Arrays.asList("50kb,1mb,5mb".split(","))) {
      int bandwidthInterval = 2500;
      int curBandwidth = bandwidthInterval;
      List<Integer> times = new ArrayList<Integer>();
      for (int i = 0; i <= sortedByBandwidth.size(); i++) {
        int bandwidth = -1;
        int completionTime = -1;
        if (i < sortedByBandwidth.size()) {
          String[] parts = sortedByBandwidth.get(i).split(",");
          bandwidth = Integer.parseInt(parts[0]);
          completionTime = Integer.parseInt(parts[2]);
          if (!parts[4].equals(filesize)) {
            continue;
          }
        }
        if (i == sortedByBandwidth.size() || bandwidth > curBandwidth) {
          String bandwidthString = String.format("%d",
              curBandwidth - (bandwidthInterval / 2));
          bw.write(filesize + "," + bandwidthString);
          if (times.size() > 1) {
            Collections.sort(times);
            for (int percentile : percentiles) {
              bw.write("," + times.get(times.size() * percentile / 100));
            }
          } else {
            for (int percentile : percentiles) {
              bw.write(",NA");
            }
          }
          bw.write("," + times.size() + "\n");
          times.clear();
          curBandwidth += bandwidthInterval;
        }
        if (i == sortedByBandwidth.size()) {
          break;
        }
        times.add(completionTime);
      }
    }
    bw.close();
  }
}

