package node;// CoordinatorImpl.java - تنفيذ واجهة Coordinator
import interfaces.CoordinatorInterface;
import interfaces.NodeInterface;
import model.User;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CoordinatorImpl extends UnicastRemoteObject implements CoordinatorInterface {
    private Map<String, User> users = new HashMap<>();
    private Map<String, String> activeTokens = new ConcurrentHashMap<>();
    private List<NodeInterface> nodes = new ArrayList<>();

    // متغير لتتبع آخر عقدة تم استخدامها (للتوازن في الحمل)  
    private int lastUsedNodeIndex = 0;

    public CoordinatorImpl() throws RemoteException {
        super();
    }

    // تسجيل عقدة جديدة  
    public void registerNode(NodeInterface node) {
        nodes.add(node);
    }
    @Override
    public List<String> getUserPermissions(String requesterToken, String targetUsername) throws RemoteException {
        String requester = activeTokens.get(requesterToken);
        if (requester == null) return null;

        User reqUser = users.get(requester);
        if (!"manager".equals(reqUser.getRole())) {
            return null; // فقط المدير يقدر يشوف صلاحيات غيره
        }

        User targetUser = users.get(targetUsername);
        if (targetUser == null) return null;

        return targetUser.getPermissions();
    }

    @Override
    public User getUserInfo(String username) throws RemoteException {
        return users.get(username); // بشرط أن كائن User implement Serializable
    }

    @Override
    public String login(String username, String password) throws RemoteException {
        User user = users.get(username);
        if (user != null && user.validatePassword(password)) {
            String token = generateToken(username);
            activeTokens.put(token, username);
            return token;
        }
        return null;
    }

    @Override
    public boolean registerUser(String username, String password, String department, String role) throws RemoteException {
        if (users.containsKey(username)) {
            return false;
        }
        users.put(username, new User(username, password, department, role));
        return true;
    }

    @Override
    public boolean setPermissions(String managerToken, String username, List<String> permissions) throws RemoteException {
        // التحقق من صلاحيات المدير  
        String managerUsername = activeTokens.get(managerToken);
        if (managerUsername == null) return false;

        User manager = users.get(managerUsername);
        if (!manager.getRole().equals("manager")) return false;

        // تعيين الصلاحيات  
        User user = users.get(username);
        if (user != null) {
            user.setPermissions(permissions);
            return true;
        }
        return false;
    }

    @Override
    public boolean uploadFile(String token, String department, String fileName, byte[] fileData) throws RemoteException {
        // التحقق من الصلاحيات  
        if (!validateAccess(token, department, "write")) return false;

        // نشر الملف على جميع العقد  
        boolean success = true;
//        for (NodeInterface node : nodes) {
//            try {
//                success = success && node.storeFile(department, fileName, fileData);
//            } catch (RemoteException e) {
//                // تسجيل الخطأ وتجاهل العقدة التي فشلت
//                System.err.println("Error uploading to node: " + e.getMessage());
//                success = false;
//            }
//        }
        System.out.println("/////////////////////");
        for (int i = 0; i < nodes.size(); i++) {
            NodeInterface node = nodes.get(i);
            try {
                System.out.println("📤 Trying to upload to node #" + i);
                success = success && node.storeFile(department, fileName, fileData);
            } catch (RemoteException e) {
                System.err.println("❌ Failed to upload to node #" + i + ": " + e.getMessage());
                success = false;
            }
        }

        return success;
    }

    @Override
    public boolean deleteFile(String token, String department, String fileName) throws RemoteException {
        if (!validateAccess(token, department, "write")) return false;

        boolean success = true;
        for (NodeInterface node : nodes) {
            try {
                success = success && node.deleteFile(department, fileName);
            } catch (RemoteException e) {
                System.err.println("Error deleting from node: " + e.getMessage());
                success = false;
            }
        }
        return success;
    }


    @Override
    public boolean updateFile(String token, String department, String fileName, byte[] newData) throws RemoteException {
        // نفس منطق uploadFile
        return uploadFile(token, department, fileName, newData);
    }

    @Override
    public byte[] viewFile(String token, String targetDepartment, String fileName) throws RemoteException {
        // التحقق من الصلاحيات  
        if (!validateAccess(token, targetDepartment, "read")) return null;

        // توزيع الحمل (Round Robin)  
        for (int attempt = 0; attempt < nodes.size(); attempt++) {
            int nodeIndex = (lastUsedNodeIndex + attempt) % nodes.size();
            try {
                byte[] fileData = nodes.get(nodeIndex).retrieveFile(targetDepartment, fileName);
                if (fileData != null) {
                    lastUsedNodeIndex = (nodeIndex + 1) % nodes.size();
                    return fileData;
                }
            } catch (RemoteException e) {
                // تسجيل الخطأ والانتقال للعقدة التالية  
                System.err.println("Error retrieving from node " + nodeIndex + ": " + e.getMessage());
            }
        }
        return null;
    }

    @Override
    public List<String> listFiles(String token, String targetDepartment) throws RemoteException {
        if (!validateAccess(token, targetDepartment, "read")) return null;

        for (int attempt = 0; attempt < nodes.size(); attempt++) {
            int nodeIndex = (lastUsedNodeIndex + attempt) % nodes.size();
            try {
                List<String> files = nodes.get(nodeIndex).listFiles(targetDepartment);
                if (files != null && !files.isEmpty()) {
                    lastUsedNodeIndex = (nodeIndex + 1) % nodes.size();
                    return files;
                }
            } catch (RemoteException e) {
                System.err.println("Error listing files from node " + nodeIndex + ": " + e.getMessage());
            }
        }
        return Collections.emptyList(); // في حال لم نجد ملفات
    }


    // دوال المساعدة  
    private boolean validateAccess(String token, String department, String operation) {
        String username = activeTokens.get(token);
        if (username == null) return false;

        User user = users.get(username);
        if (user == null) return false;

        // مدير القسم يمكنه الكتابة في قسمه فقط  
        if (operation.equals("write") && !user.getDepartment().equals(department)) {
            return false;
        }

        return user.hasPermission(operation);
    }

    private String generateToken(String username) {
        // إنشاء رمز عشوائي  
        return UUID.randomUUID().toString();
    }


}