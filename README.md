```markdown
# RESS — Connect Beyond Limits

<p align="center">
  <strong>Offline • Secure • Decentralized</strong>
</p>

<p align="center">
  An offline communication system using Android, ESP32 mesh networking, end-to-end encryption, and a local AI assistant.
</p>

---

## 📌 Overview

**RESS** is an offline communication system designed to allow users to exchange messages without depending on the Internet or cellular networks.

The system combines:

- Android smartphones
- ESP32-based mesh networking
- Wi-Fi access points
- ESP-NOW communication
- TCP communication
- End-to-end message encryption
- Local message storage
- An offline AI assistant

A smartphone connects to a nearby ESP32 node through Wi-Fi. The ESP32 nodes communicate with each other using ESP-NOW and forward messages through the mesh until they reach the node connected to the destination phone.

The message is encrypted on the Android device before entering the mesh network.

> **Stay connected, even without the Internet.**

---

# ✨ Features

## 📡 Offline Communication

RESS allows users to communicate without requiring a conventional Internet connection or cellular network.

## 🌐 ESP32 Mesh Networking

Multiple ESP32 nodes form a mesh network that allows communication to travel across multiple nodes.

## 🔀 Dynamic Routing

ESP32 nodes maintain routing information and determine the next hop toward a destination node.

## 📱 Phone Discovery

Phones connected to different ESP32 nodes can be discovered across the mesh network.

## 💬 Person-to-Person Messaging

Users communicate using unique phone IDs.

Example:

```text
PHONE_001
PHONE_002
PHONE_003
```

The user does not need to manually select or manage ESP32 nodes.

## 🔐 End-to-End Encryption

Messages are encrypted on the Android device before transmission.

Intermediate ESP32 nodes forward the encrypted data without modifying the ciphertext.

## 🛡️ AES-GCM Encryption

The Android application uses AES-GCM encryption, providing confidentiality and authentication for encrypted messages.

## 🤖 Offline AI Assistant

RESS includes a local AI assistant capable of operating without an Internet connection using a locally stored Gemma model.

## 💾 Local Chat Storage

Messages are stored locally on the Android device using Room Database.

## 🎨 Custom RESS Interface

The Android application includes a custom interface based on the RESS visual identity.

---

# 🏗️ System Architecture

RESS consists of three main layers:

```text
┌───────────────────────────────────────┐
│           ANDROID APPLICATION         │
│                                       │
│  • User Interface                     │
│  • Chat                               │
│  • Phone Identity                     │
│  • Message Encryption                 │
│  • Room Database                      │
│  • Local AI Assistant                 │
│                                       │
└───────────────────┬───────────────────┘
                    │
                    │ Wi-Fi / TCP
                    │
                    ▼
┌───────────────────────────────────────┐
│             ESP32 NODE                │
│                                       │
│  • Wi-Fi Access Point                 │
│  • TCP Server                         │
│  • Phone Registration                 │
│  • Phone Discovery                    │
│  • Routing                            │
│  • Message Delivery                   │
│                                       │
└───────────────────┬───────────────────┘
                    │
                    │ ESP-NOW
                    │
                    ▼
┌───────────────────────────────────────┐
│            ESP32 MESH                 │
│                                       │
│       NODE_A                           │
│       NODE_B                           │
│       NODE_C                           │
│       NODE_D                           │
│                                       │
└───────────────────────────────────────┘
```

---

# 🔄 How RESS Works

## 1. Android Phone Connects to ESP32

The smartphone connects to the Wi-Fi access point created by a nearby ESP32 node.

For example:

```text
ESP32_MESH_A
```

The ESP32 provides the local network connection required by the Android application.

---

## 2. Phone Registration

The Android application registers its unique phone ID with the connected ESP32.

Example:

```text
PHONE_001
```

The ESP32 associates the phone with its node.

Example:

```text
PHONE_001 → NODE_A
```

---

## 3. Phone Discovery

ESP32 nodes advertise information about phones connected to their nodes.

For example:

```text
PHONE_001 → NODE_A
PHONE_002 → NODE_D
```

This allows a phone connected to NODE_A to discover a phone connected to NODE_D.

---

## 4. Message Encryption

Before the message is transmitted to the ESP32, the Android application encrypts it.

The ESP32 receives encrypted ciphertext rather than the original plaintext.

Conceptually:

```text
Plaintext
    ↓
Android Encryption
    ↓
Ciphertext
    ↓
