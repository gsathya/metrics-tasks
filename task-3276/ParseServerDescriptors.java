import java.io.*;
import java.util.*;
public class ParseServerDescriptors {
  public static void main(String[] args) throws Exception {
    Stack<File> files = new Stack<File>();
    files.add(new File(args[0]));
    while (!files.isEmpty()) {
      File file = files.pop();
      if (file.isDirectory()) {
        for (File f : file.listFiles()) {
          files.add(f);
        }
      } else {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line, published = null, fingerprint = null;
        boolean readOnionKey = false;
        StringBuilder onionKey = new StringBuilder();
        while ((line = br.readLine()) != null) {
          if (readOnionKey && !line.startsWith("-----")) {
            onionKey.append(line);
          } else if (line.startsWith("published ")) {
            published = line.substring("published ".length());
          } else if (line.startsWith("opt fingerprint")) {
            fingerprint = line.substring("opt fingerprint ".length()).
                replaceAll(" ", "");
          } else if (line.equals("onion-key")) {
            readOnionKey = true;
          } else if (line.equals("-----END RSA PUBLIC KEY-----")) {
            readOnionKey = false;
          }
        }
        br.close();
        System.out.println(fingerprint + "," + published + ","
            + onionKey.toString());
      }
    }
  }
}

