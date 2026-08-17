import com.ayush.documentqa.exception.ModelProviderException;
import com.ayush.documentqa.observability.MetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Wraps Spring AI ChatClient for both blocking and streaming LLM calls.
 * Provider-agnostic — the actual model (OpenAI, Anthropic, etc.) is determined
 * by Spring AI auto-configuration based on which starter is on the classpath.
 *
 * Resilience (retry with exponential backoff, circuit breaker) is applied via Resilience4j annotations
 * on this service's public methods.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final ChatClient chatClient;
    private final MetricsService metricsService;

    public AiService(ChatClient chatClient, MetricsService metricsService) {
        this.chatClient = chatClient;
        this.metricsService = metricsService;
    }

    /**
     * Blocking LLM call for synchronous chat endpoint.
     * Protected by Resilience4j retry and circuit breaker.
     */
    @Retry(name = "aiService")
    @CircuitBreaker(name = "aiService")
    public String call(String systemPrompt, String userMessage) {
        Timer.Sample sample = metricsService.startTimer();
        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();
            metricsService.recordModelLatency(sample);
            return response;
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            throw new ModelProviderException("Failed to get response from AI model", e);
        }
    }

    /**
     * Streaming LLM call for SSE endpoint.
     * Returns a Flux that emits text tokens as they arrive from the model.
     */
    public Flux<String> stream(String systemPrompt, String userMessage) {
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .stream()
                    .content();
        } catch (Exception e) {
            log.error("LLM stream initiation failed: {}", e.getMessage());
            throw new ModelProviderException("Failed to initiate streaming from AI model", e);
        }
    }
}
