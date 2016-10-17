package javassist.offload.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({Runner.class, javassist.offload.reify.Tests.class})
public class AllTests {}
