import java.io.*;
import java.util.*;
import org.torproject.descriptor.*;

public class Eval {
  public static void main(String[] args) throws IOException {
    SortedMap<String, String> lines = new TreeMap<String, String>();
    File inputDirectory = new File("in");
    RelayDescriptorReader reader =
        DescriptorSourceFactory.createRelayDescriptorReader();
    reader.addDirectory(inputDirectory);
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      for (Descriptor descriptor : descriptorFile.getDescriptors()) {
        if (!(descriptor instanceof ExtraInfoDescriptor)) {
          continue;
        }
        ExtraInfoDescriptor extraInfoDescriptor =
            (ExtraInfoDescriptor) descriptor;
        BandwidthHistory dirreqWriteHistory =
            extraInfoDescriptor.getDirreqWriteHistory();
        if (dirreqWriteHistory != null) {
          String nickname = extraInfoDescriptor.getNickname();
          for (Map.Entry<Long, Long> e :
              dirreqWriteHistory.getBandwidthValues().entrySet()) {
            long intervalEnd = e.getKey();
            long writtenDirreqBytes = e.getValue();
            String key = nickname + ","
                + String.valueOf(intervalEnd / 1000L);
            String value = key + "," + String.valueOf(writtenDirreqBytes);
            lines.put(key, value);
          }
        }
      }
    }
    BufferedWriter bw = new BufferedWriter(new FileWriter("out.csv"));
    for (String line : lines.values()) {
      bw.write(line + "\n");
    }
    bw.close();
  }
}

