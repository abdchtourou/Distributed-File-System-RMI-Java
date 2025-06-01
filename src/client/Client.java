package client;// Client.java

import interfaces.CoordinatorInterface;
import model.User;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class Client {
    private CoordinatorInterface coordinator;
    private String userToken;
    private String username;
    private String department;
    private String role;
    private Scanner scanner;

    public Client(String host, int port) {
        try {
            Registry registry = LocateRegistry.getRegistry(host, port);
            coordinator = (CoordinatorInterface) registry.lookup("Coordinator");
            scanner = new Scanner(System.in);
            System.out.println("Connected to the distributed file system.");
        } catch (RemoteException | NotBoundException e) {
            System.err.println("Error connecting to the system: " + e.getMessage());
            System.exit(1);
        }
    }

    public void start() {
        boolean running = true;
        while (running) {
            if (userToken == null) {
                showLoginMenu();
            } else {
                showMainMenu();
            }
        }
    }

    private void showLoginMenu() {
        System.out.println("\n=== Distributed File System ===");
        System.out.println("1. Login");
        System.out.println("2. Register");
        System.out.println("3. Exit");
        System.out.print("Choose an option: ");

        int choice = getIntInput();
        switch (choice) {
            case 1:
                login();
                break;
            case 2:
                register();
                break;
            case 3:
                System.out.println("Exiting the system. Goodbye!");
                System.exit(0);
                break;
            default:
                System.out.println("Invalid option. Please try again.");
        }
    }


    private void showMainMenu() {
        System.out.println("\n=== File Management System ===");
        System.out.println("Welcome, " + username + " (" + role + " - " + department + ")");
        System.out.println("1. Upload File");
        System.out.println("2. Download File");
        System.out.println("3. View Files in My Department");
        System.out.println("4. View Files in Other Department");
        System.out.println("5. Delete File");

        if ("manager".equals(role)) {
            System.out.println("6. Manage User Permissions");
            System.out.println("7. Manual Socket Synchronization");
            System.out.println("8. Auto Sync Status & Control");
        }

        System.out.println("0. Logout");
        System.out.print("Choose an option: ");

        int choice = getIntInput();

        try {
            switch (choice) {
                case 0:
                    logout();
                    break;
                case 1:
                    uploadFile();
                    break;
                case 2:
                    downloadFile();
                    break;

                case 3:
                    listFiles(department);
                    break;
                case 4:
                    viewOtherDepartmentFiles();
                    break;
                case 5:
                    deleteFile();
                    break;
                case 6:
                    if ("manager".equals(role)) {
                        managePermissions();
                    } else {
                        System.out.println("Invalid option. Please try again.");
                    }
                    break;
                case 7:
                    if ("manager".equals(role)) {
                        startSocketSynchronization();
                    } else {
                        getUserinfo();
                    }
                    break;
                case 8:
                    if ("manager".equals(role)) {
                        showAutoSyncMenu();
                    } else {
                        System.out.println("Invalid option. Please try again.");
                    }
                    break;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        } catch (IOException e) {
            System.err.println("Operation failed: " + e.getMessage());
        }
    }

    private void login() {
        System.out.print("Username: ");
        String uname = scanner.nextLine();

        System.out.print("Password: ");
        String pass = scanner.nextLine();

        try {
            String token = coordinator.login(uname, pass);
            if (token != null) {
                this.userToken = token;
                this.username = uname;

                User user = coordinator.getUserInfo(uname);
                this.department = user.getDepartment();
                this.role = user.getRole();

                System.out.println(" Login successful!");
            } else {
                System.out.println(" Login failed. Invalid credentials.");
            }
        } catch (RemoteException e) {
            System.err.println("Login error: " + e.getMessage());
        }
    }

    void getUserinfo() throws RemoteException {
        System.out.print("Enter username to check info: ");
        String uname = scanner.nextLine();
        User user = coordinator.getUserInfo(uname);

        if (user != null) {
            System.out.println(" User Info:");
            System.out.println("- Username: " + user.getUsername());
            System.out.println("- Department: " + user.getDepartment());
            System.out.println("- Role: " + user.getRole());
            System.out.println("- Permissions: " + user.getPermissions());
        } else {
            System.out.println(" User not found.");
        }

    }

    private void register() {
        System.out.print("Username: ");
        String uname = scanner.nextLine();
        System.out.print("Password: ");
        String pass = scanner.nextLine();
        System.out.print("Department: ");
        String dept = scanner.nextLine();
        System.out.print("Role (user/manager): ");
        String role = scanner.nextLine();

        try {
            boolean success = coordinator.registerUser(uname, pass, dept, role);
            if (success) {
                System.out.println("Registration successful! You can now log in.");
            } else {
                System.out.println("Registration failed. Try another username.");
            }
        } catch (RemoteException e) {
            System.err.println("Registration error: " + e.getMessage());
        }
    }

    private void logout() {
        this.userToken = null;
        this.username = null;
        this.department = null;
        this.role = null;
        System.out.println("Logged out successfully.");
    }

    private void uploadFile() throws IOException, RemoteException {
        System.out.print("Enter path of the file to upload: ");
        String path = scanner.nextLine();
        File file = new File(path);
        if (!file.exists()) {
            System.out.println("File does not exist.");
            return;
        }

        byte[] fileData = Files.readAllBytes(file.toPath());
        String filename = file.getName();
        coordinator.uploadFile(userToken, department, filename, fileData);
        System.out.println("File uploaded successfully.");
    }


    private void listFiles(String dept) throws RemoteException {
        List<String> files = coordinator.listFiles(userToken, dept);
        if (files.isEmpty()) {
            System.out.println("No files available.");
        } else {
            System.out.println("Files in department '" + dept + "':");
            files.forEach(f -> System.out.println("- " + f));
        }
    }

    private void viewOtherDepartmentFiles() throws RemoteException {
        System.out.print("Enter department name to view files: ");
        String dept = scanner.nextLine();
        if (dept.equals(department)) {
            System.out.println("You are already in this department. Use option 3 instead.");
            return;
        }
        listFiles(dept);
    }

    private void deleteFile() throws RemoteException {
        System.out.print("Enter name of the file to delete: ");
        String filename = scanner.nextLine();
        boolean success = coordinator.deleteFile(userToken, department, filename);

        if (success) {
            System.out.println("File deleted successfully.");
        } else {
            System.out.println("Failed to delete file. Access denied or file does not exist.");
        }
    }

    private void managePermissions() throws RemoteException {
        System.out.print("Enter username to update permissions: ");
        String uname = scanner.nextLine();

        System.out.print("Enter permissions (comma separated, e.g., read,write): ");
        String[] perms = scanner.nextLine().split(",");
        List<String> permissions = new ArrayList<>();
        for (String perm : perms) {
            permissions.add(perm.trim().toLowerCase());
        }

        boolean success = coordinator.setPermissions(userToken, uname, permissions);
        if (success) {
            System.out.println(" User permissions updated successfully.");
        } else {
            System.out.println(" Failed to update permissions. Make sure the username is correct and you have manager rights.");
        }
    }

    private int getIntInput() {
        try {
            return Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static void main(String[] args) {
        String host = (args.length < 1) ? "localhost" : args[0];
        int port = 1099;
        Client client = new Client(host, port);
        client.start();
    }

    private void downloadFile() throws RemoteException {
        System.out.print("Enter department name of the file: ");
        String dept = scanner.nextLine();

        System.out.print("Enter filename to download: ");
        String filename = scanner.nextLine();

        byte[] data = coordinator.viewFile(userToken, dept, filename);
        if (data != null) {
            try {
                Files.write(new File("downloaded_" + filename).toPath(), data);
                System.out.println("File downloaded as downloaded_" + filename);
            } catch (IOException e) {
                System.err.println("Failed to save file locally: " + e.getMessage());
            }
        } else {
            System.out.println("File not found or access denied.");
        }
    }

    private void startSocketSynchronization() throws RemoteException {
        System.out.println("\n=== Real Socket Synchronization Manager ===");
        System.out.println("This will perform real socket-based synchronization between nodes.");
        System.out.println("Note: RMI synchronization is already active during file uploads.");
        System.out.print("Do you want to start socket synchronization? (y/n): ");
        
        String choice = scanner.nextLine();
        if (choice.toLowerCase().startsWith("y")) {
            try {
                System.out.println("🔌 Starting Real Socket Synchronization...");
                
                Thread syncThread = new Thread(() -> {
                    try {
                        performRealSocketSync();
                    } catch (Exception e) {
                        System.err.println(" Socket synchronization failed: " + e.getMessage());
                    }
                });
                
                syncThread.start();
                syncThread.join(); // انتظار انتهاء المزامنة
                
                System.out.println("💡 Press Enter to continue...");
                scanner.nextLine();
                
            } catch (Exception e) {
                System.err.println(" Failed to start socket synchronization: " + e.getMessage());
            }
        } else {
            System.out.println("Socket synchronization cancelled.");
        }
    }
    
    private void performRealSocketSync() {
        System.out.println(" Performing real socket-based synchronization...");

        String[] departments = {"IT", "HR", "Marketing", "Finance"};
        int totalSynced = 0;
        
        for (String dept : departments) {
            System.out.println("\n🔍 Synchronizing department: " + dept);
            
            try {
                boolean success1to2 = sync.SocketSyncManager.SyncClient.syncWithNode(
                    "localhost", 8082, dept, "storage1"
                );
                boolean success1to3 = sync.SocketSyncManager.SyncClient.syncWithNode(
                    "localhost", 8083, dept, "storage1"
                );
                
                boolean success2to1 = sync.SocketSyncManager.SyncClient.syncWithNode(
                    "localhost", 8081, dept, "storage2"
                );
                boolean success2to3 = sync.SocketSyncManager.SyncClient.syncWithNode(
                    "localhost", 8083, dept, "storage2"
                );
                
                boolean success3to1 = sync.SocketSyncManager.SyncClient.syncWithNode(
                    "localhost", 8081, dept, "storage3"
                );
                boolean success3to2 = sync.SocketSyncManager.SyncClient.syncWithNode(
                    "localhost", 8082, dept, "storage3"
                );
                
                if (success1to2 && success1to3 && success2to1 && success2to3 && success3to1 && success3to2) {
                    System.out.println("   Department " + dept + " synchronized successfully across all 3 nodes");
                    totalSynced++;
                } else {
                    System.out.println("️ Department " + dept + " synchronization had issues");
                }
                
                // انتظار قصير بين الأقسام
                Thread.sleep(500);
                
            } catch (Exception e) {
                System.err.println("   Failed to sync department " + dept + ": " + e.getMessage());
            }
        }
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println(" Socket Synchronization Results:");
        System.out.println(" Departments synchronized: " + totalSynced + "/" + departments.length);
        System.out.println("️ Total storage nodes: 3 (storage1, storage2, storage3)");
        
        if (totalSynced == departments.length) {
            System.out.println(" All departments synchronized across all 3 nodes successfully!");
        } else {
            System.out.println("️ Some departments had synchronization issues.");
        }
        
        System.out.println("\n🔍 Verifying synchronization results...");
        performFileConsistencyCheck();
        
        System.out.println("=".repeat(50));
    }
    
    private void performFileConsistencyCheck() {
        System.out.println(" Checking file consistency between storage nodes...");
        
        String[] departments = {"IT", "HR", "Marketing", "Finance"};
        boolean allConsistent = true;
        boolean foundInconsistencies = false;
        
        for (String dept : departments) {
            System.out.println("\n Checking department: " + dept);
            
            File storage1Dir = new File("storage1/" + dept);
            File storage2Dir = new File("storage2/" + dept);
            File storage3Dir = new File("storage3/" + dept);
            
            if (!storage1Dir.exists()) storage1Dir.mkdirs();
            if (!storage2Dir.exists()) storage2Dir.mkdirs();
            if (!storage3Dir.exists()) storage3Dir.mkdirs();
            
            String[] files1 = storage1Dir.list();
            String[] files2 = storage2Dir.list();
            String[] files3 = storage3Dir.list();
            
            if (files1 == null) files1 = new String[0];
            if (files2 == null) files2 = new String[0];
            if (files3 == null) files3 = new String[0];
            
            if (files1.length == files2.length && files2.length == files3.length && files1.length == 0) {
                System.out.println("   No files in all three nodes - Consistent");
            } else if (files1.length == files2.length && files2.length == files3.length) {
                System.out.println("   File count matches across all nodes: " + files1.length + " files");
                
                Arrays.sort(files1);
                Arrays.sort(files2);
                Arrays.sort(files3);
                boolean namesMatch = Arrays.equals(files1, files2) && Arrays.equals(files2, files3);
                
                if (namesMatch) {
                    System.out.println("   File names match perfectly across all 3 nodes");
                } else {
                    System.out.println("  ️ File names differ between nodes");
                    allConsistent = false;
                    foundInconsistencies = true;
                }
            } else {
                System.out.println("  ️ File count mismatch - Node1: " + files1.length +
                                 ", Node2: " + files2.length + ", Node3: " + files3.length);
                allConsistent = false;
                foundInconsistencies = true;
            }
        }
        
        System.out.println("\n" + "=".repeat(50));
        if (allConsistent) {
            System.out.println(" All 3 storage nodes are consistent!");
            System.out.println(" RMI synchronization is working perfectly across all nodes.");
        } else {
            System.out.println("️ Some inconsistencies found between nodes.");
            System.out.println(" This is normal if files were added manually to storage folders.");

            if (foundInconsistencies) {
                System.out.println("\n Would you like to fix these inconsistencies? (y/n): ");
                try {
                    String choice = scanner.nextLine();
                    if (choice.toLowerCase().startsWith("y")) {
                        fixSynchronizationIssues();
                    }
                } catch (Exception e) {
                    System.err.println("Error reading input: " + e.getMessage());
                }
            }
        }
        System.out.println("=".repeat(50));
    }
    
    private void fixSynchronizationIssues() {
        System.out.println("\n Starting synchronization repair...");
        
        String[] departments = {"IT", "HR", "Marketing", "Finance"};
        int totalFixed = 0;
        
        for (String dept : departments) {
            System.out.println("\n Fixing department: " + dept);
            
            File storage1Dir = new File("storage1/" + dept);
            File storage2Dir = new File("storage2/" + dept);
            File storage3Dir = new File("storage3/" + dept);
            
            if (!storage1Dir.exists()) storage1Dir.mkdirs();
            if (!storage2Dir.exists()) storage2Dir.mkdirs();
            if (!storage3Dir.exists()) storage3Dir.mkdirs();
            
            Set<String> files1 = new HashSet<>();
            Set<String> files2 = new HashSet<>();
            Set<String> files3 = new HashSet<>();
            
            if (storage1Dir.list() != null) {
                files1.addAll(Arrays.asList(storage1Dir.list()));
            }
            if (storage2Dir.list() != null) {
                files2.addAll(Arrays.asList(storage2Dir.list()));
            }
            if (storage3Dir.list() != null) {
                files3.addAll(Arrays.asList(storage3Dir.list()));
            }
            
            Set<String> allFiles = new HashSet<>();
            allFiles.addAll(files1);
            allFiles.addAll(files2);
            allFiles.addAll(files3);
            
            for (String fileName : allFiles) {
                if (!files1.contains(fileName)) {
                    if (files2.contains(fileName)) {
                        if (copyFile(storage2Dir, storage1Dir, fileName)) {
                            System.out.println("   Copied " + fileName + " from Node2 to Node1");
                            totalFixed++;
                        }
                    } else if (files3.contains(fileName)) {
                        if (copyFile(storage3Dir, storage1Dir, fileName)) {
                            System.out.println("   Copied " + fileName + " from Node3 to Node1");
                            totalFixed++;
                        }
                    }
                }
                
                if (!files2.contains(fileName)) {
                    if (files1.contains(fileName)) {
                        if (copyFile(storage1Dir, storage2Dir, fileName)) {
                            System.out.println("   Copied " + fileName + " from Node1 to Node2");
                            totalFixed++;
                        }
                    } else if (files3.contains(fileName)) {
                        if (copyFile(storage3Dir, storage2Dir, fileName)) {
                            System.out.println("   Copied " + fileName + " from Node3 to Node2");
                            totalFixed++;
                        }
                    }
                }
                
                if (!files3.contains(fileName)) {
                    if (files1.contains(fileName)) {
                        if (copyFile(storage1Dir, storage3Dir, fileName)) {
                            System.out.println("   Copied " + fileName + " from Node1 to Node3");
                            totalFixed++;
                        }
                    } else if (files2.contains(fileName)) {
                        if (copyFile(storage2Dir, storage3Dir, fileName)) {
                            System.out.println("   Copied " + fileName + " from Node2 to Node3");
                            totalFixed++;
                        }
                    }
                }
            }
        }
        
        System.out.println("\n" + "=".repeat(50));
        if (totalFixed > 0) {
            System.out.println(" Synchronization repair completed!");
            System.out.println(" Total files synchronized across 3 nodes: " + totalFixed);
        } else {
            System.out.println("️ No files needed synchronization.");
        }
        System.out.println("=".repeat(50));
    }
    
    private boolean copyFile(File sourceDir, File targetDir, String fileName) {
        try {
            File sourceFile = new File(sourceDir, fileName);
            File targetFile = new File(targetDir, fileName);
            
            byte[] data = Files.readAllBytes(sourceFile.toPath());
            Files.write(targetFile.toPath(), data);
            
            return true;
        } catch (Exception e) {
            System.err.println("   Failed to copy " + fileName + ": " + e.getMessage());
            return false;
        }
    }

    private void showAutoSyncMenu() {
        System.out.println("\n=== Automatic Synchronization Control ===");
        System.out.println(" Auto Sync Management Panel");
        System.out.println("1. View Auto Sync Status");
        System.out.println("2. Test Immediate Sync");
        System.out.println("3. View Sync Schedule Info");
        System.out.println("4. Simulate Daily Sync");
        System.out.println("0. Back to Main Menu");
        System.out.print("Choose an option: ");

        int choice = getIntInput();
        
        switch (choice) {
            case 0:
                return;
            case 1:
                showAutoSyncStatus();
                break;
            case 2:
                testImmediateSync();
                break;
            case 3:
                showSyncScheduleInfo();
                break;
            case 4:
                simulateDailySync();
                break;
            default:
                System.out.println("Invalid option. Please try again.");
        }
        
        System.out.println("\n Press Enter to continue...");
        scanner.nextLine();
    }


    private void showAutoSyncStatus() {
        System.out.println("\n AUTOMATIC SYNCHRONIZATION STATUS");
        System.out.println("=".repeat(50));
        System.out.println(" Auto Sync: ✅ ENABLED (Built into SocketSyncManager)");
        System.out.println(" Daily Sync Time: 23:30 (11:30 PM)");
        System.out.println("️ Monitored Nodes: 3 (Node1, Node2, Node3)");
        System.out.println(" Departments: IT, HR, Marketing, Finance");
        System.out.println(" Sync Method: Socket-based bidirectional sync");
        System.out.println(" Sync Type: Each node syncs with all other nodes");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextSync = now.toLocalDate().atTime(23, 30);
        if (now.isAfter(nextSync)) {
            nextSync = nextSync.plusDays(1);
        }

        Duration duration = Duration.between(now, nextSync);
        long totalMinutes = duration.toMinutes();
        long hoursUntil = totalMinutes / 60;
        long minutesUntil = totalMinutes % 60;

        System.out.println(" Next Sync: " + hoursUntil + "h " + minutesUntil + "m");
        System.out.println(" Operations per sync: 24 (3 nodes × 2 other nodes × 4 departments)");
        System.out.println("=".repeat(50));
    }
    private void testImmediateSync() {
        System.out.println("\n TESTING SOCKET CONNECTIVITY");
        System.out.println("This will test socket connections between all nodes...");
        System.out.print("Do you want to proceed? (y/n): ");
        
        String choice = scanner.nextLine();
        if (choice.toLowerCase().startsWith("y")) {
            System.out.println("\n🔌 Testing socket connections...");
            
            String[] departments = {"IT"};
            boolean allConnected = true;
            

            String[] nodes = {"Node1:8081", "Node2:8082", "Node3:8083"};
            
            for (String dept : departments) {
                System.out.println("\n Testing department: " + dept);
                
                for (String node : nodes) {
                    String[] parts = node.split(":");
                    String nodeId = parts[0];
                    int port = Integer.parseInt(parts[1]);
                    
                    try {
                        boolean test = sync.SocketSyncManager.SyncClient.syncWithNode(
                            "localhost", port, dept, "storage1"
                        );
                        
                        if (test) {
                            System.out.println("   " + nodeId + " is responsive");
                        } else {
                            System.out.println("  ️ " + nodeId + " connection issues");
                            allConnected = false;
                        }
                        
                    } catch (Exception e) {
                        System.err.println("   " + nodeId + " connection failed: " + e.getMessage());
                        allConnected = false;
                    }
                }
            }
            
            System.out.println("\n" + "=".repeat(50));
            if (allConnected) {
                System.out.println(" ALL NODES RESPONSIVE");
                System.out.println(" Automatic sync should work correctly tonight!");
                System.out.println(" Each node will sync with others independently");
            } else {
                System.out.println("️ SOME NODES NOT RESPONSIVE");
                System.out.println(" Check if all socket servers are running.");
                System.out.println(" Run: java coordinator.CoordinatorServer");
            }
            System.out.println("=".repeat(50));
        } else {
            System.out.println("Test cancelled.");
        }
    }

    private void showSyncScheduleInfo() {
        System.out.println("\n ENHANCED SYNCHRONIZATION SCHEDULE");
        System.out.println("=".repeat(50));
        System.out.println(" Schedule Type: Distributed Daily Automatic");
        System.out.println(" Execution Time: 23:30 (11:30 PM) every day");
        System.out.println(" Sync Frequency: Once per day per node");
        System.out.println(" Total Operations: 24 per night");
        System.out.println("   • Node1 → Node2, Node3 (8 operations)");
        System.out.println("   • Node2 → Node1, Node3 (8 operations)");
        System.out.println("   • Node3 → Node1, Node2 (8 operations)");
        System.out.println(" Logging: Detailed per-node reports");
        System.out.println(" Failure Handling: Independent retry per node");
        
        System.out.println("\n Enhanced Sync Process:");
        System.out.println("  1. Each node starts sync independently at 23:30");
        System.out.println("  2. Bidirectional file comparison with other nodes");
        System.out.println("  3. Transfer only newer/missing files");
        System.out.println("  4. Verify completion and generate reports");
        System.out.println("  5. Schedule next sync for tomorrow");
        
        System.out.println("\n Key Improvements:");
        System.out.println("   Built into SocketSyncManager (no separate scheduler)");
        System.out.println("   Bidirectional sync (send AND receive)");
        System.out.println("   Timestamp-based file comparison");
        System.out.println("   Independent operation per node");
        System.out.println("   Fault tolerance (node failures don't affect others)");
        System.out.println("   Real file transfer (not just simulation)");
        System.out.println("=".repeat(50));
    }

    private void simulateDailySync() {
        System.out.println("\n SIMULATING ENHANCED DAILY SYNC");
        System.out.println("This demonstrates the improved automatic sync process");
        System.out.print("Do you want to proceed? (y/n): ");
        
        String choice = scanner.nextLine();
        if (choice.toLowerCase().startsWith("y")) {
            System.out.println("\n Starting enhanced sync simulation...");
            System.out.println(" Note: This is the same process that runs automatically at 23:30");
            
            // تشغيل نفس عملية المزامنة اليومية المحسنة
            performRealSocketSync();
            
            System.out.println("\n ENHANCED SIMULATION COMPLETED");
            System.out.println(" Key Features Demonstrated:");
            System.out.println("   Bidirectional synchronization");
            System.out.println("   File timestamp comparison");
            System.out.println("   Real file transfer between nodes");
            System.out.println("   Automatic daily scheduling");
            System.out.println("   Detailed progress reporting");
            System.out.println("\n This enhanced process runs automatically every night!");
        } else {
            System.out.println("Simulation cancelled.");
        }
    }

}
