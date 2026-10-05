#include <WiFi.h>
#include <esp_now.h>

// ============================================================
// NODE CONFIGURATION
// ============================================================

// CHANGE ONLY THIS FOR EACH BOARD
#define MY_NODE "NODE_A"

// CHANGE ONLY THIS FOR EACH BOARD
const char* WIFI_SSID = "ESP32_MESH_A";

// DO NOT CHANGE
const char* WIFI_PASSWORD = "12345678";

#define WIFI_CHANNEL 1
#define TCP_PORT 3333

// ============================================================
// NODE MAC ADDRESSES
// ============================================================

uint8_t NODE_A_MAC[] = {
  0x34, 0x5F, 0x45, 0xA7, 0xEB, 0x84
};

uint8_t NODE_B_MAC[] = {
  0x44, 0x1D, 0x64, 0xF3, 0x3F, 0xF4
};

uint8_t NODE_C_MAC[] = {
  0x84, 0x1F, 0xE8, 0x1B, 0x9D, 0x54
};

uint8_t NODE_D_MAC[] = {
  0x84, 0x1F, 0xE8, 0x1B, 0x4C, 0xE4
};

// ============================================================
// WIFI / TCP
// ============================================================

WiFiServer server(TCP_PORT);
WiFiClient androidClient;

// ============================================================
// MESSAGE PACKET
// ============================================================

struct MessagePacket {

  char type[10];

  char senderID[16];
  char receiverID[16];

  char originNode[12];
  char destinationNode[12];

  int messageID;
  int ttl;

  // Encrypted ciphertext
  char data[100];
};

// ============================================================
// ACK PACKET
// ============================================================

struct AckPacket {

  char type[10];

  char senderID[16];
  char receiverID[16];

  char originNode[12];

  int messageID;
  int ttl;
};

// ============================================================
// HELLO PACKET
// ============================================================

struct HelloPacket {

  char type[10];
  char nodeID[12];

  int sequence;
};

// ============================================================
// ROUTE PACKET
// ============================================================

struct RoutePacket {

  char type[10];

  char nodeID[12];
  char destination[12];

  int metric;
  int sequence;
};

// ============================================================
// PHONE PACKET
// ============================================================

struct PhonePacket {

  char type[10];

  char phoneID[16];
  char nodeID[12];

  int sequence;
};

// ============================================================
// SEEN MESSAGE
// ============================================================

struct SeenMessage {

  char senderID[16];
  int messageID;
};

SeenMessage seenMessages[100];

int seenCount = 0;

// ============================================================
// ROUTING TABLE
// ============================================================

struct RouteEntry {

  char destination[12];

  char nextHop[12];

  int metric;

  unsigned long lastUpdate;
};

RouteEntry routingTable[10];

int routeCount = 0;

// ============================================================
// PHONE TABLE
// ============================================================

struct PhoneEntry {

  char phoneID[16];

  char nodeID[12];

  unsigned long lastUpdate;
};

PhoneEntry phoneTable[20];

int phoneCount = 0;

// ============================================================
// SEQUENCES
// ============================================================

int helloSequence = 1;
int routeSequence = 1;
int phoneSequence = 1;

int nextMessageID = 200;

// ============================================================
// TIMERS
// ============================================================

unsigned long lastHelloTime = 0;
unsigned long lastRouteTime = 0;
unsigned long lastPhoneAdvertisementTime = 0;

const unsigned long HELLO_INTERVAL = 3000;
const unsigned long ROUTE_INTERVAL = 5000;
const unsigned long PHONE_INTERVAL = 5000;

// ============================================================
// DISCOVERY FLAG
// ============================================================

// Set when a new phone is learned through ESP-NOW.
// The actual Android response is sent from loop(),
// not directly inside the ESP-NOW callback.
volatile bool phoneTableChanged = false;

// ============================================================
// FUNCTION DECLARATIONS
// ============================================================

void onDataReceive(
  const esp_now_recv_info_t* info,
  const uint8_t* data,
  int len);

void onDataSent(
  const wifi_tx_info_t* info,
  esp_now_send_status_t status);

void addPeer(uint8_t* mac);

uint8_t* getMacForNode(
  const char* nodeID);

String getNodeFromMac(
  const uint8_t* mac);

bool alreadySeen(
  const char* senderID,
  int messageID);

void markSeen(
  const char* senderID,
  int messageID);

void sendHello();

void sendRouteAdvertisement();

void sendPhoneAdvertisements();

void sendPhoneListToAndroid();

void printPhoneTable();

void printRoutingTable();

void processRoutePacket(
  RoutePacket packet);

void processPhonePacket(
  PhonePacket packet);

bool findRoute(
  const char* destination,
  char* nextHop);

bool findPhoneNode(
  const char* phoneID,
  char* nodeID);

