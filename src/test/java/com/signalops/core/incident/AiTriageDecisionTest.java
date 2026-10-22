
package com.signalops.core.incident;

import com.signalops.core.incident.dto.AiTriageDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiTriageDecisionTest {

    @Test
    void acceptsValidDecision() {
        AiTriageDecision decision = new AiTriageDecision(
                "Checkout latency is elevated",
                "HIGH",
                "Latency increased significantly"
        );

        assertEquals("HIGH", decision.recommendedSeverity());
    }

    @Test
    void rejectsUnknownSeverity() {
        assertThrows(IllegalArgumentException.class, () ->
                new AiTriageDecision(
                        "Checkout latency is elevated",
                        "URGENT",
                        "Latency increased significantly"
                )
        );
    }

    @Test
    void rejectsBlankSummary() {
        assertThrows(IllegalArgumentException.class, () ->
                new AiTriageDecision(
                        " ",
                        "HIGH",
                        "Latency increased significantly"
                )
        );
    }

    @Test
    void rejectsBlankRationale() {
        assertThrows(IllegalArgumentException.class, () ->
                new AiTriageDecision(
                        "Checkout latency is elevated",
                        "HIGH",
                        ""
                )
        );
    }
}
