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

    private int lastUsedNodeIndex = 0;

    public CoordinatorImpl() throws RemoteException {
        super();
    }

    public void registerNode(NodeInterface node) {
        nodes.add(node);
    }
    @Override
    public List<String> getUserPermissions(String requesterToken, String targetUsername) throws RemoteException {
        String requester = activeTokens.get(requesterToken);
        if (requester == null) return null;

        User reqUser = users.get(requester);
        if (!"manager".equals(reqUser.getRole())) {
            return null;
        }

        User targetUser = users.get(targetUsername);
        if (targetUser == null) return null;

        return targetUser.getPermissions();
    }

    @Override
    public User getUserInfo(String username) throws RemoteException {
        return users.get(username);
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
        String managerUsername = activeTokens.get(managerToken);
        if (managerUsername == null) return false;

        User manager = users.get(managerUsername);
        if (!manager.getRole().equals("manager")) return false;

        User user = users.get(username);
        if (user != null) {
            user.setPermissions(permissions);
            return true;
        }
        return false;
    }

    @Override
    public boolean uploadFile(String token, String department, String fileName, byte[] fileData) throws RemoteException {
        if (!validateAccess(token, department, "write")) return false;

        boolean success = true;
        System.out.println("🔄 Starting RMI synchronization...");
        
        for (int i = 0; i < nodes.size(); i++) {
            NodeInterface node = nodes.get(i);
            try {
                System.out.println("📤 Uploading to node #" + (i+1) + "...");
                boolean nodeSuccess = node.storeFile(department, fileName, fileData);
                if (nodeSuccess) {
                    System.out.println(" Successfully uploaded to node #" + (i+1));
                } else {
                    System.out.println(" Failed to upload to node #" + (i+1));
                    success = false;
                }
            } catch (RemoteException e) {
                System.err.println(" Error uploading to node #" + (i+1) + ": " + e.getMessage());
                success = false;
            }
        }
        
        if (success) {
            System.out.println(" RMI synchronization completed successfully!");
        } else {
            System.out.println("️ RMI synchronization completed with some errors.");
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
        return uploadFile(token, department, fileName, newData);
    }

    @Override
    public byte[] viewFile(String token, String targetDepartment, String fileName) throws RemoteException {
        if (!validateAccess(token, targetDepartment, "read")) return null;

        for (int attempt = 0; attempt < nodes.size(); attempt++) {
            int nodeIndex = (lastUsedNodeIndex + attempt) % nodes.size();
            try {
                byte[] fileData = nodes.get(nodeIndex).retrieveFile(targetDepartment, fileName);
                if (fileData != null) {
                    lastUsedNodeIndex = (nodeIndex + 1) % nodes.size();
                    return fileData;
                }
            } catch (RemoteException e) {
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
        return Collections.emptyList();
    }


    private boolean validateAccess(String token, String department, String operation) {
        String username = activeTokens.get(token);
        if (username == null) return false;

        User user = users.get(username);
        if (user == null) return false;

        if (operation.equals("write") && !user.getDepartment().equals(department)) {
            return false;
        }

        return user.hasPermission(operation);
    }

    private String generateToken(String username) {

        return UUID.randomUUID().toString();
    }


}