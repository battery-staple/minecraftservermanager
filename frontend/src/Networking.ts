import {CurrentRun, Runner, Server} from "./APIModels";

export const defaultHeaders = new Headers();

export function fetchWithTimeout(input: RequestInfo, timeoutMs: number, init?: RequestInit): Promise<Response> {
    return new Promise(((resolve, reject) => {
        fetch(input, init).then(resolve, reject)

        setTimeout(reject, timeoutMs, new Error("Connection timed out"));
    }))
}

export type AccessError = "loading" | "unavailable"

export function fetchServer(serverUUID: string) {
    return fetchWithTimeout(`http://localhost:8080/api/v2/rest/servers/${serverUUID}`, 3000, {
        method: "GET",
        headers: defaultHeaders
    })
        .then((response) => response.ok ? response.json() : null)
        .then((server: Server | null) => {
            if (server === null) throw Error(`Got invalid response getting server`)

            return server
        })
}

export function fetchCurrentRun(serverUUID: string) {
    return fetchWithTimeout(`http://localhost:8080/api/v2/rest/servers/${serverUUID}/currentRun`, 3000, {
        method: "GET",
        headers: defaultHeaders
    })
        .then((response) => {
            if (response.ok)
                return response.json()
            else if (response.status === 404) {
                return null
            } else {
                return undefined
            }
        })
        .then((currentRun: CurrentRun | null) => {
            if (currentRun === undefined) {
                throw Error(`Got invalid response getting runner`)
            }

            return currentRun
        })
}

export function fetchRunner(runnerUUID: string) {
    return fetchWithTimeout(`http://localhost:8080/api/v2/rest/runners/${runnerUUID}`, 3000, {
        method: "GET",
        headers: defaultHeaders
    })
        .then((response) => response.ok ? response.json() : null)
        .then((runner: Runner | null) => {
            if (runner === null) {
                throw Error(`Got invalid response getting runner`)
            }

            return runner
        })
}

export function getCurrentRunWebsocket(runnerUUID: string, serverUUID: string, onMessage: (this: WebSocket, event: MessageEvent<any>) => any) {
    const websocket = new WebSocket(
        `ws://localhost:8080/api/v2/websockets/runners/${runnerUUID}/runs/current?serverId=${serverUUID}`
    )

    websocket.onmessage = onMessage

    return websocket
}

export function getConsoleWebsocket(runnerUUID: string, runUUID: string, onMessage: (this: WebSocket, event: MessageEvent<any>) => any) {
    const websocket = new WebSocket(
        `ws://localhost:8080/api/v2/websockets/runners/${runnerUUID}/runs/current/${runUUID}/console`
    )

    websocket.onmessage = onMessage

    return websocket
}