void updateRoute(
  const char* destination,
  const char* nextHop,
  int metric);

bool updatePhone(
  const char* phoneID,
  const char* nodeID);

void sendMessagePacket(
  MessagePacket packet);

void sendAckPacket(
  AckPacket ack);

// ============================================================
// SETUP
// ============================================================

void setup() {

  Serial.begin(115200);

  delay(1000);

  Serial.println();
  Serial.println("==========================================");
  Serial.println("       OFFLINE ESP32 MESH NODE");
  Serial.println("==========================================");

  Serial.print("NODE ID: ");
  Serial.println(MY_NODE);

  // ========================================================
  // WIFI ACCESS POINT
  // ========================================================

  WiFi.mode(WIFI_AP_STA);

  bool apStarted = WiFi.softAP(
    WIFI_SSID,
    WIFI_PASSWORD,
    WIFI_CHANNEL);

  if (apStarted) {

    Serial.println();
    Serial.println("Wi-Fi Access Point started!");

    Serial.print("SSID: ");
    Serial.println(WIFI_SSID);

    Serial.print("Password: ");
    Serial.println(WIFI_PASSWORD);

    Serial.print("AP IP: ");
    Serial.println(WiFi.softAPIP());
  } else {

    Serial.println("Wi-Fi AP FAILED!");
  }

  Serial.print("Wi-Fi MAC: ");
  Serial.println(WiFi.macAddress());

  // ========================================================
  // ESP-NOW
  // ========================================================

  if (esp_now_init() != ESP_OK) {

    Serial.println("ESP-NOW initialization FAILED!");

    return;
  }

  Serial.println(
    "ESP-NOW initialized successfully!");

  esp_now_register_recv_cb(
    onDataReceive);

  esp_now_register_send_cb(
    onDataSent);

  // ========================================================
  // ADD OTHER NODES AS PEERS
  // ========================================================

  if (strcmp(MY_NODE, "NODE_A") != 0) {
    addPeer(NODE_A_MAC);
  }

  if (strcmp(MY_NODE, "NODE_B") != 0) {
    addPeer(NODE_B_MAC);
  }

  if (strcmp(MY_NODE, "NODE_C") != 0) {
    addPeer(NODE_C_MAC);
  }

  if (strcmp(MY_NODE, "NODE_D") != 0) {
    addPeer(NODE_D_MAC);
  }

  // ========================================================
  // ADD SELF ROUTE
  // ========================================================

  updateRoute(
    MY_NODE,
    MY_NODE,
    0);

  // ========================================================
  // START TCP SERVER
  // ========================================================

  server.begin();

  Serial.println();
  Serial.println("TCP server started.");

  Serial.print("TCP port: ");
  Serial.println(TCP_PORT);

  Serial.println();
  Serial.println("Automatic node discovery enabled.");
  Serial.println("Dynamic routing enabled.");
  Serial.println("Phone registration enabled.");
  Serial.println("Phone discovery enabled.");

  Serial.println();
  Serial.println("==========================================");

  Serial.print(" ");
  Serial.print(MY_NODE);
  Serial.println(" IS READY");

  Serial.println("==========================================");
}

// ============================================================
// LOOP
// ============================================================

