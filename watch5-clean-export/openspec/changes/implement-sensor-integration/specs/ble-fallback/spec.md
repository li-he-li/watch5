# BLE Fallback Capability

## ADDED Requirements

### Requirement: Phone BLE GATT Server SHALL

The system SHALL implement a BLE GATT server on the phone as a fallback communication method.

#### Scenario: BLE server startup

**Given** the Bluetooth adapter is available
**When** the BleGattServerService starts
**Then** the server SHALL open a GATT server
**And** the server SHALL create the Heart Rate Service (UUID 0x180D)
**And** the server SHALL add the Heart Rate Measurement characteristic (UUID 0x2A37)
**And** the server SHALL start advertising

#### Scenario: BLE advertising

**Given** the BLE GATT server is running
**When** advertising is active
**Then** the server SHALL advertise with the device name "HeartRate Monitor"
**And** the advertisement SHALL include the Heart Rate Service UUID
**And** the advertising SHALL be discoverable by nearby BLE clients

#### Scenario: Heart rate data update

**Given** a new heart rate reading is received from the watch
**When** the BLE server is connected to a desktop client
**Then** the server SHALL update the Heart Rate Measurement characteristic
**And** the server SHALL notify the connected client
**And** the data SHALL follow the BLE standard format (flags + heart rate value)

#### Scenario: Multiple BLE connections

**Given** the BLE GATT server is running
**When** multiple desktop clients attempt to connect
**Then** the server SHALL support at least 1 stable desktop connection in Phase 2
**And** all connected clients SHALL receive the same data

### Requirement: Desktop BLE GATT Client SHALL

The system SHALL implement a BLE GATT client on the desktop to connect to the phone.

#### Scenario: Device scanning and discovery

**Given** the desktop app is in BLE mode
**When** the user initiates a scan
**Then** the client SHALL scan for BLE devices advertising Heart Rate Service
**And** the scan SHALL timeout after 30 seconds
**And** discovered devices SHALL be displayed in a list

#### Scenario: Connection to phone

**Given** the user selects a phone from the discovered devices list
**When** the user clicks "Connect"
**Then** the client SHALL initiate a BLE GATT connection
**And** the client SHALL discover the Heart Rate Service
**And** the client SHALL subscribe to Heart Rate Measurement notifications
**And** the UI SHALL show "Connected via BLE"

#### Scenario: Receiving heart rate notifications

**Given** the desktop BLE client is connected
**When** the phone updates the Heart Rate Measurement characteristic
**Then** the desktop client SHALL receive a notification callback
**And** the client SHALL parse the BLE data format
**And** the client SHALL extract the heart rate value
**And** the UI SHALL be updated with the new heart rate

#### Scenario: BLE disconnection

**Given** the desktop BLE client is connected
**When** the phone goes out of range (>20 meters)
**Then** the client SHALL detect the disconnection
**And** the UI SHALL show "Disconnected" status
**And** the client SHALL attempt reconnection when the phone is back in range

### Requirement: Manual BLE Target Fallback SHALL

The system SHALL provide an operator-assisted BLE fallback path using phone-displayed relay information and desktop manual target input.

#### Scenario: Phone displays BLE relay connection details

**Given** the phone BLE relay is enabled
**When** the user opens the phone connection page
**Then** the phone SHALL display BLE relay state (on/off)
**And** the phone SHALL display advertised device name
**And** the phone SHALL display Heart Rate Service UUID (0x180D) and Measurement UUID (0x2A37)

#### Scenario: BLE address display is best-effort

**Given** the phone BLE relay is enabled
**When** the phone can resolve a usable local BLE address identifier
**Then** the phone SHALL display the identifier
**And** the UI SHALL label that identifier as potentially changing
**And** if no usable identifier is available, the UI SHALL show that it is unavailable on this device

#### Scenario: Desktop manual BLE target input

**Given** BLE scan-based discovery fails on desktop
**And** the user can read BLE details from phone UI
**When** the user manually enters a BLE target identifier on desktop
**Then** the desktop SHALL attempt direct connection/read using the entered target
**And** the desktop SHALL allow users to edit or clear the saved manual target

### Requirement: BLE Data Format SHALL

The system SHALL implement the standard BLE Heart Rate Service data format.

#### Scenario: Standard 8-bit heart rate format

**Given** the heart rate is 鈮?55 BPM
**When** encoding for BLE transmission
**Then** the flags byte SHALL be 0x00 (8-bit format)
**And** the heart rate SHALL be encoded as a single uint8
**And** the total data length SHALL be 2 bytes

#### Scenario: Standard 16-bit heart rate format

**Given** the heart rate is >0 or requires 16-bit precision
**When** encoding for BLE transmission
**Then** the flags byte SHALL be 0x01 (16-bit format)
**And** the heart rate SHALL be encoded as uint16 little-endian
**And** the total data length SHALL be 3 bytes

#### Scenario: Optional energy expended

**Given** the system tracks energy expenditure
**When** encoding for BLE transmission
**Then** the flags byte bit 2 SHALL be set to 1
**And** 2 bytes of energy expended (uint16) SHALL follow the heart rate
**And** the total data length SHALL be 5 bytes

#### Scenario: Optional RR-Intervals

