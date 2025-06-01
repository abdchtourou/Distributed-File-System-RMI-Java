package sync;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class SocketSyncManager {
    private final int port;
    private final String nodeId;
    private final String storagePath;
    private ServerSocket serverSocket;
    private boolean running = false;
    private ExecutorService executor;
    
    private final ScheduledExecutorService autoSyncScheduler;
    private final LocalTime dailySyncTime = LocalTime.of(23, 30); // 11:30 PM
    private boolean autoSyncEnabled = true;
    
    private final List<NodeInfo> otherNodes = new ArrayList<>();
    private final String[] departments = {"IT", "HR", "Marketing", "Finance"};

    public SocketSyncManager(String nodeId, String storagePath, int port) {
        this.nodeId = nodeId;
        this.storagePath = storagePath;
        this.port = port;
        this.executor = Executors.newFixedThreadPool(10);
        this.autoSyncScheduler = Executors.newScheduledThreadPool(2);
        
        setupOtherNodes();
    }
    
    private void setupOtherNodes() {
        if (!"Node1".equals(nodeId)) {
            otherNodes.add(new NodeInfo("Node1", "localhost", 8081, "storage1"));
        }
        if (!"Node2".equals(nodeId)) {
            otherNodes.add(new NodeInfo("Node2", "localhost", 8082, "storage2"));
        }
        if (!"Node3".equals(nodeId)) {
            otherNodes.add(new NodeInfo("Node3", "localhost", 8083, "storage3"));
        }
    }

    public void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println(" Socket server started on port " + port + " for " + nodeId);
            
            startRequestHandler();
            if (autoSyncEnabled) {
                startAutomaticDailySync();
            }
            
        } catch (IOException e) {
            System.err.println("Failed to start socket server: " + e.getMessage());
        }
    }
    private void startRequestHandler() {
        executor.submit(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executor.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        });
    }
    

    private void startAutomaticDailySync() {
        long initialDelay = calculateInitialDelay();

        System.out.println(" [" + nodeId + "] Auto sync thread started!");
        System.out.println(" [" + nodeId + "] Next sync at: " + dailySyncTime);
        System.out.println(" [" + nodeId + "] Time until sync: " + formatDuration(initialDelay));

        Thread autoSyncThread = new Thread(() -> {
            try {
                Thread.sleep(initialDelay * 60 * 1000);

                while (true) {
                    performAutomaticDailySync();
                    Thread.sleep(24 * 60 * 60 * 1000);
                }
            } catch (InterruptedException e) {
                System.err.println(" Auto sync thread interrupted");
                Thread.currentThread().interrupt();
            }
        });

        autoSyncThread.setDaemon(true);
        autoSyncThread.start();
    }



    private void performAutomaticDailySync() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("" + nodeId + "] AUTOMATIC DAILY SYNC STARTED");
        System.out.println(" Time: " + LocalDateTime.now());
        System.out.println("=".repeat(60));
        
        int totalOperations = 0;
        int successfulOperations = 0;
        
        for (NodeInfo targetNode : otherNodes) {
            System.out.println("\n🔗 [" + nodeId + "] Syncing with " + targetNode.nodeId);
            
            for (String department : departments) {
                totalOperations++;
                
                try {
                    boolean success = performBidirectionalSync(targetNode, department);
                    
                    if (success) {
                        successfulOperations++;
                        System.out.println("   [" + nodeId + "] " + department + " synced with " + targetNode.nodeId);
                    } else {
                        System.out.println("   [" + nodeId + "] " + department + " sync failed with " + targetNode.nodeId);
                    }
                    
                    Thread.sleep(300);
                    
                } catch (Exception e) {
                    System.err.println("   [" + nodeId + "] Sync error with " + targetNode.nodeId +
                                     " (" + department + "): " + e.getMessage());
                }
            }
        }
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println(" [" + nodeId + "] DAILY SYNC RESULTS:");
        System.out.println(" Successful: " + successfulOperations + "/" + totalOperations);
        System.out.println(" Success rate: " + String.format("%.1f%%",
                          (successfulOperations * 100.0 / totalOperations)));
        
        if (successfulOperations == totalOperations) {
            System.out.println(" [" + nodeId + "] ALL SYNC OPERATIONS SUCCESSFUL!");
        } else {
            System.out.println("️ [" + nodeId + "] Some sync operations failed.");
        }
        
        System.out.println("⏰ [" + nodeId + "] Next sync tomorrow at: " + dailySyncTime);
        System.out.println("=".repeat(60));
    }
    

    private boolean performBidirectionalSync(NodeInfo targetNode, String department) {
        try {
            boolean sendSuccess = sendNewerFilesToNode(targetNode, department);
            
            boolean receiveSuccess = receiveNewerFilesFromNode(targetNode, department);
            
            return sendSuccess && receiveSuccess;
            
        } catch (Exception e) {
            System.err.println(" Bidirectional sync failed: " + e.getMessage());
            return false;
        }
    }
    private boolean sendNewerFilesToNode(NodeInfo targetNode, String department) {
        try (Socket socket = new Socket(targetNode.host, targetNode.port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            out.writeObject("GET_FILES");
            out.writeObject(department);
            
            @SuppressWarnings("unchecked")
            Map<String, Long> targetFiles = (Map<String, Long>) in.readObject();
            
            Map<String, Long> localFiles = getFilesWithTimestamps(department);
            List<FileToSync> filesToSend = new ArrayList<>();
            
            for (Map.Entry<String, Long> entry : localFiles.entrySet()) {
                String fileName = entry.getKey();
                long localTime = entry.getValue();
                if (!targetFiles.containsKey(fileName) || targetFiles.get(fileName) < localTime) {
                    byte[] fileData = readFile(department, fileName);
                    filesToSend.add(new FileToSync(fileName, fileData, localTime));
                }
            }
            
            for (FileToSync fileToSync : filesToSend) {
                out.writeObject("SEND_FILE");
                out.writeObject(department);
                out.writeObject(fileToSync.fileName);
                out.writeObject(fileToSync.data);
                
                String result = (String) in.readObject();
                if ("SUCCESS".equals(result)) {
                    System.out.println("     [" + nodeId + "] Sent: " + fileToSync.fileName +
                                     " to " + targetNode.nodeId);
                } else {
                    System.err.println("     [" + nodeId + "] Failed to send: " + fileToSync.fileName);
                }
            }
            
            return true;
            
        } catch (Exception e) {
            System.err.println(" Failed to send files to " + targetNode.nodeId + ": " + e.getMessage());
            return false;
        }
    }

    private boolean receiveNewerFilesFromNode(NodeInfo targetNode, String department) {
        try (Socket socket = new Socket(targetNode.host, targetNode.port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            out.writeObject("SYNC_REQUEST");
            out.writeObject(department);
            
            Map<String, Long> localFiles = getFilesWithTimestamps(department);
            out.writeObject(localFiles);
            
            @SuppressWarnings("unchecked")
            List<String> incomingFiles = (List<String>) in.readObject();

            for (String fileName : incomingFiles) {
                byte[] fileData = (byte[]) in.readObject();
                if (fileData != null) {
                    boolean saved = writeFile(department, fileName, fileData);
                    if (saved) {
                        System.out.println("     [" + nodeId + "] Received: " + fileName +
                                         " from " + targetNode.nodeId);
                    }
                }
            }
            
            String result = (String) in.readObject();
            return "SYNC_COMPLETE".equals(result);
            
        } catch (Exception e) {
            System.err.println(" Failed to receive files from " + targetNode.nodeId + ": " + e.getMessage());
            return false;
        }
    }

    private void handleClient(Socket clientSocket) {
        try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {
            
            String command = (String) in.readObject();
            System.out.println(" [" + nodeId + "] Received: " + command + " from " +
                             clientSocket.getInetAddress());
            
            switch (command) {
                case "SYNC_REQUEST":
                    handleSyncRequest(in, out);
                    break;
                case "GET_FILES":
                    handleGetFiles(in, out);
                    break;
                case "SEND_FILE":
                    handleReceiveFile(in, out);
                    break;
                case "FULL_SYNC":
                    handleFullSync(in, out);
                    break;
                default:
                    out.writeObject("UNKNOWN_COMMAND");
            }
            
        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
            }
        }
    }

    private void handleSyncRequest(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        String department = (String) in.readObject();
        
        @SuppressWarnings("unchecked")
        Map<String, Long> clientFiles = (Map<String, Long>) in.readObject();
        
        Map<String, Long> localFiles = getFilesWithTimestamps(department);
        
        List<String> filesToSend = new ArrayList<>();
        
        for (Map.Entry<String, Long> entry : localFiles.entrySet()) {
            String fileName = entry.getKey();
            long localTime = entry.getValue();
            
            if (!clientFiles.containsKey(fileName) || clientFiles.get(fileName) < localTime) {
                filesToSend.add(fileName);
            }
        }
        
        out.writeObject(filesToSend);
        
        for (String fileName : filesToSend) {
            try {
                byte[] fileData = readFile(department, fileName);
                out.writeObject(fileData);
                System.out.println(" [" + nodeId + "] Sent file: " + fileName);
            } catch (Exception e) {
                out.writeObject(null);
                System.err.println(" Failed to send file " + fileName + ": " + e.getMessage());
            }
        }
        
        out.writeObject("SYNC_COMPLETE");
    }

    private void handleGetFiles(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        String department = (String) in.readObject();
        Map<String, Long> files = getFilesWithTimestamps(department);
        out.writeObject(files);
    }

    private void handleReceiveFile(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        String department = (String) in.readObject();
        String fileName = (String) in.readObject();
        byte[] fileData = (byte[]) in.readObject();
        
        boolean success = writeFile(department, fileName, fileData);
        out.writeObject(success ? "SUCCESS" : "FAILED");
        
        if (success) {
            System.out.println(" [" + nodeId + "] Received file: " + fileName +
                             " for department: " + department);
        }
    }
    

    private void handleFullSync(ObjectInputStream in, ObjectOutputStream out) throws Exception {
        System.out.println(" [" + nodeId + "] Starting full sync...");
        
        int totalSynced = 0;
        
        for (String department : departments) {
            try {
                handleSyncRequest(in, out);
                totalSynced++;
                System.out.println(" [" + nodeId + "] Synced department: " + department);
            } catch (Exception e) {
                System.err.println(" [" + nodeId + "] Failed to sync department " +
                                 department + ": " + e.getMessage());
            }
        }
        
        out.writeObject("FULL_SYNC_COMPLETE:" + totalSynced);
        System.out.println(" [" + nodeId + "] Full sync completed: " + totalSynced +
                         "/" + departments.length + " departments");
    }

    private Map<String, Long> getFilesWithTimestamps(String department) throws IOException {
        Map<String, Long> files = new HashMap<>();
        Path deptPath = Paths.get(storagePath, department);
        
        if (Files.exists(deptPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(deptPath)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry)) {
                        String fileName = entry.getFileName().toString();
                        long modTime = Files.getLastModifiedTime(entry).toMillis();
                        files.put(fileName, modTime);
                    }
                }
            }
        }
        
        return files;
    }

    private byte[] readFile(String department, String fileName) throws IOException {
        Path filePath = Paths.get(storagePath, department, fileName);
        return Files.readAllBytes(filePath);
    }

    private boolean writeFile(String department, String fileName, byte[] data) {
        try {
            Path deptPath = Paths.get(storagePath, department);
            if (!Files.exists(deptPath)) {
                Files.createDirectories(deptPath);
            }
            
            Path filePath = deptPath.resolve(fileName);
            Files.write(filePath, data);
            
            // تحديث وقت التعديل للملف
            Files.setLastModifiedTime(filePath, 
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
            
            return true;
        } catch (IOException e) {
            System.err.println("Error writing file: " + e.getMessage());
            return false;
        }
    }
    
    private long calculateInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextSync = now.toLocalDate().atTime(dailySyncTime);

        if (now.isAfter(nextSync)) {
            nextSync = nextSync.plusDays(1);
        }

        Duration duration = Duration.between(now, nextSync);
        return duration.toMinutes();
    }

    
    /**
     * تنسيق مدة زمنية بالدقائق
     */
    private String formatDuration(long minutes) {
        if (minutes < 60) {
            return minutes + " minutes";
        } else if (minutes < 1440) {
            long hours = minutes / 60;
            long remainingMinutes = minutes % 60;
            return hours + "h " + remainingMinutes + "m";
        } else {
            long days = minutes / 1440;
            long remainingHours = (minutes % 1440) / 60;
            return days + "d " + remainingHours + "h";
        }
    }

    public void stop() {
        running = false;
        autoSyncEnabled = false;
        
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            executor.shutdown();
            autoSyncScheduler.shutdown();
            System.out.println("🔌 [" + nodeId + "] Socket server stopped");
        } catch (IOException e) {
            System.err.println("Error stopping server: " + e.getMessage());
        }
    }
    

    public void runImmediateFullSync() {
        System.out.println("🚀 [" + nodeId + "] Starting immediate full sync...");
        performAutomaticDailySync();
    }

    public static class SyncClient {
        
        public static boolean syncWithNode(String targetHost, int targetPort, 
                                         String department, String sourceStoragePath) {
            try (Socket socket = new Socket(targetHost, targetPort);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                
                // طلب المزامنة
                out.writeObject("SYNC_REQUEST");
                out.writeObject(department);
                
                // إرسال قائمة ملفاتنا المحلية
                Map<String, Long> localFiles = getLocalFiles(sourceStoragePath, department);
                out.writeObject(localFiles);
                
                // استقبال قائمة الملفات التي ستُرسل إلينا
                @SuppressWarnings("unchecked")
                List<String> incomingFiles = (List<String>) in.readObject();
                
                // استقبال وحفظ الملفات
                for (String fileName : incomingFiles) {
                    byte[] fileData = (byte[]) in.readObject();
                    if (fileData != null) {
                        saveReceivedFile(sourceStoragePath, department, fileName, fileData);
                        System.out.println("📥 Received and saved: " + fileName);
                    }
                }
                
                String result = (String) in.readObject();
                return "SYNC_COMPLETE".equals(result);
                
            } catch (Exception e) {
                System.err.println("❌ Sync failed with " + targetHost + ":" + targetPort + 
                                 " - " + e.getMessage());
                return false;
            }
        }
        
        private static Map<String, Long> getLocalFiles(String storagePath, String department) 
                throws IOException {
            Map<String, Long> files = new HashMap<>();
            Path deptPath = Paths.get(storagePath, department);
            
            if (Files.exists(deptPath)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(deptPath)) {
                    for (Path entry : stream) {
                        if (Files.isRegularFile(entry)) {
                            String fileName = entry.getFileName().toString();
                            long modTime = Files.getLastModifiedTime(entry).toMillis();
                            files.put(fileName, modTime);
                        }
                    }
                }
            }
            
            return files;
        }
        
        private static void saveReceivedFile(String storagePath, String department, 
                                           String fileName, byte[] data) throws IOException {
            Path deptPath = Paths.get(storagePath, department);
            if (!Files.exists(deptPath)) {
                Files.createDirectories(deptPath);
            }
            
            Path filePath = deptPath.resolve(fileName);
            Files.write(filePath, data);
        }
    }
    
    private static class NodeInfo {
        final String nodeId;
        final String host;
        final int port;
        final String storagePath;
        
        NodeInfo(String nodeId, String host, int port, String storagePath) {
            this.nodeId = nodeId;
            this.host = host;
            this.port = port;
            this.storagePath = storagePath;
        }
    }
    
    private static class FileToSync {
        final String fileName;
        final byte[] data;
        final long timestamp;
        
        FileToSync(String fileName, byte[] data, long timestamp) {
            this.fileName = fileName;
            this.data = data;
            this.timestamp = timestamp;
        }
    }
} 