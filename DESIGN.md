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

## Logging
Logging is done through SLF4J and Logback.

### Log Levels
In general, log levels are used in the following way:
- `INFO`: Relevant information about application events on a high level
- `DEBUG`: More specific information about what the application is doing; many methods have one `DEBUG` level log message at the start of their execution
- `TRACE`: Fine grained information about application actions. Almost exclusively used within methods, describing the sub-steps a method uses to achieve its goal.
- `WARN`: Something slightly strange has occurred that could potentially hint at a fault, but which is entirely recoverable and most likely not a big deal
- `ERROR`: Something that likely indicates a fault has occurred. Typically, the application will not be able to fully recover.

Note that "fault" as described in `WARN` and `ERROR` refers to bugs in the application itself; user errors (such as trying to GET a server that does not exist) are not considered errors or warnings.