void loop() {

  // ========================================================
  // ANDROID CONNECTION
  // ========================================================

  if (
    !androidClient || !androidClient.connected()) {

    WiFiClient newClient =
      server.available();

    if (newClient) {

      androidClient =
        newClient;

      Serial.println();
      Serial.println(
        "Android connected!");

      androidClient.println(
        "CONNECTED|");

      androidClient.print(
        "NODE|");

      androidClient.println(
        MY_NODE);

      // If this node already knows phones,
      // immediately send them to the Android device.
      if (phoneCount > 0) {

        delay(20);

        sendPhoneListToAndroid();
      }
    }
  }

  // ========================================================
  // ANDROID DATA
  // ========================================================

  if (
    androidClient && androidClient.connected()) {

    if (
      androidClient.available()) {

      String command =
        androidClient.readStringUntil('\n');

      command.trim();

      Serial.println();
      Serial.println(
        "===== ANDROID COMMAND =====");

      Serial.println(command);

      // ====================================================
      // PING
      // ====================================================

      if (command == "PING") {

        androidClient.println(
          "PONG");
      }

      // ====================================================
      // STATUS
      // ====================================================

      else if (
        command == "STATUS") {

        androidClient.print(
          MY_NODE);

        androidClient.println(
          "_ONLINE");
      }

      // ====================================================
      // DISCOVER PHONES
      // ====================================================

      else if (
        command == "DISCOVER") {

        Serial.println(
          "Android requested phone discovery.");

        printPhoneTable();

        sendPhoneListToAndroid();
      }

      // ====================================================
      // REGISTER PHONE
      //
      // REGISTER|PHONE_001
      // ====================================================

      else if (
        command.startsWith("REGISTER|")) {

        String phoneID =
          command.substring(9);

        phoneID.trim();

        if (
          phoneID.length() > 0) {

          Serial.print(
            "Registering phone: ");

          Serial.println(
            phoneID);

          bool changed =
            updatePhone(
              phoneID.c_str(),
              MY_NODE);

          androidClient.print(
            "REGISTERED|");

          androidClient.println(
            MY_NODE);

          Serial.println(
            "Phone registration successful.");

          printPhoneTable();

          // Advertise immediately.
          sendPhoneAdvertisements();

          // Also send the current complete
          // phone list to this Android.
          delay(20);

          sendPhoneListToAndroid();

          if (changed) {

            phoneTableChanged =
              false;
          }
        }
      }

      // ====================================================
      // DISCONNECT
      // ====================================================

      else if (
        command == "DISCONNECT") {

        Serial.println();
        Serial.println("Android requested disconnect.");

        androidClient.println(
          "DISCONNECTED");

        delay(20);

        androidClient.stop();

        Serial.println(
          "Android TCP client stopped.");
      }

      // ====================================================
      // MESSAGE
      //
      // MSG|PHONE_002|ciphertext
      // ====================================================

      else if (
        command.startsWith("MSG|")) {

        int firstSeparator =
          command.indexOf('|');

        int secondSeparator =
          command.indexOf(
            '|',
            firstSeparator + 1);

        if (
          secondSeparator > 0) {

          String receiverID =
            command.substring(
              firstSeparator + 1,
              secondSeparator);

          String ciphertext =
            command.substring(
              secondSeparator + 1);

          receiverID.trim();
          ciphertext.trim();

          // ----------------------------------------------
          // FIND SENDER PHONE
          // ----------------------------------------------

          String senderID = "";

          for (
            int i = 0;
            i < phoneCount;
            i++) {

            if (
              strcmp(
                phoneTable[i].nodeID,
                MY_NODE)
              == 0) {

              senderID =
                phoneTable[i].phoneID;

              break;
            }
          }

          if (
            senderID.length() == 0) {

            androidClient.println(
              "ERROR|PHONE_NOT_REGISTERED");

            Serial.println(
              "Android has not registered!");

            return;
          }

          // ----------------------------------------------
          // FIND DESTINATION NODE
          // ----------------------------------------------

          char destinationNode[12];

          bool phoneKnown =
            findPhoneNode(
              receiverID.c_str(),
              destinationNode);

          if (!phoneKnown) {

            androidClient.println(
              "ERROR|DESTINATION_PHONE_UNKNOWN");

            Serial.println(
              "Destination phone not discovered yet.");

            printPhoneTable();

            return;
          }

          // ----------------------------------------------
          // FIND ROUTE
          // ----------------------------------------------

          char nextHop[12];

          bool routeFound =
            findRoute(
              destinationNode,
              nextHop);

          if (!routeFound) {

            androidClient.println(
              "ERROR|NO_ROUTE");

            Serial.println(
              "No route to destination node.");

            printRoutingTable();

            return;
          }

          // ----------------------------------------------
          // MESSAGE SIZE SAFETY
          // ----------------------------------------------

          if (
            ciphertext.length() >= sizeof(MessagePacket::data)) {

            androidClient.println(
              "ERROR|MESSAGE_TOO_LARGE");

            Serial.println(
              "Message rejected: ciphertext too large.");

            return;
          }

          // ----------------------------------------------
          // CREATE MESSAGE
          // ----------------------------------------------

          MessagePacket packet;

          memset(
            &packet,
            0,
            sizeof(packet));

          strcpy(
            packet.type,
            "MESSAGE");

          senderID.toCharArray(
            packet.senderID,
            sizeof(packet.senderID));

          receiverID.toCharArray(
            packet.receiverID,
            sizeof(packet.receiverID));

          strcpy(
            packet.originNode,
            MY_NODE);

          strcpy(
            packet.destinationNode,
            destinationNode);

          packet.messageID =
            nextMessageID++;

          packet.ttl = 10;

          // IMPORTANT:
          // Ciphertext is copied unchanged.
          ciphertext.toCharArray(
            packet.data,
            sizeof(packet.data));

          // ----------------------------------------------
          // MARK AS SEEN
          // ----------------------------------------------

          markSeen(
            packet.senderID,
            packet.messageID);

          // ----------------------------------------------
          // LOG
          // ----------------------------------------------

          Serial.println();
          Serial.println(
            "===== MESSAGE =====");

          Serial.print(
            "FROM PHONE: ");

          Serial.println(
            packet.senderID);

          Serial.print(
            "TO PHONE: ");

          Serial.println(
            packet.receiverID);

          Serial.print(
            "DEST NODE: ");

          Serial.println(
            packet.destinationNode);

          Serial.print(
            "NEXT HOP: ");

          Serial.println(
            nextHop);

          Serial.print(
            "MESSAGE ID: ");

          Serial.println(
            packet.messageID);

          Serial.println(
            "Ciphertext kept unchanged.");

          sendMessagePacket(
            packet);
        }
      }
    }
  }

  // ========================================================
  // PHONE TABLE CHANGED
  //
  // Send updated phone list to local Android.
  // ========================================================

  if (
    phoneTableChanged) {

    phoneTableChanged =
      false;

    if (
      androidClient && androidClient.connected()) {

      delay(10);

      sendPhoneListToAndroid();
    }
  }

  // ========================================================
  // NODE DISCOVERY
  // ========================================================

  if (
    millis() - lastHelloTime >= HELLO_INTERVAL) {

    lastHelloTime =
      millis();

    sendHello();
  }

  // ========================================================
  // ROUTE ADVERTISEMENT
  // ========================================================

  if (
    millis() - lastRouteTime >= ROUTE_INTERVAL) {

    lastRouteTime =
      millis();

    sendRouteAdvertisement();
  }

  // ========================================================
  // PHONE ADVERTISEMENT
  // ========================================================

  if (
    millis() - lastPhoneAdvertisementTime >= PHONE_INTERVAL) {

    lastPhoneAdvertisementTime =
      millis();

    sendPhoneAdvertisements();
  }

  delay(5);
}

