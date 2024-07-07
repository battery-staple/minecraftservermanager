export const defaultHeaders = new Headers();

if (process.env.NODE_ENV !== 'production') {
    const debugUsername = "User McUserface"
    const debugPassword = "Super secure password"
    defaultHeaders.append("Authorization", "Basic " + btoa(debugUsername + ":" + debugPassword));
}

export const jsonHeaders: Headers = new Headers(defaultHeaders)
jsonHeaders.append("Content-Type", "application/json")

/**
 * The timeout for typical requests that are expected to complete quickly
 */
export const DEFAULT_TIMEOUT_MS = 3000

/**
 * The timeout for potentially processing-intensive requests that may take a while
 */
export const LONG_TIMEOUT_MS = 15000

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

