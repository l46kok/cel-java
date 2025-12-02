package dev.cel.runtime.planner;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.AttributeNotFoundException;
import dev.cel.runtime.GlobalResolver;

final class MissingAttribute implements Attribute {

    private final ImmutableSet<String> missingAttributes;

    @Override
    public Object resolve(GlobalResolver ctx) {
      throw new AttributeNotFoundException(missingAttributes);
    }

    @Override
    public Attribute addQualifier(Qualifier qualifier) {
      throw new UnsupportedOperationException("Unsupported operation");
    }

    static MissingAttribute newMissingAttribute(String... attributeNames) {
      return newMissingAttribute(ImmutableSet.copyOf(attributeNames));
    }

    static MissingAttribute newMissingAttribute(ImmutableSet<String> attributeNames) {
      return new MissingAttribute(attributeNames);
    }

    private MissingAttribute(ImmutableSet<String> missingAttributes) {
      this.missingAttributes = missingAttributes;
    }
  }
