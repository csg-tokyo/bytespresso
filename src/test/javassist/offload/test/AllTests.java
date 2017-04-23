package javassist.offload.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * To run, ./javassit.jar must exist.
 * The JVM option -Djdk.internal.lambda.dumpProxyClasses=./bin must be given,
 * where ./bin is a directory included in CLASSPATH.
 */

@RunWith(Suite.class)
@SuiteClasses({Runner.class, javassist.offload.reify.Tests.class})
public class AllTests {}
