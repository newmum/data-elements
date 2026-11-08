async function start() {
  if (import.meta.env.VITE_MOCK_ENABLED === "true") {
    const { startMock } = await import("./mock/browser");
    const bridgeOnly = window.parent !== window && new URLSearchParams(location.search).get("mockBridge") === "1";
    await startMock(bridgeOnly);
    if (bridgeOnly) return;
  } else if ("serviceWorker" in navigator) {
    const scope = new URL(import.meta.env.BASE_URL, document.baseURI).href;
    const registrations = await navigator.serviceWorker.getRegistrations();
    await Promise.all(
      registrations
        .filter(
          (item) => item.scope === scope && item.active?.scriptURL.endsWith("/mockServiceWorker.js")
        )
        .map((item) => item.unregister())
    );
  }
  await import("./bootstrap");
}

void start().catch((error) => {
  console.error("应用初始化失败", error);
  const root = document.getElementById("app");
  if (root) {
    root.textContent = `应用初始化失败：${error instanceof Error ? error.message : "请刷新后重试"}`;
    root.style.cssText = "margin:48px;font:16px system-ui;color:#b42318";
  }
});
