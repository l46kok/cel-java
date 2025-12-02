package dev.cel.runtime.planner;

import dev.cel.common.CelErrorCode;
import dev.cel.common.CelRuntimeException;

final class StrictErrorException extends CelRuntimeException {

  private final long exprId;

  long exprId() {
    return exprId;
  }

  StrictErrorException(Throwable cause, CelErrorCode errorCode, long exprId) {
    super(cause, errorCode);
    this.exprId = exprId;
  }
}
