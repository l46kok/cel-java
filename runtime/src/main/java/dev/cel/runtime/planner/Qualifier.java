package dev.cel.runtime.planner;

import dev.cel.common.values.CelValue;

interface Qualifier {
  CelValue value();

  final class StringQualifier implements Qualifier {

    private final CelValue celValue;

    @Override
    public CelValue value() {
      return celValue;
    }
    StringQualifier(CelValue celValue) {
      this.celValue = celValue;
    }
  }

}
