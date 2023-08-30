import java.io.File;
import java.io.FileWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.bc.BCObjectIdentifiers;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.util.ASN1Dump;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectAltPublicKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.cert.DeltaCertificateTool;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jcajce.CompositePrivateKey;
import org.bouncycastle.jcajce.CompositePublicKey;
import org.bouncycastle.jcajce.spec.CompositeAlgorithmSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.Pack;

public class R3ArtifactGenerator
{
    private static final ASN1ObjectIdentifier[] sigAlgorithms =
        {
            BCObjectIdentifiers.dilithium2,
            BCObjectIdentifiers.dilithium3,
            BCObjectIdentifiers.dilithium5,
            BCObjectIdentifiers.sphincsPlus_sha2_128f,
            BCObjectIdentifiers.sphincsPlus_sha2_128s,
            BCObjectIdentifiers.sphincsPlus_sha2_192f,
            BCObjectIdentifiers.sphincsPlus_sha2_192s,
            BCObjectIdentifiers.sphincsPlus_sha2_256f,
            BCObjectIdentifiers.sphincsPlus_sha2_256s,
            BCObjectIdentifiers.sphincsPlus_shake_128f,
            BCObjectIdentifiers.sphincsPlus_shake_128s,
            BCObjectIdentifiers.sphincsPlus_shake_192f,
            BCObjectIdentifiers.sphincsPlus_shake_192s,
            BCObjectIdentifiers.sphincsPlus_shake_256f,
            BCObjectIdentifiers.sphincsPlus_shake_256s,
//            BCObjectIdentifiers.falcon_512,
//            BCObjectIdentifiers.falcon_1024
        };


    private static final String[] sigAlgNames =
        {
            "dilithium2",
            "dilithium3",
            "dilithium5",
            "sphincsplus",
            "sphincsplus",
            "sphincsplus",
            "sphincsplus",
            "sphincsplus",
            "sphincsplus",
            "sphincsplus",
            "sphincsplus",
            "sphincsplus",
            "sphincsplus",
            "sphincsplus",
            "sphincsplus"
//            "falcon-512",
//            "falcon-1024",
        };

    private static final ASN1ObjectIdentifier[] kemAlgorithms =
    {
        BCObjectIdentifiers.kyber512,
        BCObjectIdentifiers.kyber768,
        BCObjectIdentifiers.kyber1024
    };

    private static final String[] kemAlgNames =
        {
            "kyber512",
            "kyber768",
            "kyber1024"
        };

    private static final long BEFORE_DELTA = 60 * 1000L;
    private static final long AFTER_DELTA = 365L * 24 * 60 * 60 * 1000L;

    private static int certCount = 1;
    private static final BigInteger generateSerialNumber()
        throws Exception
    {
        MessageDigest dig = MessageDigest.getInstance("SHA1");

        byte[] sn = dig.digest(Arrays.concatenate(Pack.intToBigEndian(certCount), Pack.longToBigEndian(System.currentTimeMillis())));

        sn[0] = (byte)((sn[0] & 0x7f) | 0x40);

        return new BigInteger(sn);
    }

    private static X509Certificate createTACertificate(String algName, KeyPair taKp)
        throws Exception
    {
        X509v3CertificateBuilder crtBld = new X509v3CertificateBuilder(
            new X500Name("CN=BC " + algName + " Test TA"),
            generateSerialNumber(),
            new Date(System.currentTimeMillis() - BEFORE_DELTA),
            new Date(System.currentTimeMillis() + AFTER_DELTA),
            new X500Name("CN=BC " + algName + " Test TA"),
            SubjectPublicKeyInfo.getInstance(taKp.getPublic().getEncoded()));

        crtBld.addExtension(Extension.basicConstraints, true, new BasicConstraints(1));
        crtBld.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

        ContentSigner signer = new JcaContentSignerBuilder(algName).build(taKp.getPrivate());

        return new JcaX509CertificateConverter().getCertificate(crtBld.build(signer));
    }

