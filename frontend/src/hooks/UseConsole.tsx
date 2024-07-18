import {ConsoleMessage, CurrentRun} from "../APIModels";
import {Accessed, isPresent} from "../networking/backendAPI/AccessError";
import {useCallback, useEffect, useRef, useState} from "react";
import {getConsoleWebsocket} from "../networking/backendAPI/CurrentRuns";

/**
 * A hook that accesses the console for {@link currentRun}.
 * @return an array where the first value is the live-updating console history
 *         and the second value is a function that sends a new message to the console,
 *         returning true on success and false on send failure
 */
export function useConsole(currentRun: Accessed<CurrentRun | null>): [ConsoleMessage[], (message: string) => boolean] {
    const [logHistoryState, setLogHistoryState] = useState<ConsoleMessage[]>([])
    const logHistoryRef = useRef<ConsoleMessage[]>([]);

    function setLogHistory(newLogHistory: ConsoleMessage[]) {
        logHistoryRef.current = newLogHistory // Allows immediate update of the underlying property
        setLogHistoryState(logHistoryRef.current)
    }

    const appendToLogHistory = useCallback((newLine: ConsoleMessage) => {
        setLogHistory([...logHistoryRef.current, newLine])
    }, []);

    const consoleWebsocket = useRef<WebSocket | null>(null);

    const setConsoleWebsocket = useCallback(async (currentRun: Accessed<CurrentRun | null>) => {
        if (isPresent(currentRun)) {
            await setConsoleWebsocket(null) // Clear current websocket, if any

            consoleWebsocket.current = await getConsoleWebsocket(
                currentRun.runnerId,
                currentRun.uuid,
                (event: MessageEvent<string>) => {
                    const message: ConsoleMessage = JSON.parse(event.data)
                    appendToLogHistory(message)
                },
                function (event: CloseEvent) {
                    console.log(`Websocket closed with code ${event.code} and reason ${event.reason}`)
                    if (this === consoleWebsocket.current) consoleWebsocket.current = null
                }
            )
        } else { // currentRun is an AccessError or null
            consoleWebsocket.current?.close()
            consoleWebsocket.current = null
        }
    }, [appendToLogHistory])

    useEffect(() => { // Setup
        // noinspection JSIgnoredPromiseFromCall
        setConsoleWebsocket(currentRun)

        return () => { // Cleanup
            consoleWebsocket.current?.close()
        }
    }, [currentRun, setConsoleWebsocket])


    const sendMessage = (message: string) => {
        const socket = consoleWebsocket.current;
        if (socket !== null) {
            socket.send(message)
            return true
        } else {
            return false
        }
    }

    return [logHistoryState, sendMessage];
}