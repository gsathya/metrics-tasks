import java.io.*;
import java.util.*;
public class Eval {
  /* curl "https://onionoo.torproject.org/details?type=relay" > details.json */
  public static void main(String[] args) throws Exception {

    /* Parse relays and their families from details.json.  Also create a
     * list of Named relays. */
    BufferedReader br = new BufferedReader(new FileReader(
        "details.json"));
    String line;
    Set<String> family = new HashSet<String>();
    String nickname = null, fingerprint = null;
    boolean isParsingFamily = false;
    SortedMap<String, Set<String>> listedRelays =
        new TreeMap<String, Set<String>>();
    Map<String, String> nicknames = new HashMap<String, String>();
    Map<String, String> namedRelays = new HashMap<String, String>();
    Set<String> runningRelays = new HashSet<String>(),
        unnamedRelays = new HashSet<String>();
    while ((line = br.readLine()) != null) {
      if (isParsingFamily) {
        if (line.startsWith("  ")) {
          family.add(line.split("\"")[1]);
        } else {
          listedRelays.put(fingerprint, family);
          family = new HashSet<String>();
          isParsingFamily = false;
        }
      }
      if (line.startsWith("{\"nickname\":")) {
        nickname = line.split(":")[1].split("\"")[1];
      } else if (line.startsWith("\"fingerprint\":")) {
        fingerprint = "$" + line.split(":")[1].split("\"")[1];
        nicknames.put(fingerprint, nickname);
      } else if (line.startsWith("\"running\":")) {
        if (line.endsWith("true,")) {
          runningRelays.add(nickname + "~" + fingerprint.substring(1, 5));
        }
      } else if (line.startsWith("\"flags\":")) {
        if (line.contains("\"Named\"")) {
          if (namedRelays.containsKey(nickname)) {
            System.err.println("Two named relays with same nickname: '"
                + nickname + "'.  Exiting.");
            System.exit(1);
          }
          namedRelays.put(nickname, fingerprint);
        } else {
          unnamedRelays.add(nickname);
        }
      } else if (line.equals("\"family\":[")) {
        isParsingFamily = true;
      }
    }
    br.close();

    /* Print out unconfirmed families reported by relays, possibly
     * containing nicknames of unnamed relays. */
    SortedSet<String> unconfirmedFamilyStrings = new TreeSet<String>();
    System.out.println("Complete family relationships as reported by "
        + "running relays, not mutually confirmed and possibly "
        + "containing nicknames of unnamed relays, including non-running "
        + "relays:");
    for (Map.Entry<String, Set<String>> e : listedRelays.entrySet()) {
      StringBuilder sb = new StringBuilder();
      sb.append(nicknames.get(e.getKey()) + "~"
          + e.getKey().substring(1, 5) + " ->");
      for (String member : e.getValue()) {
        if (member.startsWith("$")) {
          sb.append(" " + (nicknames.containsKey(member) ?
              nicknames.get(member) : "(not-running)") + "~"
              + member.substring(1, 5));
        } else {
          sb.append(" " + member + "~"
              + (namedRelays.containsKey(member) ?
              namedRelays.get(member).substring(1, 5) :
              (unnamedRelays.contains(member) ? "(unnamed)" :
              "(not-running)")));
        }
      }
      unconfirmedFamilyStrings.add(sb.toString());
    }
    for (String s : unconfirmedFamilyStrings) {
      System.out.println(s);
    }
    System.out.println();

    /* Determine mutually confirmed families with two or more family
     * members. */
    SortedMap<String, SortedSet<String>> confirmedRelays =
        new TreeMap<String, SortedSet<String>>();
    for (Map.Entry<String, Set<String>> e : listedRelays.entrySet()) {
      SortedSet<String> confirmedMembers = new TreeSet<String>();
      String ownFingerprint = e.getKey();
      String ownNickname = nicknames.get(e.getKey());
      String ownRelayString = ownNickname + "~"
          + ownFingerprint.substring(1, 5);
      for (String member : e.getValue()) {
        String memberNickname = null, memberFingerprint = null;
        if (!member.startsWith("$") &&
            namedRelays.containsKey(member) &&
            listedRelays.containsKey(namedRelays.get(member))) {
          /* member is the nickname of a named relay. */
          memberNickname = member;
          memberFingerprint = namedRelays.get(member);
        } else if (member.startsWith("$") &&
            listedRelays.containsKey(member)) {
          /* member is the fingerprint of a running relay. */
          memberNickname = nicknames.get(member);
          memberFingerprint = member;
        }
        if (memberFingerprint == null) {
          continue;
        }
        String memberRelayString = memberNickname + "~"
            + memberFingerprint.substring(1, 5);
        Set<String> otherMembers = listedRelays.get(memberFingerprint);
        if (otherMembers != null && (otherMembers.contains(ownFingerprint) ||
            otherMembers.contains(ownNickname)) &&
            !(ownRelayString.equals(memberRelayString))) {
          confirmedMembers.add(memberRelayString);
        }
      }
      if (!confirmedMembers.isEmpty()) {
        confirmedRelays.put(e.getKey(), confirmedMembers);
      }
    }
    SortedSet<String> confirmedFamilyStrings = new TreeSet<String>();
    for (Map.Entry<String, SortedSet<String>> e :
        confirmedRelays.entrySet()) {
      StringBuilder sb = new StringBuilder();
      sb.append(nicknames.get(e.getKey()) + "~"
          + e.getKey().substring(1, 5) + " ->");
      for (String member : e.getValue()) {
        sb.append(" " + member);
      }
      confirmedFamilyStrings.add(sb.toString());
    }
    System.out.println("Mutually confirmed families with two or more "
        + "family members, without reporting relay itself, including "
        + "non-running relays");
    for (String s : confirmedFamilyStrings) {
      System.out.println(s);
    }
    System.out.println();

    /* Determine possibly overlapping families with two or more family
     * members. */
    Set<SortedSet<String>> overlappingFamilies =
        new HashSet<SortedSet<String>>();
    for (Map.Entry<String, SortedSet<String>> e :
        confirmedRelays.entrySet()) {
      SortedSet<String> overlappingFamily = new TreeSet<String>();
      overlappingFamily.add(nicknames.get(e.getKey()) + "~"
          + e.getKey().substring(1, 5));
      overlappingFamily.addAll(e.getValue());
      overlappingFamilies.add(overlappingFamily);
    }
    SortedSet<String> overlappingFamilyStrings = new TreeSet<String>();
    for (SortedSet<String> overlappingFamily : overlappingFamilies) {
      if (overlappingFamily.size() < 2) {
        continue;
      }
      int written = 0;
      StringBuilder sb = new StringBuilder();
      for (String member : overlappingFamily) {
        sb.append((written++ > 0 ? " " : "") + member);
      }
      overlappingFamilyStrings.add(sb.toString());
    }
    System.out.println("Possibly overlapping families with two or more "
        + "family members, including non-running relays:");
    for (String s : overlappingFamilyStrings) {
      System.out.println(s);
    }
    System.out.println();

    /* Merge possibly overlapping families into extended families. */
    Set<SortedSet<String>> extendedFamilies =
        new HashSet<SortedSet<String>>();
    for (SortedSet<String> overlappingFamily : overlappingFamilies) {
      SortedSet<String> newExtendedFamily =
          new TreeSet<String>(overlappingFamily);
      Set<SortedSet<String>> removeExtendedFamilies =
          new HashSet<SortedSet<String>>();
      for (SortedSet<String> extendedFamily : extendedFamilies) {
        for (String member : newExtendedFamily) {
          if (extendedFamily.contains(member)) {
            removeExtendedFamilies.add(extendedFamily);
            break;
          }
        }
        if (removeExtendedFamilies.contains(extendedFamily)) {
          newExtendedFamily.addAll(extendedFamily);
        }
      }
      for (SortedSet<String> removeExtendedFamily :
          removeExtendedFamilies) {
        extendedFamilies.remove(removeExtendedFamily);
      }
      extendedFamilies.add(newExtendedFamily);
    }
    SortedSet<String> extendedFamilyStrings = new TreeSet<String>();
    for (SortedSet<String> extendedFamily : extendedFamilies) {
      if (extendedFamily.size() < 2) {
        continue;
      }
      int written = 0;
      StringBuilder sb = new StringBuilder();
      for (String member : extendedFamily) {
        sb.append((written++ > 0 ? " " : "") + member);
      }
      extendedFamilyStrings.add(sb.toString());
    }
    System.out.println("Extended families based on merging possibly "
        + "overlapping families, including non-running relays:");
    for (String s : extendedFamilyStrings) {
      System.out.println(s);
    }
    System.out.println();

    /* Filter non-running relays from extended families. */
    SortedSet<String> extendedFamilyRunningRelaysStrings =
        new TreeSet<String>();
    for (SortedSet<String> extendedFamily : extendedFamilies) {
      StringBuilder sb = new StringBuilder();
      int written = 0;
      for (String relay : extendedFamily) {
        if (runningRelays.contains(relay)) {
          sb.append((written++ > 0 ? " " : "") + relay);
        }
      }
      if (written > 1) {
        extendedFamilyRunningRelaysStrings.add(sb.toString());
      }
    }
    System.out.println("Extended families, excluding non-running relays "
        + "that may previously have helped merge overlapping families:");
    for (String s : extendedFamilyRunningRelaysStrings) {
      System.out.println(s);
    }
  }
}

