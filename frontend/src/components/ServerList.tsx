import React, {useEffect, useState} from "react";
import {CurrentRun, DEFAULT_SORT_STRATEGY, Server, SortStrategy} from "../APIModels";
import {fetchCurrentRun, getPreferences, getServers, updatePreferences} from "../Networking";
import {useAutoAnimate} from "@formkit/auto-animate/react";

export function ServerList() {
    const [servers, setServers] = useState<Server[] | null | undefined>(undefined);
    const [sortStrategyState, setSortStrategyState] = useState<SortStrategy | undefined>(undefined)

    // const sortStrategy = sortStrategyState ?? DEFAULT_SORT_STRATEGY
    const setSortStrategy = (newSortStrategy: SortStrategy) => {
        setSortStrategyState(newSortStrategy)
        updatePreferences({
            serverSortStrategy: newSortStrategy
        }).then(success => {
            if (success) {
                setSortStrategyState(newSortStrategy)
            } else {
                console.error("Failed to save preferences update to server")
            }
        })
    }

    const [serversParent] = useAutoAnimate()

    useEffect(() => {
        getPreferences()
            .then(preferences => setSortStrategyState(preferences?.serverSortStrategy))

        getServers()
            .then(servers => setServers(servers))
    }, [])

    if (servers === undefined) {
        return LoadingServers()
    } else if (servers === null) {
        return CannotLoadServers()
    }

    return (
        <div className="server-list">
            <OrderDropdown sortStrategy={sortStrategyState ?? DEFAULT_SORT_STRATEGY} setSortStrategy={setSortStrategy} />
            <div className="container-fluid">
                <div className="row row-cols-1 row-cols-sm-auto g-2" ref={serversParent}>
                    {
                        servers
                            .sort(serverCompareFn(sortStrategyState ?? DEFAULT_SORT_STRATEGY))
                            .map((server) => (
                                <div className="col" key={server.uuid}>
                                    <ServerOption server={server} onClick={() => {
                                        window.location.href = `/servers/${server.uuid}`
                                    }}/>
                                </div>
                            ))
                    }
                </div>
            </div>
        </div>
    );
}

function OrderDropdown(props: {sortStrategy: SortStrategy, setSortStrategy: (sortStrategy: SortStrategy) => void}): React.JSX.Element {
    return <div className="sort-dropdown-bar">
        <div className="dropdown">
            <a className="dropdown-toggle" href="#" role="button" id="dropdownMenuLink" data-bs-toggle="dropdown"
               aria-expanded="false">
                Sorting by: {displayName(props.sortStrategy)}
            </a>
            <ul className="dropdown-menu">
                <li><a className="dropdown-item" onClick={() => {
                    props.setSortStrategy("ALPHABETICAL")
                }}>Alphabetical</a></li>
                <li><a className="dropdown-item" onClick={() => {
                    props.setSortStrategy("NEWEST")
                }}>Newest</a></li>
                <li><a className="dropdown-item" onClick={() => {
                    props.setSortStrategy("OLDEST")
                }}>Oldest</a></li>
            </ul>
        </div>
    </div>;
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
                <li>{address}</li>
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

function serverCompareFn(sortStrategy: SortStrategy): (a: Server, b: Server) => number {
    switch (sortStrategy) {
        case "ALPHABETICAL": return (a, b) => a.name.localeCompare(b.name)
        case "NEWEST": return (a, b) => +b.creationTime - +a.creationTime
        case "OLDEST": return (a, b) => +a.creationTime - +b.creationTime
    }
}

function displayName(sortStrategy: SortStrategy) {
    switch (sortStrategy) {
        case "ALPHABETICAL": return "Alphabetical"
        case "NEWEST": return "Newest"
        case "OLDEST": return "Oldest"
    }
}