// ============================================================
// ADD ESP-NOW PEER
// ============================================================

void addPeer(
  uint8_t* mac) {

  if (
    esp_now_is_peer_exist(mac)) {

    return;
  }

  esp_now_peer_info_t peerInfo = {};

  memcpy(
    peerInfo.peer_addr,
    mac,
    6);

  peerInfo.channel =
    WIFI_CHANNEL;

  peerInfo.encrypt =
    false;

  esp_err_t result =
    esp_now_add_peer(
      &peerInfo);

  if (
    result == ESP_OK) {

    Serial.println(
      "ESP-NOW peer added.");
  } else {

    Serial.print(
      "Failed to add peer. Error: ");

    Serial.println(
      result);
  }
}

// ============================================================
// GET MAC FROM NODE ID
// ============================================================

uint8_t* getMacForNode(
  const char* nodeID) {

  if (
    strcmp(
      nodeID,
      "NODE_A")
    == 0) {

    return NODE_A_MAC;
  }

  if (
    strcmp(
      nodeID,
      "NODE_B")
    == 0) {

    return NODE_B_MAC;
  }

  if (
    strcmp(
      nodeID,
      "NODE_C")
    == 0) {

    return NODE_C_MAC;
  }

  if (
    strcmp(
      nodeID,
      "NODE_D")
    == 0) {

    return NODE_D_MAC;
  }

  return nullptr;
}

// ============================================================
// GET NODE FROM MAC
// ============================================================

String getNodeFromMac(
  const uint8_t* mac) {

  if (
    memcmp(
      mac,
      NODE_A_MAC,
      6)
    == 0) {

    return "NODE_A";
  }

  if (
    memcmp(
      mac,
      NODE_B_MAC,
      6)
    == 0) {

    return "NODE_B";
  }

  if (
    memcmp(
      mac,
      NODE_C_MAC,
      6)
    == 0) {

    return "NODE_C";
  }

  if (
    memcmp(
      mac,
      NODE_D_MAC,
      6)
    == 0) {

    return "NODE_D";
  }

  return "";
}

// ============================================================
// SEND HELLO
// ============================================================

void sendHello() {

  HelloPacket packet;

  memset(
    &packet,
    0,
    sizeof(packet));

  strcpy(
    packet.type,
    "HELLO");

  strcpy(
    packet.nodeID,
    MY_NODE);

  packet.sequence =
    helloSequence++;

  uint8_t* peers[] = {
    NODE_A_MAC,
    NODE_B_MAC,
    NODE_C_MAC,
    NODE_D_MAC
  };

  for (
    int i = 0;
    i < 4;
    i++) {

    String peerNode =
      getNodeFromMac(
        peers[i]);

    if (
      peerNode.length() == 0) {
      continue;
    }

    if (
      peerNode == MY_NODE) {
      continue;
    }

    esp_now_send(
      peers[i],
      (uint8_t*)&packet,
      sizeof(packet));
  }

  Serial.println(
    "HELLO sent.");
}

// ============================================================
// SEND ROUTE ADVERTISEMENT
// ============================================================

