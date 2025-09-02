package context;

public class TestContextManager {

    private static final ThreadLocal<TestContext> tlContext =
            ThreadLocal.withInitial(TestContext::new);

    public static TestContext getContext() {
        return tlContext.get();
    }

    public static void removeContext() {
        tlContext.remove();
    }
}
