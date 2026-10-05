// 监听门户发送过来的用户信息消息
import { Storage } from "@/utils/storage";

// 对父窗口进行监听
export function addEventListener() {
  window.addEventListener("message", (event: MessageEvent) => {
    if (typeof event.data === "string" && event.data.includes("{")) {
      try {
        const obj = JSON.parse(event.data);
        const { type, data } = obj || {};
        if (type === "token") {
          Object.entries(data).forEach(([key, value]) => {
            Storage.set(key, value);
          });
        }
      } catch (e) {
        console.error("receive message parse error:", e);
      }
    }
  });
}

// 向其他窗口发送信息，默认top窗口
export function sendMessage(type: "back" | "noAuth", data: {}, win = top) {
  if (win === window) {
    return;
  } else {
    win?.postMessage(JSON.stringify({ type, data }), "*");
  }
}