void sendRouteAdvertisement() {

  uint8_t* peers[] = {
    NODE_A_MAC,
    NODE_B_MAC,
    NODE_C_MAC,
    NODE_D_MAC
  };

  for (
    int i = 0;
    i < routeCount;
    i++) {

    if (
      routingTable[i].metric >= 99) {
      continue;
    }

    RoutePacket packet;

    memset(
      &packet,
      0,
      sizeof(packet));

    strcpy(
      packet.type,
      "ROUTE");

    strcpy(
      packet.nodeID,
      MY_NODE);

    strcpy(
      packet.destination,
      routingTable[i].destination);

    packet.metric =
      routingTable[i].metric;

    packet.sequence =
      routeSequence;

    for (
      int p = 0;
      p < 4;
      p++) {

      String peerNode =
        getNodeFromMac(
          peers[p]);

      if (
        peerNode.length() == 0) {
        continue;
      }

      if (
        peerNode == MY_NODE) {
        continue;
      }

      esp_now_send(
        peers[p],
        (uint8_t*)&packet,
        sizeof(packet));
    }
  }

  routeSequence++;
}

// ============================================================
// SEND PHONE ADVERTISEMENTS
// ============================================================

void sendPhoneAdvertisements() {

  if (
    phoneCount == 0) {

    return;
  }

  uint8_t* peers[] = {
    NODE_A_MAC,
    NODE_B_MAC,
    NODE_C_MAC,
    NODE_D_MAC
  };

  for (
    int i = 0;
    i < phoneCount;
    i++) {

    PhonePacket packet;

    memset(
      &packet,
      0,
      sizeof(packet));

    strcpy(
      packet.type,
      "PHONE");

    strcpy(
      packet.phoneID,
      phoneTable[i].phoneID);

    strcpy(
      packet.nodeID,
      phoneTable[i].nodeID);

    packet.sequence =
      phoneSequence;

    for (
      int p = 0;
      p < 4;
      p++) {

      String peerNode =
        getNodeFromMac(
          peers[p]);

      if (
        peerNode.length() == 0) {
        continue;
      }

      if (
        peerNode == MY_NODE) {
        continue;
      }

      esp_now_send(
        peers[p],
        (uint8_t*)&packet,
        sizeof(packet));
    }
  }

  phoneSequence++;

  Serial.println(
    "Phone advertisements sent.");
}

// ============================================================
// SEND PHONE LIST TO ANDROID
//
// Format:
//
// PHONES|PHONE_501,PHONE_554
// ============================================================

void sendPhoneListToAndroid() {

  if (
    !androidClient || !androidClient.connected()) {

    return;
  }

  androidClient.print(
    "PHONES|");

  for (
    int i = 0;
    i < phoneCount;
    i++) {

    if (i > 0) {

      androidClient.print(
        ",");
    }

    androidClient.print(
      phoneTable[i].phoneID);
  }

  androidClient.println();

  Serial.print(
    "Phone list sent to Android: PHONES|");

  for (
    int i = 0;
    i < phoneCount;
    i++) {

    if (i > 0) {
      Serial.print(",");
    }

    Serial.print(
      phoneTable[i].phoneID);
  }

  Serial.println();
}

// ============================================================
// PRINT PHONE TABLE
// ============================================================

void printPhoneTable() {

  Serial.println();
  Serial.println(
    "===== PHONE TABLE =====");

  if (
    phoneCount == 0) {

    Serial.println(
      "No phones known.");

    return;
  }

  for (
    int i = 0;
    i < phoneCount;
    i++) {

    Serial.print(
      i);

    Serial.print(
      ": ");

    Serial.print(
      phoneTable[i].phoneID);

    Serial.print(
      " -> ");

    Serial.println(
      phoneTable[i].nodeID);
  }

  Serial.println(
    "=======================");
}

// ============================================================
// PRINT ROUTING TABLE
// ============================================================

void printRoutingTable() {

  Serial.println();
  Serial.println(
    "===== ROUTING TABLE =====");

  if (
    routeCount == 0) {

    Serial.println(
      "No routes.");

    return;
  }

  for (
    int i = 0;
    i < routeCount;
    i++) {

    Serial.print(
      routingTable[i].destination);

    Serial.print(
      " -> ");

    Serial.print(
      routingTable[i].nextHop);

    Serial.print(
      " | hops=");

    Serial.println(
      routingTable[i].metric);
  }

  Serial.println(
    "=========================");
}

// ============================================================
// UPDATE ROUTE
// ============================================================

