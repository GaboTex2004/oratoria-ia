package com.oratoria.backend.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StyleFeedbackCalculatorTests {
    final CommunicationStyleCatalog catalog = new CommunicationStyleCatalog();

    @Test void scoreWeighsCriteriaByStyle() {
        var feedback = StyleFeedbackCalculator.calculate(catalog.require("PERSUASIVO"),
                Map.of("ARGUMENTATION", 90.0, "COHERENCE", 80.0, "CLARITY", 70.0, "ORGANIZATION", 60.0), 140.0, 0, 60);
        assertThat(feedback.score()).isEqualTo(80.0);
        assertThat(feedback.observations()).anyMatch(text -> text.contains("Argumentación"));
    }

    @Test void slowPaceForTheStyleProducesATip() {
        var feedback = StyleFeedbackCalculator.calculate(catalog.require("DINAMICO"),
                Map.of("ARGUMENTATION", 70.0, "COHERENCE", 70.0, "CLARITY", 70.0, "ORGANIZATION", 70.0), 100.0, 0, 60);
        assertThat(feedback.tips()).anyMatch(text -> text.contains("100") && text.contains("140"));
    }

    @Test void frequentFillersProduceATip() {
        var feedback = StyleFeedbackCalculator.calculate(catalog.require("ACADEMICO"),
                Map.of("ARGUMENTATION", 70.0, "COHERENCE", 70.0, "CLARITY", 70.0, "ORGANIZATION", 70.0), 120.0, 12, 60);
        assertThat(feedback.tips()).anyMatch(text -> text.contains("muletillas"));
    }

    @Test void missingCriteriaAreIgnoredInTheWeightedScore() {
        var feedback = StyleFeedbackCalculator.calculate(catalog.require("NARRATIVO"), Map.of("ORGANIZATION", 80.0), null, null, null);
        assertThat(feedback.score()).isEqualTo(80.0);
    }

    @Test void unknownStyleIsRejected() {
        assertThat(catalog.find("NO_EXISTE")).isEmpty();
    }
}
