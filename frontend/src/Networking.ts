import {CurrentRun, Runner, Server, UserPreferences} from "./APIModels";

export const defaultHeaders = new Headers();
const jsonHeaders: Headers = new Headers(defaultHeaders)
jsonHeaders.append("Content-Type", "application/json")

export function fetchWithTimeout(input: RequestInfo, timeoutMs: number, init?: RequestInit): Promise<Response> {
    return new Promise(((resolve, reject) => {
        fetch(input, init).then(resolve, reject)

        setTimeout(reject, timeoutMs, new Error("Connection timed out"));
    }))
}

export async function fetchJsonWithTimeoutOrNull<T>(input: RequestInfo, timeoutMS: number, init?: RequestInit): Promise<T | null> {
    const response = await fetchWithTimeout(input, timeoutMS, init);

    return response.ok ? response.json() : null;
}

export async function fetchJsonWithTimeoutOrThrow<T>(input: RequestInfo, timeoutMS: number, init?: RequestInit): Promise<T> {
    const response = await fetchJsonWithTimeoutOrNull<T>(input, timeoutMS, init)
    if (response === null) {
        throw new Error("Request failed to retrieve body")
    }

    return response
}

export type BackendStatus = "up" | "offline" | "unauthorized"
export async function getBackendStatus(): Promise<BackendStatus> {
    try {
        const statusResponse = await fetchWithTimeout("http://localhost:8080/api/v2/rest/status", 3000, {
            method: "GET",
            headers: defaultHeaders
        })

        if (statusResponse.ok) {
            return "up"
        } else if (statusResponse.status === 401 || statusResponse.status === 403) {
            return "unauthorized"
        } else {
            return "offline"
        }
    } catch {
        return "offline"
    }
}

export type AccessError = "loading" | "unavailable"

export async function fetchServer(serverUUID: string): Promise<Server> {
    const server = await fetchJsonWithTimeoutOrNull<Server>(`http://localhost:8080/api/v2/rest/servers/${serverUUID}`, 3000, {
        method: "GET",
        headers: defaultHeaders
    });
    if (server === null) throw Error(`Got invalid response getting server`)
    return server
}

export async function fetchCurrentRun(serverUUID: string): Promise<CurrentRun | null> {
    const response = await fetchWithTimeout(`http://localhost:8080/api/v2/rest/servers/${serverUUID}/currentRun`, 3000, {
        method: "GET",
        headers: defaultHeaders
    });

    if (response.ok)
        return response.json();
    else if (response.status === 404) {
        return null;
    } else {
        throw Error(`Got invalid response getting runner`);
    }
}

export async function fetchRunner(runnerUUID: string): Promise<Runner> {
    const runner = await fetchJsonWithTimeoutOrNull<Runner>(`http://localhost:8080/api/v2/rest/runners/${runnerUUID}`, 3000, {
        method: "GET",
        headers: defaultHeaders
    });
    if (runner === null) {
        throw Error(`Got invalid response getting runner`)
    }
    return runner
}

export async function getServers(): Promise<Server[] | null> {
    const response = await fetchWithTimeout("http://localhost:8080/api/v2/rest/servers", 3000, {
        method: "GET",
        headers: defaultHeaders
    });

    if (!response.ok) return null;

    return JSON.parse(await response.text(), (key, value) => key === "creationTime" ? new Date(value) : value)
}

export function getCurrentRunWebsocket(runnerUUID: string, serverUUID: string, onMessage: (this: WebSocket, event: MessageEvent<any>) => any): WebSocket {
    const websocket = new WebSocket(
        `ws://localhost:8080/api/v2/websockets/runners/${runnerUUID}/runs/current?serverId=${serverUUID}`
    )

    websocket.onmessage = onMessage

    return websocket
}

export function getConsoleWebsocket(runnerUUID: string, runUUID: string, onMessage: (this: WebSocket, event: MessageEvent<any>) => any): WebSocket {
    const websocket = new WebSocket(
        `ws://localhost:8080/api/v2/websockets/runners/${runnerUUID}/runs/current/${runUUID}/console`
    )

    websocket.onmessage = onMessage

    return websocket
}

export async function startServer(server: Server): Promise<boolean> {
    const response = await fetch("http://localhost:8080/api/v2/rest/servers/" + server?.uuid + "/currentRun", {
        headers: jsonHeaders,
        method: "POST",
        body: JSON.stringify({
            "port": 25565,  // TODO: Customizable
            "maxHeapSizeMB": 2048,
            "minHeapSizeMB": 1024
        })
    })

    return response.ok
}

export async function stopServer(server: Server): Promise<boolean> {
    const response = await fetch("http://localhost:8080/api/v2/rest/servers/" + server?.uuid + "/currentRun", {
        headers: defaultHeaders,
        method: "DELETE"
    })

    return response.ok
}

export async function getPreferences(): Promise<UserPreferences | null> {
    const response = await fetch("http://localhost:8080/api/v2/rest/users/current/preferences")

    return response.ok ? response.json() : null
}

export async function updatePreferences(userPreferences: Partial<UserPreferences>): Promise<boolean> {
    const response = await fetch("http://localhost:8080/api/v2/rest/users/current/preferences", {
        headers: jsonHeaders,
        method: "PATCH",
        body: JSON.stringify(userPreferences)
    })

    return response.ok
}