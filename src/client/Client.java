package client;// Client.java

import interfaces.CoordinatorInterface;
import model.User;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Client {
    private CoordinatorInterface coordinator;
    private String userToken;
    private String username;
    private String department;
    private String role;
    private Scanner scanner;

    public Client(String host, int port) {
        try {
            // الاتصال بالـ RMI Registry واستخراج مرجع Coordinator  
            Registry registry = LocateRegistry.getRegistry(host, port);
            coordinator = (CoordinatorInterface) registry.lookup("Coordinator");
            scanner = new Scanner(System.in);
            System.out.println("Connected to the distributed file system.");
        } catch (RemoteException | NotBoundException e) {
            System.err.println("Error connecting to the system: " + e.getMessage());
            System.exit(1);
        }
    }

    // واجهة المستخدم الرئيسية  
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

    // واجهة تسجيل الدخول  
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

    // قائمة العمليات الرئيسية  
    private void showMainMenu() {
        System.out.println("\n=== File Management System ===");
        System.out.println("Welcome, " + username + " (" + role + " - " + department + ")");
        System.out.println("1. Upload File");
        System.out.println("2. Download File");
        System.out.println("3. View Files in My Department");
        System.out.println("4. View Files in Other Department");
        System.out.println("5. Delete File");

        // إضافة خيارات للمدير  
        if ("manager".equals(role)) {
            System.out.println("6. Manage User Permissions");
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

                    getUserinfo();

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

                // بعد تسجيل الدخول، نطلب بيانات المستخدم
                User user = coordinator.getUserInfo(uname);  // 🔹 تحتاج إضافة هذا التابع في الواجهة + التنفيذ
                this.department = user.getDepartment();
                this.role = user.getRole();

                System.out.println("✅ Login successful!");
            } else {
                System.out.println("❌ Login failed. Invalid credentials.");
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
            System.out.println("👤 User Info:");
            System.out.println("- Username: " + user.getUsername());
            System.out.println("- Department: " + user.getDepartment());
            System.out.println("- Role: " + user.getRole());
            // إذا عندك دالة getPermissions() كمان:
            System.out.println("- Permissions: " + user.getPermissions());
        } else {
            System.out.println("❌ User not found.");
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
            System.out.println("✅ User permissions updated successfully.");
        } else {
            System.out.println("❌ Failed to update permissions. Make sure the username is correct and you have manager rights.");
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

}
