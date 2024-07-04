// Copyright 2024 Google LLC
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

package dev.cel.policy;

import static com.google.common.base.Preconditions.checkArgument;
import static dev.cel.policy.YamlHelper.ERROR;
import static dev.cel.policy.YamlHelper.assertYamlType;

import dev.cel.common.CelIssue;
import dev.cel.common.CelSourceHelper;
import dev.cel.common.CelSourceLocation;
import dev.cel.common.Source;
import dev.cel.policy.YamlHelper.YamlNodeType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;

/** Package-private class to assist with storing policy parsing context. */
final class YamlParserContextImpl implements ParserContext<Node> {

  private final ArrayList<CelIssue> issues;
  private final HashMap<Long, CelSourceLocation> idToLocationMap;
  private final HashMap<Long, Integer> idToOffsetMap;
  private final Source policySource;
  private long id;

  @Override
  public void reportError(long id, String message) {
    issues.add(CelIssue.formatError(idToLocationMap.get(id), message));
  }

  @Override
  public List<CelIssue> getIssues() {
    return issues;
  }

  @Override
  public Map<Long, Integer> getIdToOffsetMap() {
    return idToOffsetMap;
  }

  @Override
  public ValueString newValueString(Node node) {
    long id = collectMetadata(node);
    if (!assertYamlType(this, id, node, YamlNodeType.STRING, YamlNodeType.TEXT)) {
      return ValueString.of(id, ERROR);
    }

    ScalarNode scalarNode = (ScalarNode) node;
    ScalarStyle style = scalarNode.getScalarStyle();
    if (style.equals(ScalarStyle.FOLDED) || style.equals(ScalarStyle.LITERAL)) {
      int line =  scalarNode.getStartMark().getLine() + 2;
      String text = policySource.getSnippet(line).get();
      String indent = "";
      for (int i = 0; i < text.length(); i++) {
        Character c = text.charAt(i);
        if (!c.equals(' ')) {
          break;
        }

        indent += " ";
      }
      int column = indent.length();

      StringBuilder raw = new StringBuilder();
      // raw.append(indent);
      while (text.startsWith(indent)) {
        line++;
        // raw.append(text.trim());
        raw.append(text);
        text = policySource.getSnippet(line).orElse("");
        if (text.isEmpty()) {
          break;
        }
        if (text.startsWith(indent)) {
          raw.append("\n");
        }
      }

      // idToOffsetMap
      // int offset = getLocationOffsetImpl()

      // idToOffsetMap.put(id, idToOffsetMap.get(id) - indent.length());
      // idToOffsetMap.put(id, idToOffsetMap.get(id) + indent.length());
      // return ValueString.of(id, "        'test'\n.format(variables.missing])");
      return ValueString.of(id, raw.toString());
      // System.out.println(raw.toString());
    }



    // TODO: Compute relative source for multiline strings
    return ValueString.of(id, scalarNode.getValue());
  }

  private static Optional<Integer> getLocationOffsetImpl(
      List<Integer> lineOffsets, int line, int column) {
    checkArgument(line > 0);
    checkArgument(column >= 0);
    int offset = CelSourceHelper.findLineOffset(lineOffsets, line);
    if (offset == -1) {
      return Optional.empty();
    }
    return Optional.of(offset + column);
  }

  @Override
  public long collectMetadata(Node node) {
    long id = nextId();
    int line = node.getStartMark().getLine() + 1; // Yaml lines are 0 indexed
    int column = node.getStartMark().getColumn();
    if (node instanceof ScalarNode) {
      DumperOptions.ScalarStyle style = ((ScalarNode) node).getScalarStyle();
      if (style.equals(DumperOptions.ScalarStyle.SINGLE_QUOTED)
          || style.equals(DumperOptions.ScalarStyle.DOUBLE_QUOTED)) {
        column++;
      } else if (
          style.equals(ScalarStyle.FOLDED) || style.equals(ScalarStyle.LITERAL)
      ) {
        // Actual string content for multiline YAML is below the indicator (| or >)
        line++;
        // Columns (indentations) need to be computed separately.
        column = 0;
        // int nodeLineCount = node.getEndMark().getLine() - node.getStartMark().getLine();
        // String text = policySource.getSnippet(line).orElse("");
        // for (char c : text.toCharArray()) {
        //   if (!Character.isWhitespace(c)) {
        //     break;
        //   }
        //   column++;
        // }

        // column *= nodeLineCount;
      }
    }
    idToLocationMap.put(id, CelSourceLocation.of(line, column));

    int offset = 0;
    if (line > 1) {
      offset = policySource.getContent().lineOffsets().get(line - 2) + column;
    }
    idToOffsetMap.put(id, offset);

    return id;
  }

  @Override
  public long nextId() {
    return ++id;
  }

  static ParserContext<Node> newInstance(Source source) {
    return new YamlParserContextImpl(source);
  }

  private YamlParserContextImpl(Source source) {
    this.issues = new ArrayList<>();
    this.idToLocationMap = new HashMap<>();
    this.idToOffsetMap = new HashMap<>();
    this.policySource = source;
  }
}
