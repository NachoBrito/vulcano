import { socketManager } from "./socket.js";
import { telemetryCharts } from "./charts/index.js";
import { ChartjsProvider } from "./charts/chartjs.js";

const chatHistory = document.querySelector("#chat-history");
const queryInput = document.querySelector("#query");
const searchButton = document.querySelector("#search");
const telemetrySidebar = document.querySelector("#telemetry-sidebar");
const telemetryToggle = document.querySelector("#telemetry-toggle");
const statusIndicator = document.querySelector("#connection-status");
const statusTerminal = document.querySelector("#status-terminal");
const reindexLink = document.querySelector("#reindex-link");

/**
 * UI Functions
 */
function addChatMessage(text, isBot = false) {
  const messageDiv = document.createElement("div");
  messageDiv.classList.add("message");
  messageDiv.classList.add(isBot ? "bot-message" : "user-message");
  messageDiv.textContent = text;
  chatHistory.appendChild(messageDiv);
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
  socketManager.send("chat", { query });
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
  addChatMessage(e.detail.response, true);
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
    "Vector Search",
    { latencyColor: "#646cff", countColor: "#ffb300" }
  );

  // Document Insert (Multi-Axis)
  telemetryCharts.createChart(
    "doc-insert",
    "doc-insert-chart",
    "multiaxis",
    "Document Insert",
    { latencyColor: "#4caf50", countColor: "#ffb300" }
  );

  // Recall (Gauge)
  telemetryCharts.createChart("recall", "recall-gauge", "gauge", "Recall", {
    color: "#646cff",
  });

  // Set initial indicator values
  telemetryCharts.setIndicator("off-heap", "00");
  telemetryCharts.setIndicator("dist-calcs", "00");
  telemetryCharts.setIndicator("doc-count", "00");
}

// Initialize
initCharts();
socketManager.connect();
