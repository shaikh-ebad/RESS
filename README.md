# RESS — Connect Beyond Limits

**An Offline, Decentralized Person-to-Person Communication System Using ESP32 Mesh Networking**

RESS is a prototype communication system designed to enable person-to-person messaging without relying on the internet or centralized messaging servers. It combines an Android application with a network of four ESP32 devices to forward messages between phones connected to different nodes.

**Offline • Secure • Decentralized**

---

## Table of Contents

- [Overview](#overview)
- [Objectives](#objectives)
- [Key Features](#key-features)
- [System Architecture](#system-architecture)
- [Hardware Requirements](#hardware-requirements)
- [Software and Technologies](#software-and-technologies)
- [Network Configuration](#network-configuration)
- [How It Works](#how-it-works)
- [Message Routing](#message-routing)
- [Security](#security)
- [Local AI Assistant](#local-ai-assistant)
- [Installation and Setup](#installation-and-setup)
- [Testing](#testing)
- [Limitations](#limitations)
- [Future Scope](#future-scope)

---

## Overview

Traditional messaging applications generally depend on internet connectivity and centralized infrastructure. RESS explores an alternative approach by using a local wireless network and ESP32 devices to relay messages between Android phones.

Each ESP32 node provides a Wi-Fi access point for nearby phones. The Android application communicates with its connected node using TCP, while ESP32 nodes exchange messages with neighboring nodes through ESP-NOW.

The Android application also maintains local chat history using Room persistence and includes an offline AI assistant designed to respond to supported queries without requiring cloud-based inference.

RESS is an educational prototype intended to demonstrate offline communication, decentralized message forwarding, local data storage, and embedded wireless networking.

## Objectives

- Develop an Android application for offline person-to-person messaging.
- Establish communication between Android phones and ESP32 nodes.
- Forward messages between ESP32 nodes using ESP-NOW.
- Implement basic route discovery and message forwarding.
- Maintain local message history using Room.
- Explore message acknowledgements and duplicate-message protection.
- Implement prototype message encryption.
- Integrate a locally running AI assistant into the Android application.

## Key Features

- **Offline messaging:** Send messages through the local ESP32 network without requiring internet access.
- **Multi-node communication:** Relay messages across multiple ESP32 nodes.
- **Phone-to-node communication:** Use local Wi-Fi and TCP to connect Android phones to ESP32 devices.
- **ESP-NOW communication:** Exchange packets between ESP32 nodes.
- **Message acknowledgements:** Track message delivery acknowledgements in the prototype.
- **Route discovery:** Exchange routing information and select routes based on advertised metrics.
- **Duplicate protection:** Use message identifiers to help prevent repeated processing of the same message.
- **Local chat history:** Store messages on the Android device using Room.
- **Prototype encryption:** Use AES-256-GCM for message encryption.
- **Offline AI assistant:** Run a supported language model locally on Android.

## System Architecture

The system contains two main communication layers:

1. **Android phone to ESP32:** Local Wi-Fi connection using TCP.
2. **ESP32 to ESP32:** Wireless packet exchange using ESP-NOW.

Example architecture:

```text
+------------------+                         +------------------+
| Android Phone A  |                         | Android Phone B  |
|                  |                         |                  |
| Chat Application |                         | Chat Application |
| Room Database    |                         | Room Database    |
+--------+---------+                         +--------+---------+
         |                                            |
     Wi-Fi / TCP                                  Wi-Fi / TCP
         |                                            |
+--------v---------+      ESP-NOW              +------v-----------+
|     NODE_A       | <------------------------> |      NODE_B      |
| ESP32 DevKit V1  |                            | ESP32 DevKit V1  |
+------------------+                            +--------+--------+
                                                        |
                                                    ESP-NOW
                                                        |
                                               +--------v--------+
                                               |      NODE_C     |
                                               | ESP32 DevKit V1 |
                                               +--------+--------+
                                                        |
                                                    ESP-NOW
                                                        |
                                               +--------v--------+
                                               |      NODE_D     |
                                               | ESP32 DevKit V1 |
                                               +-----------------+
```

This is a conceptual illustration of the architecture. The actual route taken by a message depends on the routing information available in the running network.

## Hardware Requirements

- 4 × ESP32 DevKit V1 development boards
- 2 or more Android phones for communication testing
- USB cables for programming and powering ESP32 boards
- A computer for Android development and ESP32 firmware development

## Software and Technologies

| Technology | Purpose |
|---|---|
| Android | Mobile application platform |
| Kotlin/Android development tools | Android application implementation |
| Room | Local message and chat persistence |
| Wi-Fi | Phone-to-node wireless connection |
| TCP | Communication between Android phones and their connected ESP32 nodes |
| ESP-NOW | Wireless communication between ESP32 nodes |
| Arduino IDE | ESP32 firmware development |
| ESP32 Arduino Core 3.3.11 | ESP32 development environment used during testing |
| AES-256-GCM | Prototype authenticated message encryption |
| Gemma 3 1B Instruct (int4) | Local AI assistant model |

The exact Android SDK, Gradle, Kotlin, and dependency versions should be taken from the project configuration.

## Network Configuration

The prototype uses four ESP32 nodes.

| Node | ESP32 MAC Address |
|---|---|
| NODE_A | `34:5F:45:A7:EB:84` |
| NODE_B | `44:1D:64:F3:3F:F4` |
| NODE_C | `84:1F:E8:1B:9D:54` |
| NODE_D | `84:1F:E8:1B:4C:E4` |

**Network settings**

| Parameter | Configuration |
|---|---|
| Wi-Fi mode | Access point |
| Access point password | `12345678` |
| Wi-Fi channel | 1 |
| TCP port | `3333` |
| Phone identity examples | `PHONE_001`, `PHONE_002` |
| Inter-node communication | ESP-NOW |

**Important:** These MAC addresses and network settings should be checked against the actual firmware and Serial Monitor output before deployment. ESP-NOW communication also depends on compatible channel configuration and peer setup.

## How It Works

### 1. Phone Connection

The Android phone connects to the Wi-Fi access point provided by a nearby ESP32 node.

### 2. Message Creation

The user selects a recipient and enters a message in the Android application.

### 3. Message Transmission

The application prepares the message and sends it to its connected ESP32 node over TCP.

### 4. Inter-Node Forwarding

The ESP32 node checks the message destination and available routing information. If the recipient is not directly reachable, the node forwards the message toward the selected next hop using ESP-NOW.

### 5. Message Reception

The destination node forwards the message to the recipient's Android phone through the local TCP connection.

### 6. Local Storage

The Android application stores chat messages in its Room database, allowing conversation history to remain available locally.

### 7. Acknowledgements

The prototype uses message acknowledgements to provide feedback about message delivery or processing, depending on the acknowledgement stage implemented.

## Message Routing

RESS uses a basic distance-vector-like routing approach.

- **HELLO messages:** Help nodes discover neighboring nodes.
- **ROUTE advertisements:** Share information about reachable destinations.
- **Route metrics:** Help nodes select among advertised routes.
- **Message identifiers:** Support duplicate detection.
- **Time to Live (TTL):** Limits how many forwarding steps a message may take.
- **Acknowledgements:** Provide feedback for supported delivery stages.

This is a prototype routing mechanism, not a complete production-grade mesh routing protocol. Route aging, robust link-failure recovery, and advanced link-quality metrics are limitations for future development.

## Security

RESS includes a prototype encryption mechanism using AES-256-GCM.

AES-GCM provides encryption and authentication when implemented with correct key and nonce management. However, the current prototype uses a statically configured shared key rather than a complete secure key-establishment system.

Consequently, RESS should **not be considered production-ready secure messaging**.

Further work is required to implement secure key exchange, per-message nonce management, identity verification, secure provisioning, and comprehensive replay protection. Do not publish encryption keys, credentials, or other secrets in the repository.

## Local AI Assistant

RESS includes an offline AI assistant that runs locally on the Android device rather than on the ESP32 nodes.

The project uses the `gemma3-1B-it-int4.task` model, approximately 529 MB in size. The model file is excluded from Git version control because of its size.

The assistant is intended to answer supported queries without requiring cloud-based model inference. Actual functionality depends on the Android integration, model availability, and device resources.

### Model Setup

If the application requires the model asset:

1. Obtain the correct model file from its authorized source.
2. Follow the application's expected model-loading instructions.
3. Place the model in the location expected by the application.
4. Run the app and verify that the assistant loads successfully.

The model is not included in this repository. Confirm the exact asset path and supported download source from the implementation before distributing setup instructions.

## Installation and Setup

### Prerequisites

Install the tools required for the relevant parts of the project:

- Android Studio
- Android SDK components required by the project
- Arduino IDE
- ESP32 board support package
- Four ESP32 DevKit V1 boards
- Android phones for testing

### Step 1: Clone the Repository

```bash
git clone https://github.com/shaikh-ebad/RESS.git
cd RESS
```

### Step 2: Open the Android Project

1. Open Android Studio.
2. Select **Open** and choose the cloned repository or its Android project directory.
3. Allow Gradle to sync.
4. Resolve any missing SDK or dependency requirements reported by Android Studio.

### Step 3: Configure the ESP32 Firmware

1. Open the relevant ESP32 firmware sketch in Arduino IDE.
2. Select the appropriate ESP32 board and port.
3. Verify each node's identity and MAC address.
4. Check the access point credentials, Wi-Fi channel, TCP port, and ESP-NOW peer configuration.
5. Ensure routing and message-forwarding settings match the deployed topology.

### Step 4: Flash the ESP32 Nodes

Upload the correct firmware to each ESP32 board. Start the nodes and inspect their Serial Monitor output to confirm that initialization, peer discovery, and routing behave as expected.

### Step 5: Configure the Android Application

Verify that the app uses the correct node connection settings and phone identity. Ensure that the phone is connected to the intended ESP32 access point.

### Step 6: Install and Run

Build and install the Android application on the test phones. Connect the phones to their respective nodes and test communication across the network.

**Note:** The exact firmware sketch paths, Android module paths, and build commands depend on the repository structure and should be confirmed in the checked-out source.

## Testing

The project has been developed to explore the following scenarios:

| Test Area | Purpose |
|---|---|
| Phone-to-node connection | Verify local Wi-Fi/TCP connectivity |
| Node discovery | Check whether nodes discover neighboring nodes |
| Cross-node messaging | Verify message forwarding between nodes |
| Acknowledgements | Check the acknowledgement behavior implemented by the firmware |
| Duplicate protection | Verify handling of repeated message identifiers |
| Chat persistence | Confirm messages remain in local Room storage |
| Incoming-message UI | Verify received messages appear in the conversation interface |
| Reconnection | Check behavior after a phone or node reconnects |
| Offline AI | Confirm the assistant works with the local model and without cloud inference |

Cross-node discovery, messaging-related UI updates, and reconnection behavior have been addressed during development. Formal test evidence, detailed test results, and performance measurements should be added from actual test runs before presenting this project as fully validated.

## Limitations

- Communication depends on the availability and configuration of the ESP32 nodes.
- The prototype does not provide internet-independent communication beyond its reachable wireless network.
- Routing and recovery mechanisms are limited compared with mature mesh networking protocols.
- The shared-key encryption approach does not provide complete production-grade key management.
- Packet size and payload constraints limit the amount of data that can be carried in a single ESP-NOW message.
- The offline AI model requires local storage, compatible runtime support, and sufficient device resources.
- Reliability, scalability, range, throughput, and battery consumption require additional systematic testing.

## Future Scope

Possible improvements include:

- More robust route discovery and route-failure recovery.
- Better handling of nodes joining or leaving the network.
- Secure key exchange and device authentication.
- Improved acknowledgement and retransmission mechanisms.
- Message fragmentation and reassembly for larger messages.
- Additional offline features, such as file sharing and emergency broadcasts.
- Battery optimization and low-power operation.
- Performance testing across larger node networks.
- Improved monitoring and network diagnostics.
- Enhanced local AI capabilities and model management.
  
---

## Repository

GitHub: https://github.com/shaikh-ebad/RESS

## Disclaimer

RESS is an educational prototype developed to explore offline communication using Android and ESP32 devices. It is not intended to replace production messaging systems or provide emergency-grade communication guarantees. Security, reliability, and performance depend on the implementation and deployment environment.
