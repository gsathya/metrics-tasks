/* Copyright 2012 The Tor Project
 * See LICENSE for licensing information */
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.security.Security;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.encodings.PKCS1Encoding;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;
import org.torproject.descriptor.DirectoryKeyCertificate;
import org.torproject.descriptor.DirectorySignature;
import org.torproject.descriptor.RelayNetworkStatusConsensus;
import org.torproject.descriptor.ServerDescriptor;

/*
 * Verify server descriptors using the contained signing key.  Verify that
 * 1) a contained fingerprint is actually a hash of the signing key and
 * 2) a router signature was created using the signing key.
 *
 * Verify consensuses using the separate certs.  Verify that
 * 1) the fingerprint in a cert is actually a hash of the identity key,
 * 2) a cert was signed using the identity key,
 * 3) a consensus was signed using the signing key from the cert.
 *
 * Usage:
 * - Put certs in in/certs/, consensuses in in/consensuses/, and server
 *   descriptors in in/server-descriptors/.
 * - Clone metrics-lib, run `ant jar`, and copy descriptor.jar to this
 *   directory.
 * - Download Apache Commons Codec and Compress jar files
 *   commons-codec-1.4.jar and commons-compress-1.3.jar and put them in
 *   this directory.
 * - Download BouncyCastle 1.47 jar files bcprov-jdk15on-147.jar and
 *   bcpkix-jdk15on-147.jar and put them in this directory.
 * - Compile and run this class: ./run.sh.
 */
