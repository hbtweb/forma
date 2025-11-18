/**
 * Forma Hot Reload Client Library
 * Phase 5.5: Browser-side hot reload support
 *
 * Features:
 * - WebSocket connection to reload server
 * - CSS injection without page reload
 * - Smart page reloading
 * - Reconnection handling
 * - Visual feedback
 */

(function(window) {
  'use strict';

  // =============================================================================
  // Configuration
  // =============================================================================

  const DEFAULT_CONFIG = {
    host: 'localhost',
    port: 3449,
    reconnectDelay: 1000,
    maxReconnectAttempts: 10,
    heartbeatInterval: 30000,
    showNotifications: true,
    cssReloadDelay: 50,
    debug: false
  };

  // =============================================================================
  // FormaReload Class
  // =============================================================================

  class FormaReload {
    constructor(config = {}) {
      this.config = { ...DEFAULT_CONFIG, ...config };
      this.ws = null;
      this.reconnectAttempts = 0;
      this.reconnectTimeout = null;
      this.heartbeatInterval = null;
      this.connected = false;
      this.statistics = {
        cssInjected: 0,
        pageReloaded: 0,
        rebuilds: 0,
        errors: 0
      };

      this.log('FormaReload initialized', this.config);
    }

    // -------------------------------------------------------------------------
    // Logging
    // -------------------------------------------------------------------------

    log(...args) {
      if (this.config.debug) {
        console.log('[FormaReload]', ...args);
      }
    }

    error(...args) {
      console.error('[FormaReload]', ...args);
      this.statistics.errors++;
    }

    // -------------------------------------------------------------------------
    // Connection Management
    // -------------------------------------------------------------------------

    connect() {
      const url = `ws://${this.config.host}:${this.config.port}`;
      this.log('Connecting to', url);

      try {
        this.ws = new WebSocket(url);
        this.setupEventHandlers();
      } catch (err) {
        this.error('Connection failed:', err);
        this.scheduleReconnect();
      }
    }

    disconnect() {
      this.log('Disconnecting');
      this.connected = false;

      if (this.heartbeatInterval) {
        clearInterval(this.heartbeatInterval);
        this.heartbeatInterval = null;
      }

      if (this.reconnectTimeout) {
        clearTimeout(this.reconnectTimeout);
        this.reconnectTimeout = null;
      }

      if (this.ws) {
        this.ws.close();
        this.ws = null;
      }
    }

    scheduleReconnect() {
      if (this.reconnectAttempts >= this.config.maxReconnectAttempts) {
        this.error('Max reconnect attempts reached');
        this.showNotification('Hot reload disconnected', 'error');
        return;
      }

      this.reconnectAttempts++;
      const delay = this.config.reconnectDelay * this.reconnectAttempts;
      this.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`);

      this.reconnectTimeout = setTimeout(() => {
        this.connect();
      }, delay);
    }

    setupEventHandlers() {
      this.ws.onopen = () => {
        this.log('Connected');
        this.connected = true;
        this.reconnectAttempts = 0;
        this.showNotification('Hot reload connected', 'success');
        this.startHeartbeat();
      };

      this.ws.onclose = () => {
        this.log('Disconnected');
        this.connected = false;
        this.stopHeartbeat();
        this.scheduleReconnect();
      };

      this.ws.onerror = (err) => {
        this.error('WebSocket error:', err);
      };

      this.ws.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data);
          this.handleMessage(message);
        } catch (err) {
          this.error('Failed to parse message:', err);
        }
      };
    }

    startHeartbeat() {
      this.heartbeatInterval = setInterval(() => {
        if (this.connected && this.ws.readyState === WebSocket.OPEN) {
          this.ws.send('ping');
        }
      }, this.config.heartbeatInterval);
    }

    stopHeartbeat() {
      if (this.heartbeatInterval) {
        clearInterval(this.heartbeatInterval);
        this.heartbeatInterval = null;
      }
    }

    // -------------------------------------------------------------------------
    // Message Handling
    // -------------------------------------------------------------------------

    handleMessage(message) {
      this.log('Received message:', message);

      switch (message.type) {
        case 'connected':
          this.log('Server acknowledged connection');
          break;

        case 'heartbeat':
          // Heartbeat response
          break;

        case 'pong':
          // Ping response
          break;

        case 'inject':
          this.injectCSS(message);
          break;

        case 'reload':
          this.reloadPage(message);
          break;

        case 'rebuild':
          this.handleRebuild(message);
          break;

        default:
          this.log('Unknown message type:', message.type);
      }
    }

    // -------------------------------------------------------------------------
    // Reload Strategies
    // -------------------------------------------------------------------------

    injectCSS(message) {
      this.log('Injecting CSS:', message.path);

      // Find existing stylesheet
      const links = document.querySelectorAll('link[rel="stylesheet"]');
      let updated = false;

      for (const link of links) {
        if (link.href.includes(message.path)) {
          // Update existing stylesheet with cache-busting timestamp
          const url = new URL(link.href);
          url.searchParams.set('t', Date.now());
          link.href = url.toString();
          updated = true;
          this.log('Updated existing stylesheet:', link.href);
        }
      }

      // If not found, inject new stylesheet
      if (!updated && message.content) {
        const style = document.createElement('style');
        style.setAttribute('data-forma-path', message.path);
        style.textContent = message.content;
        document.head.appendChild(style);
        this.log('Injected new stylesheet');
      }

      this.statistics.cssInjected++;
      this.showNotification(`CSS updated: ${message.path}`, 'info');
    }

    reloadPage(message) {
      this.log('Reloading page:', message.path);
      this.statistics.pageReloaded++;
      this.showNotification('Page reloading...', 'info');

      setTimeout(() => {
        window.location.reload();
      }, this.config.cssReloadDelay);
    }

    handleRebuild(message) {
      this.log('Handling rebuild:', message.path);
      this.statistics.rebuilds++;

      // Check if build was successful
      if (message.buildResult && message.buildResult.success) {
        this.showNotification(`Rebuilt: ${message.path}`, 'success');
        // Reload page after successful rebuild
        this.reloadPage(message);
      } else {
        this.showNotification(`Build failed: ${message.path}`, 'error');
      }
    }

    // -------------------------------------------------------------------------
    // Visual Feedback
    // -------------------------------------------------------------------------

    showNotification(message, type = 'info') {
      if (!this.config.showNotifications) {
        return;
      }

      // Remove existing notifications
      const existing = document.getElementById('forma-reload-notification');
      if (existing) {
        existing.remove();
      }

      // Create notification
      const notification = document.createElement('div');
      notification.id = 'forma-reload-notification';
      notification.className = `forma-reload-notification forma-reload-${type}`;
      notification.textContent = message;

      // Add styles
      Object.assign(notification.style, {
        position: 'fixed',
        bottom: '20px',
        right: '20px',
        padding: '12px 20px',
        borderRadius: '4px',
        fontSize: '14px',
        fontFamily: 'system-ui, -apple-system, sans-serif',
        boxShadow: '0 2px 8px rgba(0,0,0,0.2)',
        zIndex: '10000',
        transition: 'opacity 0.3s',
        backgroundColor: type === 'error' ? '#ef4444' :
                        type === 'success' ? '#10b981' : '#3b82f6',
        color: 'white'
      });

      document.body.appendChild(notification);

      // Auto-remove after 3 seconds
      setTimeout(() => {
        notification.style.opacity = '0';
        setTimeout(() => notification.remove(), 300);
      }, 3000);
    }

    // -------------------------------------------------------------------------
    // Statistics
    // -------------------------------------------------------------------------

    getStatistics() {
      return {
        ...this.statistics,
        connected: this.connected,
        reconnectAttempts: this.reconnectAttempts
      };
    }

    logStatistics() {
      console.table(this.getStatistics());
    }
  }

  // =============================================================================
  // Auto-initialization
  // =============================================================================

  // Auto-connect if in development mode
  if (document.currentScript && document.currentScript.dataset.autoConnect !== 'false') {
    const formaReload = new FormaReload({
      debug: document.currentScript.dataset.debug === 'true'
    });

    // Connect when DOM is ready
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', () => formaReload.connect());
    } else {
      formaReload.connect();
    }

    // Expose globally for debugging
    window.formaReload = formaReload;
  }

  // =============================================================================
  // Export
  // =============================================================================

  window.FormaReload = FormaReload;

})(window);
