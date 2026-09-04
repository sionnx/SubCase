(() => {
  "use strict";

  const executionChunks = new Map();
  const httpChunks = new Map();
  const httpCallbacks = new Map();
  let activeExecution = null;
  let requestSequence = 0;

  function decodeBase64Utf8(value) {
    const binary = atob(value);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
    return new TextDecoder().decode(bytes);
  }

  function encodeBase64Utf8(value) {
    const bytes = new TextEncoder().encode(value);
    let binary = "";
    const blockSize = 0x8000;
    for (let i = 0; i < bytes.length; i += blockSize) {
      binary += String.fromCharCode(...bytes.subarray(i, i + blockSize));
    }
    return btoa(binary);
  }

  function base64ToUint8Array(value) {
    const binary = atob(value);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
    return bytes;
  }

  function uint8ArrayToBase64(value) {
    const bytes = value instanceof Uint8Array
      ? value
      : new Uint8Array(value.buffer || value);
    let binary = "";
    const blockSize = 0x8000;
    for (let i = 0; i < bytes.length; i += blockSize) {
      binary += String.fromCharCode(...bytes.subarray(i, i + blockSize));
    }
    return btoa(binary);
  }

  function postDone(executionId, result) {
    const encoded = encodeBase64Utf8(JSON.stringify(result == null ? {} : result));
    const chunkSize = 256 * 1024;
    const total = Math.max(1, Math.ceil(encoded.length / chunkSize));
    __loonHost.doneStart(executionId, total);
    for (let index = 0; index < total; index += 1) {
      __loonHost.doneChunk(
        executionId,
        index,
        encoded.slice(index * chunkSize, (index + 1) * chunkSize),
      );
    }
    __loonHost.doneEnd(executionId);
  }

  function installLoonGlobals(context) {
    const executionId = context.executionId;
    globalThis.$loon = {
      deviceName: "Android",
      systemVersion: context.systemVersion,
      loonVersion: "SubCase WebView",
      build: context.appVersion,
    };
    globalThis.$argument = context.argument;
    globalThis.$request = context.request;
    globalThis.$script = {
      name: context.scriptTag,
      startTime: Date.now(),
    };

    globalThis.$persistentStore = {
      read(key) {
        return __persistentStore.read(String(key));
      },
      write(value, key) {
        return __persistentStore.write(
          value == null ? null : String(value),
          String(key),
        );
      },
    };

    globalThis.$notification = {
      post(title, subtitle, content, attachment) {
        __loonHost.notify(
          executionId,
          String(title || ""),
          String(subtitle || ""),
          String(content || ""),
          attachment == null ? "{}" : JSON.stringify(attachment),
        );
      },
    };

    const client = {};
    for (const method of ["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"]) {
      client[method.toLowerCase()] = (options, callback) => {
        const normalized = typeof options === "string" ? { url: options } : (options || {});
        if (normalized.body instanceof ArrayBuffer || ArrayBuffer.isView(normalized.body)) {
          normalized.body = uint8ArrayToBase64(normalized.body);
          normalized["body-base64"] = true;
        }
        const requestId = `${executionId}-${++requestSequence}`;
        httpCallbacks.set(requestId, callback);
        __loonHost.startHttpRequest(
          executionId,
          requestId,
          method,
          JSON.stringify(normalized),
        );
      };
    }
    globalThis.$httpClient = client;

    let completed = false;
    globalThis.$done = (result) => {
      if (completed) return;
      completed = true;
      postDone(executionId, result);
    };
  }

  function failActiveExecution(message) {
    if (!activeExecution || activeExecution.completed) return;
    activeExecution.completed = true;
    __loonHost.scriptError(activeExecution.id, String(message || "JavaScript error"));
  }

  globalThis.__loonBridge = {
    receiveExecutionChunk(executionId, index, total, chunk) {
      const state = executionChunks.get(executionId) || {
        total,
        chunks: new Array(total),
        received: 0,
      };
      if (typeof state.chunks[index] !== "string") state.received += 1;
      state.chunks[index] = chunk;
      executionChunks.set(executionId, state);
    },

    startExecution(executionId, scriptUrl) {
      const state = executionChunks.get(executionId);
      if (!state || state.received !== state.total) {
        __loonHost.scriptError(executionId, "请求上下文分块不完整");
        return;
      }
      executionChunks.delete(executionId);
      const context = JSON.parse(decodeBase64Utf8(state.chunks.join("")));
      activeExecution = { id: executionId, completed: false };
      installLoonGlobals(context);

      const script = document.createElement("script");
      script.src = `${scriptUrl}?execution=${encodeURIComponent(executionId)}`;
      script.onerror = () => failActiveExecution(`脚本加载失败: ${scriptUrl}`);
      document.head.appendChild(script);
    },

    receiveHttpChunk(requestId, index, total, chunk) {
      const state = httpChunks.get(requestId) || {
        total,
        chunks: new Array(total),
        received: 0,
      };
      if (typeof state.chunks[index] !== "string") state.received += 1;
      state.chunks[index] = chunk;
      httpChunks.set(requestId, state);
      if (state.received !== state.total) return;

      httpChunks.delete(requestId);
      const callback = httpCallbacks.get(requestId);
      httpCallbacks.delete(requestId);
      if (typeof callback !== "function") return;

      const payload = JSON.parse(decodeBase64Utf8(state.chunks.join("")));
      if (payload.error) {
        callback(payload.error, null, null);
        return;
      }
      const body = payload.binary
        ? base64ToUint8Array(payload.body || "")
        : (payload.body || "");
      callback(null, {
        status: payload.status,
        statusCode: payload.status,
        headers: payload.headers || {},
      }, body);
    },

    clearExecution(executionId) {
      for (const requestId of Array.from(httpCallbacks.keys())) {
        if (requestId.startsWith(`${executionId}-`)) httpCallbacks.delete(requestId);
      }
      for (const requestId of Array.from(httpChunks.keys())) {
        if (requestId.startsWith(`${executionId}-`)) httpChunks.delete(requestId);
      }
      if (activeExecution && activeExecution.id === executionId) {
        activeExecution.completed = true;
        activeExecution = null;
      }
    },
  };

  window.addEventListener("error", (event) => failActiveExecution(event.message));
  window.addEventListener("unhandledrejection", (event) => {
    failActiveExecution(event.reason && event.reason.message ? event.reason.message : event.reason);
  });
  __loonHost.runnerReady();
})();