    private static X509Certificate createEECertificate(String taAlgName, PKIXPair taPair, String eeAlgName, KeyPair eeKp)
        throws Exception
    {
        X509v3CertificateBuilder crtBld = new X509v3CertificateBuilder(
            new X500Name("CN=BC " + taAlgName + " Test TA"),
            generateSerialNumber(),
            new Date(System.currentTimeMillis() - BEFORE_DELTA),
            new Date(System.currentTimeMillis() + AFTER_DELTA),
            new X500Name("CN=BC " + eeAlgName + " Test EE"),
            SubjectPublicKeyInfo.getInstance(eeKp.getPublic().getEncoded()));

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils(
            new JcaDigestCalculatorProviderBuilder().build().get(
                new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1, DERNull.INSTANCE)));
        
        crtBld.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        crtBld.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyEncipherment));
        crtBld.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(eeKp.getPublic()));
        crtBld.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(taPair.cert));

        ContentSigner signer = new JcaContentSignerBuilder(taAlgName).build(taPair.priv);

        return new JcaX509CertificateConverter().getCertificate(crtBld.build(signer));
    }

    private static X509Certificate createCatalystHybridTACertificate(String algName, KeyPair taKp, String altAlgName, PKIXPair altTaKp)
        throws Exception
    {
        X509v3CertificateBuilder crtBld = new X509v3CertificateBuilder(
            new X500Name("CN=BC " + algName + " with " + altAlgName + " Test TA"),
            generateSerialNumber(),
            new Date(System.currentTimeMillis() - BEFORE_DELTA),
            new Date(System.currentTimeMillis() + AFTER_DELTA),
            new X500Name("CN=BC " + algName + " with " + altAlgName + " Test TA"),
            SubjectPublicKeyInfo.getInstance(taKp.getPublic().getEncoded()));

        crtBld.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        crtBld.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        crtBld.addExtension(Extension.subjectAltPublicKeyInfo, true, SubjectAltPublicKeyInfo.getInstance(altTaKp.cert.getPublicKey().getEncoded()));

        ContentSigner signer = new JcaContentSignerBuilder(algName).build(taKp.getPrivate());
        ContentSigner altSigner = new JcaContentSignerBuilder(altAlgName).build(altTaKp.priv);

        return new JcaX509CertificateConverter().getCertificate(crtBld.build(signer, true, altSigner));
    }

    private static X509Certificate createCompositeHybridTACertificate(String algName, KeyPair taKp, String altAlgName, PKIXPair altTaKp)
        throws Exception
    {
        CompositeAlgorithmSpec compAlgSpec = new CompositeAlgorithmSpec.Builder()
            .add(algName)
            .add(altAlgName)
            .build();

        CompositePublicKey compPub = new CompositePublicKey(taKp.getPublic(), altTaKp.cert.getPublicKey());
        CompositePrivateKey compPrivKey = new CompositePrivateKey(taKp.getPrivate(), altTaKp.priv);

        ContentSigner signer = new JcaContentSignerBuilder("COMPOSITE", compAlgSpec).build(compPrivKey);

        X509v3CertificateBuilder crtBld = new X509v3CertificateBuilder(
            new X500Name("CN=BC " + algName + " with " + altAlgName + " Test TA"),
            generateSerialNumber(),
            new Date(System.currentTimeMillis() - BEFORE_DELTA),
            new Date(System.currentTimeMillis() + AFTER_DELTA),
            new X500Name("CN=BC " + algName + " with " + altAlgName + " Test TA"),
            SubjectPublicKeyInfo.getInstance(compPub.getEncoded()));

        crtBld.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        crtBld.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

        return new JcaX509CertificateConverter().getCertificate(crtBld.build(signer));
    }

    private static X509Certificate createChameleonHybridTACertificate(String algName, KeyPair taKp, String altAlgName, PKIXPair altTaKp)
        throws Exception
    {
        long now = System.currentTimeMillis();
        X509v3CertificateBuilder crtBld = new X509v3CertificateBuilder(
            new X500Name("CN=BC " + algName + " Test Chameleon Outer TA"),
            generateSerialNumber(),
            new Date(now - BEFORE_DELTA),
            new Date(now + AFTER_DELTA),
            new X500Name("CN=BC " + algName + " Test Chameleon Outer TA"),
            SubjectPublicKeyInfo.getInstance(taKp.getPublic().getEncoded()));

        crtBld.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        crtBld.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

        X509v3CertificateBuilder altCrtBld = new X509v3CertificateBuilder(
            new X500Name("CN=BC " + altAlgName + " Test Chameleon Inner TA"),
            generateSerialNumber(),
            new Date(now - BEFORE_DELTA),
            new Date(now + AFTER_DELTA),
            new X500Name("CN=BC " + altAlgName + " Test Chameleon Inner TA"),
            SubjectPublicKeyInfo.getInstance(altTaKp.cert.getPublicKey().getEncoded()));

        altCrtBld.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        altCrtBld.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

        ContentSigner altSigner = new JcaContentSignerBuilder(altAlgName).build(altTaKp.priv);
        X509CertificateHolder deltaCert = altCrtBld.build(altSigner);
        
        Extension deltaExt = DeltaCertificateTool.makeDeltaCertificateExtension(
            false,
            DeltaCertificateTool.signature
                | DeltaCertificateTool.issuer | DeltaCertificateTool.subject | DeltaCertificateTool.extensions,
            deltaCert);
        crtBld.addExtension(deltaExt);

        ContentSigner signer = new JcaContentSignerBuilder(algName).build(taKp.getPrivate());

        X509CertificateHolder chameleonCert = crtBld.build(signer);
        X509CertificateHolder exDeltaCert = DeltaCertificateTool.extractDeltaCertificate(chameleonCert);

        return new JcaX509CertificateConverter().getCertificate(chameleonCert);
    }

    private static void pemOutput(File parent, String name, Object obj)
        throws Exception
    {
        FileWriter fWrt = new FileWriter(new File(parent, name));
        JcaPEMWriter pemWriter = new JcaPEMWriter(fWrt);

        pemWriter.writeObject(obj);

        pemWriter.close();
        fWrt.close();
    }

    public static void main(String[] args)
        throws Exception
    {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
        Security.insertProviderAt(new BouncyCastlePQCProvider(), 2);

        File aDir = new File("artifacts_certs_r3");

        aDir.mkdir();

        //
        // Build TA certificates
        //
        Map<String, PKIXPair> sigParams = new HashMap<String, PKIXPair>();
        for (int alg = 0; alg != sigAlgorithms.length; alg++)
        {
            KeyPairGenerator kpGen = KeyPairGenerator.getInstance(sigAlgorithms[alg].getId());

            KeyPair taKp = kpGen.generateKeyPair();

            X509Certificate taCert = createTACertificate(sigAlgNames[alg], taKp);

            pemOutput(aDir, sigAlgorithms[alg] + "_ta.pem", taCert);

            sigParams.put(sigAlgNames[alg], new PKIXPair(taKp.getPrivate(), taCert));
        }

        //
        // Build KEM EE certificates
        //
        for (int alg = 0; alg != kemAlgorithms.length; alg++)
        {
            PKIXPair taPair = sigParams.get(sigAlgNames[alg]);
            KeyPairGenerator kpGen = KeyPairGenerator.getInstance(kemAlgorithms[alg].getId());

            KeyPair eeKp = kpGen.generateKeyPair();

            X509Certificate eeCert = createEECertificate(sigAlgNames[alg], taPair, kemAlgNames[alg], eeKp);

            pemOutput(aDir, kemAlgorithms[alg] + "_ee.pem", eeCert);
        }

        //
        // Build Hybrid certificates
        //
        KeyPairGenerator rsaKpg = KeyPairGenerator.getInstance("RSA", "BC");
        rsaKpg.initialize(3072);
        KeyPair rsaKp = rsaKpg.generateKeyPair();

        KeyPairGenerator p256Kpg = KeyPairGenerator.getInstance("EC", "BC");
        p256Kpg.initialize(new ECGenParameterSpec("P-256"));
        KeyPair p256Kp = p256Kpg.generateKeyPair();

        KeyPairGenerator p521Kpg = KeyPairGenerator.getInstance("EC", "BC");
        p521Kpg.initialize(new ECGenParameterSpec("P-521"));
        KeyPair p521Kp = p521Kpg.generateKeyPair();
        
        X509Certificate hybridCert = createCatalystHybridTACertificate("SHA256withRSA", rsaKp, "Dilithium2", sigParams.get("dilithium2"));
        pemOutput(aDir, "catalyst_" + PKCSObjectIdentifiers.sha256WithRSAEncryption + "_with_" + BCObjectIdentifiers.dilithium2 + "_ta.pem", hybridCert);
        hybridCert = createCatalystHybridTACertificate("SHA256withECDSA", p256Kp, "Dilithium2", sigParams.get("dilithium2"));
        pemOutput(aDir, "catalyst_" + X9ObjectIdentifiers.ecdsa_with_SHA256 + "_with_" + BCObjectIdentifiers.dilithium2 + "_ta.pem", hybridCert);
        hybridCert = createCatalystHybridTACertificate("SHA512withECDSA", p521Kp, "Dilithium5", sigParams.get("dilithium5"));
        pemOutput(aDir, "catalyst_" + X9ObjectIdentifiers.ecdsa_with_SHA512 + "_with_" + BCObjectIdentifiers.dilithium5 + "_ta.pem", hybridCert);

        hybridCert = createCompositeHybridTACertificate("SHA256withRSA", rsaKp, "Dilithium2", sigParams.get("dilithium2"));
        pemOutput(aDir, "composite_" + PKCSObjectIdentifiers.sha256WithRSAEncryption + "_with_" + BCObjectIdentifiers.dilithium2 + "_ta.pem", hybridCert);
        hybridCert = createCompositeHybridTACertificate("SHA256withECDSA", p256Kp, "Dilithium2", sigParams.get("dilithium2"));
        pemOutput(aDir, "composite_" + X9ObjectIdentifiers.ecdsa_with_SHA256 + "_with_" + BCObjectIdentifiers.dilithium2 + "_ta.pem", hybridCert);
        hybridCert = createCompositeHybridTACertificate("SHA512withECDSA", p521Kp, "Dilithium5", sigParams.get("dilithium5"));
        pemOutput(aDir, "composite_" + X9ObjectIdentifiers.ecdsa_with_SHA512 + "_with_" + BCObjectIdentifiers.dilithium5 + "_ta.pem", hybridCert);

        hybridCert = createChameleonHybridTACertificate("SHA256withRSA", rsaKp, "Dilithium2", sigParams.get("dilithium2"));
        pemOutput(aDir, "chameleon_" + PKCSObjectIdentifiers.sha256WithRSAEncryption + "_with_" + BCObjectIdentifiers.dilithium2 + "_ta.pem", hybridCert);
        hybridCert = createChameleonHybridTACertificate("SHA256withECDSA", p256Kp, "Dilithium2", sigParams.get("dilithium2"));
        pemOutput(aDir, "chameleon_" + X9ObjectIdentifiers.ecdsa_with_SHA256 + "_with_" + BCObjectIdentifiers.dilithium2 + "_ta.pem", hybridCert);
        hybridCert = createChameleonHybridTACertificate("SHA512withECDSA", p521Kp, "Dilithium5", sigParams.get("dilithium5"));
        pemOutput(aDir, "chameleon_" + X9ObjectIdentifiers.ecdsa_with_SHA512 + "_with_" + BCObjectIdentifiers.dilithium5 + "_ta.pem", hybridCert);

    }

    private static class PKIXPair
    {
        final PrivateKey priv;
        final X509Certificate cert;

        PKIXPair(PrivateKey priv, X509Certificate cert)
        {
            this.priv = priv;
            this.cert = cert;
        }
    }
}
