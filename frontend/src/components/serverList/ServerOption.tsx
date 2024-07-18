import {Server} from "../../APIModels";
import {JSX, useCallback, useMemo} from "react";
import {deleteServer} from "../../networking/backendAPI/Servers";
import {useCurrentRunLive} from "../../hooks/UseCurrentRunLive";
import {isError} from "../../networking/backendAPI/AccessError";

export function ServerOption(props: {
    server: Server,
    editing: boolean,
    setEditing: (editing: boolean) => void,
    onClick: () => void
}): JSX.Element {
    const currentRun = useCurrentRunLive(props.server);

    const isRunning = useCallback(() => currentRun !== null, [currentRun]);

    const properties = useMemo(() => {
        if (!isError(currentRun) && currentRun?.address) { // Server is running
            return <ul className="server-option-properties">
                <li><strong>host: </strong> {currentRun.address.host}</li>
                <li><strong>port: </strong> {currentRun.address.port}</li>
            </ul>
        } else { // Server is not running
            return <ul className="server-option-properties">
                <li><strong>version: </strong> {props.server.version}</li>
                <li><br/></li>
            </ul>
        }
    }, [currentRun, props.server.version]);

    return (
        <div className="server-option">
            {props.editing ?
                <button className="server-option-x" onClick={() => deleteServer(props.server.uuid)}>
                    <img src="/X.png" alt=""></img>
                </button> : null}
            <button className="server-option-button" onClick={props.onClick} disabled={props.editing}>
                <div className="server-option-title">{props.server.name}</div>
                {properties}
                <div className="server-option-status" style={{
                    color: isRunning() ? "green" : "red"
                }}>{isRunning() ? "Running" : "Not Running"}</div>
            </button>
        </div>
    )
}