**Given** the sensor provides RR-intervals (time between beats)
**When** encoding for BLE transmission
**Then** the flags byte bit 4 SHALL be set to 1
**And** RR-intervals SHALL follow as uint16 array
**And** each RR-interval SHALL be in 1/1024 second units

### Requirement: BLE Performance SHALL

The system SHALL meet performance requirements for BLE communication.

#### Scenario: Connection latency

**Given** the desktop client attempts to connect to the phone
**When** the BLE connection is established
**Then** the connection SHALL complete within 5 seconds
**And** the discovery SHALL complete within 3 seconds

#### Scenario: Data transmission latency

**Given** a heart rate reading is ready
**When** transmitted via BLE notification
**Then** the notification SHALL be received within 1 second (p95)
**And** the total system latency (sensor to desktop) SHALL be <1.5 seconds (p95)

#### Scenario: BLE range

**Given** the desktop client is connected via BLE
**When** the phone moves beyond 20 meters
**Then** the connection SHALL be lost
**And** when the phone returns within 10 meters
**Then** the connection SHALL be recoverable

### Requirement: BLE Permissions SHALL

The system SHALL request necessary BLE permissions on Android.

#### Scenario: Android 12+ BLE permissions

**Given** the phone app is running on Android 12+
**When** the app needs to use BLE
**Then** the app SHALL request BLUETOOTH_SCAN permission
**And** the app SHALL request BLUETOOTH_CONNECT permission
**And** the app SHALL request BLUETOOTH_ADVERTISE permission
**And** the app SHALL show an explanation for each permission

#### Scenario: Permission denied handling

**Given** the user denies BLUETOOTH_SCAN permission
**When** the app attempts to use BLE
**Then** the app SHALL display an error message
**And** the app SHALL explain that BLE is required for communication
**And** the app SHALL provide a button to open app settings

### Requirement: BLE Error Handling SHALL

The system SHALL gracefully handle BLE errors and edge cases.

#### Scenario: Bluetooth not available

**Given** the phone does not have Bluetooth
**Or** Bluetooth is disabled in settings
**When** the app attempts to start the BLE server
**Then** the app SHALL display an error message
**And** the app SHALL prompt the user to enable Bluetooth
**And** the app SHALL not crash

#### Scenario: BLE advertising failure

**Given** the BLE server attempts to start advertising
**When** advertising fails (hardware error, etc.)
**Then** the server SHALL log the error
**And** the server SHALL retry advertising after 5 seconds
**And** the server SHALL notify the user of the failure

#### Scenario: Characteristic write failure

**Given** the desktop client is connected
**When** a write operation fails
**Then** the client SHALL log the error
**And** the client SHALL attempt reconnection
**And** the client SHALL notify the user of the failure

### Requirement: BLE Battery Optimization SHALL

The system SHALL optimize BLE for battery efficiency.

#### Scenario: Advertising interval optimization

**Given** the BLE server is advertising
**When** configuring advertising parameters
**Then** the advertising interval SHALL be 1000ms (1 second)
**And** the timeout SHALL be 30 seconds
**And** the settings SHALL balance discovery speed with battery usage

#### Scenario: Connection interval optimization

**Given** a BLE connection is established
**When** negotiating connection parameters
**Then** the connection interval SHALL be 30-50ms
**And** the slave latency SHALL be 0 (low latency)
**And** the supervision timeout SHALL be 2000ms

#### Scenario: Adaptive connection parameters

**Given** the system is in low-activity mode (sleeping, sitting)
**When** the heart rate is stable
**Then** the connection interval SHALL be increased to 100ms
**And** when activity increases, the interval SHALL return to 30-50ms

### Requirement: BLE Fallback Trigger SHALL

The system SHALL automatically fall back to BLE when WebSocket is unavailable.

#### Scenario: WebSocket connection timeout

**Given** the desktop app attempts WebSocket connection
**When** the connection attempt times out after 3 retries
**Then** the system SHALL automatically initiate BLE scanning
**And** the system SHALL notify the user: "WebSocket unavailable, trying BLE..."

#### Scenario: WebSocket connection lost

**Given** the desktop app is connected via WebSocket
**When** the connection is lost unexpectedly
**Then** the system SHALL attempt WebSocket reconnection 3 times
**And** if reconnection fails, the system SHALL initiate BLE fallback
**And** the system SHALL notify the user of the mode change

#### Scenario: WebSocket recovery from BLE

**Given** the system is connected via BLE fallback
**When** the WebSocket server becomes available again
**Then** the system SHALL attempt to reconnect via WebSocket every 30 seconds
**And** upon successful WebSocket connection, the system SHALL disconnect BLE
**And** the system SHALL notify the user: "Reconnected via WebSocket"

### Requirement: BLE and WebSocket Coexistence SHALL

The system SHALL manage both WebSocket and BLE services simultaneously.

#### Scenario: Both services running

**Given** the phone app is running
**When** both WebSocket and BLE services are started
**Then** both services SHALL run concurrently
**And** WebSocket SHALL be the primary (preferred) connection
**And** BLE SHALL be the fallback connection
**And** the services SHALL not interfere with each other

#### Scenario: Service priority

**Given** a desktop client is connected via BLE fallback
**When** a new client connects via WebSocket
**Then** the WebSocket connection SHALL take priority
**And** the BLE fallback SHALL remain available for other clients
**And** the system SHALL support mixed connections (some WebSocket, some BLE)



