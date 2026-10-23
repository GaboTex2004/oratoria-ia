package com.oratoria.backend.simulation.client;
import static com.oratoria.backend.simulation.client.AiSimulationClient.AiSimulationClientException;
import com.oratoria.backend.simulation.dto.SimulationDtos.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;

@Component
public class HttpAiSimulationClient implements AiSimulationClient {
 private final HttpClient http; private final ObjectMapper mapper; private final String baseUrl; private final String key; private final Duration timeout;
 public HttpAiSimulationClient(ObjectMapper mapper,@Value("${app.ai-service.url:http://localhost:8000}") String url,@Value("${app.ai-service.internal-api-key:dev-internal-oratoria-key-change-in-prod}") String key,@Value("${app.ai-service.timeout-seconds:65}") long seconds){this.mapper=mapper;baseUrl=(url.endsWith("/")?url.substring(0,url.length()-1):url)+"/api/v1/simulation";this.key=key;timeout=Duration.ofSeconds(Math.max(5,seconds));http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();}
 public AiReplyResponse generateReply(AiReplyRequest request){return post("/reply",request,AiReplyResponse.class);}
 public AiFeedbackResponse generateFeedback(AiFeedbackRequest request){return post("/feedback",request,AiFeedbackResponse.class);}
 private <T>T post(String path,Object payload,Class<T> type){String requestId=UUID.randomUUID().toString();try{
  HttpRequest req=HttpRequest.newBuilder(URI.create(baseUrl+path)).timeout(timeout).header("X-Internal-Api-Key",key).header("X-Request-Id",requestId).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
  HttpResponse<String> res=http.send(req,HttpResponse.BodyHandlers.ofString());
  if(res.statusCode()>=200&&res.statusCode()<300)return mapper.readValue(res.body(),type);
  throw mapError(res.statusCode(),res.body());
 }catch(HttpTimeoutException e){throw new AiSimulationClientException("AI_SERVICE_TIMEOUT","El servicio de simulación agotó el tiempo de espera.",true);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AiSimulationClientException("AI_SERVICE_INTERRUPTED","La simulación fue interrumpida.",true);}catch(AiSimulationClientException e){throw e;}catch(IOException e){throw new AiSimulationClientException("AI_SERVICE_UNAVAILABLE","El servicio de IA no está disponible.",true);}catch(Exception e){throw new AiSimulationClientException("INVALID_PROVIDER_RESPONSE","El servicio de simulación devolvió una respuesta inválida.",true);}}
 private AiSimulationClientException mapError(int status,String body){try{JsonNode n=mapper.readTree(body);return new AiSimulationClientException(n.hasNonNull("code")?n.get("code").asText():"AI_SERVICE_ERROR",n.hasNonNull("message")?n.get("message").asText():"Error del servicio de simulación.",n.hasNonNull("retryable")?n.get("retryable").asBoolean():status>=500);}catch(Exception e){return new AiSimulationClientException("AI_SERVICE_HTTP_"+status,"El servicio de simulación rechazó la solicitud.",status>=500||status==429);}}
}
