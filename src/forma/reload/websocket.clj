(ns forma.reload.websocket
  "Phase 5.5: WebSocket Server for Hot Reload

  Provides:
  - WebSocket server for browser connections
  - Client connection management
  - Message broadcasting
  - Heartbeat / keep-alive"
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [java.net ServerSocket Socket InetSocketAddress]
           [java.io BufferedReader InputStreamReader PrintWriter]
           [java.util.concurrent Executors]
           [java.security MessageDigest]
           [java.util Base64]))

;; =============================================================================
;; WebSocket Protocol
;; =============================================================================

(defn- calculate-accept-key
  "Calculate WebSocket accept key for handshake"
  [client-key]
  (let [magic "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        sha1 (MessageDigest/getInstance "SHA-1")
        combined (str client-key magic)
        hash (.digest sha1 (.getBytes combined))]
    (.encodeToString (Base64/getEncoder) hash)))

(defn- parse-http-headers
  "Parse HTTP headers from request"
  [reader]
  (loop [headers {}]
    (let [line (.readLine reader)]
      (if (or (nil? line) (empty? line))
        headers
        (let [[key value] (clojure.string/split line #":\s*" 2)]
          (recur (assoc headers (clojure.string/lower-case key) value)))))))

(defn- perform-websocket-handshake
  "Perform WebSocket handshake"
  [reader writer]
  (try
    ;; Read request line
    (let [request-line (.readLine reader)]
      (when (clojure.string/starts-with? request-line "GET")
        ;; Parse headers
        (let [headers (parse-http-headers reader)
              websocket-key (get headers "sec-websocket-key")]

          (when websocket-key
            ;; Send handshake response
            (.println writer "HTTP/1.1 101 Switching Protocols")
            (.println writer "Upgrade: websocket")
            (.println writer "Connection: Upgrade")
            (.println writer (str "Sec-WebSocket-Accept: " (calculate-accept-key websocket-key)))
            (.println writer "")
            (.flush writer)
            {:success true}))))
    (catch Exception e
      {:success false :error (.getMessage e)})))

(defn- encode-websocket-frame
  "Encode message as WebSocket frame"
  [message]
  (let [payload (.getBytes message "UTF-8")
        length (alength payload)
        header (cond
                 (< length 126) (byte-array [0x81 length])
                 (< length 65536) (byte-array (concat [0x81 126]
                                                      [(bit-shift-right length 8)
                                                       (bit-and length 0xFF)]))
                 :else (byte-array [0x81 127 0 0 0 0
                                   (bit-shift-right length 24)
                                   (bit-shift-right length 16)
                                   (bit-shift-right length 8)
                                   (bit-and length 0xFF)]))]
    (byte-array (concat header payload))))

(defn- decode-websocket-frame
  "Decode WebSocket frame (simplified)"
  [input-stream]
  (try
    (let [b1 (.read input-stream)
          b2 (.read input-stream)
          masked? (bit-test b2 7)
          payload-len (bit-and b2 0x7F)]

      (when (and (not= b1 -1) (not= b2 -1))
        (let [actual-len (cond
                          (< payload-len 126) payload-len
                          (= payload-len 126) (+ (* (.read input-stream) 256)
                                                 (.read input-stream))
                          :else 0)  ; Extended payload not fully implemented
              mask (when masked?
                     (byte-array (repeatedly 4 #(.read input-stream))))
              payload (byte-array actual-len)]

          (.read input-stream payload 0 actual-len)

          (when masked?
            (dotimes [i actual-len]
              (aset payload i
                    (byte (bit-xor (aget payload i)
                                  (aget mask (mod i 4)))))))

          (String. payload "UTF-8"))))
    (catch Exception e
      nil)))

;; =============================================================================
;; Client Connection Management
;; =============================================================================

(defrecord WebSocketClient
  [id            ; Unique client ID
   socket        ; Socket connection
   writer        ; Output writer
   connected?    ; Atom - connection status
   last-activity ; Atom - last activity timestamp
   metadata])    ; Additional client metadata

(defn create-websocket-client
  "Create WebSocket client from socket"
  [socket]
  (let [output-stream (.getOutputStream socket)
        writer (PrintWriter. output-stream true)]
    (map->WebSocketClient
      {:id (str (java.util.UUID/randomUUID))
       :socket socket
       :writer writer
       :connected? (atom true)
       :last-activity (atom (System/currentTimeMillis))
       :metadata {}})))

(defn send-message
  "Send message to WebSocket client"
  [client message]
  (when @(:connected? client)
    (try
      (let [output-stream (.getOutputStream (:socket client))
            frame (encode-websocket-frame (json/write-str message))]
        (.write output-stream frame)
        (.flush output-stream)
        (reset! (:last-activity client) (System/currentTimeMillis))
        {:success true})
      (catch Exception e
        (reset! (:connected? client) false)
        {:success false :error (.getMessage e)}))))

(defn close-client
  "Close WebSocket client connection"
  [client]
  (reset! (:connected? client) false)
  (try
    (.close (:socket client))
    (catch Exception _)))

;; =============================================================================
;; WebSocket Server
;; =============================================================================

(defrecord WebSocketServer
  [config        ; Server configuration
   server-socket ; ServerSocket
   clients       ; Atom - set of connected clients
   running?      ; Atom - is server running?
   executor])    ; Thread executor

(defn create-websocket-server
  "Create WebSocket server

  Parameters:
  - config: Server configuration map

  Returns WebSocketServer instance"
  [config]
  (map->WebSocketServer
    {:config config
     :server-socket nil
     :clients (atom #{})
     :running? (atom false)
     :executor (Executors/newCachedThreadPool)}))

(defn- handle-client-connection
  "Handle individual client connection"
  [server client]
  ;; Add to clients
  (swap! (:clients server) conj client)

  ;; Listen for messages
  (future
    (try
      (let [input-stream (.getInputStream (:socket client))]
        (while @(:connected? client)
          (when-let [message (decode-websocket-frame input-stream)]
            (reset! (:last-activity client) (System/currentTimeMillis))
            ;; Handle ping/pong
            (when (= message "ping")
              (send-message client {:type "pong"})))))
      (catch Exception e
        (reset! (:connected? client) false)))

    ;; Remove client on disconnect
    (swap! (:clients server) disj client)
    (close-client client)))

(defn start-websocket-server
  "Start WebSocket server

  Parameters:
  - server: WebSocketServer instance

  Returns server with running flag set"
  [server]
  (let [config (:config server)
        port (:port config 3449)
        server-socket (ServerSocket. port)]

    ;; Start accepting connections
    (future
      (reset! (:running? server) true)
      (try
        (while @(:running? server)
          (let [socket (.accept server-socket)
                input-stream (.getInputStream socket)
                reader (BufferedReader. (InputStreamReader. input-stream))
                output-stream (.getOutputStream socket)
                writer (PrintWriter. output-stream true)]

            ;; Perform handshake
            (when (:success (perform-websocket-handshake reader writer))
              (let [client (create-websocket-client socket)]
                (handle-client-connection server client)

                ;; Send welcome message
                (send-message client {:type "connected"
                                     :message "Hot reload connected"})))))
        (catch Exception e
          (when-let [on-error (:on-error config)]
            (on-error {:type :server-error :error (.getMessage e)})))))

    ;; Start heartbeat
    (future
      (while @(:running? server)
        (Thread/sleep (get-in config [:heartbeat-ms] 30000))
        (doseq [client @(:clients server)]
          (send-message client {:type "heartbeat"}))))

    (assoc server :server-socket server-socket)))

(defn stop-websocket-server
  "Stop WebSocket server

  Parameters:
  - server: WebSocketServer instance

  Returns stopped server"
  [server]
  (reset! (:running? server) false)

  ;; Close all client connections
  (doseq [client @(:clients server)]
    (close-client client))

  ;; Close server socket
  (when-let [server-socket (:server-socket server)]
    (.close server-socket))

  ;; Shutdown executor
  (.shutdown (:executor server))

  (assoc server :server-socket nil))

(defn broadcast-message
  "Broadcast message to all connected clients

  Parameters:
  - server: WebSocketServer instance
  - message: Message map to broadcast

  Returns number of clients reached"
  [server message]
  (let [clients @(:clients server)
        results (map #(send-message % message) clients)
        successful (count (filter :success results))]
    {:total (count clients)
     :successful successful
     :failed (- (count clients) successful)}))

(defn server-statistics
  "Get WebSocket server statistics

  Parameters:
  - server: WebSocketServer instance

  Returns statistics map"
  [server]
  {:running? @(:running? server)
   :connected-clients (count @(:clients server))
   :port (get-in server [:config :port])})

(comment
  ;; Start WebSocket server
  (def ws-server
    (-> (create-websocket-server {:port 3449})
        (start-websocket-server)))

  ;; Broadcast message
  (broadcast-message ws-server
    {:type "reload"
     :path "components/button.edn"})

  ;; Check statistics
  (server-statistics ws-server)

  ;; Stop server
  (stop-websocket-server ws-server)
  )