ESP32 Mesh
```

---

## 5. Destination Lookup

The ESP32 determines which node currently hosts the destination phone.

For example:

```text
PHONE_002 → NODE_D
```

---

## 6. Route Selection

The source ESP32 checks its routing table to determine the next node.

For example:

```text
NODE_A → NODE_B → NODE_C → NODE_D
```

---

## 7. Mesh Forwarding

The encrypted message is forwarded from node to node using ESP-NOW.

Intermediate nodes do not need to decrypt the message.

```text
NODE_A
   ↓
NODE_B
   ↓
NODE_C
   ↓
NODE_D
```

---

## 8. Destination Delivery

When the message reaches NODE_D, the node checks whether the destination phone is connected.

If the destination phone is:

```text
PHONE_002
```

the encrypted message is delivered to that Android device.

---

## 9. Android Decryption

The destination Android application decrypts the ciphertext and displays the original message.

```text
Ciphertext
    ↓
Android Decryption
    ↓
Original Message
```

---

# 📡 ESP32 Mesh Network

The current prototype uses four ESP32 nodes:

```text
NODE_A
NODE_B
NODE_C
NODE_D
```

Each node has its own Wi-Fi access point.

Example:

```text
NODE_A → ESP32_MESH_A
NODE_B → ESP32_MESH_B
NODE_C → ESP32_MESH_C
NODE_D → ESP32_MESH_D
```

The ESP32 nodes communicate with each other using **ESP-NOW**.

---

# 🔀 Routing

RESS uses routing information maintained by the ESP32 nodes.

Each node can maintain information similar to:

```text
Destination    Next Hop    Hops
--------------------------------
NODE_A         NODE_A      0
NODE_B         NODE_B      1
NODE_C         NODE_B      2
NODE_D         NODE_C      3
```

The routing system allows messages to travel through multiple ESP32 nodes instead of requiring every phone to connect to the same ESP32.

---

# 📱 Phone Discovery

Phone discovery allows ESP32 nodes to share information about connected phones.

Example:

```text
Phone ID       Node
----------------------
PHONE_001      NODE_A
PHONE_002      NODE_D
```

This allows the source node to determine where the destination phone is located.

---

# 💬 Message Flow Example

Suppose:

```text
PHONE_001
```

is connected to:

```text
NODE_A
```

and:

```text
PHONE_002
```

is connected to:

```text
NODE_D
```

The communication path can be:

```text
PHONE_001
    │
    ▼
 NODE_A
    │
    ▼
 NODE_B
    │
    ▼
 NODE_C
    │
    ▼
 NODE_D
    │
    ▼
PHONE_002
```

The response follows the routing system back toward the originating phone.

---

# 🔐 Security

RESS uses encryption on the Android side before messages enter the mesh network.

The current Android implementation uses:

```text
AES
GCM Mode
256-bit Key
12-byte Nonce
128-bit Authentication Tag
```

The ESP32 nodes transport the encrypted data through the mesh.

Intermediate ESP32 nodes do not need to decrypt the message.

### Important Prototype Note

The current encryption-key arrangement is designed for the academic prototype.

A production implementation should use a secure key-exchange and key-management mechanism instead of relying on a static application-level key.

---

# 🤖 Offline AI Assistant

RESS includes a local AI assistant.

The AI is designed to operate locally on the Android device rather than requiring an Internet connection.

The project uses a Gemma model stored as a `.task` file.

## Why the AI model is not included in GitHub

The model file is approximately **529 MB**.

GitHub's standard repository file limit is **100 MB per file**, so the model cannot be included directly in this repository.

The model is therefore excluded from Git using `.gitignore`.

---

# 🧠 AI Model Setup

After cloning the project, the AI model must be added separately.

The expected filename is:

```text
gemma3-1B-it-int4.task
```

Place it inside:

```text
app/src/main/assets/
```

The final structure should look like:

```text
RESS/
│
├── app/
│   └── src/
│       └── main/
│           └── assets/
│               └── gemma3-1B-it-int4.task
│
└── README.md
```

After adding the model, build the Android application normally.

> The repository does not redistribute the model file. Obtain the required model separately from its appropriate source and comply with its license and usage terms.

---

# 🧰 Technologies Used

## Android

- Kotlin
- Android Studio
- Android SDK
- Room Database
- TCP sockets
- Wi-Fi networking
- AES-256-GCM
- Local AI inference

## ESP32

- ESP32 DevKit V1
- Arduino framework
- Wi-Fi
- ESP-NOW
- TCP server
- Dynamic routing
- Phone discovery

## AI

- Gemma 3 1B
- Local inference
- `.task` model format

---

# 🔧 Hardware Requirements

For the current prototype:

| Component | Quantity |
|---|---:|
| ESP32 DevKit V1 | 4 |
| Android smartphones | 2 or more |
| USB data cable | 1 or more |
| Computer | 1 |
| Wi-Fi capable Android device | Required |

The number of ESP32 nodes can be changed depending on the desired mesh topology.

---

# 💻 Software Requirements

You will need:

- Android Studio
- Android SDK
- Kotlin support
- Arduino IDE
- ESP32 board support for Arduino
- Git
- Android smartphone with USB debugging enabled

---

# 📥 Getting the Project

Clone the repository:

```bash
git clone https://github.com/shaikh-ebad/RESS.git
```

Enter the project directory:

```bash
cd RESS
```

Open the project in Android Studio.

Allow Gradle to synchronize the project.

---

# 📱 Building the Android Application

Open the project in Android Studio.

Then select:

```text
Build → Make Project
```

To run the application:

```text
Run → Run 'app'
```

Connect an Android phone with USB debugging enabled, or use a compatible Android emulator.

---

# 📡 ESP32 Setup

The ESP32 firmware is responsible for:

- Creating the Wi-Fi access point
- Running the TCP server
- Registering Android phones
- Discovering phones
- Discovering other ESP32 nodes
- Maintaining routing information
- Forwarding messages
- Delivering messages
- Sending delivery acknowledgements

The current prototype uses:

```text
NODE_A
NODE_B
NODE_C
NODE_D
```

Each board is configured with its corresponding node identity.

---

# 🧩 ESP32 Communication

Android communicates with the ESP32 using TCP.

The ESP32 nodes communicate with each other using ESP-NOW.

Therefore, RESS uses two communication layers:

```text
Android ↔ ESP32
     Wi-Fi / TCP

