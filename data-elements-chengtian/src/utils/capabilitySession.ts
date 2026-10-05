import { activePlatformToken } from './platformSessionChannel';
import { logoutPlatformSession } from './platformLogout';
import { capabilityCenterBases } from './capabilityCenters';

// Only configured first-party capability applications may read this tab's
// platform session. No token in URLs, wildcard origins or extra storage.
const configuredCenters = Object.values(capabilityCenterBases)
  .map((url) => new URL(url, window.location.href).origin);
window.addEventListener("message", async (event: MessageEvent) => {
  if (window.parent === window || event.source !== window.parent || !configuredCenters.includes(event.origin)) return;
  if (typeof event.data?.nonce !== "string") return;
  const nonce = event.data.nonce;
  if (event.data.type === 'data-elements:logout-request') {
    try {
      await logoutPlatformSession();
      window.parent.postMessage({ type: 'data-elements:logout-response', nonce, success: true }, event.origin);
    } catch (error) {
      window.parent.postMessage({ type: 'data-elements:logout-response', nonce, success: false, message: error instanceof Error ? error.message : '退出登录失败，请重试' }, event.origin);
    }
    return;
  }
  if (event.data.type !== "data-elements:session-request") return;
  const token = await activePlatformToken();
  window.parent.postMessage({ type: "data-elements:session-response", nonce, token }, event.origin);
});
