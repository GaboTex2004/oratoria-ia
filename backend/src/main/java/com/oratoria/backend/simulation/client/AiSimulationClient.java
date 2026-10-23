package com.oratoria.backend.simulation.client;
import com.oratoria.backend.simulation.dto.SimulationDtos.*;
public interface AiSimulationClient {
 AiReplyResponse generateReply(AiReplyRequest request);
 AiFeedbackResponse generateFeedback(AiFeedbackRequest request);
 class AiSimulationClientException extends RuntimeException {
  private final String errorCode; private final boolean retryable;
  public AiSimulationClientException(String code,String message,boolean retryable){super(message);errorCode=code;this.retryable=retryable;}
  public String getErrorCode(){return errorCode;} public boolean isRetryable(){return retryable;}
 }
}
