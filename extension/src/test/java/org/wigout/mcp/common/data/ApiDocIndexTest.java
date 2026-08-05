package org.wigout.mcp.common.data;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ApiDocIndex — loads the real generated bitwig-api-index.json
 * from the classpath, so these tests also pin the generated data.
 */
class ApiDocIndexTest {

    @Test
    void testLoadsAndListsTypes() {
        ApiDocIndex index = ApiDocIndex.load();
        assertTrue(index.typeNames().size() >= 250);
        assertTrue(index.typeNames().contains("Transport"));
        assertTrue(index.typeNames().contains("SettableRangedValue"));
    }

    @Test
    void testMethodLookupOnDeclaringType() {
        Optional<ApiDocIndex.MethodDoc> doc = ApiDocIndex.load().forMethod("Subscribable", "subscribe");
        assertTrue(doc.isPresent());
        assertFalse(doc.get().deprecated());
    }

    @Test
    void testMethodLookupWalksExtendsChain() {
        // getItemAt is declared on Bank; TrackBank extends (eventually) Bank.
        Optional<ApiDocIndex.MethodDoc> doc = ApiDocIndex.load().forMethod("TrackBank", "getItemAt");
        assertTrue(doc.isPresent(), "getItemAt should be found via the extends chain");
    }

    @Test
    void testDeprecatedCarriesReplacement() {
        Optional<ApiDocIndex.MethodDoc> doc = ApiDocIndex.load().forMethod("TrackBank", "getTrack");
        assertTrue(doc.isPresent());
        assertTrue(doc.get().deprecated());
        assertNotNull(doc.get().replacement());
        assertTrue(doc.get().replacement().contains("getItemAt"));
    }

    @Test
    void testUnknownLookupsAreEmpty() {
        assertTrue(ApiDocIndex.load().forMethod("Nope", "nope").isEmpty());
        assertTrue(ApiDocIndex.load().typeDoc("Nope").isEmpty());
    }
}
