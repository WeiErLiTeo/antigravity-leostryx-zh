/* Leostryx Plugin Host Bridge — Open interface for sidecar/frontend plugins */
(function() {
  "use strict";
  if (window.__agyPluginHostInstalled) return;
  window.__agyPluginHostInstalled = true;

  window.__agyPlugins = {
    installed: [],
    register: function(plugin) {
      if (!plugin || !plugin.id) return;
      this.installed.push(plugin);
      console.log("[AgyPluginHost] registered plugin:", plugin.id);
      if (typeof plugin.onMount === "function") {
        try { plugin.onMount(); } catch(e) { console.error(e); }
      }
    },
    getPlugin: function(id) {
      return this.installed.find(function(p) { return p.id === id; });
    }
  };

  // Provide host endpoints & token discovery for inline telemetry/dashboard plugins
  window.addEventListener("DOMContentLoaded", function() {
    console.log("[AgyPluginHost] ready. Host origin:", window.location.origin);
  });
})();