void updateRoute(
  const char* destination,
  const char* nextHop,
  int metric) {

  // Never route a remote destination through ourselves.
  if (
    strcmp(
      nextHop,
      MY_NODE)
      == 0
    && strcmp(
         destination,
         MY_NODE)
         != 0) {

    return;
  }

  // ----------------------------------------------
  // Existing route
  // ----------------------------------------------

  for (
    int i = 0;
    i < routeCount;
    i++) {

    if (
      strcmp(
        routingTable[i].destination,
        destination)
      == 0) {

      if (
        metric < routingTable[i].metric) {

        strcpy(
          routingTable[i].nextHop,
          nextHop);

        routingTable[i].metric =
          metric;

        routingTable[i].lastUpdate =
          millis();

        Serial.print(
          "Route updated: ");

        Serial.print(
          destination);

        Serial.print(
          " via ");

        Serial.print(
          nextHop);

        Serial.print(
          " hops=");

        Serial.println(
          metric);
      } else {

        // Refresh existing route.
        routingTable[i].lastUpdate =
          millis();
      }

      return;
    }
  }

  // ----------------------------------------------
  // New route
  // ----------------------------------------------

  if (
    routeCount < 10) {

    strcpy(
      routingTable[routeCount].destination,
      destination);

    strcpy(
      routingTable[routeCount].nextHop,
      nextHop);

    routingTable[routeCount].metric =
      metric;

    routingTable[routeCount].lastUpdate =
      millis();

    routeCount++;

    Serial.print(
      "New route: ");

    Serial.print(
      destination);

    Serial.print(
      " via ");

    Serial.print(
      nextHop);

    Serial.print(
      " hops=");

    Serial.println(
      metric);
  }
}

// ============================================================
// FIND ROUTE
// ============================================================

bool findRoute(
  const char* destination,
  char* nextHop) {

  for (
    int i = 0;
    i < routeCount;
    i++) {

    if (
      strcmp(
        routingTable[i].destination,
        destination)
      == 0) {

      strcpy(
        nextHop,
        routingTable[i].nextHop);

      return true;
    }
  }

  return false;
}

// ============================================================
// UPDATE PHONE
// ============================================================
//
// Returns true if a new/changed entry was created.
// ============================================================

bool updatePhone(
  const char* phoneID,
  const char* nodeID) {

  for (
    int i = 0;
    i < phoneCount;
    i++) {

    if (
      strcmp(
        phoneTable[i].phoneID,
        phoneID)
      == 0) {

      bool changed =
        strcmp(
          phoneTable[i].nodeID,
          nodeID)
        != 0;

      strcpy(
        phoneTable[i].nodeID,
        nodeID);

      phoneTable[i].lastUpdate =
        millis();

      if (changed) {

        Serial.print(
          "Phone location updated: ");

        Serial.print(
          phoneID);

        Serial.print(
          " -> ");

        Serial.println(
          nodeID);

        phoneTableChanged =
          true;
      }

      return changed;
    }
  }

  // ----------------------------------------------
  // New phone
  // ----------------------------------------------

  if (
    phoneCount < 20) {

    strcpy(
      phoneTable[phoneCount].phoneID,
      phoneID);

    strcpy(
      phoneTable[phoneCount].nodeID,
      nodeID);

    phoneTable[phoneCount].lastUpdate =
      millis();

    phoneCount++;

    Serial.print(
      "New phone discovered: ");

    Serial.print(
      phoneID);

    Serial.print(
      " -> ");

    Serial.println(
      nodeID);

    phoneTableChanged =
      true;

    return true;
  }

  Serial.println(
    "Phone table full!");

  return false;
}

// ============================================================
// FIND PHONE NODE
// ============================================================

bool findPhoneNode(
  const char* phoneID,
  char* nodeID) {

  for (
    int i = 0;
    i < phoneCount;
    i++) {

    if (
      strcmp(
        phoneTable[i].phoneID,
        phoneID)
      == 0) {

      strcpy(
        nodeID,
        phoneTable[i].nodeID);

      return true;
    }
  }

  return false;
}

// ============================================================
// SEND MESSAGE
// ============================================================

void sendMessagePacket(
  MessagePacket packet) {

  char nextHop[12];

  if (
    !findRoute(
      packet.destinationNode,
      nextHop)) {

    Serial.println(
      "No route available.");

    if (
      androidClient && androidClient.connected()) {

      androidClient.println(
        "ERROR|NO_ROUTE");
    }

    return;
  }

  uint8_t* mac =
    getMacForNode(
      nextHop);

  if (
    mac == nullptr) {

    Serial.println(
      "Invalid next hop.");

    return;
  }

  Serial.print(
    "Routing message through: ");

  Serial.println(
    nextHop);

  esp_now_send(
    mac,
    (uint8_t*)&packet,
    sizeof(packet));
}

// ============================================================
// SEND ACK
// ============================================================

