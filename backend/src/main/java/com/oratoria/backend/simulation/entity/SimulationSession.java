package com.oratoria.backend.simulation.entity;

import com.oratoria.backend.training.TrainingType;
import com.oratoria.backend.user.entity.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "simulation_sessions")
public class SimulationSession {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private UserAccount user;
    @Column(name = "scenario_id", nullable = false, length = 80) private String scenarioId;
    @Enumerated(EnumType.STRING) @Column(name = "training_type", nullable = false, length = 40) private TrainingType trainingType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SimulationDifficulty difficulty;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private SimulationStatus status = SimulationStatus.ACTIVE;
    @Column(name = "current_turn", nullable = false) private int currentTurn;
    @Column(name = "max_turns", nullable = false) private int maxTurns;
    @Column(name = "final_feedback_json", columnDefinition = "TEXT") private String finalFeedbackJson;
    @Column(name = "overall_score") private Double overallScore;
    @Column(name = "sufficient_evidence") private Boolean sufficientEvidence;
    @Column(name = "error_code", length = 80) private String errorCode;
    @Column(name = "error_message", length = 500) private String errorMessage;
    @Column(nullable = false, name = "created_at", updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    @Column(name = "completed_at") private Instant completedAt;

    protected SimulationSession() {}
    public SimulationSession(UserAccount user, String scenarioId, TrainingType trainingType, SimulationDifficulty difficulty, int maxTurns) {
        this.user=user; this.scenarioId=scenarioId; this.trainingType=trainingType; this.difficulty=difficulty; this.maxTurns=maxTurns;
    }
    @PreUpdate void touch(){ updatedAt=Instant.now(); }
    public void beginProcessing(){ status=SimulationStatus.PROCESSING; errorCode=null; errorMessage=null; }
    public void completeTurn(boolean shouldEnd){ currentTurn++; status=(shouldEnd || currentTurn>=maxTurns)?SimulationStatus.READY_TO_FINISH:SimulationStatus.ACTIVE; }
    public void restoreAfterFailure(String code,String message){ status=currentTurn>=maxTurns?SimulationStatus.READY_TO_FINISH:SimulationStatus.ACTIVE; errorCode=code; errorMessage=message; }
    public void complete(String feedbackJson, Boolean sufficient, Double score){ finalFeedbackJson=feedbackJson; sufficientEvidence=sufficient; overallScore=score; status=SimulationStatus.COMPLETED; completedAt=Instant.now(); errorCode=null; errorMessage=null; }
    public UUID getId(){return id;} public UserAccount getUser(){return user;} public String getScenarioId(){return scenarioId;} public TrainingType getTrainingType(){return trainingType;} public SimulationDifficulty getDifficulty(){return difficulty;} public SimulationStatus getStatus(){return status;} public int getCurrentTurn(){return currentTurn;} public int getMaxTurns(){return maxTurns;} public String getFinalFeedbackJson(){return finalFeedbackJson;} public Double getOverallScore(){return overallScore;} public Boolean getSufficientEvidence(){return sufficientEvidence;} public String getErrorCode(){return errorCode;} public String getErrorMessage(){return errorMessage;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public Instant getCompletedAt(){return completedAt;}
}
