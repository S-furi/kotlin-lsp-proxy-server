const WS_URL = process.env.WS_URL || "ws://localhost:8089/ws/complete";

function pretty(v) {
    try {
        return JSON.stringify(v, null, 2);
    } catch {
        return String(v);
    }
}

function once(ws, event) {
    return new Promise((resolve) => {
        const handler = (arg) => {
            ws.removeEventListener(event, handler);
            resolve(arg);
        };
        ws.addEventListener(event, handler);
    });
}

function parseMessageData(data) {
    try {
        const s = typeof data === "string" ? data : data.toString();
        return JSON.parse(s);
    } catch {
        return data;
    }
}

function buildProject(sessionId, content, fileName = `${sessionId}.kt`) {
    return {
        files: [
            {
                text: content,
                name: fileName,
            },
        ],
        confType: "java",
    };
}

function buildCompletionRequest(project, line, ch) {
    return { project, line, ch };
}

async function sendAndAwait(ws, payload) {
    ws.send(JSON.stringify(payload));
    const msg = await once(ws, "message");
    return parseMessageData(msg.data);
}

function extractCompletions(serverMsg) {
    if (!serverMsg || serverMsg.type !== "completions") return [];
    const data = serverMsg.additionalData || {};
    return Array.isArray(data.completions) ? data.completions : [];
}

async function run() {
    console.log(`Connecting to ${WS_URL} ...`);
    const ws = new WebSocket(WS_URL);

    await once(ws, "open");
    console.log("WebSocket open.");

    const initMsgEvt = await once(ws, "message");
    const initMsg = parseMessageData(initMsgEvt.data);
    console.log("Init message:");
    console.log(pretty(initMsg));

    if (!initMsg || initMsg.type !== "connection_established") {
        console.error("Did not receive expected init message. Exiting.");
        ws.close();
        return;
    }

    const sessionId = initMsg.additionalData?.sessionId;
    if (!sessionId) {
        console.error("No sessionId in init message. Exiting.");
        ws.close();
        return;
    }

    const content1 = `
fun main() {
    3.0.toIn
}
  `.trim();
    const project1 = buildProject(sessionId, content1);
    const req1 = buildCompletionRequest(project1, 1, 11); // line=1, ch=11
    console.log("\nSending completion request 1 (toIn):");
    console.log(pretty(req1));
    const resp1 = await sendAndAwait(ws, req1);
    console.log("Response 1:");
    console.log(pretty(resp1));

    const compl1 = extractCompletions(resp1).map((c) => c.text);
    const hasToInt = compl1.includes("toInt");
    const hasToUInt = compl1.includes("toUInt");
    console.log(`Contains "toInt": ${hasToInt}, "toUInt": ${hasToUInt}`);

    const content2 = `
fun main() {
    val tmp = 42
    val y = 1 + tm
}
  `.trim();
    const project2 = buildProject(sessionId, content2);
    const req2 = buildCompletionRequest(project2, 2, 17); // line=2, ch=17
    console.log("\nSending completion request 2 (variable completion):");
    console.log(pretty(req2));
    const resp2 = await sendAndAwait(ws, req2);
    console.log("Response 2:");
    console.log(pretty(resp2));

    const compl2 = extractCompletions(resp2).map((c) => c.text);
    const hasTmp = compl2.includes("tmp");
    console.log(`Contains "tmp": ${hasTmp}`);

    ws.close();
    await once(ws, "close");
    console.log("WebSocket closed.");
}

run().catch((err) => {
    console.error(err);
    process.exit(1);
});
