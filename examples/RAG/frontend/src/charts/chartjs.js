import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Title,
  Tooltip,
  Legend,
  DoughnutController,
  ArcElement,
} from "chart.js";
import { BaseChart } from "./index.js";

// Register Chart.js components
Chart.register(
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Title,
  Tooltip,
  Legend,
  DoughnutController,
  ArcElement
);

class ChartjsMultiAxis extends BaseChart {
  constructor(containerId, title) {
    super(containerId, title);
    this.instance = null;
    this.maxPoints = 20;
  }

  init(options = {}) {
    const ctx = document.getElementById(this.containerId).getContext("2d");
    this.instance = new Chart(ctx, {
      type: "line",
      data: {
        labels: [],
        datasets: [
          {
            label: "Latency",
            data: [],
            borderColor: options.latencyColor || "#646cff",
            backgroundColor: (options.latencyColor || "#646cff") + "33",
            yAxisID: "y-latency",
            tension: 0.3,
            fill: true,
            pointRadius: 0,
          },
          {
            label: "Count",
            data: [],
            borderColor: options.countColor || "#ffb300",
            backgroundColor: (options.countColor || "#ffb300") + "33",
            yAxisID: "y-count",
            tension: 0.3,
            fill: false,
            pointRadius: 0,
            borderDash: [5, 5],
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
        },
        scales: {
          x: { display: false },
          "y-latency": {
            type: "linear",
            display: true,
            position: "left",
            beginAtZero: true,
            grid: { color: "#333" },
            ticks: { color: "#888", font: { size: 10 } },
          },
          "y-count": {
            type: "linear",
            display: true,
            position: "right",
            beginAtZero: true,
            grid: { drawOnChartArea: false },
            ticks: { color: "#888", font: { size: 10 } },
          },
        },
      },
    });
  }

  update(data) {
    if (!this.instance) return;

    const label = new Date().toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });

    this.instance.data.labels.push(label);
    this.instance.data.datasets[0].data.push(data.latency);
    this.instance.data.datasets[1].data.push(data.count);

    if (this.instance.data.labels.length > this.maxPoints) {
      this.instance.data.labels.shift();
      this.instance.data.datasets[0].data.shift();
      this.instance.data.datasets[1].data.shift();
    }

    this.instance.update("none");
  }
}

class ChartjsGauge extends BaseChart {
  constructor(containerId, title) {
    super(containerId, title);
    this.instance = null;
  }

  init(options = {}) {
    const ctx = document.getElementById(this.containerId).getContext("2d");
    this.instance = new Chart(ctx, {
      type: "doughnut",
      data: {
        labels: [this.title, "Remaining"],
        datasets: [
          {
            data: [0, 100],
            backgroundColor: [options.color || "#646cff", "#333"],
            borderWidth: 0,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        circumference: 180,
        rotation: 270,
        cutout: "80%",
        plugins: {
          legend: { display: false },
          tooltip: { enabled: false },
        },
      },
    });
  }

  update(data) {
    if (!this.instance) return;
    const value = Math.min(Math.max(data.value, 0), 100);
    this.instance.data.datasets[0].data = [value, 100 - value];
    this.instance.update();
  }
}

export const ChartjsProvider = {
  create(containerId, type, title, _options) {
    if (type === "multiaxis") {
      return new ChartjsMultiAxis(containerId, title);
    } else if (type === "gauge") {
      return new ChartjsGauge(containerId, title);
    }
    throw new Error(`Unsupported chart type: ${type}`);
  },
};
