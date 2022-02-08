import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Hmac {

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    private static final String KEY = "CC-LEI";

    // GERAR A CRIPTOGRAFIA COM HMAC - acrescentar os 20 bytes no final da mensagem
    // ao enviar.
    // Ao receber, tirar estes 20bytes para o lado, calcular outra vez o HMAC disto
    // e verificar se é igual ao que chegou!
    public static byte[] calculateHMAC(byte[] msg) throws SignatureException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec signingKey = new SecretKeySpec(KEY.getBytes(), HMAC_SHA1_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
        mac.init(signingKey);

        return mac.doFinal(msg);
    }

    public static byte[] addHmac(ByteBuffer buff) {

        byte[] msg = buff.array();

        byte[] mac = null;
        try {
            mac = calculateHMAC(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // concatenate 2 byte array

        int msgLen = msg.length;
        int macLen = mac.length;
        byte[] packet = new byte[msgLen + macLen];

        System.arraycopy(msg, 0, packet, 0, msgLen);
        System.arraycopy(mac, 0, packet, msgLen, macLen);

        return packet;

    }

    public static boolean verifyHMAC(byte[] msg, byte[] hmac)
            throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {

        byte[] newHmac = calculateHMAC(msg); // calculate the HMAC of the received message

        if (Arrays.equals(newHmac, hmac)) { // check if the HMAC's do match
            // System.out.println("HMAC verified - Authenticity & Identity OK");
            return true;
        } else {
            // System.out.println("ERROR! Hmac not verified");
            return false;
        }
    }

}
