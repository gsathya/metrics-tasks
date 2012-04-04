import java.io.*;
public class OneLinePerDescriptor {
  public static void main(String[] args) throws Exception {
    BufferedReader br = new BufferedReader(new FileReader(
        "bridge-bandwidth-histories-raw.txt"));
    String line;
    BufferedWriter bw = new BufferedWriter(new FileWriter(
        "bridge-bandwidth-histories-by-fingerprint.txt"));
    while ((line = br.readLine()) != null) {
      if (line.startsWith("extra-info ")) {
        bw.write("\n" + line);
      } else {
        bw.write(" " + line);
      }
    }
    bw.close();
    br.close();
  }
}

