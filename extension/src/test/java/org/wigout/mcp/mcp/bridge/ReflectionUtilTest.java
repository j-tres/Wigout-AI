package org.wigout.mcp.mcp.bridge;

import com.bitwig.extension.controller.api.Parameter;
import com.bitwig.extension.controller.api.Track;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live-gate evidence: a real Bitwig implementation class implements internal,
 * obfuscated interfaces (e.g. BDZ, JWa, XU2) beyond the public
 * com.bitwig.extension API, and ReflectionUtil.collectInterfaces used to
 * accept all of them, leaking members like getAssertionChecker/
 * disposeOnEventThread into bw_describe/PathResolver. Simulated here with a
 * Mockito "extra interface" that isn't in the com.bitwig.extension package.
 *
 * Live-gate fix pass 3: a SEPARATE, more damaging shape of the same
 * underlying issue — Parameter-like proxies' real implementation classes
 * implement ONLY an internal interface that itself EXTENDS the public API
 * interface (e.g. an internal iface extends Parameter). Pruning the
 * traversal AT non-public interfaces (the pass-1 fix) lost Parameter
 * entirely in that shape, since it's reachable only via the internal
 * interface's own superinterfaces. Simulated here with a declared interface
 * that extends Parameter directly.
 */
class ReflectionUtilTest {

    /**
     * Stand-in for an internal, non-API interface a real impl class might
     * implement. Must be public: Mockito/ByteBuddy generates the mock class
     * in Track's OWN package (com.bitwig.extension.controller.api) to reach
     * its package-private members, so a package-private interface here
     * (org.wigout.mcp.mcp.bridge) would be inaccessible to it.
     */
    public interface InternalObfuscatedInterface {
        Object getAssertionChecker();
        void disposeOnEventThread();
    }

    /**
     * Stand-in for the real shape observed live on Parameter-like proxies:
     * an internal interface that EXTENDS a public API interface directly,
     * plus its own non-API member that must never be exposed.
     */
    public interface InternalParameterProxy extends Parameter {
        Object getInternalOnlyThing();
    }

    @Test
    void testNonBitwigInterfaceMembersAreNotExposed() {
        Track target = Mockito.mock(Track.class,
            Mockito.withSettings().extraInterfaces(InternalObfuscatedInterface.class));

        List<Method> methods = ReflectionUtil.publicApiMethods(target);

        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("exists")),
            "expected a real Track member (exists) to still be exposed");
        assertTrue(methods.stream().noneMatch(m -> m.getName().equals("getAssertionChecker")),
            "internal interface member getAssertionChecker must not be exposed");
        assertTrue(methods.stream().noneMatch(m -> m.getName().equals("disposeOnEventThread")),
            "internal interface member disposeOnEventThread must not be exposed");
    }

    @Test
    void testFindMethodDoesNotResolveNonBitwigInterfaceMembers() {
        Track target = Mockito.mock(Track.class,
            Mockito.withSettings().extraInterfaces(InternalObfuscatedInterface.class));

        assertTrue(ReflectionUtil.findMethod(target, "getAssertionChecker", 0).isEmpty());
        assertTrue(ReflectionUtil.findMethodsNamed(target, "disposeOnEventThread").isEmpty());
        assertTrue(ReflectionUtil.findMethod(target, "exists", 0).isPresent());
    }

    @Test
    void testFindRawMethodsNamedAlsoExcludesNonBitwigInterfaceMembers() {
        // findRawMethodsNamed skips the BridgeExclusions filter but must still
        // stay within the public com.bitwig.extension API surface.
        Track target = Mockito.mock(Track.class,
            Mockito.withSettings().extraInterfaces(InternalObfuscatedInterface.class));

        assertTrue(ReflectionUtil.findRawMethodsNamed(target, "getAssertionChecker").isEmpty());
    }

    @Test
    void testPublicApiMembersReachableOnlyThroughInternalInterfaceAreExposed() {
        // Target implements ONLY InternalParameterProxy — Parameter's own
        // members (value, displayedValue, name, exists, modulatedValue, ...)
        // are reachable exclusively via that internal interface's own
        // superinterface, not directly on the mock's implemented-interfaces
        // list. Mirrors the real shape observed live on transport.tempo().
        InternalParameterProxy target = Mockito.mock(InternalParameterProxy.class);

        List<Method> methods = ReflectionUtil.publicApiMethods(target);

        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("value")),
            "expected Parameter.value() to be exposed despite being reachable only via the internal interface");
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("displayedValue")));
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("name")));
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("exists")));
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("modulatedValue")));
        assertTrue(methods.stream().noneMatch(m -> m.getName().equals("getInternalOnlyThing")),
            "the internal interface's OWN member must never be exposed");
    }

    @Test
    void testFindMethodResolvesPublicApiMemberThroughInternalInterface() {
        InternalParameterProxy target = Mockito.mock(InternalParameterProxy.class);

        assertTrue(ReflectionUtil.findMethod(target, "value", 0).isPresent());
        assertTrue(ReflectionUtil.findMethod(target, "displayedValue", 0).isPresent());
        assertTrue(ReflectionUtil.findMethod(target, "getInternalOnlyThing", 0).isEmpty());
    }

    @Test
    void testApiTypeNameReachesParameterThroughInternalInterface() {
        InternalParameterProxy target = Mockito.mock(InternalParameterProxy.class);

        assertEquals("Parameter", ReflectionUtil.apiTypeName(target));
    }

    @Test
    void testPublicApiMethodsIsMemoizedPerClassAndDoesNotLeakAcrossClasses() {
        // Mockito reuses ONE generated class for repeat mock(Track.class)
        // calls (ByteBuddy's TypeCache) — two independent Track mocks here
        // are provably the same runtime class, exercising the cache's
        // "same class, second call" path for real, not just in theory.
        Track trackA = Mockito.mock(Track.class);
        Track trackB = Mockito.mock(Track.class);
        assertSame(trackA.getClass(), trackB.getClass(),
            "test premise: Mockito must reuse the generated mock class across repeat mock(Track.class) calls");

        List<Method> methodsA = ReflectionUtil.publicApiMethods(trackA);
        List<Method> methodsB = ReflectionUtil.publicApiMethods(trackB);

        assertEquals(methodsA, methodsB, "same runtime class must yield equal (cached) method lists");
        assertSame(methodsA, methodsB, "expected the cached list instance to be reused for the same class");

        // A genuinely different runtime class must not reuse Track's cached
        // list — a leak here would either hide Parameter's members or leak
        // Track's members onto InternalParameterProxy (or vice versa).
        InternalParameterProxy proxy = Mockito.mock(InternalParameterProxy.class);
        List<Method> proxyMethods = ReflectionUtil.publicApiMethods(proxy);

        assertNotEquals(methodsA, proxyMethods, "a different runtime class must not reuse Track's cached method list");
        assertTrue(proxyMethods.stream().anyMatch(m -> m.getName().equals("value")),
            "Parameter's own members must appear for the proxy's class");
        assertTrue(methodsA.stream().noneMatch(m -> m.getName().equals("value")),
            "Track's cached list must not have picked up Parameter's members");
    }
}
