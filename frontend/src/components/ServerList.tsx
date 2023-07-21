import React, {useEffect, useState} from "react";
import {CurrentRun, Server} from "../APIModels";
import {defaultHeaders, fetchCurrentRun, fetchWithTimeout} from "../Networking";

export function ServerList() {
    const [servers, setServers] = useState<Server[] | null | undefined>(undefined);

    useEffect(() => {
        fetchWithTimeout("http://localhost:8080/api/v2/rest/servers", 3000, {
            method: "GET",
            headers: defaultHeaders
        })
            .then((response) => response.json())
            .then((json) => { setServers(json) })
            .catch(() => { setServers(null) })
    }, [])

    if (servers === undefined) {
        return LoadingServers()
    } else if (servers === null) {
        return CannotLoadServers()
    }

    return (
        <div className="server-list">
            {
                servers.map((server) => (
                    <ServerOption server={server} onClick={() => {
                        window.location.href = `/servers/${server.uuid}`
                    }}/>
                ))
            }
        </div>
    );
}

function ServerOption(props: { server: Server, onClick: () => void }): React.JSX.Element {
    const [currentRun, setCurrentRun] = useState<CurrentRun | null>(null);

    useEffect(() => {
        fetchCurrentRun(props.server.uuid)
            .then(currentRun => setCurrentRun(currentRun))
    }, [props.server.uuid])

    const isRunning = () => currentRun !== null

    let address = null
    if (currentRun?.address) {
        address = <li><strong>address: </strong> {currentRun.address.fullAddress}</li>
    }

    return (
        <button className="server-button" onClick={props.onClick}>
            <div className="server-button-title">{props.server.name}</div>
            <ul className="server-button-properties">
                <li><strong>version: </strong> {props.server.version}</li>
                {address}
            </ul>
            <div className="server-button-status" style={{
                color: isRunning() ? "green" : "red"
            }}>{isRunning() ? "Running" : "Not Running"}</div>
        </button>
    )
}

function CannotLoadServers(): React.JSX.Element {
    return (
        <p>Cannot load servers :(</p>
    )
}

function LoadingServers(): React.JSX.Element {
    return (
        <p>Loading servers...</p>
    )
}