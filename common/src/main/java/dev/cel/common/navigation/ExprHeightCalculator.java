package dev.cel.common.navigation;

import static java.lang.Math.exp;
import static java.lang.Math.max;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import dev.cel.common.ast.CelExpr.CelComprehension;
import dev.cel.common.ast.CelExpr.CelCreateList;
import dev.cel.common.ast.CelExpr.CelCreateMap;
import dev.cel.common.ast.CelExpr.CelCreateStruct;
import dev.cel.common.ast.CelExpr.CelSelect;

/**
 * Package-private class to assist computing the height of expression nodes.
 */
final class ExprHeightCalculator {
  private final ImmutableMap.Builder<Long, Integer> idToHeightBuilder;
  private final ImmutableMap<Long, Integer> idToHeight;

  ExprHeightCalculator(CelExpr celExpr) {
    this.idToHeightBuilder = ImmutableMap.builder();
    visit(celExpr);
    this.idToHeight = idToHeightBuilder.build();
  }

  int getHeight(Long exprId) {
    if (!idToHeight.containsKey(exprId)) {
      throw new IllegalStateException("Height not found for expression id: " + exprId);
    }

    return idToHeight.get(exprId);
  }

  private int visit(CelExpr celExpr) {
    int height = 1;
    switch (celExpr.exprKind().getKind()) {
      case CALL:
        height += visit(celExpr.call());
        break;
      case CREATE_LIST:
        height += visit(celExpr.createList());
        break;
      case SELECT:
        height += visit(celExpr.select());
        break;
      case CREATE_STRUCT:
        height += visitStruct(celExpr.createStruct());
        break;
      case CREATE_MAP:
        height += visitMap(celExpr.createMap());
        break;
      case COMPREHENSION:
        height += visit(celExpr.comprehension());
        break;
      default:
        // This is a leaf node
        height = 0;
        break;
    }

    idToHeightBuilder.put(celExpr.id(), height);
    return height;
  }

  private int visit(CelCall call) {
    int targetHeight = 0;
    if (call.target().isPresent()) {
      targetHeight = visit(call.target().get());
    }

    int argumentHeight = visitExprList(call.args());
    return max(targetHeight, argumentHeight);
  }

  private int visit(CelCreateList createList) {
    return visitExprList(createList.elements());
  }

  private int visit(CelSelect selectExpr) {
    return visit(selectExpr.operand());
  }

  private int visit(CelComprehension comprehension) {
    int maxHeight = 0;
    maxHeight = max(visit(comprehension.iterRange()), maxHeight);
    maxHeight = max(visit(comprehension.accuInit()), maxHeight);
    maxHeight =
        max(visit(comprehension.loopCondition()), maxHeight);
    maxHeight = max(visit(comprehension.loopStep()), maxHeight);
    maxHeight = max(visit(comprehension.result()), maxHeight);

    return maxHeight;
  }

  private int visitStruct(CelCreateStruct struct) {
    int maxHeight = 0;
    for (CelCreateStruct.Entry entry : struct.entries()) {
      maxHeight = max(visit(entry.value()), maxHeight);
    }
    return maxHeight;
  }

  private int visitMap(CelCreateMap map) {
    int maxHeight = 0;
    for (CelCreateMap.Entry entry : map.entries()) {
      maxHeight = max(visit(entry.key()), maxHeight);
      maxHeight = max(visit(entry.value()), maxHeight);
    }
    return maxHeight;
  }

  private int visitExprList(
      ImmutableList<CelExpr> createListExpr) {
    int maxHeight = 0;
    for (CelExpr expr : createListExpr) {
      maxHeight = max(visit(expr), maxHeight);
    }
    return maxHeight;
  }
}
