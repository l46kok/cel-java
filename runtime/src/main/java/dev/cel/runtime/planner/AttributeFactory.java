// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cel.runtime.planner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelContainer;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.CelAttribute;
import dev.cel.runtime.CelAttributePattern;
import dev.cel.runtime.CelUnknownSet;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.PartialActivation;

@Immutable

final class AttributeFactory {

  private final CelContainer container;
  private final CelTypeProvider typeProvider;
  private final CelValueConverter celValueConverter;
  private final boolean enableUnknowns;

  Attribute newAbsoluteAttribute(String... names) {
    NamespacedAttribute attribute =
        NamespacedAttribute.create(typeProvider, celValueConverter, ImmutableSet.copyOf(names));
    if (enableUnknowns) {
      return new UnknownAttribute(attribute, attribute.candidateVariableNames());
    }
    return attribute;
  }

  NamespacedAttribute newNamespacedAttribute(String... names) {
    return NamespacedAttribute.create(typeProvider, celValueConverter, ImmutableSet.copyOf(names));
  }

  RelativeAttribute newRelativeAttribute(PlannedInterpretable operand) {
    return new RelativeAttribute(operand, celValueConverter);
  }

  Attribute newMaybeAttribute(String name) {
    // When there's a single name with a dot prefix, it indicates that the 'maybe' attribute is a
    // globally namespaced identifier.
    // Otherwise, the candidate names resolved from the container should be inferred.
    ImmutableSet<String> names =
        name.startsWith(".") ? ImmutableSet.of(name) : container.resolveCandidateNames(name);

    MaybeAttribute attribute =
        new MaybeAttribute(
            this,
            ImmutableList.of(NamespacedAttribute.create(typeProvider, celValueConverter, names)));
    if (enableUnknowns) {
      return new UnknownAttribute(attribute, names);
    }
    return attribute;
  }

  static AttributeFactory newAttributeFactory(
      CelContainer celContainer,
      CelTypeProvider typeProvider,
      CelValueConverter celValueConverter,
      boolean enableUnknowns) {
    return new AttributeFactory(celContainer, typeProvider, celValueConverter, enableUnknowns);
  }

  static AttributeFactory newAttributeFactory(
      CelContainer celContainer,
      CelTypeProvider typeProvider,
      CelValueConverter celValueConverter) {
    return new AttributeFactory(celContainer, typeProvider, celValueConverter, false);
  }

  private AttributeFactory(
      CelContainer container,
      CelTypeProvider typeProvider,
      CelValueConverter celValueConverter,
      boolean enableUnknowns) {
    this.container = container;
    this.typeProvider = typeProvider;
    this.celValueConverter = celValueConverter;
    this.enableUnknowns = enableUnknowns;
  }

  @Immutable
  private static final class UnknownAttribute implements Attribute {
    private final Attribute attribute;
    private final ImmutableSet<String> variableNames;
    private final ImmutableList<CelAttribute.Qualifier> qualifiers;

    private UnknownAttribute(Attribute attribute, ImmutableSet<String> variableNames) {
      this(attribute, variableNames, ImmutableList.of());
    }

    private UnknownAttribute(
        Attribute attribute,
        ImmutableSet<String> variableNames,
        ImmutableList<CelAttribute.Qualifier> qualifiers) {
      this.attribute = attribute;
      this.variableNames = variableNames;
      this.qualifiers = qualifiers;
    }

    @Override
    public Object resolve(GlobalResolver resolver, ExecutionFrame frame) {
      if (resolver instanceof PartialActivation) {
        PartialActivation partialActivation = (PartialActivation) resolver;
        for (CelAttributePattern pattern : partialActivation.unknownAttributePatterns()) {
          for (String variable : variableNames) {
            CelAttribute celAttribute =
                CelAttribute.create(
                    ImmutableList.<CelAttribute.Qualifier>builder()
                        .add(CelAttribute.Qualifier.ofString(variable))
                        .addAll(qualifiers)
                        .build());
            if (pattern.isMatch(celAttribute)) {
              return CelUnknownSet.create(pattern.simplify(celAttribute));
            }
          }
        }
      }

      return attribute.resolve(resolver, frame);
    }

    @Override
    public Attribute addQualifier(Qualifier qualifier) {
      CelAttribute.Qualifier celQualifier;
      if (qualifier instanceof StringQualifier) {
        celQualifier = CelAttribute.Qualifier.ofString((String) qualifier.value());
      } else if (qualifier instanceof EvalConstant) {
        Object value = ((EvalConstant) qualifier).value();
        if (value instanceof Long) {
          celQualifier = CelAttribute.Qualifier.ofInt((Long) value);
        } else if (value instanceof Boolean) {
          celQualifier = CelAttribute.Qualifier.ofBool((Boolean) value);
        } else if (value instanceof Double) {
          // TODO: This is lossy, but existing CelAttribute doesn't support double qualifiers?
          // For now, let's treat it as not a valid qualifier for unknown matching unless we fix CelAttribute.
          return new UnknownAttribute(
              attribute.addQualifier(qualifier), variableNames, qualifiers);
        } else if (value instanceof String) {
          celQualifier = CelAttribute.Qualifier.ofString((String) value);
        } else {
             // Fallback
             return new UnknownAttribute(
              attribute.addQualifier(qualifier), variableNames, qualifiers);
        }
      } else {
        // Dynamic qualifier (attribute)
        return new UnknownAttribute(
            attribute.addQualifier(qualifier), variableNames, qualifiers);
      }

      return new UnknownAttribute(
          attribute.addQualifier(qualifier),
          variableNames,
          ImmutableList.<CelAttribute.Qualifier>builder()
              .addAll(qualifiers)
              .add(celQualifier)
              .build());
    }
  }
}
