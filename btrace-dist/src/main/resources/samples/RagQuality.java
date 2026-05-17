import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Duration;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnEvent;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.OnTimer;
import io.btrace.rag.RagQualityService;

import static io.btrace.core.BTraceUtils.*;

/**
 * Traces RAG pipeline performance: vector DB query latency, similarity
 * scores, and empty retrieval rates. Targets Pinecone Java client.
 *
 * <p>Attach to a JVM running a RAG pipeline:
 * <pre>
 * btrace &lt;pid&gt; RagQuality.java
 * </pre>
 */
@BTrace
public class RagQuality {

  @Injected
  private static RagQualityService rag;

  // ==================== Pinecone ====================

  @OnMethod(
      clazz = "/io\\.pinecone\\..*/",
      method = "query",
      location = @Location(Kind.RETURN))
  public static void onPineconeQuery(@Duration long dur) {
    rag.recordQuery("pinecone", dur);
  }

  // ==================== Milvus ====================

  @OnMethod(
      clazz = "/io\\.milvus\\.client\\..*/",
      method = "search",
      location = @Location(Kind.RETURN))
  public static void onMilvusSearch(@Duration long dur) {
    rag.recordQuery("milvus", dur);
  }

  // ==================== Weaviate ====================

  @OnMethod(
      clazz = "/io\\.weaviate\\.client\\..*/",
      method = "/get|search/",
      location = @Location(Kind.RETURN))
  public static void onWeaviateQuery(@Duration long dur) {
    rag.recordQuery("weaviate", dur);
  }

  // ==================== Chroma ====================

  @OnMethod(
      clazz = "/tech\\.amikos\\.chromadb\\..*/",
      method = "query",
      location = @Location(Kind.RETURN))
  public static void onChromaQuery(@Duration long dur) {
    rag.recordQuery("chroma", dur);
  }

  // ==================== Periodic summary ====================

  @OnTimer(30000)
  public static void periodicSummary() {
    println(rag.getSummary());
  }

  @OnEvent("summary")
  public static void onDemandSummary() {
    println(rag.getSummary());
  }
}
