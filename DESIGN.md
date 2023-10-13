# Design
## REST API

## Websockets API


## Authentication
Users are authenticated via OIDC, with only Google Accounts currently supported.
Credentials are persisted in a cookie for 30 days.

## Authorization
Only authorized users can access the application. 
Authorized User IDs are stored, newline separated, in a file on the server, at `/minecraftservermanager/whitelist.txt` by default.

## Users
Users are represented by their unique `UserID`, currently implemented as their Google User ID.
A list of all users is not actually stored; only their preferences are.
### Preferences 
User preferences currently only include their preference for sorting of servers on the main page.