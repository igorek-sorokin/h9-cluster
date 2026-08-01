package net.adminrunet.h9cluster.skins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SkinRegistryTest {
    @Test
    public void builtInSkinsRemainRegisteredInStableOrder() {
        SkinRegistry.Definition[] definitions =
                SkinRegistry.getDefinitions();

        assertEquals(4, definitions.length);
        assertEquals(SkinRegistry.FACTORY, definitions[0].id);
        assertEquals(SkinRegistry.CLASSIC, definitions[1].id);
        assertEquals(SkinRegistry.SPORT, definitions[2].id);
        assertEquals(SkinRegistry.HORIZON, definitions[3].id);
    }

    @Test
    public void factoryOptionDoesNotOverlayCluster() {
        assertFalse(SkinRegistry.overlaysCluster(SkinRegistry.FACTORY));
        assertTrue(SkinRegistry.overlaysCluster(SkinRegistry.CLASSIC));
        assertFalse(SkinRegistry.getDefinition(SkinRegistry.FACTORY)
                .overlaysCluster());
    }

    @Test
    public void builtInSkinsDoNotExposeUnrelatedSettings() {
        for (SkinRegistry.Definition definition
                : SkinRegistry.getDefinitions()) {
            assertFalse(definition.hasSettings());
            assertTrue(definition.getDefaultSettings().isEmpty());
            assertTrue(definition.normalizeSettings(
                    SkinSettings.builder()
                            .putBoolean("foreign.option", true)
                            .build())
                    .isEmpty());
        }
    }

    @Test
    public void unknownSkinStillNormalizesToClassic() {
        assertEquals(
                SkinRegistry.CLASSIC,
                SkinRegistry.getDefinition("unknown").id);
    }
}
