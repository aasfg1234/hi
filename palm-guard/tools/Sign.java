import com.android.apksig.ApkSigner;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** 用法：java Sign <keystore> <密碼> <別名> <未簽名.apk> <輸出.apk> */
public final class Sign {
    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("usage: Sign <keystore> <password> <alias> <in.apk> <out.apk>");
            System.exit(2);
        }
        char[] password = args[1].toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(args[0])) {
            ks.load(in, password);
        }
        PrivateKey key = (PrivateKey) ks.getKey(args[2], password);
        X509Certificate cert = (X509Certificate) ks.getCertificate(args[2]);

        ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder(
                "CERT", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(new File(args[3]))
                .setOutputApk(new File(args[4]))
                .setMinSdkVersion(29)
                // 最低支援 Android 10，只要 v2 簽名就夠；這版 apksig 的 v1 簽名在新版 Java 會壞掉
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .build()
                .sign();
    }
}
