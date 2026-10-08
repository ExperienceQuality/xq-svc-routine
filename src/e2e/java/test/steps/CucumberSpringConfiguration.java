package test.steps;

import com.xq.jvmtestkit.cucumber.spring.XqCucumberSpringConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;

/** Uses the JVM Test Kit's scenario-scoped XqCucumberContext. */
@CucumberContextConfiguration
public final class CucumberSpringConfiguration extends XqCucumberSpringConfiguration {}
