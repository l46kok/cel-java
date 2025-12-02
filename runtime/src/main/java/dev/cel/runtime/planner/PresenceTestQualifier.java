package dev.cel.runtime.planner;

import dev.cel.common.values.SelectableValue;

import java.util.Map;

import static dev.cel.runtime.planner.MissingAttribute.newMissingAttribute;

final class PresenceTestQualifier implements Qualifier {

    @SuppressWarnings("Immutable")
    private final Object value;

    @Override
    public Object value() {
      return value;
    }

    @Override
    public Object qualify(Object obj) {
      if (obj instanceof SelectableValue) {
        return ((SelectableValue<Object>) obj).find(value).isPresent();
      } else if (obj instanceof Map) {
        Map<?, ?> map = (Map<?, ?>) obj;
        return map.containsKey(value);
      }

      return newMissingAttribute(value.toString());
    }

    PresenceTestQualifier(Object value) {
      this.value = value;
    }
  }
