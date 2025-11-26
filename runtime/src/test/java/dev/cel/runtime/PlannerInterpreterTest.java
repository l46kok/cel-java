package dev.cel.runtime;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.extensions.CelExtensions;
import dev.cel.testing.BaseInterpreterTest;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class PlannerInterpreterTest extends BaseInterpreterTest {

  public PlannerInterpreterTest() {
    super(
            CelRuntimeFactory.plannerCelRuntimeBuilder()
                    .addLibraries(CelExtensions.optional())
                    .build()
    );
  }
}
