import java.io.*;
import java.text.*;
import java.util.*;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
public class AnalyzeStatsCoverage {
  public static void main(String[] args) throws Exception {
    File inDirectory = new File("in");
    File tempDirectory = new File("temp");
    File outFile = new File("stats-coverage.csv");

    /* Extract relevant lines from extra-info descriptors in inDirectory
     * and write them to files tempDirectory/$date/$fingerprint-$date for
     * later processing by fingerprint and date. */
    SimpleDateFormat dateTimeFormat =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    if (inDirectory.exists() && inDirectory.isDirectory()) {
      System.out.println("Parsing descriptors in '"
          + inDirectory.getAbsolutePath() + "'.");
      long started = System.currentTimeMillis();
      tempDirectory.mkdirs();
      Stack<File> dirs = new Stack<File>();
      SortedSet<File> files = new TreeSet<File>();
      dirs.add(inDirectory);
      while (!dirs.isEmpty()) {
        File file = dirs.pop();
        if (file.isDirectory()) {
          if (file.getName().equals("statuses")) {
            continue;
          }
          for (File f : file.listFiles()) {
            dirs.add(f);
          }
        } else {
          files.add(file);
        }
      }
      int totalFiles = files.size(), fileNumber = 0;
      for (File file : files) {
        if (++fileNumber % (totalFiles / 1000) == 0) {
          int numberLength = String.valueOf(totalFiles).length();
          long minutesLeft = (((System.currentTimeMillis() - started)
              * (totalFiles - fileNumber)) / fileNumber) / (60L * 1000L);
          System.out.printf("Parsed %" + numberLength + "d of %"
              + numberLength + "d descriptors (%3d %%) %d minutes left%n",
              fileNumber, totalFiles, (fileNumber * 100) / totalFiles,
              minutesLeft);
        }
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line, fingerprint = null, publishedLine = null;
        SortedMap<String, SortedSet<String>> linesByDate =
            new TreeMap<String, SortedSet<String>>();
        while ((line = br.readLine()) != null) {
          if (line.startsWith("extra-info ")) {
            fingerprint = line.split(" ")[2];
          } else if (line.startsWith("write-history ") ||
              line.startsWith("read-history ")) {
            String[] parts = line.split(" ");
            if (parts.length < 6) {
              continue;
            }
            String historyEndDate = parts[1];
            long historyEndMillis = dateTimeFormat.parse(parts[1] + " "
                + parts[2]).getTime();
            long intervalLength = Long.parseLong(parts[3].substring(1));
            if (intervalLength != 900L) {
              System.out.println("Non-standard interval length in "
                  + "line '" + line + "' in file "
                  + file.getAbsolutePath() + ".  Skipping this line.");
              continue;
            }
            int intervals = parts[5].split(",").length;
            long historyStartMillis = historyEndMillis
                - (intervals * intervalLength * 1000L);
            long currentMillis = historyStartMillis;
            String currentDate;
            while ((currentDate = dateFormat.format(currentMillis)).
                compareTo(historyEndDate) <= 0) {
              if (!linesByDate.containsKey(currentDate)) {
                linesByDate.put(currentDate, new TreeSet<String>());
              }
              linesByDate.get(currentDate).add(line);
              currentMillis += 24L * 60L * 60L * 1000L;
            }
          } else if (line.startsWith("dirreq-stats-end ") ||
              line.startsWith("entry-stats-end ") ||
              line.startsWith("exit-stats-end ") ||
              line.startsWith("cell-stats-end ") ||
              line.startsWith("conn-bi-direct ") ||
              line.startsWith("bridge-stats-end ")) {
            String[] parts = line.split(" ");
            if (parts.length < 5) {
              System.out.println("Malformed line '" + line + "' in "
                  + "file " + file.getAbsolutePath() + ".  Skipping "
                  + "this line.");
              continue;
            }
            String statsEndDate = parts[1];
            long statsEndMillis = dateTimeFormat.parse(parts[1] + " "
                + parts[2]).getTime();
            long intervalLength = Long.parseLong(parts[3].substring(1));
            long statsStartMillis = statsEndMillis
                - intervalLength * 1000L;
            long currentMillis = statsStartMillis;
            String currentDate;
            while ((currentDate = dateFormat.format(currentMillis)).
                compareTo(statsEndDate) <= 0) {
              if (!linesByDate.containsKey(currentDate)) {
                linesByDate.put(currentDate, new TreeSet<String>());
              }
              linesByDate.get(currentDate).add(line);
              currentMillis += 24L * 60L * 60L * 1000L;
            }
          } else if (line.startsWith("published ")) {
            publishedLine = line;
          } else if (line.startsWith("geoip-start-time ")) {
            if (publishedLine == null) {
              System.out.println("Missing published line in file "
                  + file.getAbsolutePath() + ".  Skipping "
                  + "geoip-start-time line.");
              continue;
            }
            String[] publishedParts = publishedLine.split(" ");
            if (publishedParts.length < 3) {
              System.out.println("Malformed line '" + publishedLine
                  + "' in file " + file.getAbsolutePath() + ".  "
                  + "Skipping geoip-start-time line.");
              continue;
            }
            String[] parts = line.split(" ");
            if (parts.length < 3) {
              System.out.println("Malformed line '" + line + "' in "
                  + "file " + file.getAbsolutePath() + ".  Skipping "
                  + "this line.");
              continue;
            }
            String statsEndDate = parts[1];
            long statsEndMillis = dateTimeFormat.parse(
                publishedParts[1] + " " + publishedParts[2]).getTime();
            long statsStartMillis = dateTimeFormat.parse(parts[1] + " "
                + parts[2]).getTime();
            long intervalLength = (statsEndMillis - statsStartMillis)
                / 1000L;
            String rewrittenLine = "geoip-stats-end "
                + publishedParts[1] + " " + publishedParts[2] + " ("
                + intervalLength + " s)";
            long currentMillis = statsStartMillis;
            String currentDate;
            while ((currentDate = dateFormat.format(currentMillis)).
                compareTo(statsEndDate) <= 0) {
              if (!linesByDate.containsKey(currentDate)) {
                linesByDate.put(currentDate, new TreeSet<String>());
              }
              linesByDate.get(currentDate).add(rewrittenLine);
              currentMillis += 24L * 60L * 60L * 1000L;
            }
          }
        }
        br.close();
        for (Map.Entry<String, SortedSet<String>> e :
            linesByDate.entrySet()) {
          String date = e.getKey();
          SortedSet<String> lines = e.getValue();
          File outputFile = new File(tempDirectory, date + "/"
              + fingerprint + "-" + date);
          if (outputFile.exists()) {
            br = new BufferedReader(new FileReader(outputFile));
            while ((line = br.readLine()) != null) {
              lines.add(line);
            }
            br.close();
          }
          outputFile.getParentFile().mkdirs();
          BufferedWriter bw = new BufferedWriter(new FileWriter(
              outputFile));
          for (String l : lines) {
            bw.write(l + "\n");
          }
          bw.close();
        }
      }
    }

    /* Parse bridge network statuses and append "running " lines to
     * files tempDirectory/$date/$fingerprint-$date for later processing
     * by fingerprint and date. */
    SimpleDateFormat statusFormat =
        new SimpleDateFormat("yyyyMMdd-HHmmss");
    statusFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    if (inDirectory.exists() && inDirectory.isDirectory()) {
      System.out.println("Parsing statuses in '"
          + inDirectory.getAbsolutePath() + "'.");
      long started = System.currentTimeMillis();
      tempDirectory.mkdirs();
      Stack<File> dirs = new Stack<File>();
      SortedSet<File> files = new TreeSet<File>();
      dirs.add(inDirectory);
      while (!dirs.isEmpty()) {
        File file = dirs.pop();
        if (file.isDirectory()) {
          if (file.getName().equals("extra-infos")) {
            continue;
          }
          for (File f : file.listFiles()) {
            dirs.add(f);
          }
        } else {
          files.add(file);
        }
      }
      int totalFiles = files.size(), fileNumber = 0;
      for (File file : files) {
        if (++fileNumber % (totalFiles / 1000) == 0) {
          int numberLength = String.valueOf(totalFiles).length();
          long minutesLeft = (((System.currentTimeMillis() - started)
              * (totalFiles - fileNumber)) / fileNumber) / (60L * 1000L);
          System.out.printf("Parsed %" + numberLength + "d of %"
              + numberLength + "d statuses (%3d %%) %d minutes left%n",
              fileNumber, totalFiles, (fileNumber * 100) / totalFiles,
              minutesLeft);
        }
        long statusPublishedMillis = statusFormat.parse(
            file.getName().substring(0, "YYYYMMdd-HHmmss".length())).
            getTime();
        SortedSet<String> statusPublishedDates = new TreeSet<String>();
        String statusPublishedString = dateTimeFormat.format(
            statusPublishedMillis);
        statusPublishedDates.add(dateFormat.format(
            statusPublishedMillis));
        statusPublishedDates.add(dateFormat.format(
            statusPublishedMillis + 15L * 60L * 1000L));
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line, rLine = null;
        while ((line = br.readLine()) != null) {
          if (line.startsWith("r ")) {
            rLine = line;
          } else if (line.startsWith("s ") && line.contains(" Running") &&
              rLine != null) {
            String[] parts = rLine.split(" ");
            if (parts.length != 9) {
              System.out.println("Illegal line '" + rLine + "' in "
                  + file.getAbsolutePath() + ".  Skipping this line.");
              continue;
            }
            String fingerprint = Hex.encodeHexString(Base64.decodeBase64(
                parts[2] + "=="));
            for (String date : statusPublishedDates) {
              File outputFile = new File(tempDirectory, date + "/"
                  + fingerprint.toUpperCase() + "-" + date);
              outputFile.getParentFile().mkdirs();
              BufferedWriter bw = new BufferedWriter(new FileWriter(
                  outputFile, true));
              bw.write("running " + statusPublishedString + "\n");
              bw.close();
            }
          }
        }
      }
    }

    /* Parse relevant lines by fingerprint and date.  The result will be
     * how many bytes that relay or bridge read/wrote in total, and how
     * many bytes were included in the different reported statistics.
     * Other results are the number of seconds for which this relay or
     * bridge reported byte histories and other statistics, either based
     * on self-reported bandwidth histories or based on the Running flag
     * in bridge network statuses. */
    if (tempDirectory.exists() && tempDirectory.isDirectory()) {
      System.out.println("Evaluating previously parsed descriptors in '"
          + tempDirectory.getAbsolutePath() + "'.");
      BufferedWriter bw = new BufferedWriter(new FileWriter(outFile));
      bw.write("fingerprint,date,totalwritten,totalread,totalseconds,"
          + "totalrunning,dirreqwritten,dirreqread,dirreqseconds,"
          + "dirreqrunning,entrywritten,entryread,entryseconds,"
          + "entryrunning,exitwritten,exitread,exitseconds,exitrunning,"
          + "cellwritten,cellread,cellseconds,cellrunning,"
          + "connbidirectwritten,connbidirectread,connbidirectseconds,"
          + "connbidirectrunning,bridgewritten,bridgeread,bridgeseconds,"
          + "bridgerunning,geoipwritten,geoipread,geoipseconds,"
          + "geoiprunning\n");
      Stack<File> dirs = new Stack<File>();
      SortedSet<File> files = new TreeSet<File>();
      dirs.add(tempDirectory);
      while (!dirs.isEmpty()) {
        File file = dirs.pop();
        if (file.isDirectory()) {
          for (File f : file.listFiles()) {
            dirs.add(f);
          }
        } else {
          files.add(file);
        }
      }
      int totalFiles = files.size(), fileNumber = 0;
      for (File file : files) {
        if (++fileNumber % (totalFiles / 1000) == 0) {
          int numberLength = String.valueOf(totalFiles).length();
          System.out.printf("Evaluated %" + numberLength + "d of %"
              + numberLength + "d descriptors/days (%3d %%)%n",
              fileNumber, totalFiles, (fileNumber * 100) / totalFiles);
        }
        String fingerprint = file.getName().substring(0, 40);
        String date = file.getName().substring(41);
        long dateStartMillis = dateFormat.parse(date).getTime();
        long dateEndMillis = dateStartMillis + 24L * 60L * 60L * 1000L;
        long[] writeHistory = new long[96], readHistory = new long[96];
        boolean[] upBridge = new boolean[96],
            upStatus = new boolean[96],
            dirreqStats = new boolean[96],
            entryStats = new boolean[96],
            exitStats = new boolean[96],
            cellStats = new boolean[96],
            connBiDirectStats = new boolean[96],
            bridgeStats = new boolean[96],
            geoipStats = new boolean[96];
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        while ((line = br.readLine()) != null) {
          if (line.startsWith("running ")) {
            long statusPublishedMillis = dateTimeFormat.parse(
                line.substring("running ".length())).getTime();
            int j = (int) ((statusPublishedMillis - dateStartMillis)
                / (900L * 1000L));
            for (int i = 0; i < 2; i++) {
              if (j + i >= 0 && j + i < 96) {
                upStatus[j + i] = true;
              }
            }
          } else if (line.startsWith("write-history ") ||
              line.startsWith("read-history ")) {
            long[] history = line.startsWith("write-history ")
                ? writeHistory : readHistory;
            String[] parts = line.split(" ");
            long historyEndMillis = dateTimeFormat.parse(parts[1] + " "
                + parts[2]).getTime();
            String[] historyValues = parts[5].split(",");
            long historyStartMillis = historyEndMillis
                - (historyValues.length * 900L * 1000L);
            long currentMillis = historyStartMillis;
            for (int i = 0; i < historyValues.length; i++) {
              if (currentMillis >= dateStartMillis &&
                  currentMillis < dateEndMillis) {
                int j = (int) ((currentMillis - dateStartMillis)
                    / (900L * 1000L));
                if (j < 0 || j >= 96) {
                  System.out.println("Internal error when processing "
                      + "line '" + line + "'.  Index = " + j
                      + ".  Exiting.");
                  System.exit(1);
                }
                history[j] = Long.parseLong(historyValues[i]);
                upBridge[j] = true;
              }
              currentMillis += 15L * 60L * 1000L;
            }
          } else if (line.startsWith("dirreq-stats-end ") ||
              line.startsWith("entry-stats-end ") ||
              line.startsWith("exit-stats-end ") ||
              line.startsWith("cell-stats-end ") ||
              line.startsWith("conn-bi-direct ") ||
              line.startsWith("bridge-stats-end ") ||
              line.startsWith("geoip-stats-end ")) {
            boolean[] stats = null;
            if (line.startsWith("dirreq-stats-end ")) {
              stats = dirreqStats;
            } else if (line.startsWith("entry-stats-end ")) {
              stats = entryStats;
            } else if (line.startsWith("exit-stats-end ")) {
              stats = exitStats;
            } else if (line.startsWith("cell-stats-end ")) {
              stats = cellStats;
            } else if (line.startsWith("conn-bi-direct ")) {
              stats = connBiDirectStats;
            } else if (line.startsWith("bridge-stats-end ")) {
              stats = bridgeStats;
            } else if (line.startsWith("geoip-stats-end ")) {
              stats = geoipStats;
            } else {
              System.out.println("Internal error when processing line '"
                  + line + "'.  Exiting.");
              System.exit(1);
            }
            String[] parts = line.split(" ");
            long statsEndMillis = dateTimeFormat.parse(parts[1] + " "
                + parts[2]).getTime();
            long intervalLength = Long.parseLong(parts[3].substring(1));
            long statsStartMillis = statsEndMillis
                - intervalLength * 1000L;
            long currentMillis = statsStartMillis;
            while (currentMillis < dateEndMillis) {
              if (currentMillis >= dateStartMillis) {
                int j = (int) ((currentMillis - dateStartMillis)
                    / (900L * 1000L));
                if (j < 0 || j >= 96) {
                  System.out.println("Internal error when processing "
                      + "line '" + line + "'.  Index = " + j
                      + ".  Exiting.");
                  System.exit(1);
                }
                stats[j] = true;
              }
              currentMillis += 15L * 60L * 1000L;
            }
          }
        }
        br.close();
        bw.write(fingerprint + "," + date + ",");
        long totalWritten = 0L, totalRead = 0L, totalSeconds = 0L,
            totalRunning = 0L, dirreqWritten = 0L, dirreqRead = 0L,
            dirreqSeconds = 0L, dirreqRunning = 0L, entryWritten = 0L,
            entryRead = 0L, entrySeconds = 0L, entryRunning = 0L,
            exitWritten = 0L, exitRead = 0L, exitSeconds = 0L,
            exitRunning = 0L, cellWritten = 0L, cellRead = 0L,
            cellSeconds = 0L, cellRunning = 0L, connBiDirectWritten = 0L,
            connBiDirectRead = 0L, connBiDirectSeconds = 0L,
            connBiDirectRunning = 0L, bridgeWritten = 0L, bridgeRead = 0L,
            bridgeSeconds = 0L, bridgeRunning = 0L, geoipWritten = 0L,
            geoipRead = 0L, geoipSeconds = 0L, geoipRunning = 0L;
        for (int i = 0; i < 96; i++) {
          totalWritten += writeHistory[i];
          totalRead += readHistory[i];
          totalSeconds += upBridge[i] ? 900L : 0L;
          totalRunning += upStatus[i] ? 900L : 0L;
          dirreqWritten += dirreqStats[i] ? writeHistory[i] : 0L;
          dirreqRead += dirreqStats[i] ? readHistory[i] : 0L;
          dirreqSeconds += dirreqStats[i] && upBridge[i] ? 900L : 0L;
          dirreqRunning += dirreqStats[i] && upStatus[i] ? 900L : 0L;
          entryWritten += entryStats[i] ? writeHistory[i] : 0L;
          entryRead += entryStats[i] ? readHistory[i] : 0L;
          entrySeconds += entryStats[i] && upBridge[i] ? 900L : 0L;
          entryRunning += entryStats[i] && upStatus[i] ? 900L : 0L;
          exitWritten += exitStats[i] ? writeHistory[i] : 0L;
          exitRead += exitStats[i] ? readHistory[i] : 0L;
          exitSeconds += exitStats[i] && upBridge[i] ? 900L : 0L;
          exitRunning += exitStats[i] && upStatus[i] ? 900L : 0L;
          cellWritten += cellStats[i] ? writeHistory[i] : 0L;
          cellRead += cellStats[i] ? readHistory[i] : 0L;
          cellSeconds += cellStats[i] && upBridge[i] ? 900L : 0L;
          cellRunning += cellStats[i] && upStatus[i] ? 900L : 0L;
          connBiDirectWritten += connBiDirectStats[i] ? writeHistory[i]
              : 0L;
          connBiDirectRead += connBiDirectStats[i] ? readHistory[i]
              : 0L;
          connBiDirectSeconds += connBiDirectStats[i] && upBridge[i]
              ? 900L : 0L;
          connBiDirectRunning += connBiDirectStats[i] && upStatus[i]
              ? 900L : 0L;
          bridgeWritten += bridgeStats[i] ? writeHistory[i] : 0L;
          bridgeRead += bridgeStats[i] ? readHistory[i] : 0L;
          bridgeSeconds += bridgeStats[i] && upBridge[i] ? 900L : 0L;
          bridgeRunning += bridgeStats[i] && upStatus[i] ? 900L : 0L;
          geoipWritten += geoipStats[i] ? writeHistory[i] : 0L;
          geoipRead += geoipStats[i] ? readHistory[i] : 0L;
          geoipSeconds += geoipStats[i] && upBridge[i] ? 900L : 0L;
          geoipRunning += geoipStats[i] && upStatus[i] ? 900L : 0L;
        }
        bw.write(totalWritten + "," + totalRead + "," + totalSeconds + ","
            + totalRunning + "," + dirreqWritten + "," + dirreqRead + ","
            + dirreqSeconds + "," + dirreqRunning + "," + entryWritten
            + "," + entryRead + "," + entrySeconds + "," + entryRunning
            + "," + exitWritten + "," + exitRead + "," + exitSeconds + ","
            + exitRunning + "," + cellWritten + "," + cellRead + ","
            + cellSeconds + "," + cellRunning + "," + connBiDirectWritten
            + "," + connBiDirectRead + "," + connBiDirectSeconds + ","
            + connBiDirectRunning + "," + bridgeWritten + "," + bridgeRead
            + "," + bridgeSeconds + "," + bridgeRunning + ","
            + geoipWritten + "," + geoipRead + "," + geoipSeconds + ","
            + geoipRunning + "\n");
      }
      bw.close();
    }
  }
}

