// noinspection RequiredAttributes

import React, {useEffect, useState} from 'react';
import { Switch, Route, RouteComponentProps } from 'react-router-dom'
import './App.css';
import {
    fetchWithTimeout,
    defaultHeaders, getBackendStatus, BackendStatus,
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
            <div className="header-main-bar">
                <a href="/">
                    <h1>Server Manager</h1>
                </a>
            </div>
        </header>
    )
}

function Body() {
    let [backendStatus, setBackendStatus] = useState<BackendStatus>('offline')

    useEffect(() => {
        getBackendStatus().then(setBackendStatus)
    }, [])

    if (backendStatus === 'offline') {
        return <main><p>Couldn't connect! Are you sure your internet is working?</p></main>
    }

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
