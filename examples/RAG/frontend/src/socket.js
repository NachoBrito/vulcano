/**
 * WebSocket Manager for VulcanoDB RAG Frontend
 */
export class VulcanoSocket {
  constructor(url = `ws://${window.location.host}/ws`) {
    this.url = url;
    this.socket = null;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.reconnectDelay = 2000;
  }

  connect() {
    console.log(`Connecting to WebSocket: ${this.url}`);
    this.socket = new WebSocket(this.url);

    this.socket.onopen = () => {
      console.log("WebSocket connected");
      this.reconnectAttempts = 0;
      this.dispatchEvent("vulcano:connection", { status: "connected" });
      this.dispatchEvent("vulcano:status", {
        message: "Connected to VulcanoDB server",
      });
    };

    this.socket.onclose = () => {
      console.log("WebSocket disconnected");
      this.dispatchEvent("vulcano:connection", { status: "disconnected" });
      this.dispatchEvent("vulcano:status", {
        message: "Disconnected from server",
      });
      this.attemptReconnect();
    };

    this.socket.onerror = (error) => {
      console.error("WebSocket error:", error);
      this.dispatchEvent("vulcano:status", {
        message: "Connection error occurred",
      });
    };

    this.socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        this.handleMessage(data);
      } catch (e) {
        console.error("Failed to parse WebSocket message:", e);
      }
    };
  }

  handleMessage(data) {
    // data format: { type: 'chat' | 'status' | 'telemetry', payload: ... }
    switch (data.type) {
      case "chat":
        this.dispatchEvent("vulcano:chat", data.payload);
        break;
      case "status":
        this.dispatchEvent("vulcano:status", data.payload);
        break;
      case "telemetry":
        this.dispatchEvent("vulcano:telemetry", data.payload);
        break;
      default:
        console.warn("Unknown message type:", data.type);
    }
  }

  send(type, payload) {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ type, payload }));
    } else {
      console.error("WebSocket is not open. Cannot send message.");
      this.dispatchEvent("vulcano:status", {
        message: "Error: Cannot send message, not connected",
      });
    }
  }

  attemptReconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1);
      this.dispatchEvent("vulcano:status", {
        message: `Attempting to reconnect in ${delay / 1000}s... (Attempt ${this.reconnectAttempts})`,
      });
      setTimeout(() => this.connect(), delay);
    } else {
      this.dispatchEvent("vulcano:status", {
        message: "Max reconnection attempts reached.",
      });
    }
  }

  dispatchEvent(name, detail) {
    const event = new CustomEvent(name, { detail });
    window.dispatchEvent(event);
  }
}

export const socketManager = new VulcanoSocket();
