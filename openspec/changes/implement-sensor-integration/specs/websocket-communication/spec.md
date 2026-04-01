# WebSocket Communication Capability

## ADDED Requirements

### Requirement: Phone WebSocket Server SHALL

The system SHALL implement a WebSocket server on the phone to broadcast heart rate data to desktop clients.

#### Scenario: Server startup and binding

**Given** the phone app starts the WebSocketServerService
**When** the service initializes
**Then** the server SHALL bind to the configured port (default 8080)
**And** the server SHALL listen for incoming WebSocket connections
**And** the server SHALL log the startup success

#### Scenario: Client connection

**Given** the WebSocket server is running on port 8080
**When** a desktop client connects to ws://phone-ip:8080/ws
**Then** the server SHALL accept the connection
**And** the server SHALL add the client to the broadcast list
**And** the server SHALL send a welcome message

#### Scenario: Multiple client connections

**Given** the WebSocket server is running
**When** multiple desktop clients connect (e.g., 3 computers)
**Then** the server SHALL accept all connections
**And** the server SHALL broadcast heart rate data to all clients
**And** each client SHALL receive the same data simultaneously

#### Scenario: Heart rate data broadcast

**Given** the WebSocket server has active clients
**When** a new heart rate reading is received from the watch
**Then** the server SHALL serialize the HeartRateData to JSON
**And** the server SHALL broadcast the JSON to all connected clients
**And** the broadcast SHALL complete within 300ms (p95)

#### Scenario: Client disconnection

**Given** a desktop client is connected
**When** the client disconnects (gracefully or unexpectedly)
**Then** the server SHALL remove the client from the broadcast list
**And** the server SHALL continue serving other clients
**And** the server SHALL log the disconnection

#### Scenario: Server configuration

**Given** the user wants to change the WebSocket port
**When** the user updates the port in Settings
**Then** the server SHALL restart on the new port
**And** the port SHALL be validated (1024-65535)
**And** the configuration SHALL persist across app restarts

### Requirement: Desktop WebSocket Client SHALL

The system SHALL implement a WebSocket client on the desktop to receive heart rate data from the phone.

#### Scenario: Connection to phone server

**Given** the desktop app is running
**And** the user enters the phone IP address (or uses auto-discovery)
**When** the user clicks "Connect"
**Then** the client SHALL connect to ws://phone-ip:8080/ws
**And** the client SHALL show "Connecting..." status
**And** upon successful connection, show "Connected via WebSocket"

#### Scenario: Receiving heart rate data

**Given** the desktop client is connected to the phone
**When** the phone sends a heart rate data message
**Then** the client SHALL parse the JSON message
**And** the client SHALL update the UI with the new heart rate
**And** the update SHALL complete within 300ms (p95) of receiving the message

#### Scenario: Connection loss and reconnection

**Given** the desktop client is connected to the phone
**When** the connection is lost (phone out of range, app closed, etc.)
**Then** the client SHALL detect the disconnection
**And** the client SHALL show "Disconnected" status
**And** the client SHALL attempt reconnection after 2 seconds
**And** reconnection attempts SHALL continue with exponential backoff (max 30s)

#### Scenario: Manual disconnect

**Given** the desktop client is connected
**When** the user clicks "Disconnect"
**Then** the client SHALL gracefully close the WebSocket connection
**And** the client SHALL show "Disconnected" status
**And** the client SHALL stop reconnection attempts

#### Scenario: Ping-pong health check

**Given** the desktop client is connected
**When** the client has not received data for 30 seconds
**Then** the client SHALL send a ping message
**And** the server SHALL respond with pong
**And** if pong is not received within 5 seconds, the client SHALL reconnect

### Requirement: WebSocket Discovery SHALL

The system SHALL provide automatic discovery of the phone WebSocket server.

#### Scenario: mDNS service discovery

**Given** the phone WebSocket server is running
**When** the server starts, it SHALL advertise via mDNS as `_heartrate._tcp.local`
**And** the desktop app SHALL scan for this service
**When** the desktop app finds the service
**Then** the desktop app SHALL auto-populate the IP address
**And** the user can click "Connect" without manual IP entry

#### Scenario: Manual IP configuration

**Given** mDNS discovery is not available or fails
**When** the user wants to connect
**Then** the user SHALL be able to manually enter the IP address and port
**And** the system SHALL validate the IP format
**And** the system SHALL save the last successful connection for quick reconnect

#### Scenario: Multiple phones on network

