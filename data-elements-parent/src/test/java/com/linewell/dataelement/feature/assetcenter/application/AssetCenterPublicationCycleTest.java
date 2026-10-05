package com.linewell.dataelement.feature.assetcenter.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssetCenterPublicationCycleTest {

    @Test
    void checksPrefetchedDirectoryGraphWithoutDatabaseLookups() {
        AssetCenterPublicationService service = new AssetCenterPublicationService(null, null, null, null);
        service.checkCycle("root", "child", Map.of("child", List.of("leaf")), new HashSet<>(), 0);

        assertThatThrownBy(() -> service.checkCycle("root", "child",
                Map.of("child", List.of("leaf"), "leaf", List.of("root")), new HashSet<>(), 0))
                .isInstanceOf(AssetCenterException.class)
                .satisfies(error -> assertThat(((AssetCenterException) error).status()).isEqualTo(400));
    }
}
