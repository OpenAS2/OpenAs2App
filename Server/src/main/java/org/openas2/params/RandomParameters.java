package org.openas2.params;

import org.bouncycastle.util.encoders.Base64;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Random;
import java.util.UUID;

public class RandomParameters extends ParameterParser {
    public void setParameter(String key, String value) throws InvalidParameterException {
        throw new InvalidParameterException("Set not supported", this, key, value);
    }

    public String getParameter(String key) throws InvalidParameterException {
        if (key == null) {
            throw new InvalidParameterException("Invalid key", this, key, null);
        }
        if ("uuid".equalsIgnoreCase(key)) {
            return UUID.randomUUID().toString();
        }
        if ("shortUUID".equalsIgnoreCase(key)) {
            return getRandomBase62UUID();
        }
        int wanted = key.length();
        String fmt = "";
        int max = 1;

        while (wanted-- > 0) {
            fmt += "0";
            max *= 10;
        }

        DecimalFormat randomFormatter = new DecimalFormat(fmt);
        return randomFormatter.format(new Random().nextInt(max));
    }

    private static String getRandomBase62UUID() {
        UUID uuid = UUID.randomUUID();
        ByteBuffer uuidBytes = ByteBuffer.wrap(new byte[16]);
        uuidBytes.putLong(uuid.getMostSignificantBits());
        uuidBytes.putLong(uuid.getLeastSignificantBits());
        return base64ToBase62(Base64.toBase64String(uuidBytes.array()));
    }

    private static String base64ToBase62(String base64String) {
        StringBuffer buffer = new StringBuffer(base64String.length() * 2);
        for (int i = 0; i < base64String.length(); i++) {
            char c = base64String.charAt(i);
            switch (c) {
                case 'i':
                    buffer.append("ii");
                    break;
                case '+':
                    buffer.append("ip");
                    break;
                case '/':
                    buffer.append("is");
                    break;
                case '=':
                    buffer.append("ie");
                    break;
                case '\n':
                    // Skip
                    break;
                default:
                    buffer.append(c);
            }
        }
        return buffer.toString();
    }

}