ESP32 ↔ ESP32
     ESP-NOW
```

This combination allows phones to use normal Wi-Fi networking while the ESP32 nodes form the mesh backbone.

---

# 📨 Message Packet

The ESP32 message structure contains information such as:

```text
Message Type
Sender Phone ID
Receiver Phone ID
Origin Node
Destination Node
Message ID
TTL
Encrypted Data
```

The encrypted data is carried inside the message packet.

The ciphertext is forwarded without being modified by intermediate nodes.

---

# ⏳ TTL and Duplicate Protection

Messages contain a **TTL (Time To Live)** value.

TTL helps prevent a packet from being forwarded indefinitely.

The mesh also maintains information about previously seen messages.

This helps prevent duplicate messages from being processed repeatedly.

---

# ✅ Delivery Acknowledgement

RESS supports delivery acknowledgements.

When the destination node receives a message, an acknowledgement can be sent toward the originating node.

Conceptually:

```text
PHONE_001
    │
    │ Message
    ▼
NODE_A → NODE_B → NODE_C → NODE_D
                              │
                              ▼
                           PHONE_002

PHONE_002
    │
    │ ACK
    ▼
NODE_D → NODE_C → NODE_B → NODE_A
                              │
                              ▼
                         PHONE_001
```

---

# 💾 Local Database

The Android application uses **Room Database** to store local message history.

This provides persistent chat storage on the device.

Messages can therefore remain available even when the mesh connection is temporarily unavailable.

---

# 🗂️ Project Structure

```text
RESS/
│
├── app/
│   ├── src/
│   │   ├── androidTest/
│   │   │
│   │   └── main/
│   │       ├── assets/
│   │       │   └── gemma3-1B-it-int4.task
│   │       │
│   │       ├── java/
│   │       │   └── com/example/offlinemeshchat/
│   │       │       │
│   │       │       ├── AppContextHolder.kt
│   │       │       ├── AppDatabase.kt
│   │       │       ├── ChatActivity.kt
│   │       │       ├── ConnectionManagerHolder.kt
│   │       │       ├── EncryptionHelper.kt
│   │       │       ├── MainActivity.kt
│   │       │       ├── MeshConnectionManager.kt
│   │       │       ├── MeshProtocol.kt
│   │       │       ├── Message.kt
│   │       │       ├── MessageDao.kt
│   │       │       ├── PhoneIdentity.kt
│   │       │       └── SplashActivity.kt
│   │       │
│   │       │       └── ai/
│   │       │           ├── AIAssistantActivity.kt
│   │       │           ├── AIMessage.kt
│   │       │           ├── AIMesssageDao.kt
│   │       │           └── LocalAIAssistant.kt
│   │       │
│   │       └── res/
│   │
│   └── build.gradle.kts
│
├── gradle/
│
├── .gitignore
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
└── README.md
```

> The AI model is excluded from the Git repository and must be added separately.

---

# 🧭 Example Network

A simple deployment can look like:

```text
                  NODE_B
                 /      \
                /        \
           NODE_A        NODE_C
                \        /
                 \      /
                  NODE_D
