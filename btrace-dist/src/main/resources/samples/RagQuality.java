import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.extensions.Injected;
import org.openjdk.btrace.rag.RagQualityService;

import static org.openjdk.btrace.core.BTraceUtils.*;

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
