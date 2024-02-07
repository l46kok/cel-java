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

package dev.cel.optimizer.optimizers;

import static com.google.common.truth.Truth.assertThat;
import static dev.cel.common.CelOverloadDecl.newGlobalOverload;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelBuilder;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelSource.Extension;
import dev.cel.common.CelSource.Extension.Component;
import dev.cel.common.CelSource.Extension.Version;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelVarDecl;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.navigation.CelNavigableAst;
import dev.cel.common.navigation.CelNavigableExpr;
import dev.cel.common.types.ListType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.extensions.CelExtensions;
import dev.cel.extensions.CelOptionalLibrary;
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.optimizer.CelOptimizer;
import dev.cel.optimizer.CelOptimizerFactory;
import dev.cel.optimizer.MutableAst;
import dev.cel.optimizer.optimizers.SubexpressionOptimizer.SubexpressionOptimizerOptions;
import dev.cel.parser.CelStandardMacro;
import dev.cel.parser.CelUnparser;
import dev.cel.parser.CelUnparserFactory;
import dev.cel.parser.Operator;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeFactory;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.NestedTestAllTypes;
import dev.cel.testing.testdata.proto3.TestAllTypesProto.TestAllTypes;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class SubexpressionOptimizerTest {

  private static final Cel CEL = newCelBuilder().build();

  private static final Cel CEL_FOR_EVALUATING_BLOCK =
      CelFactory.standardCelBuilder()
          .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
          .addFunctionDeclarations(
              // These are test only declarations, as the actual function is made internal using @
              // symbol.
              // If the main function declaration needs updating, be sure to update the test
              // declaration as well.
              CelFunctionDecl.newFunctionDeclaration(
                  "cel.block",
                  CelOverloadDecl.newGlobalOverload(
                      "block_test_only_overload",
                      SimpleType.DYN,
                      ListType.create(SimpleType.DYN),
                      SimpleType.DYN)),
              SubexpressionOptimizer.newCelBlockFunctionDecl(SimpleType.DYN),
              CelFunctionDecl.newFunctionDeclaration(
                  "get_true",
                  CelOverloadDecl.newGlobalOverload("get_true_overload", SimpleType.BOOL)))
          // Similarly, this is a test only decl (index0 -> @index0)
          .addVarDeclarations(
              CelVarDecl.newVarDeclaration("index0", SimpleType.DYN),
              CelVarDecl.newVarDeclaration("index1", SimpleType.DYN),
              CelVarDecl.newVarDeclaration("index2", SimpleType.DYN),
              CelVarDecl.newVarDeclaration("@index0", SimpleType.DYN),
              CelVarDecl.newVarDeclaration("@index1", SimpleType.DYN),
              CelVarDecl.newVarDeclaration("@index2", SimpleType.DYN))
          .addMessageTypes(TestAllTypes.getDescriptor())
          .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()))
          .build();

  private static final CelUnparser CEL_UNPARSER = CelUnparserFactory.newUnparser();

  private static final TestAllTypes TEST_ALL_TYPES_INPUT =
      TestAllTypes.newBuilder()
          .setSingleInt64(3L)
          .setSingleInt32(5)
          .setOneofType(
              NestedTestAllTypes.newBuilder()
                  .setPayload(
                      TestAllTypes.newBuilder()
                          .setSingleInt32(8)
                          .setSingleInt64(10L)
                          .putMapInt32Int64(0, 1)
                          .putMapInt32Int64(1, 5)
                          .putMapInt32Int64(2, 2)
                          .putMapStringString("key", "A")))
          .build();

  private static CelBuilder newCelBuilder() {
    return CelFactory.standardCelBuilder()
        .addMessageTypes(TestAllTypes.getDescriptor())
        .setContainer("dev.cel.testing.testdata.proto3")
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
        .setOptions(
            CelOptions.current().enableTimestampEpoch(true).populateMacroCalls(true).build())
        .addCompilerLibraries(CelOptionalLibrary.INSTANCE, CelExtensions.bindings())
        .addRuntimeLibraries(CelOptionalLibrary.INSTANCE)
        .addFunctionDeclarations(
            CelFunctionDecl.newFunctionDeclaration(
                "custom_func",
                newGlobalOverload("custom_func_overload", SimpleType.INT, SimpleType.INT)))
        .addVar("x", SimpleType.DYN)
        .addVar("opt_x", OptionalType.create(SimpleType.DYN))
        .addVar("msg", StructTypeReference.create(TestAllTypes.getDescriptor().getFullName()));
  }

  private static CelOptimizer newCseOptimizer(SubexpressionOptimizerOptions options) {
    return CelOptimizerFactory.standardCelOptimizerBuilder(CEL)
        .addAstOptimizers(SubexpressionOptimizer.newInstance(options))
        .build();
  }

  @Test
  public void cse_withCelBind_producesOptimizedAst() throws Exception {
    CelAbstractSyntaxTree ast =
        CEL.compile("size([0]) + size([0]) + size([1,2]) + size([1,2])").getAst();

    CelAbstractSyntaxTree optimizedAst =
        newCseOptimizer(
                SubexpressionOptimizerOptions.newBuilder()
                    .populateMacroCalls(false)
                    .enableCelBlock(false)
                    .build())
            .optimize(ast);

    assertThat(CEL.createProgram(optimizedAst).eval()).isEqualTo(6);
    assertThat(optimizedAst.getExpr().toString())
        .isEqualTo(
            "COMPREHENSION [1] {\n"
                + "  iter_var: #unused\n"
                + "  iter_range: {\n"
                + "    CREATE_LIST [2] {\n"
                + "      elements: {\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "  accu_var: @r1\n"
                + "  accu_init: {\n"
                + "    CALL [3] {\n"
                + "      function: size\n"
                + "      args: {\n"
                + "        CREATE_LIST [4] {\n"
                + "          elements: {\n"
                + "            CONSTANT [5] { value: 1 }\n"
                + "            CONSTANT [6] { value: 2 }\n"
                + "          }\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "  loop_condition: {\n"
                + "    CONSTANT [7] { value: false }\n"
                + "  }\n"
                + "  loop_step: {\n"
                + "    IDENT [8] {\n"
                + "      name: @r1\n"
                + "    }\n"
                + "  }\n"
                + "  result: {\n"
                + "    CALL [9] {\n"
                + "      function: _+_\n"
                + "      args: {\n"
                + "        CALL [10] {\n"
                + "          function: _+_\n"
                + "          args: {\n"
                + "            COMPREHENSION [11] {\n"
                + "              iter_var: #unused\n"
                + "              iter_range: {\n"
                + "                CREATE_LIST [12] {\n"
                + "                  elements: {\n"
                + "                  }\n"
                + "                }\n"
                + "              }\n"
                + "              accu_var: @r0\n"
                + "              accu_init: {\n"
                + "                CALL [13] {\n"
                + "                  function: size\n"
                + "                  args: {\n"
                + "                    CREATE_LIST [14] {\n"
                + "                      elements: {\n"
                + "                        CONSTANT [15] { value: 0 }\n"
                + "                      }\n"
                + "                    }\n"
                + "                  }\n"
                + "                }\n"
                + "              }\n"
                + "              loop_condition: {\n"
                + "                CONSTANT [16] { value: false }\n"
                + "              }\n"
                + "              loop_step: {\n"
                + "                IDENT [17] {\n"
                + "                  name: @r0\n"
                + "                }\n"
                + "              }\n"
                + "              result: {\n"
                + "                CALL [18] {\n"
                + "                  function: _+_\n"
                + "                  args: {\n"
                + "                    IDENT [19] {\n"
                + "                      name: @r0\n"
                + "                    }\n"
                + "                    IDENT [20] {\n"
                + "                      name: @r0\n"
                + "                    }\n"
                + "                  }\n"
                + "                }\n"
                + "              }\n"
                + "            }\n"
                + "            IDENT [21] {\n"
                + "              name: @r1\n"
                + "            }\n"
                + "          }\n"
                + "        }\n"
                + "        IDENT [22] {\n"
                + "          name: @r1\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}");
  }

  @Test
  public void cse_withCelBlock_producesOptimizedAst() throws Exception {
    CelAbstractSyntaxTree ast =
        CEL.compile("size([0]) + size([0]) + size([1,2]) + size([1,2])").getAst();
    CelOptimizer celOptimizer =
        newCseOptimizer(SubexpressionOptimizerOptions.newBuilder().enableCelBlock(true).build());
    CelAbstractSyntaxTree optimizedAst = celOptimizer.optimize(ast);

    assertThat(CEL.createProgram(optimizedAst).eval()).isEqualTo(6);
    assertThat(optimizedAst.getExpr().toString())
        .isEqualTo(
            "CALL [0] {\n"
                + "  function: cel.@block\n"
                + "  args: {\n"
                + "    CREATE_LIST [1] {\n"
                + "      elements: {\n"
                + "        CALL [2] {\n"
                + "          function: size\n"
                + "          args: {\n"
                + "            CREATE_LIST [3] {\n"
                + "              elements: {\n"
                + "                CONSTANT [4] { value: 0 }\n"
                + "              }\n"
                + "            }\n"
                + "          }\n"
                + "        }\n"
                + "        CALL [5] {\n"
                + "          function: size\n"
                + "          args: {\n"
                + "            CREATE_LIST [6] {\n"
                + "              elements: {\n"
                + "                CONSTANT [7] { value: 1 }\n"
                + "                CONSTANT [8] { value: 2 }\n"
                + "              }\n"
                + "            }\n"
                + "          }\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "    CALL [9] {\n"
                + "      function: _+_\n"
                + "      args: {\n"
                + "        CALL [10] {\n"
                + "          function: _+_\n"
                + "          args: {\n"
                + "            CALL [11] {\n"
                + "              function: _+_\n"
                + "              args: {\n"
                + "                IDENT [12] {\n"
                + "                  name: @index0\n"
                + "                }\n"
                + "                IDENT [13] {\n"
                + "                  name: @index0\n"
                + "                }\n"
                + "              }\n"
                + "            }\n"
                + "            IDENT [14] {\n"
                + "              name: @index1\n"
                + "            }\n"
                + "          }\n"
                + "        }\n"
                + "        IDENT [15] {\n"
                + "          name: @index1\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}");
  }

  private enum CseTestCase {
    SIZE_1(
        "size([1,2]) + size([1,2]) + 1 == 5",
        "cel.bind(@r0, size([1, 2]), @r0 + @r0) + 1 == 5",
        "cel.@block([size([1, 2])], @index0 + @index0 + 1 == 5)"),
    SIZE_2(
        "2 + size([1,2]) + size([1,2]) + 1 == 7",
        "cel.bind(@r0, size([1, 2]), 2 + @r0 + @r0) + 1 == 7",
        "cel.@block([size([1, 2])], 2 + @index0 + @index0 + 1 == 7)"),
    SIZE_3(
        "size([0]) + size([0]) + size([1,2]) + size([1,2]) == 6",
        "cel.bind(@r1, size([1, 2]), cel.bind(@r0, size([0]), @r0 + @r0) + @r1 + @r1) == 6",
        "cel.@block([size([0]), size([1, 2])], @index0 + @index0 + @index1 + @index1 == 6)"),
    SIZE_4(
        "5 + size([0]) + size([0]) + size([1,2]) + size([1,2]) + "
            + "size([1,2,3]) + size([1,2,3]) == 17",
        "cel.bind(@r2, size([1, 2, 3]), cel.bind(@r1, size([1, 2]), cel.bind(@r0, size([0]), 5 +"
            + " @r0 + @r0) + @r1 + @r1) + @r2 + @r2) == 17",
        "cel.@block([size([0]), size([1, 2]), size([1, 2, 3])], 5 + @index0 + @index0 + @index1 +"
            + " @index1 + @index2 + @index2 == 17)"),
    /**
     * Unparsed form:
     *
     * <pre>
     * {@code
     * // With binds
     * cel.bind(@r0, timestamp(int(timestamp(1000000000))).getFullYear(),
     *    cel.bind(@r3, timestamp(int(timestamp(75))),
     *      cel.bind(@r2, timestamp(int(timestamp(200))).getFullYear(),
     *        cel.bind(@r1, timestamp(int(timestamp(50))),
     *          @r0 + @r3.getFullYear() + @r1.getFullYear() + @r0 + @r1.getSeconds()
     *        ) + @r2 + @r2
     *      ) + @r3.getMinutes()
     *    ) + @r0
     *) == 13934
     * }
     * </pre>
     * <pre>
     * {@code
     * // With block
     * cel.@block(
     *     [
     *      timestamp(int(timestamp(1000000000))).getFullYear(),
     *      timestamp(int(timestamp(50))),
     *      timestamp(int(timestamp(200))).getFullYear(),
     *      timestamp(int(timestamp(75)))
     *     ],
     *     @index0 + @index3.getFullYear() + @index1.getFullYear() + @index0 +
     *     @index1.getSeconds() + @index2 + @index2 + @index3.getMinutes() + @index0 == 13934
     * )
     * </pre>
     * }
     */
    TIMESTAMP(
        "timestamp(int(timestamp(1000000000))).getFullYear() +"
            + " timestamp(int(timestamp(75))).getFullYear() + "
            + " timestamp(int(timestamp(50))).getFullYear() + "
            + " timestamp(int(timestamp(1000000000))).getFullYear() + "
            + " timestamp(int(timestamp(50))).getSeconds() + "
            + " timestamp(int(timestamp(200))).getFullYear() + "
            + " timestamp(int(timestamp(200))).getFullYear() + "
            + " timestamp(int(timestamp(75))).getMinutes() + "
            + " timestamp(int(timestamp(1000000000))).getFullYear() == 13934",
        "cel.bind(@r0, timestamp(int(timestamp(1000000000))).getFullYear(), "
            + "cel.bind(@r3, timestamp(int(timestamp(75))), "
            + "cel.bind(@r2, timestamp(int(timestamp(200))).getFullYear(), "
            + "cel.bind(@r1, timestamp(int(timestamp(50))), "
            + "@r0 + @r3.getFullYear() + @r1.getFullYear() + "
            + "@r0 + @r1.getSeconds()) + @r2 + @r2) + @r3.getMinutes()) + @r0) == 13934",
        "cel.@block([timestamp(int(timestamp(1000000000))).getFullYear(),"
            + " timestamp(int(timestamp(50))), timestamp(int(timestamp(200))).getFullYear(),"
            + " timestamp(int(timestamp(75)))], @index0 + @index3.getFullYear() +"
            + " @index1.getFullYear() + @index0 + @index1.getSeconds() + @index2 + @index2 +"
            + " @index3.getMinutes() + @index0 == 13934)"),
    MAP_INDEX(
        "{\"a\": 2}[\"a\"] + {\"a\": 2}[\"a\"] * {\"a\": 2}[\"a\"] == 6",
        "cel.bind(@r0, {\"a\": 2}[\"a\"], @r0 + @r0 * @r0) == 6",
        "cel.@block([{\"a\": 2}[\"a\"]], @index0 + @index0 * @index0 == 6)"),
    /**
     * Input map is:
     *
     * <pre>{@code
     * {
     *    "a": { "b": 1 },
     *    "c": { "b": 1 },
     *    "d": {
     *       "e": { "b": 1 }
     *    },
     *    "e":{
     *       "e": { "b": 1 }
     *    }
     * }
     * }</pre>
     */
    NESTED_MAP_CONSTRUCTION(
        "size({'a': {'b': 1}, 'c': {'b': 1}, 'd': {'e': {'b': 1}}, 'e': {'e': {'b': 1}}}) == 4",
        "size(cel.bind(@r0, {\"b\": 1}, cel.bind(@r1, {\"e\": @r0}, {\"a\": @r0, \"c\": @r0, \"d\":"
            + " @r1, \"e\": @r1}))) == 4",
        "cel.@block([{\"b\": 1}, {\"e\": @index0}], size({\"a\": @index0, \"c\": @index0, \"d\":"
            + " @index1, \"e\": @index1}) == 4)"),
    NESTED_LIST_CONSTRUCTION(
        "size([1, [1,2,3,4], 2, [1,2,3,4], 5, [1,2,3,4], 7, [[1,2], [1,2,3,4]], [1,2]]) == 9",
        "size(cel.bind(@r0, [1, 2, 3, 4], "
            + "cel.bind(@r1, [1, 2], [1, @r0, 2, @r0, 5, @r0, 7, [@r1, @r0], @r1]))) == 9",
        "cel.@block([[1, 2, 3, 4], [1, 2]], size([1, @index0, 2, @index0, 5, @index0, 7, [@index1,"
            + " @index0], @index1]) == 9)"),
    SELECT(
        "msg.single_int64 + msg.single_int64 == 6",
        "cel.bind(@r0, msg.single_int64, @r0 + @r0) == 6",
        "cel.@block([msg.single_int64], @index0 + @index0 == 6)"),
    SELECT_NESTED(
        "msg.oneof_type.payload.single_int64 + msg.oneof_type.payload.single_int32 + "
            + "msg.oneof_type.payload.single_int64 + "
            + "msg.single_int64 + msg.oneof_type.payload.oneof_type.payload.single_int64 == 31",
        "cel.bind(@r0, msg.oneof_type.payload, "
            + "cel.bind(@r1, @r0.single_int64, @r1 + @r0.single_int32 + @r1) + "
            + "msg.single_int64 + @r0.oneof_type.payload.single_int64) == 31",
        "cel.@block([msg.oneof_type.payload, @index0.single_int64], @index1 + @index0.single_int32"
            + " + @index1 + msg.single_int64 + @index0.oneof_type.payload.single_int64 == 31)"),
    SELECT_NESTED_MESSAGE_MAP_INDEX_1(
        "msg.oneof_type.payload.map_int32_int64[1] + "
            + "msg.oneof_type.payload.map_int32_int64[1] + "
            + "msg.oneof_type.payload.map_int32_int64[1] == 15",
        "cel.bind(@r0, msg.oneof_type.payload.map_int32_int64[1], @r0 + @r0 + @r0) == 15",
        "cel.@block([msg.oneof_type.payload.map_int32_int64[1]], @index0 + @index0 + @index0 =="
            + " 15)"),
    SELECT_NESTED_MESSAGE_MAP_INDEX_2(
        "msg.oneof_type.payload.map_int32_int64[0] + "
            + "msg.oneof_type.payload.map_int32_int64[1] + "
            + "msg.oneof_type.payload.map_int32_int64[2] == 8",
        "cel.bind(@r0, msg.oneof_type.payload.map_int32_int64, @r0[0] + @r0[1] + @r0[2]) == 8",
        "cel.@block([msg.oneof_type.payload.map_int32_int64], @index0[0] + @index0[1] + @index0[2]"
            + " == 8)"),
    TERNARY(
        "(msg.single_int64 > 0 ? msg.single_int64 : 0) == 3",
        "cel.bind(@r0, msg.single_int64, (@r0 > 0) ? @r0 : 0) == 3",
        "cel.@block([msg.single_int64], ((@index0 > 0) ? @index0 : 0) == 3)"),
    TERNARY_BIND_RHS_ONLY(
        "false ? false : (msg.single_int64) + ((msg.single_int64 + 1) * 2) == 11",
        "false ? false : (cel.bind(@r0, msg.single_int64, @r0 + (@r0 + 1) * 2) == 11)",
        "cel.@block([msg.single_int64], false ? false : (@index0 + (@index0 + 1) * 2 == 11))"),
    NESTED_TERNARY(
        "(msg.single_int64 > 0 ? (msg.single_int32 > 0 ? "
            + "msg.single_int64 + msg.single_int32 : 0) : 0) == 8",
        "cel.bind(@r0, msg.single_int64, (@r0 > 0) ? "
            + "cel.bind(@r1, msg.single_int32, (@r1 > 0) ? (@r0 + @r1) : 0) : 0) == 8",
        "cel.@block([msg.single_int64, msg.single_int32], ((@index0 > 0) ? ((@index1 > 0) ?"
            + " (@index0 + @index1) : 0) : 0) == 8)"),
    MULTIPLE_MACROS(
        // Note that all of these have different iteration variables, but they are still logically
        // the same.
        "size([[1].exists(i, i > 0)]) + size([[1].exists(j, j > 0)]) + "
            + "size([[2].exists(k, k > 1)]) + size([[2].exists(l, l > 1)]) == 4",
        "cel.bind(@r1, size([[2].exists(@c0, @c0 > 1)]), "
            + "cel.bind(@r0, size([[1].exists(@c0, @c0 > 0)]), @r0 + @r0) + @r1 + @r1) == 4",
        "cel.@block([size([[1].exists(@c0, @c0 > 0)]), size([[2].exists(@c0, @c0 > 1)])], @index0 +"
            + " @index0 + @index1 + @index1 == 4)"),
    NESTED_MACROS(
        "[1,2,3].map(i, [1, 2, 3].map(i, i + 1)) == [[2, 3, 4], [2, 3, 4], [2, 3, 4]]",
        "cel.bind(@r0, [1, 2, 3], @r0.map(@c0, @r0.map(@c1, @c1 + 1))) == "
            + "cel.bind(@r1, [2, 3, 4], [@r1, @r1, @r1])",
        "cel.@block([[1, 2, 3], [2, 3, 4]], @index0.map(@c0, @index0.map(@c1, @c1 + 1)) =="
            + " [@index1, @index1, @index1])"),
    INCLUSION_LIST(
        "1 in [1,2,3] && 2 in [1,2,3] && 3 in [3, [1,2,3]] && 1 in [1,2,3]",
        "cel.bind(@r0, [1, 2, 3], cel.bind(@r1, 1 in @r0, @r1 && 2 in @r0 && 3 in [3, @r0] &&"
            + " @r1))",
        "cel.@block([[1, 2, 3], 1 in @index0], @index1 && 2 in @index0 && 3 in [3, @index0] &&"
            + " @index1)"),
    INCLUSION_MAP(
        "2 in {'a': 1, 2: {true: false}, 3: {true: false}}",
        "2 in cel.bind(@r0, {true: false}, {\"a\": 1, 2: @r0, 3: @r0})",
        "cel.@block([{true: false}], 2 in {\"a\": 1, 2: @index0, 3: @index0})"),
    MACRO_SHADOWED_VARIABLE(
        "[x - 1 > 3 ? x - 1 : 5].exists(x, x - 1 > 3) || x - 1 > 3",
        "cel.bind(@r0, x - 1, cel.bind(@r1, @r0 > 3, [@r1 ? @r0 : 5].exists(@c0, @c0 - 1 > 3) ||"
            + " @r1))",
        "cel.@block([x - 1, @index0 > 3], [@index1 ? @index0 : 5].exists(@c0, @c0 - 1 > 3) ||"
            + " @index1)"),
    MACRO_SHADOWED_VARIABLE_2(
        "size([\"foo\", \"bar\"].map(x, [x + x, x + x]).map(x, [x + x, x + x])) == 2",
        "size([\"foo\", \"bar\"].map(@c1, cel.bind(@r0, @c1 + @c1, [@r0, @r0]))"
            + ".map(@c0, cel.bind(@r1, @c0 + @c0, [@r1, @r1]))) == 2",
        "Currently Unsupported"), // TODO: Handle comprehension variables that fall
                                  // outside the cel.block scope
    PRESENCE_TEST(
        "has({'a': true}.a) && {'a':true}['a']",
        "cel.bind(@r0, {\"a\": true}, has(@r0.a) && @r0[\"a\"])",
        "cel.@block([{\"a\": true}], has(@index0.a) && @index0[\"a\"])"),
    PRESENCE_TEST_WITH_TERNARY(
        "(has(msg.oneof_type.payload) ? msg.oneof_type.payload.single_int64 : 0) == 10",
        "cel.bind(@r0, msg.oneof_type, has(@r0.payload) ? @r0.payload.single_int64 : 0) == 10",
        "cel.@block([msg.oneof_type], (has(@index0.payload) ? @index0.payload.single_int64 : 0) =="
            + " 10)"),
    PRESENCE_TEST_WITH_TERNARY_2(
        "(has(msg.oneof_type.payload) ? msg.oneof_type.payload.single_int64 :"
            + " msg.oneof_type.payload.single_int64 * 0) == 10",
        "cel.bind(@r0, msg.oneof_type, cel.bind(@r1, @r0.payload.single_int64, has(@r0.payload) ?"
            + " @r1 : (@r1 * 0))) == 10",
        "cel.@block([msg.oneof_type, @index0.payload.single_int64], (has(@index0.payload) ? @index1"
            + " : (@index1 * 0)) == 10)"),
    PRESENCE_TEST_WITH_TERNARY_3(
        "(has(msg.oneof_type.payload.single_int64) ? msg.oneof_type.payload.single_int64 :"
            + " msg.oneof_type.payload.single_int64 * 0) == 10",
        "cel.bind(@r0, msg.oneof_type.payload, cel.bind(@r1, @r0.single_int64,"
            + " has(@r0.single_int64) ? @r1 : (@r1 * 0))) == 10",
        "cel.@block([msg.oneof_type.payload, @index0.single_int64], (has(@index0.single_int64) ?"
            + " @index1 : (@index1 * 0)) == 10)"),
    /**
     * Input:
     *
     * <pre>{@code
     * (
     *   has(msg.oneof_type) &&
     *   has(msg.oneof_type.payload) &&
     *   has(msg.oneof_type.payload.single_int64)
     * ) ?
     *   (
     *     (
     *       has(msg.oneof_type.payload.map_string_string) &&
     *       has(msg.oneof_type.payload.map_string_string.key)
     *     ) ?
     *       msg.oneof_type.payload.map_string_string.key == "A"
     *     : false
     *   )
     * : false
     * }</pre>
     *
     * Unparsed:
     *
     * <pre>{@code
     * // With binds
     * cel.bind(
     *   @r0, msg.oneof_type,
     *   cel.bind(
     *     @r1, @r0.payload,
     *     has(msg.oneof_type) && has(@r0.payload) && has(@r1.single_int64) ?
     *       cel.bind(
     *         @r2, @r1.map_string_string,
     *         has(@r1.map_string_string) && has(@r2.key) ? @r2.key == "A" : false,
     *       )
     *     : false,
     *   ),
     * )
     * }</pre>
     * <pre>{@code
     * // With block
     * cel.@block(
     *   [
     *     msg.oneof_type,
     *     @index0.payload,
     *     @index1.map_string_string
     *   ],
     *   (has(msg.oneof_type) && has(@index0.payload) && has(@index1.single_int64)) ?
     *     (
     *       (has(@index1.map_string_string) && has(@index2.key)) ?
     *         (@index2.key == "A") : false
     *     )
     *     : false
     *   )
     * }</pre>
     */
    PRESENCE_TEST_WITH_TERNARY_NESTED(
        "(has(msg.oneof_type) && has(msg.oneof_type.payload) &&"
            + " has(msg.oneof_type.payload.single_int64)) ?"
            + " ((has(msg.oneof_type.payload.map_string_string) &&"
            + " has(msg.oneof_type.payload.map_string_string.key)) ?"
            + " msg.oneof_type.payload.map_string_string.key == 'A' : false) : false",
        "cel.bind(@r0, msg.oneof_type, cel.bind(@r1, @r0.payload, (has(msg.oneof_type) &&"
            + " has(@r0.payload) && has(@r1.single_int64)) ? cel.bind(@r2, @r1.map_string_string,"
            + " (has(@r1.map_string_string) && has(@r2.key)) ? (@r2.key == \"A\") : false) :"
            + " false))",
        "cel.@block([msg.oneof_type, @index0.payload, @index1.map_string_string],"
            + " (has(msg.oneof_type) && has(@index0.payload) && has(@index1.single_int64)) ?"
            + " ((has(@index1.map_string_string) && has(@index2.key)) ? (@index2.key == \"A\") :"
            + " false) : false)"),
    OPTIONAL_LIST(
        "[10, ?optional.none(), [?optional.none(), ?opt_x], [?optional.none(), ?opt_x]] == [10,"
            + " [5], [5]]",
        "cel.bind(@r0, [?optional.none(), ?opt_x], [10, ?optional.none(), @r0, @r0]) =="
            + " cel.bind(@r1, [5], [10, @r1, @r1])",
        "cel.@block([[?optional.none(), ?opt_x], [5]], [10, ?optional.none(), @index0, @index0] =="
            + " [10, @index1, @index1])"),
    OPTIONAL_MAP(
        "{?'hello': optional.of('hello')}['hello'] + {?'hello': optional.of('hello')}['hello'] =="
            + " 'hellohello'",
        "cel.bind(@r0, {?\"hello\": optional.of(\"hello\")}[\"hello\"], @r0 + @r0) =="
            + " \"hellohello\"",
        "cel.@block([{?\"hello\": optional.of(\"hello\")}[\"hello\"]], @index0 + @index0 =="
            + " \"hellohello\")"),
    OPTIONAL_MESSAGE(
        "TestAllTypes{?single_int64: optional.ofNonZeroValue(1), ?single_int32:"
            + " optional.of(4)}.single_int32 + TestAllTypes{?single_int64:"
            + " optional.ofNonZeroValue(1), ?single_int32: optional.of(4)}.single_int64 == 5",
        "cel.bind(@r0, TestAllTypes{"
            + "?single_int64: optional.ofNonZeroValue(1), ?single_int32: optional.of(4)}, "
            + "@r0.single_int32 + @r0.single_int64) == 5",
        "cel.@block([TestAllTypes{?single_int64: optional.ofNonZeroValue(1), ?single_int32:"
            + " optional.of(4)}], @index0.single_int32 + @index0.single_int64 == 5)"),
    ;

    private final String source;
    private final String unparsedBind;
    private final String unparsedBlock;

    CseTestCase(String source, String unparsedBind, String unparsedBlock) {
      this.source = source;
      this.unparsedBind = unparsedBind;
      this.unparsedBlock = unparsedBlock;
    }
  }

  @Test
  public void cse_withCelBind_macroMapPopulated(@TestParameter CseTestCase testCase)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(testCase.source).getAst();

    CelAbstractSyntaxTree optimizedAst =
        newCseOptimizer(
                SubexpressionOptimizerOptions.newBuilder()
                    .populateMacroCalls(true)
                    .enableCelBlock(false)
                    .build())
            .optimize(ast);

    assertThat(
            CEL.createProgram(optimizedAst)
                .eval(
                    ImmutableMap.of(
                        "msg", TEST_ALL_TYPES_INPUT, "x", 5L, "opt_x", Optional.of(5L))))
        .isEqualTo(true);
    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo(testCase.unparsedBind);
  }

  @Test
  public void cse_withCelBind_macroMapUnpopulated(@TestParameter CseTestCase testCase)
      throws Exception {
    CelBuilder celWithoutMacroMap =
        newCelBuilder().setOptions(CelOptions.current().enableTimestampEpoch(true).build());
    CelAbstractSyntaxTree ast = celWithoutMacroMap.build().compile(testCase.source).getAst();

    CelAbstractSyntaxTree optimizedAst =
        newCseOptimizer(
                SubexpressionOptimizerOptions.newBuilder().populateMacroCalls(false).build())
            .optimize(ast);

    assertThat(optimizedAst.getSource().getMacroCalls()).isEmpty();
    assertThat(
            celWithoutMacroMap
                .build()
                .createProgram(optimizedAst)
                .eval(
                    ImmutableMap.of(
                        "msg", TEST_ALL_TYPES_INPUT, "x", 5L, "opt_x", Optional.of(5L))))
        .isEqualTo(true);
  }

  @Test
  public void cse_withCelBlock_macroMapPopulated(@TestParameter CseTestCase testCase)
      throws Exception {
    if (testCase.equals(CseTestCase.MACRO_SHADOWED_VARIABLE_2)) {
      // TODO: Handle comprehension variables that fall outside the cel.block scope
      return;
    }
    CelOptimizer celOptimizer =
        newCseOptimizer(
            SubexpressionOptimizerOptions.newBuilder()
                .populateMacroCalls(true)
                .enableCelBlock(true)
                .build());
    CelAbstractSyntaxTree ast = CEL.compile(testCase.source).getAst();

    CelAbstractSyntaxTree optimizedAst = celOptimizer.optimize(ast);

    assertThat(
            CEL.createProgram(optimizedAst)
                .eval(
                    ImmutableMap.of(
                        "msg", TEST_ALL_TYPES_INPUT, "x", 5L, "opt_x", Optional.of(5L))))
        .isEqualTo(true);
    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo(testCase.unparsedBlock);
  }

  @Test
  public void cse_withCelBlock_macroMapUnpopulated(@TestParameter CseTestCase testCase)
      throws Exception {
    if (testCase.equals(CseTestCase.MACRO_SHADOWED_VARIABLE_2)) {
      // TODO: Handle comprehension variables that fall outside the cel.block scope
      return;
    }
    CelOptimizer celOptimizer =
        newCseOptimizer(
            SubexpressionOptimizerOptions.newBuilder()
                .populateMacroCalls(false)
                .enableCelBlock(true)
                .build());
    CelAbstractSyntaxTree ast = CEL.compile(testCase.source).getAst();

    CelAbstractSyntaxTree optimizedAst = celOptimizer.optimize(ast);

    assertThat(optimizedAst.getSource().getMacroCalls()).isEmpty();
    assertThat(
            CEL.createProgram(optimizedAst)
                .eval(
                    ImmutableMap.of(
                        "msg", TEST_ALL_TYPES_INPUT, "x", 5L, "opt_x", Optional.of(5L))))
        .isEqualTo(true);
  }

  @Test
  public void cse_resultTypeSet_celBlockOptimizationSuccess() throws Exception {
    Cel cel = newCelBuilder().setResultType(SimpleType.BOOL).build();
    CelOptimizer celOptimizer =
        CelOptimizerFactory.standardCelOptimizerBuilder(cel)
            .addAstOptimizers(
                SubexpressionOptimizer.newInstance(
                    SubexpressionOptimizerOptions.newBuilder().enableCelBlock(true).build()))
            .build();
    CelAbstractSyntaxTree ast = CEL.compile("size('a') + size('a') == 2").getAst();

    CelAbstractSyntaxTree optimizedAst = celOptimizer.optimize(ast);

    assertThat(CEL.createProgram(optimizedAst).eval()).isEqualTo(true);
    assertThat(CEL_UNPARSER.unparse(optimizedAst))
        .isEqualTo("cel.@block([size(\"a\")], @index0 + @index0 == 2)");
  }

  @Test
  // Nothing to optimize
  @TestParameters("{source: 'size(\"hello\")'}")
  // Constants and identifiers
  @TestParameters("{source: '2 + 2 + 2 + 2'}")
  @TestParameters("{source: 'x + x + x + x'}")
  @TestParameters("{source: 'true == true && false == false'}")
  // Constants and identifiers within a function
  @TestParameters("{source: 'size(\"hello\" + \"hello\" + \"hello\")'}")
  @TestParameters("{source: 'string(x + x + x)'}")
  // Non-standard functions are considered non-pure for time being
  @TestParameters("{source: 'custom_func(1) + custom_func(1)'}")
  // Duplicated but nested calls.
  @TestParameters("{source: 'int(timestamp(int(timestamp(1000000000))))'}")
  // This cannot be optimized. Extracting the common subexpression would presence test
  // the bound identifier (e.g: has(@r0)), which is not valid.
  @TestParameters("{source: 'has(msg.single_any) ? msg.single_any : 10'}")
  public void cse_withCelBind_noop(String source) throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(source).getAst();

    CelAbstractSyntaxTree optimizedAst =
        newCseOptimizer(
                SubexpressionOptimizerOptions.newBuilder()
                    .populateMacroCalls(true)
                    .enableCelBlock(false)
                    .build())
            .optimize(ast);

    assertThat(ast.getExpr()).isEqualTo(optimizedAst.getExpr());
    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo(source);
  }

  @Test
  // Nothing to optimize
  @TestParameters("{source: 'size(\"hello\")'}")
  // Constants and identifiers
  @TestParameters("{source: '2 + 2 + 2 + 2'}")
  @TestParameters("{source: 'x + x + x + x'}")
  @TestParameters("{source: 'true == true && false == false'}")
  // Constants and identifiers within a function
  @TestParameters("{source: 'size(\"hello\" + \"hello\" + \"hello\")'}")
  @TestParameters("{source: 'string(x + x + x)'}")
  // Non-standard functions are considered non-pure for time being
  @TestParameters("{source: 'custom_func(1) + custom_func(1)'}")
  // Duplicated but nested calls.
  @TestParameters("{source: 'int(timestamp(int(timestamp(1000000000))))'}")
  // This cannot be optimized. Extracting the common subexpression would presence test
  // the bound identifier (e.g: has(@r0)), which is not valid.
  @TestParameters("{source: 'has(msg.single_any) ? msg.single_any : 10'}")
  public void cse_withCelBlock_noop(String source) throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile(source).getAst();

    CelAbstractSyntaxTree optimizedAst =
        newCseOptimizer(
                SubexpressionOptimizerOptions.newBuilder()
                    .populateMacroCalls(true)
                    .enableCelBlock(true)
                    .build())
            .optimize(ast);

    assertThat(ast.getExpr()).isEqualTo(optimizedAst.getExpr());
    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo(source);
  }

  @Test
  public void cse_largeCalcExpr() throws Exception {
    StringBuilder sb = new StringBuilder();
    int limit = 40;
    for (int i = 0; i < limit; i++) {
      sb.append("size([1]) + ");
      sb.append("size([1,2]) + ");
      sb.append("size([1,2,3]) +");
      sb.append("size([1,2,3,4])");
      if (i < limit - 1) {
        sb.append("+");
      }
    }
    CelAbstractSyntaxTree ast = CEL.compile(sb.toString()).getAst();

    CelAbstractSyntaxTree optimizedAst =
        newCseOptimizer(
                SubexpressionOptimizerOptions.newBuilder()
                    .populateMacroCalls(true)
                    .enableCelBlock(false)
                    .build())
            .optimize(ast);

    assertThat(CEL_UNPARSER.unparse(optimizedAst))
        .isEqualTo(
            "cel.bind(@r3, size([1, 2, 3, 4]), cel.bind(@r2, size([1, 2, 3]), cel.bind(@r1,"
                + " size([1, 2]), cel.bind(@r0, size([1]), @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2"
                + " + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 +"
                + " @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 +"
                + " @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 +"
                + " @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 +"
                + " @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 +"
                + " @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 +"
                + " @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 +"
                + " @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 +"
                + " @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 +"
                + " @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 +"
                + " @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0 + @r1 +"
                + " @r2 + @r3 + @r0 + @r1 + @r2 + @r3 + @r0) + @r1) + @r2) + @r3)");
    assertThat(CEL.createProgram(optimizedAst).eval()).isEqualTo(400L);
  }

  @Test
  public void cse_largeNestedBinds() throws Exception {
    StringBuilder sb = new StringBuilder();
    int limit = 50;
    for (int i = 0; i < limit; i++) {
      sb.append(String.format("size([%d, %d]) + ", i, i + 1));
      sb.append(String.format("size([%d, %d]) ", i, i + 1));
      if (i < limit - 1) {
        sb.append("+");
      }
    }
    CelAbstractSyntaxTree ast = CEL.compile(sb.toString()).getAst();

    CelAbstractSyntaxTree optimizedAst =
        newCseOptimizer(
                SubexpressionOptimizerOptions.newBuilder()
                    .populateMacroCalls(true)
                    .enableCelBlock(false)
                    .build())
            .optimize(ast);

    assertThat(CEL_UNPARSER.unparse(optimizedAst))
        .isEqualTo(
            "cel.bind(@r49, size([49, 50]), cel.bind(@r48, size([48, 49]), cel.bind(@r47, size([47,"
                + " 48]), cel.bind(@r46, size([46, 47]), cel.bind(@r45, size([45, 46]),"
                + " cel.bind(@r44, size([44, 45]), cel.bind(@r43, size([43, 44]), cel.bind(@r42,"
                + " size([42, 43]), cel.bind(@r41, size([41, 42]), cel.bind(@r40, size([40, 41]),"
                + " cel.bind(@r39, size([39, 40]), cel.bind(@r38, size([38, 39]), cel.bind(@r37,"
                + " size([37, 38]), cel.bind(@r36, size([36, 37]), cel.bind(@r35, size([35, 36]),"
                + " cel.bind(@r34, size([34, 35]), cel.bind(@r33, size([33, 34]), cel.bind(@r32,"
                + " size([32, 33]), cel.bind(@r31, size([31, 32]), cel.bind(@r30, size([30, 31]),"
                + " cel.bind(@r29, size([29, 30]), cel.bind(@r28, size([28, 29]), cel.bind(@r27,"
                + " size([27, 28]), cel.bind(@r26, size([26, 27]), cel.bind(@r25, size([25, 26]),"
                + " cel.bind(@r24, size([24, 25]), cel.bind(@r23, size([23, 24]), cel.bind(@r22,"
                + " size([22, 23]), cel.bind(@r21, size([21, 22]), cel.bind(@r20, size([20, 21]),"
                + " cel.bind(@r19, size([19, 20]), cel.bind(@r18, size([18, 19]), cel.bind(@r17,"
                + " size([17, 18]), cel.bind(@r16, size([16, 17]), cel.bind(@r15, size([15, 16]),"
                + " cel.bind(@r14, size([14, 15]), cel.bind(@r13, size([13, 14]), cel.bind(@r12,"
                + " size([12, 13]), cel.bind(@r11, size([11, 12]), cel.bind(@r10, size([10, 11]),"
                + " cel.bind(@r9, size([9, 10]), cel.bind(@r8, size([8, 9]), cel.bind(@r7, size([7,"
                + " 8]), cel.bind(@r6, size([6, 7]), cel.bind(@r5, size([5, 6]), cel.bind(@r4,"
                + " size([4, 5]), cel.bind(@r3, size([3, 4]), cel.bind(@r2, size([2, 3]),"
                + " cel.bind(@r1, size([1, 2]), cel.bind(@r0, size([0, 1]), @r0 + @r0) + @r1 + @r1)"
                + " + @r2 + @r2) + @r3 + @r3) + @r4 + @r4) + @r5 + @r5) + @r6 + @r6) + @r7 + @r7) +"
                + " @r8 + @r8) + @r9 + @r9) + @r10 + @r10) + @r11 + @r11) + @r12 + @r12) + @r13 +"
                + " @r13) + @r14 + @r14) + @r15 + @r15) + @r16 + @r16) + @r17 + @r17) + @r18 +"
                + " @r18) + @r19 + @r19) + @r20 + @r20) + @r21 + @r21) + @r22 + @r22) + @r23 +"
                + " @r23) + @r24 + @r24) + @r25 + @r25) + @r26 + @r26) + @r27 + @r27) + @r28 +"
                + " @r28) + @r29 + @r29) + @r30 + @r30) + @r31 + @r31) + @r32 + @r32) + @r33 +"
                + " @r33) + @r34 + @r34) + @r35 + @r35) + @r36 + @r36) + @r37 + @r37) + @r38 +"
                + " @r38) + @r39 + @r39) + @r40 + @r40) + @r41 + @r41) + @r42 + @r42) + @r43 +"
                + " @r43) + @r44 + @r44) + @r45 + @r45) + @r46 + @r46) + @r47 + @r47) + @r48 +"
                + " @r48) + @r49 + @r49)");
    assertThat(CEL.createProgram(optimizedAst).eval()).isEqualTo(200L);
  }

  @Test
  public void cse_largeFlattenedBlocks() throws Exception {
    StringBuilder sb = new StringBuilder();
    int limit = 50;
    for (int i = 0; i < limit; i++) {
      sb.append(String.format("size([%d, %d]) + ", i, i + 1));
      sb.append(String.format("size([%d, %d]) ", i, i + 1));
      if (i < limit - 1) {
        sb.append("+");
      }
    }
    CelAbstractSyntaxTree ast = CEL.compile(sb.toString()).getAst();

    CelAbstractSyntaxTree optimizedAst =
        newCseOptimizer(
                SubexpressionOptimizerOptions.newBuilder()
                    .populateMacroCalls(true)
                    .enableCelBlock(true)
                    .build())
            .optimize(ast);

    assertThat(CEL_UNPARSER.unparse(optimizedAst))
        .isEqualTo(
            "cel.@block([size([0, 1]), size([1, 2]), size([2, 3]), size([3, 4]), size([4, 5]),"
                + " size([5, 6]), size([6, 7]), size([7, 8]), size([8, 9]), size([9, 10]),"
                + " size([10, 11]), size([11, 12]), size([12, 13]), size([13, 14]), size([14, 15]),"
                + " size([15, 16]), size([16, 17]), size([17, 18]), size([18, 19]), size([19, 20]),"
                + " size([20, 21]), size([21, 22]), size([22, 23]), size([23, 24]), size([24, 25]),"
                + " size([25, 26]), size([26, 27]), size([27, 28]), size([28, 29]), size([29, 30]),"
                + " size([30, 31]), size([31, 32]), size([32, 33]), size([33, 34]), size([34, 35]),"
                + " size([35, 36]), size([36, 37]), size([37, 38]), size([38, 39]), size([39, 40]),"
                + " size([40, 41]), size([41, 42]), size([42, 43]), size([43, 44]), size([44, 45]),"
                + " size([45, 46]), size([46, 47]), size([47, 48]), size([48, 49]), size([49,"
                + " 50])], @index0 + @index0 + @index1 + @index1 + @index2 + @index2 + @index3 +"
                + " @index3 + @index4 + @index4 + @index5 + @index5 + @index6 + @index6 + @index7 +"
                + " @index7 + @index8 + @index8 + @index9 + @index9 + @index10 + @index10 +"
                + " @index11 + @index11 + @index12 + @index12 + @index13 + @index13 + @index14 +"
                + " @index14 + @index15 + @index15 + @index16 + @index16 + @index17 + @index17 +"
                + " @index18 + @index18 + @index19 + @index19 + @index20 + @index20 + @index21 +"
                + " @index21 + @index22 + @index22 + @index23 + @index23 + @index24 + @index24 +"
                + " @index25 + @index25 + @index26 + @index26 + @index27 + @index27 + @index28 +"
                + " @index28 + @index29 + @index29 + @index30 + @index30 + @index31 + @index31 +"
                + " @index32 + @index32 + @index33 + @index33 + @index34 + @index34 + @index35 +"
                + " @index35 + @index36 + @index36 + @index37 + @index37 + @index38 + @index38 +"
                + " @index39 + @index39 + @index40 + @index40 + @index41 + @index41 + @index42 +"
                + " @index42 + @index43 + @index43 + @index44 + @index44 + @index45 + @index45 +"
                + " @index46 + @index46 + @index47 + @index47 + @index48 + @index48 + @index49 +"
                + " @index49)");
    assertThat(CEL.createProgram(optimizedAst).eval()).isEqualTo(200L);
  }

  @Test
  public void cse_withCelBind_largeNestedMacro() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("size([1,2,3]");
    int limit = 8;
    for (int i = 0; i < limit; i++) {
      sb.append(".map(i, [1, 2, 3]");
    }
    for (int i = 0; i < limit; i++) {
      sb.append(")");
    }
    sb.append(")");
    String nestedMapCallExpr = sb.toString(); // size([1,2,3].map(i, [1,2,3].map(i, [1,2,3].map(...
    // Add this large macro call 8 times
    for (int i = 0; i < limit; i++) {
      sb.append("+");
      sb.append(nestedMapCallExpr);
    }
    CelAbstractSyntaxTree ast = CEL.compile(sb.toString()).getAst();

    CelAbstractSyntaxTree optimizedAst =
        newCseOptimizer(
                SubexpressionOptimizerOptions.newBuilder()
                    .populateMacroCalls(true)
                    .enableCelBlock(false)
                    .build())
            .optimize(ast);

    assertThat(CEL_UNPARSER.unparse(optimizedAst))
        .isEqualTo(
            "cel.bind(@r0, [1, 2, 3], cel.bind(@r1, size(@r0.map(@c0, @r0.map(@c1, @r0.map(@c2, "
                + "@r0.map(@c3, @r0.map(@c4, @r0.map(@c5, @r0.map(@c6, @r0.map(@c7, @r0))))))))), "
                + "@r1 + @r1 + @r1 + @r1 + @r1 + @r1 + @r1 + @r1 + @r1))");
    assertThat(CEL.createProgram(optimizedAst).eval()).isEqualTo(27);
  }

  @Test
  public void cse_withCelBlock_largeNestedMacro() throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("size([1,2,3]");
    int limit = 8;
    for (int i = 0; i < limit; i++) {
      sb.append(".map(i, [1, 2, 3]");
    }
    for (int i = 0; i < limit; i++) {
      sb.append(")");
    }
    sb.append(")");
    String nestedMapCallExpr = sb.toString(); // size([1,2,3].map(i, [1,2,3].map(i, [1,2,3].map(...
    // Add this large macro call 8 times
    for (int i = 0; i < limit; i++) {
      sb.append("+");
      sb.append(nestedMapCallExpr);
    }
    CelAbstractSyntaxTree ast = CEL.compile(sb.toString()).getAst();

    CelAbstractSyntaxTree optimizedAst =
        newCseOptimizer(
                SubexpressionOptimizerOptions.newBuilder()
                    .populateMacroCalls(true)
                    .enableCelBlock(true)
                    .build())
            .optimize(ast);

    assertThat(CEL_UNPARSER.unparse(optimizedAst))
        .isEqualTo(
            "cel.@block([[1, 2, 3], size(@index0.map(@c0, @index0.map(@c1, @index0.map(@c2,"
                + " @index0.map(@c3, @index0.map(@c4, @index0.map(@c5, @index0.map(@c6,"
                + " @index0.map(@c7, @index0)))))))))], @index1 + @index1 + @index1 + @index1 +"
                + " @index1 + @index1 + @index1 + @index1 + @index1)");
    assertThat(CEL.createProgram(optimizedAst).eval()).isEqualTo(27);
  }

  @Test
  public void cse_applyConstFoldingAfter() throws Exception {
    CelAbstractSyntaxTree ast =
        CEL.compile("size([1+1+1]) + size([1+1+1]) + size([1,1+1+1]) + size([1,1+1+1]) + x")
            .getAst();
    CelOptimizer optimizer =
        CelOptimizerFactory.standardCelOptimizerBuilder(CEL)
            .addAstOptimizers(
                SubexpressionOptimizer.newInstance(
                    SubexpressionOptimizerOptions.newBuilder().build()),
                ConstantFoldingOptimizer.getInstance())
            .build();

    CelAbstractSyntaxTree optimizedAst = optimizer.optimize(ast);

    assertThat(optimizedAst.getExpr())
        .isEqualTo(
            CelExpr.ofCallExpr(
                1L,
                Optional.empty(),
                Operator.ADD.getFunction(),
                ImmutableList.of(
                    CelExpr.ofConstantExpr(2L, CelConstant.ofValue(6L)),
                    CelExpr.ofIdentExpr(3L, "x"))));
    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo("6 + x");
  }

  @Test
  @TestParameters("{enableCelBlock: false, unparsed: 'cel.bind(@r0, size(x), @r0 + @r0)'}")
  @TestParameters("{enableCelBlock: true, unparsed: 'cel.@block([size(x)], @index0 + @index0)'}")
  public void cse_applyConstFoldingAfter_nothingToFold(boolean enableCelBlock, String unparsed)
      throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("size(x) + size(x)").getAst();
    CelOptimizer optimizer =
        CelOptimizerFactory.standardCelOptimizerBuilder(CEL)
            .addAstOptimizers(
                SubexpressionOptimizer.newInstance(
                    SubexpressionOptimizerOptions.newBuilder()
                        .populateMacroCalls(true)
                        .enableCelBlock(enableCelBlock)
                        .build()),
                ConstantFoldingOptimizer.getInstance())
            .build();

    CelAbstractSyntaxTree optimizedAst = optimizer.optimize(ast);

    assertThat(CEL_UNPARSER.unparse(optimizedAst)).isEqualTo(unparsed);
  }

  @Test
  @TestParameters("{enableCelBlock: false}")
  @TestParameters("{enableCelBlock: true}")
  public void iterationLimitReached_throws(boolean enableCelBlock) throws Exception {
    StringBuilder largeExprBuilder = new StringBuilder();
    int iterationLimit = 100;
    for (int i = 0; i < iterationLimit; i++) {
      largeExprBuilder.append("[1,2]");
      if (i < iterationLimit - 1) {
        largeExprBuilder.append("+");
      }
    }
    CelAbstractSyntaxTree ast = CEL.compile(largeExprBuilder.toString()).getAst();

    CelOptimizationException e =
        assertThrows(
            CelOptimizationException.class,
            () ->
                newCseOptimizer(
                        SubexpressionOptimizerOptions.newBuilder()
                            .iterationLimit(iterationLimit)
                            .enableCelBlock(enableCelBlock)
                            .build())
                    .optimize(ast));
    assertThat(e).hasMessageThat().isEqualTo("Optimization failure: Max iteration count reached.");
  }

  @Test
  public void celBlock_astExtensionTagged() throws Exception {
    CelAbstractSyntaxTree ast = CEL.compile("size(x) + size(x)").getAst();
    CelOptimizer optimizer =
        CelOptimizerFactory.standardCelOptimizerBuilder(CEL)
            .addAstOptimizers(
                SubexpressionOptimizer.newInstance(
                    SubexpressionOptimizerOptions.newBuilder()
                        .populateMacroCalls(true)
                        .enableCelBlock(true)
                        .build()),
                ConstantFoldingOptimizer.getInstance())
            .build();

    CelAbstractSyntaxTree optimizedAst = optimizer.optimize(ast);

    assertThat(optimizedAst.getSource().getExtensions())
        .containsExactly(
            Extension.create("cel_block", Version.of(1L, 1L), Component.COMPONENT_RUNTIME));
  }

  private enum BlockTestCase {
    BOOL_LITERAL("cel.block([true, false], index0 || index1)"),
    STRING_CONCAT("cel.block(['a' + 'b', index0 + 'c'], index1 + 'd') == 'abcd'"),

    BLOCK_WITH_EXISTS_TRUE("cel.block([[1, 2, 3], [3, 4, 5].exists(e, e in index0)], index1)"),
    BLOCK_WITH_EXISTS_FALSE("cel.block([[1, 2, 3], ![4, 5].exists(e, e in index0)], index1)"),
    ;

    private final String source;

    BlockTestCase(String source) {
      this.source = source;
    }
  }

  @Test
  public void block_success(@TestParameter BlockTestCase testCase) throws Exception {
    CelAbstractSyntaxTree ast = compileUsingInternalFunctions(testCase.source);

    Object evaluatedResult = CEL_FOR_EVALUATING_BLOCK.createProgram(ast).eval();

    assertThat(evaluatedResult).isNotNull();
  }

  @Test
  @SuppressWarnings("Immutable") // Test only
  public void lazyEval_blockIndexNeverReferenced() throws Exception {
    AtomicInteger invocation = new AtomicInteger();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "get_true_overload",
                    ImmutableList.of(),
                    arg -> {
                      invocation.getAndIncrement();
                      return true;
                    }))
            .build();
    CelAbstractSyntaxTree ast =
        compileUsingInternalFunctions(
            "cel.block([get_true()], has(msg.single_int64) ? index0 : false)");

    boolean result =
        (boolean)
            celRuntime
                .createProgram(ast)
                .eval(ImmutableMap.of("msg", TestAllTypes.getDefaultInstance()));

    assertThat(result).isFalse();
    assertThat(invocation.get()).isEqualTo(0);
  }

  @Test
  @SuppressWarnings("Immutable") // Test only
  public void lazyEval_blockIndexEvaluatedOnlyOnce() throws Exception {
    AtomicInteger invocation = new AtomicInteger();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "get_true_overload",
                    ImmutableList.of(),
                    arg -> {
                      invocation.getAndIncrement();
                      return true;
                    }))
            .build();
    CelAbstractSyntaxTree ast =
        compileUsingInternalFunctions("cel.block([get_true()], index0 && index0 && index0)");

    boolean result = (boolean) celRuntime.createProgram(ast).eval();

    assertThat(result).isTrue();
    assertThat(invocation.get()).isEqualTo(1);
  }

  @Test
  @SuppressWarnings("Immutable") // Test only
  public void lazyEval_multipleBlockIndices_inResultExpr() throws Exception {
    AtomicInteger invocation = new AtomicInteger();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "get_true_overload",
                    ImmutableList.of(),
                    arg -> {
                      invocation.getAndIncrement();
                      return true;
                    }))
            .build();
    CelAbstractSyntaxTree ast =
        compileUsingInternalFunctions(
            "cel.block([get_true(), get_true(), get_true()], index0 && index0 && index1 && index1"
                + " && index2 && index2)");

    boolean result = (boolean) celRuntime.createProgram(ast).eval();

    assertThat(result).isTrue();
    assertThat(invocation.get()).isEqualTo(3);
  }

  @Test
  @SuppressWarnings("Immutable") // Test only
  public void lazyEval_multipleBlockIndices_cascaded() throws Exception {
    AtomicInteger invocation = new AtomicInteger();
    CelRuntime celRuntime =
        CelRuntimeFactory.standardCelRuntimeBuilder()
            .addMessageTypes(TestAllTypes.getDescriptor())
            .addFunctionBindings(
                CelFunctionBinding.from(
                    "get_true_overload",
                    ImmutableList.of(),
                    arg -> {
                      invocation.getAndIncrement();
                      return true;
                    }))
            .build();
    CelAbstractSyntaxTree ast =
        compileUsingInternalFunctions("cel.block([get_true(), index0, index1], index2)");

    boolean result = (boolean) celRuntime.createProgram(ast).eval();

    assertThat(result).isTrue();
    assertThat(invocation.get()).isEqualTo(1);
  }

  @Test
  @TestParameters("{source: 'cel.block([])'}")
  @TestParameters("{source: 'cel.block([1])'}")
  @TestParameters("{source: 'cel.block(1, 2)'}")
  @TestParameters("{source: 'cel.block(1, [1])'}")
  public void block_invalidArguments_throws(String source) {
    CelValidationException e =
        assertThrows(CelValidationException.class, () -> compileUsingInternalFunctions(source));

    assertThat(e).hasMessageThat().contains("found no matching overload for 'cel.block'");
  }

  @Test
  public void blockIndex_invalidArgument_throws() {
    CelValidationException e =
        assertThrows(
            CelValidationException.class,
            () -> compileUsingInternalFunctions("cel.block([1], index)"));

    assertThat(e).hasMessageThat().contains("undeclared reference");
  }

  /**
   * Converts AST containing cel.block related test functions to internal functions (e.g: cel.block
   * -> cel.@block)
   */
  private static CelAbstractSyntaxTree compileUsingInternalFunctions(String expression)
      throws CelValidationException {
    MutableAst mutableAst = MutableAst.newInstance(1000);
    CelAbstractSyntaxTree astToModify = CEL_FOR_EVALUATING_BLOCK.compile(expression).getAst();
    while (true) {
      CelExpr celExpr =
          CelNavigableAst.fromAst(astToModify)
              .getRoot()
              .allNodes()
              .filter(node -> node.getKind().equals(Kind.CALL))
              .map(CelNavigableExpr::expr)
              .filter(expr -> expr.call().function().equals("cel.block"))
              .findAny()
              .orElse(null);
      if (celExpr == null) {
        break;
      }
      astToModify =
          mutableAst.replaceSubtree(
              astToModify,
              celExpr.toBuilder()
                  .setCall(celExpr.call().toBuilder().setFunction("cel.@block").build())
                  .build(),
              celExpr.id());
    }

    while (true) {
      CelExpr celExpr =
          CelNavigableAst.fromAst(astToModify)
              .getRoot()
              .allNodes()
              .filter(node -> node.getKind().equals(Kind.IDENT))
              .map(CelNavigableExpr::expr)
              .filter(expr -> expr.ident().name().startsWith("index"))
              .findAny()
              .orElse(null);
      if (celExpr == null) {
        break;
      }
      String internalIdentName = "@" + celExpr.ident().name();
      astToModify =
          mutableAst.replaceSubtree(
              astToModify,
              celExpr.toBuilder()
                  .setIdent(celExpr.ident().toBuilder().setName(internalIdentName).build())
                  .build(),
              celExpr.id());
    }

    return CEL_FOR_EVALUATING_BLOCK.check(astToModify).getAst();
  }
}
