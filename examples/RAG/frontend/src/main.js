import { marked } from "marked";
import { socketManager } from "./socket.js";
import { telemetryCharts } from "./charts/index.js";
import { ChartjsProvider } from "./charts/chartjs.js";
import { telemetryPoller } from "./telemetry.js";

const chatHistory = document.querySelector("#chat-history");
const queryInput = document.querySelector("#query");
const searchButton = document.querySelector("#search");
const telemetrySidebar = document.querySelector("#telemetry-sidebar");
const telemetryToggle = document.querySelector("#telemetry-toggle");
const statusIndicator = document.querySelector("#connection-status");
const statusTerminal = document.querySelector("#status-terminal");
const reindexLink = document.querySelector("#reindex-link");
const statusSection = document.querySelector("#status-collapsible");
const toggleStatusBtn = document.querySelector("#toggle-status");

/**
 * UI Functions
 */
function addChatMessage(text, isBot = false, responseId = null) {
  let messageDiv;
  let currentContent = "";

  if (responseId) {
    messageDiv = document.getElementById(responseId);
    if (messageDiv) {
      // Store raw markdown in a data attribute to reconstruct the full text for re-parsing
      currentContent = messageDiv.getAttribute("data-raw") || "";
      currentContent += text;
      messageDiv.setAttribute("data-raw", currentContent);
      messageDiv.innerHTML = marked.parse(currentContent);
    }
  }

  if (!messageDiv) {
    messageDiv = document.createElement("div");
    messageDiv.classList.add("message");
    messageDiv.classList.add(isBot ? "bot-message" : "user-message");
    if (responseId) {
      messageDiv.id = responseId;
      messageDiv.setAttribute("data-raw", text);
    }
    messageDiv.innerHTML = marked.parse(text);
    chatHistory.appendChild(messageDiv);
  }

  chatHistory.scrollTop = chatHistory.scrollHeight;
}

function addStatusLine(message, isSystem = false) {
  const line = document.createElement("div");
  line.classList.add("terminal-line");
  if (isSystem) line.classList.add("system-line");

  const timestamp = new Date().toLocaleTimeString([], {
    hour12: false,
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
  line.textContent = `[${timestamp}] ${message}`;

  statusTerminal.appendChild(line);
  statusTerminal.scrollTop = statusTerminal.scrollHeight;
}

function updateConnectionStatus(status) {
  statusIndicator.className = "status-indicator " + status;
  statusIndicator.title =
    status.charAt(0).toUpperCase() + status.slice(1);
}

/**
 * Event Listeners
 */
function handleSearch() {
  const query = queryInput.value.trim();
  if (!query) return;

  addChatMessage(query, false);
  socketManager.send("chat", { message: query });
  queryInput.value = "";
}

searchButton.addEventListener("click", handleSearch);

queryInput.addEventListener("keypress", (e) => {
  if (e.key === "Enter") {
    handleSearch();
  }
});

telemetryToggle.addEventListener("click", () => {
  telemetrySidebar.classList.toggle("open");
});

toggleStatusBtn.addEventListener("click", () => {
  statusSection.classList.toggle("collapsed");
});

reindexLink.addEventListener("click", (e) => {
  e.preventDefault();
  socketManager.send("reindex", { dataset: "arxiv" });
});

// Close drawer when clicking outside on mobile
document.addEventListener("click", (e) => {
  if (window.innerWidth <= 768) {
    if (
      !telemetrySidebar.contains(e.target) &&
      !telemetryToggle.contains(e.target) &&
      telemetrySidebar.classList.contains("open")
    ) {
      telemetrySidebar.classList.remove("open");
    }
  }
});

/**
 * WebSocket Event Handling
 */
window.addEventListener("vulcano:connection", (e) => {
  updateConnectionStatus(e.detail.status);
});

window.addEventListener("vulcano:status", (e) => {
  addStatusLine(e.detail.message, true);
});

window.addEventListener("vulcano:chat", (e) => {
  addChatMessage(e.detail.response, true, e.detail.responseId);
});

window.addEventListener("vulcano:telemetry", (e) => {
  const { id, type, data, value } = e.detail;

  if (type === "chart") {
    telemetryCharts.updateChart(id, data);
  } else if (type === "indicator") {
    telemetryCharts.setIndicator(id, value);
  }
});

/**
 * Initialize Charts
 */
function initCharts() {
  telemetryCharts.setProvider(ChartjsProvider);

  // Vector Search (Multi-Axis)
  telemetryCharts.createChart(
    "vector-search",
    "vector-search-chart",
    "multiaxis",
    "Queue Latency (P99, ms)",
    { latencyColor: "#646cff", countColor: "#ffb300" }
  );

  // Document Insert (Multi-Axis)
  telemetryCharts.createChart(
    "doc-insert",
    "doc-insert-chart",
    "multiaxis",
    "Insert Latency (P99, ms)",
    { latencyColor: "#4caf50", countColor: "#ffb300" }
  );

  // Recall (Gauge)
  telemetryCharts.createChart("recall", "recall-gauge", "gauge", "Recall", {
    color: "#646cff",
  });

  // Set initial indicator values
  telemetryCharts.setIndicator("off-heap", "00");
  telemetryCharts.setIndicator("doc-count", "00");
  telemetryCharts.setIndicator("insert-queue", "00");
}

// Initialize
initCharts();
socketManager.connect();
telemetryPoller.start();