**Given** there are multiple phones running the app on the network
**When** the desktop app scans for services
**Then** the desktop app SHALL show all discovered phones
**And** the user SHALL select which phone to connect to
**And** the selection SHALL be remembered

### Requirement: Manual Endpoint Fallback SHALL

The system SHALL provide an operator-assisted fallback path where the phone displays connection details and the desktop connects using manual input.

#### Scenario: Phone displays current WebSocket endpoint

**Given** the phone WebSocket relay is enabled
**When** the user opens the phone connection page
**Then** the phone SHALL display the current LAN IPv4 address
**And** the phone SHALL display the full endpoint `ws://<ip>:<port>/heartrate`
**And** the phone SHALL provide a copyable endpoint string

#### Scenario: Endpoint auto-refresh on network change

**Given** the phone is displaying the current endpoint
**When** the active network changes (Wi-Fi reconnect, SSID switch, hotspot switch)
**Then** the displayed endpoint SHALL refresh automatically
**And** stale endpoint values SHALL not be shown as current

#### Scenario: Desktop manual endpoint connection

**Given** service discovery is unavailable or unreliable
**And** the user can read the endpoint from the phone UI
**When** the user enters the endpoint on desktop and clicks connect
**Then** the desktop SHALL attempt connection using the entered endpoint
**And** the desktop SHALL persist the last successful endpoint for quick reconnect

### Requirement: WebSocket Data Format SHALL

The system SHALL use a standardized JSON format for WebSocket messages.

#### Scenario: Heart rate data message

**Given** a heart rate reading is ready to send
**When** serialized to JSON for WebSocket
**Then** the JSON SHALL have this format:
```json
{
  "type": "heart_rate",
  "timestamp": 1678123456789,
  "heartRate": 72,
  "deviceId": "galaxy-watch-5",
  "batteryLevel": 85,
  "signalQuality": 95
}
```

#### Scenario: Status message

**Given** the system needs to send status updates
**When** a status message is sent
**Then** the JSON SHALL have this format:
```json
{
  "type": "status",
  "status": "connected",
  "message": "Monitoring active"
}
```

#### Scenario: Error message

**Given** an error occurs on the phone
**When** an error message is sent
**Then** the JSON SHALL have this format:
```json
{
  "type": "error",
  "code": "SENSOR_UNAVAILABLE",
  "message": "Heart rate sensor not found"
}
```

### Requirement: WebSocket Performance SHALL

The system SHALL meet performance requirements for WebSocket communication.

#### Scenario: Latency requirement

**Given** a heart rate reading is received on the phone
**When** it is transmitted via WebSocket to the desktop
**Then** the end-to-end latency SHALL be <300ms (p95)
**And** the total system latency (sensor to desktop) SHALL be <1.5 seconds (p95)

#### Scenario: Throughput requirement

**Given** the system is sampling at 5 Hz (5 readings/second)
**When** transmitting all readings via WebSocket
**Then** the WebSocket SHALL handle the throughput
**And** no readings SHALL be dropped
**And** the desktop SHALL receive all readings in near real-time

#### Scenario: Bandwidth usage

**Given** the system is transmitting at 5 Hz
**When** measuring bandwidth consumption
**Then** the WebSocket SHALL use <1 KB/second
**And** the bandwidth SHALL not impact network performance

### Requirement: WebSocket Security SHALL

The system SHALL implement basic security measures for WebSocket communication.

#### Scenario: Local network only

**Given** the WebSocket server is configured for local use
**When** binding to the network interface
**Then** the server SHALL bind to 0.0.0.0 (all interfaces)
**And** the server SHALL not require encryption for local network (ws://)
**And** the server SHALL log all connection attempts

#### Scenario: Remote access (optional)

**Given** the user wants to access the phone from outside the local network
**When** remote access is enabled
**Then** the server SHALL support WSS (WebSocket Secure)
**And** the server SHALL use TLS encryption
**And** the server SHALL require authentication

### Requirement: WebSocket Fallback SHALL

The system SHALL fall back to BLE when WebSocket is unavailable.

#### Scenario: WebSocket unavailable

**Given** the desktop app cannot connect via WebSocket
**When** the connection attempt fails after 3 retries
**Then** the system SHALL attempt BLE connection
**And** the system SHALL notify the user of the fallback
**And** the UI SHALL show "Connected via BLE (fallback)"

#### Scenario: WebSocket recovery

**Given** the system is connected via BLE fallback
**When** WebSocket becomes available again
**Then** the system SHALL attempt to reconnect via WebSocket
**And** upon successful WebSocket connection, the system SHALL disconnect BLE
**And** the UI SHALL show "Connected via WebSocket"


