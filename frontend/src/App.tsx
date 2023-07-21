// noinspection RequiredAttributes

import React, {useEffect, useState} from 'react';
import { Switch, Route, RouteComponentProps } from 'react-router-dom'
import './App.css';
import {CurrentRun, Runner, Server} from "./APIModels";
import {
    fetchWithTimeout,
    defaultHeaders,
    fetchServer,
    fetchCurrentRun,
    fetchRunner,
    getCurrentRunWebsocket, getConsoleWebsocket
} from "./Networking";
import {ServerList} from "./components/ServerList";
import {ServerPage} from "./components/ServerPage";

function Page() {
    return (
        <div className="App">
            <Header />
            <Body />
        </div>
    );
}

function Header() {
    return (
        <header className="App-header">
            <a href="/">
                <h1>Server Manager</h1>
            </a>
        </header>
    )
}

function Body() {
    type BackendStatus = "up" | "offline" | "unauthorized"
    let [backendStatus, setBackendStatus] = useState<BackendStatus>('offline')

    useEffect(() => {
        fetchWithTimeout("http://localhost:8080/api/v2/rest/status", 3000, {
            method: "GET",
            headers: defaultHeaders
        }).then(response => {
            if (response.ok) {
                setBackendStatus('up')
            } else if (response.status === 401 || response.status === 403) {
                setBackendStatus('unauthorized')
            }
        }).catch(() => {
            setBackendStatus('offline')
        })
    })

    return (
        <main className="App-body">
            <Switch>
                <Route exact path="/servers/:serverId" component={({ match }: RouteComponentProps<{ serverId: string }>) => (
                    <ServerPage serverUUID={match.params.serverId} />
                )}/>
                <Route exact path="/test">
                    <p>hi</p>
                </Route>
                <Route exact path="/">
                    <ServerList/>
                </Route>
            </Switch>
        </main>
    )
}

export default Page;
