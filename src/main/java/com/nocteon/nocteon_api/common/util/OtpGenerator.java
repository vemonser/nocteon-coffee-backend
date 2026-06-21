package com.nocteon.nocteon_api.common.util;

import java.security.SecureRandom;

public class OtpGenerator {
    private OtpGenerator() {
    }

    public static String generate() {
        return String.format("%06d", new SecureRandom().nextInt(999999));
    }

}
