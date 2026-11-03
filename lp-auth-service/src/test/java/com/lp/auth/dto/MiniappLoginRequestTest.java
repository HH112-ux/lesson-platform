package com.lp.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies the request validation that produces {@code 40001} for a miniapp login body.
 *
 * @author Caspian
 * @since 0.1.0
 */
class MiniappLoginRequestTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * Rejects a missing code so the client cannot omit it.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsMissingCode() {
        Set<ConstraintViolation<MiniappLoginRequest>> violations =
                VALIDATOR.validate(new MiniappLoginRequest());

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("code is required");
    }

    /**
     * Rejects a code longer than the documented limit.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsOverlongCode() {
        MiniappLoginRequest request = new MiniappLoginRequest();
        request.setCode("a".repeat(65));

        Set<ConstraintViolation<MiniappLoginRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("code must not exceed 64 characters");
    }

    /**
     * Accepts a well formed code.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void acceptsValidCode() {
        MiniappLoginRequest request = new MiniappLoginRequest();
        request.setCode("mock-code");

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }
}
