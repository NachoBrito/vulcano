/**
 * Telemetry module to poll metrics from VulcanoDB
 */

const POLL_INTERVAL = 2000; // 2 seconds
const METRICS_URL = "http://localhost:9999";

class TelemetryPoller {
  constructor() {
    this.intervalId = null;
    this.state = {
      docInsert: {
        count: 0,
        latency: 0,
      },
      vectorSearch: {
        count: 0,
        latency: 0,
      },
    };
  }

  async fetchMetrics() {
    try {
      const response = await fetch(METRICS_URL);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      return await response.text();
    } catch (error) {
      console.error(`Failed to fetch metrics from ${METRICS_URL}:`, error);
      return null;
    }
  }

  /**
   * Simple Prometheus text format parser.
   * Extracts values for specific metrics, prioritizing P99 for summaries.
   */
  parsePrometheus(text) {
    const metrics = {};
    const lines = text.split("\n");

    for (const line of lines) {
      if (!line || line.startsWith("#")) continue;

      // Match metric name, optional labels, and value
      // Example: vulcanodb_document_insert_latency_seconds{quantile="0.99"} 3.08281344
      // Example: vulcanodb_document_insert_queue 10.0
      const match = line.match(/^([a-zA-Z_][a-zA-Z0-9_]*)({[^}]+})?\s+(.+)$/);
      if (match) {
        const name = match[1];
        const labelsStr = match[2] || "";
        const value = parseFloat(match[3]);

        if (!metrics[name]) {
          metrics[name] = { values: [], labels: [] };
        }
        metrics[name].values.push(value);
        metrics[name].labels.push(labelsStr);
      }
    }
    return metrics;
  }

  getMetricValue(metrics, name, labelFilter = null) {
    const metric = metrics[name];
    if (!metric) return 0;

    if (labelFilter) {
      const index = metric.labels.findIndex((l) => l.includes(labelFilter));
      return index !== -1 ? metric.values[index] : 0;
    }

    return metric.values[0] || 0;
  }

  async poll() {
    const rawData = await this.fetchMetrics();
    if (!rawData) return;

    const metrics = this.parsePrometheus(rawData);

    // 1. Doc Inserts Queue & Latency (P99)
    const insertQueue = this.getMetricValue(metrics, "vulcanodb_document_insert_queue");
    const insertLatencyP99 = this.getMetricValue(
      metrics,
      "vulcanodb_document_insert_latency_seconds",
      'quantile="0.99"'
    );

    window.dispatchEvent(
      new CustomEvent("vulcano:telemetry", {
        detail: {
          id: "doc-insert",
          type: "chart",
          data: {
            count: 0, // Only latency
            latency: insertLatencyP99 * 1000,
          },
        },
      })
    );

    window.dispatchEvent(
      new CustomEvent("vulcano:telemetry", {
        detail: {
          id: "insert-queue",
          type: "indicator",
          value: insertQueue,
        },
      })
    );

    // 2. Vector Search Count & Latency (P99)
    const searchCount = this.getMetricValue(metrics, "vulcanodb_search_count_total");
    const searchLatencyP99 = this.getMetricValue(
      metrics,
      "vulcanodb_search_latency_seconds",
      'quantile="0.99"'
    );

    window.dispatchEvent(
      new CustomEvent("vulcano:telemetry", {
        detail: {
          id: "vector-search",
          type: "chart",
          data: {
            count: 0, // Only latency (requested queue latency, but backend searchCount seems to be used as count axis)
            latency: searchLatencyP99 * 1000,
          },
        },
      })
    );

    // 3. Doc Count
    const docCount = this.getMetricValue(metrics, "vulcanodb_storage_storeddocuments");
    window.dispatchEvent(
      new CustomEvent("vulcano:telemetry", {
        detail: {
          id: "doc-count",
          type: "indicator",
          value: docCount,
        },
      })
    );

    // 4. Off-Heap Memory
    const offHeap = this.getMetricValue(metrics, "vulcanodb_memory_offheap");
    const offHeapGB = (offHeap / (1024 * 1024 * 1024)).toFixed(2);
    window.dispatchEvent(
      new CustomEvent("vulcano:telemetry", {
        detail: {
          id: "off-heap",
          type: "indicator",
          value: offHeapGB,
        },
      })
    );
  }

  start() {
    if (this.intervalId) return;
    this.poll(); // Initial poll
    this.intervalId = setInterval(() => this.poll(), POLL_INTERVAL);
  }

  stop() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }
}

export const telemetryPoller = new TelemetryPoller();
