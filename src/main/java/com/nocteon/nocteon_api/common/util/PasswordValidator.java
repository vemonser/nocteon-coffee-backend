package com.nocteon.nocteon_api.common.util;

import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.common.exception.user.WeakPasswordException;
import com.nulabinc.zxcvbn.Strength;
import com.nulabinc.zxcvbn.Zxcvbn;

@Component
public class PasswordValidator {

    private final Zxcvbn zxcvbn = new Zxcvbn();

    public void validate(String password) {
        Strength strength = zxcvbn.measure(password);

        if (strength.getScore() < 2) {
            throw new WeakPasswordException();
        }
    }
}
