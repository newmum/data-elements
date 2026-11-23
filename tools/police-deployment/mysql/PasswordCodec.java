import java.util.*;
import java.nio.charset.StandardCharsets;
import org.bouncycastle.crypto.digests.SM3Digest;
public class PasswordCodec {
public static void main(String[] args) { String p=System.getenv("POLICE_PASSWORD_INPUT"); System.out.print(args[0].equals("encode") ? encode(p) : sm3(p).toLowerCase(java.util.Locale.ROOT)); }
    private static String encode(String password) {
        String[] s = UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT).split("-");
        return s[4] + s[0] + s[2] + sm3(s[0] + s[1] + sm3(password) + s[3] + s[4]) + s[1] + s[3];
    }

    private static String sm3(String input) {
        var digest = new SM3Digest();
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        digest.update(bytes, 0, bytes.length);
        byte[] hash = new byte[32];
        digest.doFinal(hash, 0);
        return HexFormat.of().withUpperCase().formatHex(hash);
    }
}