```

Phones can connect to different nodes:

```text
PHONE_001 → NODE_A

PHONE_002 → NODE_D
```

The ESP32 routing system allows the phones to communicate across the mesh.

---

# 🌍 Potential Applications

RESS can demonstrate offline communication concepts in situations where normal connectivity is unavailable or unreliable.

Possible applications include:

- Remote areas
- Hiking and camping
- Educational demonstrations
- Mesh networking research
- Temporary communication networks
- IoT experiments
- Remote communities
- Communication during network outages
- Academic networking projects

> RESS is an experimental prototype and is not a certified emergency communication system.

---

# 🚧 Current Limitations

RESS is currently an academic/project prototype.

Some limitations include:

- Static encryption-key arrangement
- Limited ESP32 memory
- ESP-NOW payload limitations
- Limited routing-table size
- Prototype-level routing
- Limited route recovery
- No production-grade key-management system
- AI model must be downloaded separately
- Communication range depends on the environment
- ESP32 nodes require a power source
- No certification for emergency communication

---

# 🚀 Future Improvements

Possible future improvements include:

### 🔐 Security

- Secure key exchange
- Improved device authentication
- Digital signatures
- Secure key storage
- Better identity management

### 🌐 Networking

- Automatic route expiration
- Improved route recovery
- Link-quality-based routing
- Failed-node detection
- Better packet retry mechanisms
- Packet fragmentation

### 💬 Communication

- Group messaging
- File transfer
- Image transfer
- Voice communication
- Multimedia messaging

### 🤖 AI

- Improved local models
- Better model management
- More offline AI features
- AI-assisted message composition
- Local document assistance

---

# 🎨 RESS Branding

## Name

**RESS**

## Tagline

**Connect Beyond Limits**

## Feature Line

**Offline • Secure • Decentralized**

### Color Palette

```text
Deep Plum       #190019
Dark Purple     #2B124C
Purple          #522B5B
Mauve           #854F6C
Dusty Pink      #DFB6B2
Soft Cream      #FBE4D8
```

---


# 🧪 Testing

The system can be tested using multiple Android phones and ESP32 nodes.

Example test:

```text
Phone 1
   │
   ▼
NODE_A
   │
   ▼
NODE_B
   │
   ▼
NODE_C
   │
   ▼
NODE_D
   │
   ▼
Phone 2
```

Test the following:

- ESP32 node startup
- Android connection
- Phone registration
- Phone discovery
- Cross-node messaging
- Message encryption
- Message decryption
- Delivery acknowledgement
- Local message storage
- Offline AI functionality

---

# 📦 APK

A built APK can be generated from Android Studio.

Use:

```text
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

The debug APK is normally generated under:

```text
app/build/outputs/apk/debug/
```

The APK can then be installed on compatible Android devices for testing.

---

# 👨‍💻 Project Information

**Project Name:** RESS

**Tagline:** Connect Beyond Limits

**Category:** Offline Communication / Mesh Networking / Android / IoT / Local AI

**Core Technologies:**

```text
Android
Kotlin
ESP32
ESP-NOW
Wi-Fi
TCP
AES-256-GCM
Room Database
Gemma
```

---

# 📚 Educational Purpose

RESS demonstrates concepts from several areas of computer science and electronics, including:

- Computer networking
- Wireless communication
- Mesh networking
- Mobile application development
- Cryptography
- Database management
- Internet of Things
- Embedded systems
- Artificial intelligence
- Distributed systems

The project is intended to demonstrate how independent devices can cooperate to provide communication without relying on traditional Internet infrastructure.

---

# ⚠️ Disclaimer

RESS is an educational and experimental prototype.

It is intended to demonstrate concepts related to:

- Offline communication
- ESP32 mesh networking
- Android development
- Encryption
- Embedded systems
- Local AI

It should **not** be considered a replacement for certified emergency communication infrastructure or commercially deployed secure communication systems.

---

# 📄 License

This project does not currently specify an open-source license.

If you intend to allow others to freely modify, distribute, or reuse the project, consider adding an appropriate open-source license.

---

# ⭐ RESS

**Connect Beyond Limits**

```text
OFFLINE • SECURE • DECENTRALIZED
```

Built to explore what communication can look like when the Internet isn't available.
```
