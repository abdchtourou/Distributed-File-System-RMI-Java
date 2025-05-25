// SynchronizationManager.java  
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class SynchronizationManager {
    private final List<NodeInfo> nodes;
    private final int syncPort;

    // معلومات عن العقد  
    static class NodeInfo {
        String nodeId;
        String hostname;
        String storagePath;

        public NodeInfo(String nodeId, String hostname, String storagePath) {
            this.nodeId = nodeId;
            this.hostname = hostname;
            this.storagePath = storagePath;
        }
    }

    public SynchronizationManager(List<NodeInfo> nodes, int syncPort) {
        this.nodes = nodes;
        this.syncPort = syncPort;
    }

    // بدء عملية المزامنة اليومية  
    public void startDailySynchronization() {
        Thread syncThread = new Thread(() -> {
            while (true) {
                try {
                    // المزامنة عند منتصف الليل  
                    scheduleDailySync();

                    // انتظار 24 ساعة  
                    Thread.sleep(24 * 60 * 60 * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        syncThread.setDaemon(true);
        syncThread.start();
    }

    private void scheduleDailySync() {
        // جدولة المزامنة في نهاية اليوم  
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 0);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronizeAllNodes();
            }
        }, calendar.getTime());
    }

    // مزامنة جميع العقد  
    private void synchronizeAllNodes() {
        System.out.println("Starting daily synchronization process...");

        // عملية المزامنة تتم بين كل زوج من العقد  
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                synchronizeNodes(nodes.get(i), nodes.get(j));
            }
        }

        System.out.println("Synchronization completed.");
    }

    // مزامنة بين عقدتين  
    private void synchronizeNodes(NodeInfo source, NodeInfo target) {
        try (Socket socket = new Socket(target.hostname, syncPort)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // إرسال طلب المزامنة  
            out.writeObject("SYNC_REQUEST");
            out.writeObject(source.nodeId);

            // استلام الرد من العقدة الهدف  
            String response = (String) in.readObject();
            if ("READY".equals(response)) {
                // إعداد قائمة المجلدات للمزامنة  
                String[] departments = {"IT", "HR", "Marketing", "Finance"};

                for (String dept : departments) {
                    // إرسال اسم القسم للمزامنة  
                    out.writeObject(dept);

                    // المزامنة المفصلة للملفات  
                    syncDepartmentFiles(dept, source, target, out, in);
                }

                // إنهاء المزامنة  
                out.writeObject("DONE");
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error during synchronization: " + e.getMessage());
        }
    }

    private void syncDepartmentFiles(String department, NodeInfo source, NodeInfo target,
                                     ObjectOutputStream out, ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        // استخراج قائمة الملفات وتواريخ التعديل من المصدر  
        Path sourcePath = Paths.get(source.storagePath, department);
        Map<String, Long> sourceFiles = getFilesWithTimestamps(sourcePath);

        // إرسال المعلومات إلى الهدف  
        out.writeObject(sourceFiles);

        // استلام قائمة الملفات المطلوبة من الهدف  
        List<String> requestedFiles = (List<String>) in.readObject();

        // إرسال الملفات المطلوبة  
        for (String fileName : requestedFiles) {
            Path filePath = sourcePath.resolve(fileName);
            byte[] fileData = Files.readAllBytes(filePath);
            out.writeObject(fileData);
        }
    }

    private Map<String, Long> getFilesWithTimestamps(Path directory) throws IOException {
        Map<String, Long> filesWithTimestamps = new HashMap<>();

        if (Files.exists(directory)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry)) {
                        String fileName = entry.getFileName().toString();
                        long modTime = Files.getLastModifiedTime(entry).toMillis();
                        filesWithTimestamps.put(fileName, modTime);
                    }
                }
            }
        }

        return filesWithTimestamps;
    }

    // خادم المزامنة الذي يعمل على كل عقدة  
    public static class SyncServer {
        private final int port;
        private final String storagePath;
        private final String nodeId;
        private boolean running = false;

        public SyncServer(String nodeId, String storagePath, int port) {
            this.nodeId = nodeId;
            this.storagePath = storagePath;
            this.port = port;
        }

        public void start() {
            running = true;
            Thread serverThread = new Thread(this::runServer);
            serverThread.setDaemon(true);
            serverThread.start();
        }

        private void runServer() {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("Sync server started on port " + port);

                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        // معالجة كل اتصال في خيط مستقل  
                        new Thread(() -> handleSyncRequest(clientSocket)).start();
                    } catch (IOException e) {
                        if (running) {
                            System.err.println("Error accepting connection: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Could not start sync server: " + e.getMessage());
            }
        }

        private void handleSyncRequest(Socket socket) {
            try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

                // استلام طلب المزامنة  
                String request = (String) in.readObject();
                String sourceNodeId = (String) in.readObject();

                if ("SYNC_REQUEST".equals(request)) {
                    System.out.println("Sync request received from Node " + sourceNodeId);

                    // الرد بالاستعداد للمزامنة  
                    out.writeObject("READY");

                    // معالجة مزامنة المجلدات  
                    String department;
                    while (!(department = (String) in.readObject()).equals("DONE")) {
                        handleDepartmentSync(department, in, out);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Error handling sync request: " + e.getMessage());
            }
        }

        private void handleDepartmentSync(String department, ObjectInputStream in, ObjectOutputStream out)
                throws IOException, ClassNotFoundException {
            // استلام قائمة الملفات مع طوابع الوقت  
            Map<String, Long> sourceFiles = (Map<String, Long>) in.readObject();

            // الملفات المحلية في هذا القسم  
            Path localDeptPath = Paths.get(storagePath, department);
            Map<String, Long> localFiles = getLocalFilesWithTimestamps(localDeptPath);

            // تحديد الملفات المطلوب تحديثها  
            List<String> requestedFiles = determineFilesToUpdate(sourceFiles, localFiles);
            // إرسال قائمة الملفات المطلوبة للتحديث
            out.writeObject(requestedFiles);

            // استلام الملفات وتخزينها
            for (String fileName : requestedFiles) {
                byte[] fileData = (byte[]) in.readObject();
                Path filePath = localDeptPath.resolve(fileName);

                // التأكد من وجود المجلد
                if (!Files.exists(filePath.getParent())) {
                    Files.createDirectories(filePath.getParent());
                }

                // حفظ الملف
                Files.write(filePath, fileData);
                System.out.println("Updated file: " + filePath);
            }
        }

        // تحديد أي الملفات يجب طلبها من العقدة المصدر
        private List<String> determineFilesToUpdate(Map<String, Long> sourceFiles, Map<String, Long> localFiles) {
            List<String> filesToRequest = new ArrayList<>();

            for (Map.Entry<String, Long> entry : sourceFiles.entrySet()) {
                String fileName = entry.getKey();
                long sourceTimestamp = entry.getValue();

                // إذا كان الملف غير موجود محلياً أو أحدث في المصدر
                if (!localFiles.containsKey(fileName) || sourceTimestamp > localFiles.get(fileName)) {
                    filesToRequest.add(fileName);
                }
            }

            return filesToRequest;
        }

        private Map<String, Long> getLocalFilesWithTimestamps(Path directory) throws IOException {
            Map<String, Long> filesWithTimestamps = new HashMap<>();

            if (Files.exists(directory)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                    for (Path entry : stream) {
                        if (Files.isRegularFile(entry)) {
                            String fileName = entry.getFileName().toString();
                            long modTime = Files.getLastModifiedTime(entry).toMillis();
                            filesWithTimestamps.put(fileName, modTime);
                        }
                    }
                }
            }

            return filesWithTimestamps;
        }

        public void stop() {
            running = false;
        }
    }
}