void sendAckPacket(
  AckPacket ack) {

  char destinationNode[12];

  if (
    strcmp(
      ack.receiverID,
      "")
    == 0) {

    return;
  }

  if (
    !findPhoneNode(
      ack.receiverID,
      destinationNode)) {

    Serial.println(
      "ACK destination phone unknown.");

    return;
  }

  char nextHop[12];

  if (
    !findRoute(
      destinationNode,
      nextHop)) {

    Serial.println(
      "No route for ACK.");

    return;
  }

  uint8_t* mac =
    getMacForNode(
      nextHop);

  if (
    mac == nullptr) {

    return;
  }

  Serial.print(
    "ACK routed through: ");

  Serial.println(
    nextHop);

  esp_now_send(
    mac,
    (uint8_t*)&ack,
    sizeof(ack));
}

// ============================================================
// ESP-NOW RECEIVE
// ============================================================

void onDataReceive(
  const esp_now_recv_info_t* info,
  const uint8_t* data,
  int len) {

  // ========================================================
  // HELLO
  // ========================================================

  if (
    len == sizeof(HelloPacket)) {

    HelloPacket packet;

    memcpy(
      &packet,
      data,
      sizeof(packet));

    if (
      strcmp(
        packet.type,
        "HELLO")
      == 0) {

      String senderNode =
        packet.nodeID;

      Serial.print(
        "Node discovered: ");

      Serial.println(
        senderNode);

      updateRoute(
        packet.nodeID,
        packet.nodeID,
        1);

      return;
    }
  }

  // ========================================================
  // ROUTE
  // ========================================================

  if (
    len == sizeof(RoutePacket)) {

    RoutePacket packet;

    memcpy(
      &packet,
      data,
      sizeof(packet));

    if (
      strcmp(
        packet.type,
        "ROUTE")
      == 0) {

      String senderNode =
        getNodeFromMac(
          info->src_addr);

      if (
        senderNode.length() == 0) {

        return;
      }

      if (
        strcmp(
          packet.destination,
          MY_NODE)
        == 0) {

        return;
      }

      int newMetric =
        packet.metric + 1;

      if (
        newMetric >= 10) {

        return;
      }

      updateRoute(
        packet.destination,
        senderNode.c_str(),
        newMetric);

      Serial.print(
        "Route learned: ");

      Serial.print(
        packet.destination);

      Serial.print(
        " via ");

      Serial.print(
        senderNode);

      Serial.print(
        " hops=");

      Serial.println(
        newMetric);

      return;
    }
  }

  // ========================================================
  // PHONE DISCOVERY
  // ========================================================

  if (
    len == sizeof(PhonePacket)) {

    PhonePacket packet;

    memcpy(
      &packet,
      data,
      sizeof(packet));

    if (
      strcmp(
        packet.type,
        "PHONE")
      == 0) {

      String senderNode =
        getNodeFromMac(
          info->src_addr);

      Serial.println();
      Serial.println(
        "===== PHONE ADVERTISEMENT =====");

      Serial.print(
        "PHONE: ");

      Serial.println(
        packet.phoneID);

      Serial.print(
        "LOCATION: ");

      Serial.println(
        packet.nodeID);

      Serial.print(
        "RECEIVED FROM NODE: ");

      Serial.println(
        senderNode);

      updatePhone(
        packet.phoneID,
        packet.nodeID);

      return;
    }
  }

  // ========================================================
  // MESSAGE
  // ========================================================

  if (
    len == sizeof(MessagePacket)) {

    MessagePacket packet;

    memcpy(
      &packet,
      data,
      sizeof(packet));

    if (
      strcmp(
        packet.type,
        "MESSAGE")
      != 0) {

      return;
    }

    Serial.println();
    Serial.println(
      "===== MESH MESSAGE =====");

    Serial.print(
      "FROM PHONE: ");

    Serial.println(
      packet.senderID);

    Serial.print(
      "TO PHONE: ");

    Serial.println(
      packet.receiverID);

    Serial.print(
      "DEST NODE: ");

    Serial.println(
      packet.destinationNode);

    Serial.print(
      "ID: ");

    Serial.println(
      packet.messageID);

    // ======================================================
    // DUPLICATE PROTECTION
    // ======================================================

    if (
      alreadySeen(
        packet.senderID,
        packet.messageID)) {

      Serial.println(
        "Duplicate message ignored.");

      return;
    }

    markSeen(
      packet.senderID,
      packet.messageID);

    // ======================================================
    // TTL
    // ======================================================

    if (
      packet.ttl <= 0) {

      Serial.println(
        "TTL expired.");

      return;
    }

    // ======================================================
    // DESTINATION NODE
    // ======================================================

    if (
      strcmp(
        packet.destinationNode,
        MY_NODE)
      == 0) {

      Serial.println(
        "Message reached destination node.");

      bool correctPhone =
        false;

      for (
        int i = 0;
        i < phoneCount;
        i++) {

        if (
          strcmp(
            phoneTable[i].phoneID,
            packet.receiverID)
            == 0
          && strcmp(
               phoneTable[i].nodeID,
               MY_NODE)
               == 0) {

          correctPhone =
            true;

          break;
        }
      }

      if (
        correctPhone && androidClient && androidClient.connected()) {

        androidClient.print(
          "MESSAGE|");

        androidClient.print(
          packet.senderID);

        androidClient.print(
          "|");

        androidClient.println(
          packet.data);

        Serial.println(
          "Encrypted message delivered to Android.");

        Serial.println(
          "Ciphertext was not modified.");
      } else {

        Serial.println(
          "Correct destination phone is not connected.");
      }

      // ====================================================
      // CREATE ACK
      // ====================================================

      AckPacket ack;

      memset(
        &ack,
        0,
        sizeof(ack));

      strcpy(
        ack.type,
        "ACK");

      strcpy(
        ack.senderID,
        packet.receiverID);

      strcpy(
        ack.receiverID,
        packet.senderID);

      strcpy(
        ack.originNode,
        packet.originNode);

      ack.messageID =
        packet.messageID;

      ack.ttl = 10;

      sendAckPacket(
        ack);

      return;
    }

    // ======================================================
    // FORWARD MESSAGE
    // ======================================================

    packet.ttl--;

    if (
      packet.ttl <= 0) {

      Serial.println(
        "TTL expired before forwarding.");

      return;
    }

    char nextHop[12];

    if (
      !findRoute(
        packet.destinationNode,
        nextHop)) {

      Serial.println(
        "No route to destination.");

      return;
    }

    uint8_t* nextMac =
      getMacForNode(
        nextHop);

    if (
      nextMac == nullptr) {

      return;
    }

    Serial.print(
      "Forwarding through: ");

    Serial.println(
      nextHop);

    // Ciphertext remains untouched.
    esp_now_send(
      nextMac,
      (uint8_t*)&packet,
      sizeof(packet));

    return;
  }

  // ========================================================
  // ACK
  // ========================================================

  if (
    len == sizeof(AckPacket)) {

    AckPacket ack;

    memcpy(
      &ack,
      data,
      sizeof(ack));

    if (
      strcmp(
        ack.type,
        "ACK")
      != 0) {

      return;
    }

    Serial.println();
    Serial.println(
      "===== ACK RECEIVED =====");

    Serial.print(
      "FROM: ");

    Serial.println(
      ack.senderID);

    Serial.print(
      "TO: ");

    Serial.println(
      ack.receiverID);

    Serial.print(
      "MESSAGE ID: ");

    Serial.println(
      ack.messageID);

    // ======================================================
    // ACK REACHED ORIGIN NODE
    // ======================================================

    if (
      strcmp(
        ack.originNode,
        MY_NODE)
      == 0) {

      if (
        androidClient && androidClient.connected()) {

        androidClient.print(
          "DELIVERED|");

        androidClient.println(
          ack.messageID);

        Serial.println(
          "Delivery confirmation sent to Android.");
      }

      return;
    }

    // ======================================================
    // FORWARD ACK
    // ======================================================

    char nextHop[12];

    if (
      !findRoute(
        ack.originNode,
        nextHop)) {

      Serial.println(
        "No route for ACK.");

      return;
    }

    uint8_t* nextMac =
      getMacForNode(
        nextHop);

    if (
      nextMac == nullptr) {

      return;
    }

    ack.ttl--;

    if (
      ack.ttl <= 0) {

      return;
    }

    Serial.print(
      "Forwarding ACK through: ");

    Serial.println(
      nextHop);

    esp_now_send(
      nextMac,
      (uint8_t*)&ack,
      sizeof(ack));
  }
}

// ============================================================
// DUPLICATE CHECK
// ============================================================

bool alreadySeen(
  const char* senderID,
  int messageID) {

  for (
    int i = 0;
    i < seenCount;
    i++) {

    if (
      strcmp(
        seenMessages[i].senderID,
        senderID)
        == 0
      && seenMessages[i].messageID == messageID) {

      return true;
    }
  }

  return false;
}

// ============================================================
// MARK SEEN
// ============================================================

void markSeen(
  const char* senderID,
  int messageID) {

  if (
    seenCount >= 100) {

    seenCount = 0;
  }

  strcpy(
    seenMessages[seenCount].senderID,
    senderID);

  seenMessages[seenCount].messageID =
    messageID;

  seenCount++;
}

// ============================================================
// ESP-NOW SEND CALLBACK
// ============================================================

void onDataSent(
  const wifi_tx_info_t* info,
  esp_now_send_status_t status) {

  if (
    status == ESP_NOW_SEND_SUCCESS) {

    Serial.println(
      "ESP-NOW transmission successful.");
  } else {

    Serial.println(
      "ESP-NOW transmission failed.");
  }
}