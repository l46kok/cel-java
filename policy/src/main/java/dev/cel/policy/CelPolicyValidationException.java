package dev.cel.policy;

import dev.cel.common.annotations.Internal;

public final class CelPolicyValidationException extends Exception {

  @Internal
  public CelPolicyValidationException(String message) {
    super(message);
  }

  @Internal
  public CelPolicyValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}