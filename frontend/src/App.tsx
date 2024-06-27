// noinspection RequiredAttributes

import React, {JSX, useEffect, useState} from 'react';
import { Switch, Route, RouteComponentProps } from 'react-router-dom'
import './App.css';
import {ServerList} from "./components/serverList/ServerList";
import {ServerPage} from "./components/ServerPage";

import {BackendStatus, getBackendStatus} from "./networking/backendAPI/BackendStatus";

function Page() {
    const [additionalHeaderElements, setAdditionalHeaderElements] = useState<JSX.Element | null>(null)

    return (
        <div className="App">
            <Header additionalHeaderElements={additionalHeaderElements}/>
            <Body setHeader={setAdditionalHeaderElements}/>
        </div>
    );
}

function Header(props: { additionalHeaderElements: JSX.Element | null }) {
    return (
        <header className="App-header">
            <div className="header-main-bar">
                <a href="/">
                    <h1>Server Manager</h1>
                </a>
                {props.additionalHeaderElements}
            </div>
        </header>
    )
}

function Body(props: { setHeader: (headerElement: JSX.Element) => void }) {
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
                <Route exact path="/">
                    <ServerList setHeader={props.setHeader}/>
                </Route>
            </Switch>
        </main>
    )
}

export default Page;
