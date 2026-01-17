/**
 * Generic Chart Interface to avoid vendor lock-in.
 */
export class BaseChart {
  /**
   * @param {string} containerId - The ID of the HTML element (usually a canvas).
   * @param {string} title - The title of the chart.
   */
  constructor(containerId, title) {
    this.containerId = containerId;
    this.title = title;
  }

  /**
   * Initialize the chart. To be implemented by providers.
   * @param {Object} _options - Configuration options.
   */
  init(_options = {}) {
    throw new Error("init() must be implemented by a provider");
  }

  /**
   * Update the chart with new data. To be implemented by providers.
   * @param {any} _data - The new data point or set.
   */
  update(_data) {
    throw new Error("update() must be implemented by a provider");
  }
}

/**
 * Registry and factory for charts.
 */
export class ChartManager {
  constructor() {
    this.charts = new Map();
    this.provider = null;
  }

  /**
   * Set the library provider (e.g., ChartjsProvider).
   */
  setProvider(provider) {
    this.provider = provider;
  }

  /**
   * Create and register a new chart.
   * @param {string} id - Unique identifier for the chart.
   * @param {string} containerId - DOM element ID.
   * @param {string} type - 'timeseries' or 'gauge'.
   * @param {string} title - Display title.
   * @param {Object} options - Optional configuration.
   */
  createChart(id, containerId, type, title, options = {}) {
    if (!this.provider) throw new Error("Chart provider not set");

    const chart = this.provider.create(containerId, type, title, options);
    chart.init(options);
    this.charts.set(id, chart);
    return chart;
  }

  /**
   * Set a numeric indicator value.
   */
  setIndicator(id, value) {
    const el = document.getElementById(`stat-${id}`);
    if (el) {
      el.textContent = value;
    }
  }

  /**
   * Get a chart by ID.
   */
  getChart(id) {
    return this.charts.get(id);
  }

  /**
   * Update a specific chart with new data.
   */
  updateChart(id, data) {
    const chart = this.getChart(id);
    if (chart) {
      chart.update(data);
    }
  }
}

export const telemetryCharts = new ChartManager();