public class VerifyDescriptors {
  public static void main(String[] args) throws Exception {
    System.out.println("Verifying consensuses...");
    if (Security.getProvider("BC") == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
    verifyServerDescriptors();
    verifyConsensuses();
  }

  private static void verifyServerDescriptors() throws Exception {
    File serverDescriptorDirectory = new File("in/server-descriptors");
    if (!serverDescriptorDirectory.exists()) {
      return;
    }
    DescriptorReader descriptorReader = DescriptorSourceFactory
        .createDescriptorReader();
    descriptorReader.addDirectory(serverDescriptorDirectory);
    Iterator<DescriptorFile> descriptorFiles =
        descriptorReader.readDescriptors();
    int processedDescriptors = 0, verifiedDescriptors = 0;
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getException() != null) {
        System.err.println("Could not read/parse descriptor file "
            + descriptorFile.getFileName() + ": "
            + descriptorFile.getException().getMessage());
        continue;
      }
      if (descriptorFile.getDescriptors() == null) {
        continue;
      }
      for (Descriptor descriptor : descriptorFile.getDescriptors()) {
        if (!(descriptor instanceof ServerDescriptor)) {
          continue;
        }
        ServerDescriptor serverDescriptor = (ServerDescriptor) descriptor;
        boolean isVerified = true;

        /* Verify that the contained fingerprint is a hash of the signing
         * key. */
        String signingKeyHashString = determineKeyHash(
            serverDescriptor.getSigningKey());
        String fingerprintString =
            serverDescriptor.getFingerprint().toLowerCase();
        if (!signingKeyHashString.equals(fingerprintString)) {
          System.out.println("In " + descriptorFile.getFile()
              + ", server descriptor, the calculated signing key hash "
              + " does not match the contained fingerprint!");
          isVerified = false;
        }

        /* Verify that the router signature was created using the signing
         * key. */
        if (!verifySignature(serverDescriptor.getServerDescriptorDigest(),
            serverDescriptor.getRouterSignature(),
            serverDescriptor.getSigningKey())) {
          System.out.println("In " + descriptorFile.getFile()
              + ", the decrypted signature does not match the descriptor "
              + "digest!");
          isVerified = false;
        }

        processedDescriptors++;
        if (isVerified) {
          verifiedDescriptors++;
        }
      }
    }
    System.out.println("Verified " + verifiedDescriptors + "/"
        + processedDescriptors + " server descriptors.");
  }

  private static void verifyConsensuses() throws Exception {
    File certsDirectory = new File("in/certs");
    File consensusDirectory = new File("in/consensuses");
    if (!certsDirectory.exists() || !consensusDirectory.exists()) {
      return;
    }
    Map<String, String> signingKeys = new HashMap<String, String>();

    DescriptorReader certsReader = DescriptorSourceFactory
        .createDescriptorReader();
    certsReader.addDirectory(certsDirectory);
    Iterator<DescriptorFile> descriptorFiles =
        certsReader.readDescriptors();
    int processedCerts = 0, verifiedCerts = 0;
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getException() != null) {
        System.err.println("Could not read/parse descriptor file "
            + descriptorFile.getFileName() + ": "
            + descriptorFile.getException().getMessage());
        continue;
      }
      if (descriptorFile.getDescriptors() == null) {
        continue;
      }
      for (Descriptor descriptor : descriptorFile.getDescriptors()) {
        if (!(descriptor instanceof DirectoryKeyCertificate)) {
          continue;
        }
        DirectoryKeyCertificate cert =
            (DirectoryKeyCertificate) descriptor;
        boolean isVerified = true;

        /* Verify that the contained fingerprint is a hash of the signing
         * key. */
        String dirIdentityKeyHashString = determineKeyHash(
            cert.getDirIdentityKey());
        String fingerprintString = cert.getFingerprint().toLowerCase();
        if (!dirIdentityKeyHashString.equals(fingerprintString)) {
          System.out.println("In " + descriptorFile.getFile()
              + ", the calculated directory identity key hash "
              + dirIdentityKeyHashString
              + " does not match the contained fingerprint "
              + fingerprintString + "!");
          isVerified = false;
        }

        /* Verify that the router signature was created using the signing
         * key. */
        if (!verifySignature(cert.getCertificateDigest(),
            cert.getDirKeyCertification(), cert.getDirIdentityKey())) {
          System.out.println("In " + descriptorFile.getFile()
              + ", the decrypted directory key certification does not "
              + "match the certificate digest!");
          isVerified = false;
        }

        /* Determine the signing key digest and remember the signing key
         * to verify consensus signatures. */
        String dirSigningKeyString = cert.getDirSigningKey();
        PEMReader pemReader2 = new PEMReader(new StringReader(
            dirSigningKeyString));
        RSAPublicKey dirSigningKey =
            (RSAPublicKey) pemReader2.readObject();
        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        new ASN1OutputStream(baos2).writeObject(
            new org.bouncycastle.asn1.pkcs.RSAPublicKey(
            dirSigningKey.getModulus(),
            dirSigningKey.getPublicExponent()).toASN1Primitive());
        byte[] pkcs2 = baos2.toByteArray();
        byte[] dirSigningKeyHashBytes = new byte[20];
        SHA1Digest sha1_2 = new SHA1Digest();
        sha1_2.update(pkcs2, 0, pkcs2.length);
        sha1_2.doFinal(dirSigningKeyHashBytes, 0);
        String dirSigningKeyHashString = Hex.encodeHexString(
            dirSigningKeyHashBytes).toUpperCase();
        signingKeys.put(dirSigningKeyHashString, cert.getDirSigningKey());

        processedCerts++;
        if (isVerified) {
          verifiedCerts++;
        }
      }
    }
    System.out.println("Verified " + verifiedCerts + "/"
        + processedCerts + " certs.");

    DescriptorReader consensusReader = DescriptorSourceFactory
        .createDescriptorReader();
    consensusReader.addDirectory(consensusDirectory);
    Iterator<DescriptorFile> consensusFiles =
        consensusReader.readDescriptors();
    int processedConsensuses = 0, verifiedConsensuses = 0;
    while (consensusFiles.hasNext()) {
      DescriptorFile consensusFile = consensusFiles.next();
      if (consensusFile.getException() != null) {
        System.err.println("Could not read/parse descriptor file "
            + consensusFile.getFileName() + ": "
            + consensusFile.getException().getMessage());
        continue;
      }
      if (consensusFile.getDescriptors() == null) {
        continue;
      }
      for (Descriptor descriptor : consensusFile.getDescriptors()) {
        if (!(descriptor instanceof RelayNetworkStatusConsensus)) {
          continue;
        }
        RelayNetworkStatusConsensus consensus =
            (RelayNetworkStatusConsensus) descriptor;
        boolean isVerified = true;

        /* Verify all signatures using the corresponding certificates. */
        if (consensus.getDirectorySignatures().isEmpty()) {
          System.out.println(consensusFile.getFile()
              + " does not contain any signatures.");
          continue;
        }
        for (DirectorySignature signature :
            consensus.getDirectorySignatures().values()) {
          String signingKeyDigest = signature.getSigningKeyDigest();
          if (!signingKeys.containsKey(signingKeyDigest)) {
            System.out.println("Cannot find signing key with digest "
                + signingKeyDigest + "!");
          }
          if (!verifySignature(consensus.getConsensusDigest(),
              signature.getSignature(),
              signingKeys.get(signingKeyDigest))) {
            System.out.println("In " + consensusFile.getFile()
                + ", the decrypted signature digest does not match the "
                + "consensus digest!");
            isVerified = false;
          }
        }
        processedConsensuses++;
        if (isVerified) {
          verifiedConsensuses++;
        }
      }
    }
    System.out.println("Verified " + verifiedConsensuses + "/"
        + processedConsensuses + " consensuses.");
  }

  private static String determineKeyHash(String key) throws Exception {
    PEMReader pemReader = new PEMReader(new StringReader(key));
    RSAPublicKey dirIdentityKey = (RSAPublicKey) pemReader.readObject();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    new ASN1OutputStream(baos).writeObject(
        new org.bouncycastle.asn1.pkcs.RSAPublicKey(
        dirIdentityKey.getModulus(),
        dirIdentityKey.getPublicExponent()).toASN1Primitive());
    byte[] pkcs = baos.toByteArray();
    byte[] dirIdentityKeyHashBytes = new byte[20];
    SHA1Digest sha1 = new SHA1Digest();
    sha1.update(pkcs, 0, pkcs.length);
    sha1.doFinal(dirIdentityKeyHashBytes, 0);
    String keyHash = Hex.encodeHexString(dirIdentityKeyHashBytes);
    return keyHash;
  }

  private static boolean verifySignature(String digest, String signature,
      String signingKey) throws Exception {
    byte[] signatureBytes = Base64.decodeBase64(signature.substring(
        0 + "-----BEGIN SIGNATURE-----\n".length(),
        signature.length() - "-----END SIGNATURE-----\n".length()).
        replaceAll("\n", ""));
    RSAPublicKey rsaSigningKey = (RSAPublicKey) new PEMReader(
        new StringReader(signingKey)).readObject();
    RSAKeyParameters rsakp = new RSAKeyParameters(false,
        rsaSigningKey.getModulus(),
        rsaSigningKey.getPublicExponent());
    PKCS1Encoding pe = new PKCS1Encoding(new RSAEngine());
    pe.init(false, rsakp);
    byte[] decryptedSignatureDigest = pe.processBlock(
        signatureBytes, 0, signatureBytes.length);
    String decryptedSignatureDigestString =
        Hex.encodeHexString(decryptedSignatureDigest);
    return decryptedSignatureDigestString.equalsIgnoreCase(digest);
  }
}

