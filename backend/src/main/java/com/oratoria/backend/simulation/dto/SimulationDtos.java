package com.oratoria.backend.simulation.dto;
import com.oratoria.backend.simulation.entity.*;
import com.oratoria.backend.training.TrainingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
public final class SimulationDtos {
 private SimulationDtos(){}
 public record ScenarioResponse(String id,String title,String description,TrainingType trainingType,String aiRole,List<SimulationDifficulty> difficulties){}
 public record CreateRequest(@NotBlank String scenarioId,@NotNull SimulationDifficulty difficulty){}
 public record TurnResponse(UUID id,int sequenceNumber,SimulationSpeaker speaker,SimulationTurnStatus status,String text,boolean hasAudio,Integer audioDurationSeconds,String errorCode,String errorMessage,Instant createdAt){}
 public record SessionResponse(UUID id,String scenarioId,String scenarioTitle,TrainingType trainingType,SimulationDifficulty difficulty,SimulationStatus status,int currentTurn,int maxTurns,List<TurnResponse> turns,FeedbackResponse feedback,String errorCode,String errorMessage,Instant createdAt,Instant completedAt){}
 public record FeedbackResponse(boolean sufficientEvidence,String insufficientEvidenceReason,Double overallScore,String summary,List<String> strengths,List<String> areasForImprovement,List<String> recommendations,Map<String,Double> criteriaScores){}
 public record AiHistoryMessage(String speaker,String text){}
 public record AiReplyRequest(String simulationId,String scenarioId,String trainingType,String difficulty,String aiRole,String context,String objective,List<String> rules,List<AiHistoryMessage> history,String userTranscript,int turnNumber,int maxTurns){}
 public record AiReplyResponse(String replyText,String intent,boolean shouldEnd,String focus,String provider,String model){}
 public record AiFeedbackRequest(String simulationId,String scenarioId,String trainingType,String difficulty,String context,String objective,List<AiHistoryMessage> history){}
 public record AiFeedbackResponse(boolean sufficientEvidence,String insufficientEvidenceReason,Double overallScore,String summary,List<String> strengths,List<String> areasForImprovement,List<String> recommendations,Map<String,Double> criteriaScores,String provider,String model){}
}
