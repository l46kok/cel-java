package dev.cel.runtime;

import dev.cel.runtime.CelStandardFunctions.StandardFunction;
import dev.cel.runtime.standard.CelStandardOverload;

public class DebugStandardFunctions {
  public static void main(String[] args) {
    System.out.println("Printing StandardFunction names and overloads:");
    for (StandardFunction func : StandardFunction.values()) {
        System.out.println("Function: " + func.name() + ", FunctionName: " + func.getFunctionName());
        for (CelStandardOverload overload : func.getOverloads()) {
             System.out.println("  Overload: " + overload);
        }
    }
  }
}
