package dev.cel.common;

import com.google.common.base.Joiner;
import dev.cel.common.annotations.Internal;

import java.util.Arrays;
import java.util.Collection;

@Internal
public final class AttributeNotFoundException extends CelRuntimeException {

  private static final Joiner JOINER = Joiner.on(", ");

  public AttributeNotFoundException(String... missingAttributes) {
    this(Arrays.asList(missingAttributes));
  }

  public AttributeNotFoundException(Collection<String> missingAttributes) {
    super(formatErrorMessage(missingAttributes), CelErrorCode.ATTRIBUTE_NOT_FOUND);
  }

  private static String formatErrorMessage(Collection<String> missingAttributes) {
    String maybePlural = "";
    if (missingAttributes.size() > 1) {
      maybePlural = "s";
    }

     return String.format("Error resolving field%s '%s'. Field selections must be performed on messages or maps.", maybePlural, JOINER.join(missingAttributes));
  }
}
