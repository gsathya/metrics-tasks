import java.io.*;
import java.text.*;
import java.util.*;
import org.torproject.descriptor.*;

/* Extract client speed trends from download times of network status
 * consensuses.  Directory mirrors report mininum, maximum, median,
 * quartiles, and deciles of measured client bandwidths in their
 * extra-info descriptors.  Combine statistics from the top 50 percent of
 * directory mirrors by finished downloads, under the assumption that
 * these directory mirrors had enough available bandwidth to serve even
 * fast clients.  Calculate statistics on a given day by reconstructing
 * original client bandwidths from reported percentiles by assuming a
 * linear increase between two reported percentiles and calculating
 * percentiles of all client bandwidth values on that day. */
public class ExtractClientSpeedTrends {
  public static void main(String[] args) throws Exception {

    System.out.println(new Date() + " Starting.");

    /* Read dirreq-v3-tunneled-dl lines from in/extra-infos/ and append
     * them to files status/YYYY-MM-DD, prefixing lines with the relay
     * fingerprint to detect duplicates. */
    System.out.println(new Date() + " Reading in/extra-infos/* ...");
    SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
    dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    SimpleDateFormat dateTimeFormatter = new SimpleDateFormat(
        "yyyy-MM-dd hh:mm:ss");
    dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    dateTimeFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    DescriptorReader extraInfoReader = DescriptorSourceFactory
        .createDescriptorReader();
    extraInfoReader.addDirectory(new File("in/extra-infos"));
    extraInfoReader.setExcludeFiles(new File(
        "status/extra-info-history"));
    Iterator<DescriptorFile> extraInfoFiles =
        extraInfoReader.readDescriptors();
    List<String> columns = Arrays.asList(
        "complete,min,d1,d2,q1,d3,d4,md,d6,d7,q3,d8,d9,max".split(","));
    while (extraInfoFiles.hasNext()) {
      DescriptorFile extraInfoFile = extraInfoFiles.next();
      if (extraInfoFile.getDescriptors() == null) {
        continue;
      }
      for (Descriptor descriptor : extraInfoFile.getDescriptors()) {
        ExtraInfoDescriptor extraInfoDescriptor =
            (ExtraInfoDescriptor) descriptor;
        SortedMap<String, Integer> dirreqV3TunneledDl =
            extraInfoDescriptor.getDirreqV3TunneledDl();
        if (dirreqV3TunneledDl == null ||
            !dirreqV3TunneledDl.keySet().containsAll(columns)) {
          continue;
        }
        long dirreqStatsEndMillis = extraInfoDescriptor.
            getDirreqStatsEndMillis();
        String dirreqStatsEndDate = dateFormatter.format(
            dirreqStatsEndMillis);
        String dirreqStatsEndDateTime = dateTimeFormatter.format(
            dirreqStatsEndMillis);
        String fingerprint = extraInfoDescriptor.getFingerprint();
        File statusFile = new File("status/" + dirreqStatsEndDate);
        statusFile.getParentFile().mkdirs();
        BufferedWriter bw = new BufferedWriter(new FileWriter(statusFile,
            true));
        bw.write(fingerprint + "," + dirreqStatsEndDateTime);
        for (String column : columns) {
          bw.write("," + String.valueOf(dirreqV3TunneledDl.get(column)));
        }
        bw.write("\n");
        bw.close();
      }
    }
          
    /* Iterate over files status/YYYY-MM-DD, aggregate client speed
     * statistics, and write them to out/client-speed-trends.csv. */
    System.out.println(new Date() + " Writing "
        + "out/client-speed-trends.csv ...");
    File clientSpeedTrendsFile = new File("out/client-speed-trends.csv");
    if (!clientSpeedTrendsFile.exists()) {
      clientSpeedTrendsFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          clientSpeedTrendsFile));
      bw.write("date,lines,complete,p0,p10,p20,p25,p30,p40,p50,p60,p70,"
          + "p75,p80,p90,p100\n");
      for (File statusFile : new File("status").listFiles()) {
        if (!statusFile.getName().startsWith("20")) {
          continue;
        }
        BufferedReader br = new BufferedReader(new FileReader(
            statusFile));
        String line;
        Set<String> statsLines = new HashSet<String>();
        List<Integer> completes = new ArrayList<Integer>();
        while ((line = br.readLine()) != null) {
          int complete = Integer.parseInt(line.split(",")[2]);
          completes.add(complete);
          statsLines.add(line);
        }
        br.close();
        Collections.sort(completes);
        int completeP50 = completes.get((completes.size() - 1) / 2);
        List<Integer> downloadTimes = new ArrayList<Integer>();
        List<Integer> percentiles = Arrays.asList(new Integer[] { 0, 10,
            20, 25, 30, 40, 50, 60, 70, 75, 80, 90, 100 });
        int containedStatsLines = 0;
        for (String statsLine : statsLines) {
          String[] parts = statsLine.split(",");
          int complete = Integer.parseInt(parts[2]);
          if (complete < completeP50) {
            continue;
          }
          containedStatsLines++;
          int previousDownloadTime = Integer.parseInt(parts[3]);
          downloadTimes.add(previousDownloadTime);
          int previousPercentile = 0;
          for (int i = 4; i < parts.length; i++) {
            int currentDownloadTime = Integer.parseInt(parts[i]);
            int currentPercentile = percentiles.get(i - 3);
            int values = complete * (currentPercentile
                - previousPercentile) / 100;
            if (values > 0) {
              int increment = (currentDownloadTime - previousDownloadTime)
                  / values;
              int downloadTime = previousDownloadTime;
              for (int j = 0; j < values; j++) {
                downloadTime += increment;
                downloadTimes.add(downloadTime);
              }
            }
            previousDownloadTime = currentDownloadTime;
            previousPercentile = currentPercentile;
          }
        }
        bw.write(statusFile.getName() + "," + containedStatsLines + ","
            + downloadTimes.size());
        Collections.sort(downloadTimes);
        for (int percentile : percentiles) {
          int downloadTime = downloadTimes.get(((percentile
              * (downloadTimes.size() - 1)) / 100));
          bw.write("," + downloadTime);
        }
        bw.write("\n");
      }
      bw.close();
    }

    System.out.println(new Date() + " Terminating.");
  }
}

