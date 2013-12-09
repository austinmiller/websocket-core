# org.codefrags.websocket

A simple websocket implementation using Java NIO. Relies only on apache common logging and junit.  A single thread can handle hundreds of connection websockets.

## License

## Protocol Compliance

Handles all control frames for version 13.  Handles only UTF-8 text frames at this time.  The library handles fragmented frames and maintains minimal synchronization.

## Example

The project includes a ChatServer example and a chat.html.  This implements a simple chat server that allows multiple people to connect to an end point, change their chat room name, and communicate in all languages supported by UTF-8.

## Maven Dependency

After downloading and running mvn install to put this into the local repository, add this to the project intending to use the code.

```
<dependency>
	<groupId>org.codefrags.websocket</groupId>
	<artifactId>websocket-core</artifactId>
	<versionId>0.1.0-SNAPSHOT</versionId>
</dependency>
```
