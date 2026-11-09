package com.oratoria.backend.body;

import com.oratoria.backend.session.entity.TrainingSession;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "body_analyses")
public class BodyAnalysis {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private TrainingSession session;
    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    protected BodyAnalysis() {}
    public BodyAnalysis(TrainingSession session, String json) { this.session = session; this.resultJson = json; }
    public String getResultJson() { return resultJson; }
    public void updateResult(String json) { this.resultJson = json; this.createdAt = Instant.now(); }
}
