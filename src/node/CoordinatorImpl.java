package node;
import interfaces.CoordinatorInterface;
import interfaces.NodeInterface;
import model.User;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class CoordinatorImpl extends UnicastRemoteObject implements CoordinatorInterface {
    private Map<String, User> users = new HashMap<>();
    private Map<String, String> activeTokens = new ConcurrentHashMap<>();
    private List<NodeInterface> nodes = new ArrayList<>();
    
    private final Map<String, String> fileLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> lockTimestamps = new ConcurrentHashMap<>();
    private final ReentrantLock lockManagerLock = new ReentrantLock();
    private static final long LOCK_TIMEOUT_MS = 300000;

    private int lastUsedNodeIndex = 0;

    public CoordinatorImpl() throws RemoteException {
        super();
        initializeDefaultUsers();
    }

    public void registerNode(NodeInterface node) {
        nodes.add(node);
    }

    private void initializeDefaultUsers() {

        users.put("admin", new User("admin", "admin123", "IT", "manager"));
        users.put("john", new User("john", "john123", "IT", "user"));
        users.put("alice", new User("alice", "alice123", "HR", "user"));
        users.put("bob", new User("bob", "bob123", "Marketing", "user"));
        users.put("manager1", new User("manager1", "manager123", "Finance", "manager"));
        
        System.out.println(" Default users initialized:");
        System.out.println("   • admin (IT Manager) - password: admin123");
        System.out.println("   • john (IT User) - password: john123");
        System.out.println("   • alice (HR User) - password: alice123");
        System.out.println("   • bob (Marketing User) - password: bob123");
        System.out.println("   • manager1 (Finance Manager) - password: manager123");
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
        System.out.println(" Starting RMI synchronization...");
        
        for (int i = 0; i < nodes.size(); i++) {
            NodeInterface node = nodes.get(i);
            try {
                System.out.println(" Uploading to node #" + (i+1) + "...");
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
        if (!validateAccess(token, department, "delete")) return false;

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
        if (!validateAccess(token, department, "write")) return false;

        String username = activeTokens.get(token);
        String fileKey = department + ":" + fileName;
        
        System.out.println(" User '" + username + "' attempting to update file: " + fileKey);
        
        boolean alreadyLocked = false;
        String currentLockOwner = fileLocks.get(fileKey);
        if (currentLockOwner != null && currentLockOwner.equals(username)) {
            alreadyLocked = true;
            System.out.println(" User '" + username + "' already holds lock for '" + fileKey + "' - proceeding with update");
        } else {
            if (!acquireFileLock(fileKey, username)) {
                String lockOwner = fileLocks.get(fileKey);
                System.err.println(" LOCK DENIED: File '" + fileKey + "' is currently being updated by user: " + lockOwner);
                System.err.println(" User '" + username + "' must wait for the lock to be released.");
                
                System.err.println(" Current locks: " + fileLocks.size() + " active");
                fileLocks.forEach((key, owner) -> 
                    System.err.println("   📄 " + key + " locked by: " + owner));
                
                return false;
            }
        }

        boolean success = true;
        try {
            if (!alreadyLocked) {
                System.out.println(" LOCK ACQUIRED: File '" + fileKey + "' locked for update by user: " + username);
            }
            System.out.println(" Starting distributed file update...");
            
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            boolean fileExists = false;
            for (NodeInterface node : nodes) {
                try {
                    byte[] existingData = node.retrieveFile(department, fileName);
                    if (existingData != null) {
                        fileExists = true;
                        break;
                    }
                } catch (RemoteException e) {
                    System.err.println("Error checking file existence on node: " + e.getMessage());
                }
            }
            
            if (!fileExists) {
                System.err.println(" Cannot update: File " + fileName + " does not exist in " + department);
                return false;
            }
            
            System.out.println(" Lock still held by '" + username + "' - proceeding with update...");
            
            for (int i = 0; i < nodes.size(); i++) {
                NodeInterface node = nodes.get(i);
                try {
                    System.out.println(" Updating file on node #" + (i+1) + "... (Lock held by: " + username + ")");
                    boolean nodeSuccess = node.updateFile(department, fileName, newData);
                    if (nodeSuccess) {
                        System.out.println(" Successfully updated on node #" + (i+1));
                    } else {
                        System.out.println(" Failed to update on node #" + (i+1));
                        success = false;
                    }
                } catch (RemoteException e) {
                    System.err.println(" Error updating on node #" + (i+1) + ": " + e.getMessage());
                    success = false;
                }
            }
            
            if (success) {
                System.out.println(" Distributed file update completed successfully by user: " + username);
            } else {
                System.out.println("️ Distributed file update completed with some errors by user: " + username);
            }

        } finally {
            if (!alreadyLocked) {
                releaseFileLock(fileKey, username);
                System.out.println(" LOCK RELEASED: File '" + fileKey + "' lock released by user: " + username);
                System.out.println(" File is now available for other users to update.");
            } else {
                System.out.println(" Lock retained: User '" + username + "' still holds lock on '" + fileKey + "' (explicitly acquired)");
            }
        }

        return success;
    }


    private boolean acquireFileLock(String fileKey, String username) {
        lockManagerLock.lock();
        try {
            System.out.println(" Lock request: User '" + username + "' wants to lock '" + fileKey + "'");
            
            cleanupExpiredLocks();
            
            String currentLockOwner = fileLocks.get(fileKey);
            if (currentLockOwner != null) {
                if (!currentLockOwner.equals(username)) {
                    System.out.println(" Lock denied: File '" + fileKey + "' is already locked by '" + currentLockOwner + "'");
                    return false;
                }
                lockTimestamps.put(fileKey, System.currentTimeMillis());
                System.out.println(" Lock refreshed: User '" + username + "' refreshed lock on '" + fileKey + "'");
                return true;
            }
            
            fileLocks.put(fileKey, username);
            lockTimestamps.put(fileKey, System.currentTimeMillis());
            System.out.println(" Lock granted: User '" + username + "' acquired lock on '" + fileKey + "'");
            System.out.println(" Total active locks: " + fileLocks.size());
            return true;
            
        } finally {
            lockManagerLock.unlock();
        }
    }


    private void releaseFileLock(String fileKey, String username) {
        lockManagerLock.lock();
        try {
            String currentLockOwner = fileLocks.get(fileKey);
            if (currentLockOwner != null && currentLockOwner.equals(username)) {
                fileLocks.remove(fileKey);
                lockTimestamps.remove(fileKey);
                System.out.println(" Lock released: User '" + username + "' released lock on '" + fileKey + "'");
                System.out.println(" Total active locks: " + fileLocks.size());
            } else if (currentLockOwner != null) {
                System.out.println("️ Lock release denied: User '" + username + "' tried to release lock on '" + fileKey + "' but it's owned by '" + currentLockOwner + "'");
            } else {
                System.out.println("️ Lock release ignored: File '" + fileKey + "' was not locked");
            }
        } finally {
            lockManagerLock.unlock();
        }
    }

    private void cleanupExpiredLocks() {
        long currentTime = System.currentTimeMillis();
        List<String> expiredLocks = new ArrayList<>();
        
        for (Map.Entry<String, Long> entry : lockTimestamps.entrySet()) {
            if (currentTime - entry.getValue() > LOCK_TIMEOUT_MS) {
                expiredLocks.add(entry.getKey());
            }
        }
        
        for (String fileKey : expiredLocks) {
            String expiredUser = fileLocks.remove(fileKey);
            lockTimestamps.remove(fileKey);
            System.out.println(" Expired lock removed for file: " + fileKey + " (was locked by: " + expiredUser + ")");
        }
    }


    public Map<String, String> getCurrentLocks() throws RemoteException {
        lockManagerLock.lock();
        try {
            cleanupExpiredLocks();
            return new HashMap<>(fileLocks);
        } finally {
            lockManagerLock.unlock();
        }
    }


    @Override
    public String isFileLocked(String department, String fileName) throws RemoteException {
        String fileKey = department + ":" + fileName;
        lockManagerLock.lock();
        try {
            cleanupExpiredLocks();
            return fileLocks.get(fileKey);
        } finally {
            lockManagerLock.unlock();
        }
    }


    @Override
    public boolean acquireUpdateLock(String token, String department, String fileName) throws RemoteException {
        if (!validateAccess(token, department, "write")) return false;
        
        String username = activeTokens.get(token);
        String fileKey = department + ":" + fileName;
        
        System.out.println(" Explicit lock request: User '" + username + "' wants to acquire update lock for '" + fileKey + "'");
        
        boolean acquired = acquireFileLock(fileKey, username);
        if (acquired) {
            System.out.println(" Update lock acquired: User '" + username + "' can now update '" + fileKey + "'");
        } else {
            String lockOwner = fileLocks.get(fileKey);
            System.out.println(" Update lock denied: File '" + fileKey + "' is locked by '" + lockOwner + "'");
        }
        
        return acquired;
    }

    @Override
    public boolean releaseUpdateLock(String token, String department, String fileName) throws RemoteException {
        String username = activeTokens.get(token);
        if (username == null) return false;
        
        String fileKey = department + ":" + fileName;
        
        lockManagerLock.lock();
        try {
            String currentLockOwner = fileLocks.get(fileKey);
            if (currentLockOwner != null && currentLockOwner.equals(username)) {
                fileLocks.remove(fileKey);
                lockTimestamps.remove(fileKey);
                System.out.println(" Update lock released: User '" + username + "' released lock on '" + fileKey + "'");
                return true;
            } else {
                System.out.println("️ Update lock release denied: User '" + username + "' doesn't own lock on '" + fileKey + "'");
                return false;
            }
        } finally {
            lockManagerLock.unlock();
        }
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
        if (!validateAccess(token, targetDepartment, "read")) {
            System.err.println(" Access denied for listFiles - token: " + (token != null ? "valid" : "null") +
                             ", department: " + targetDepartment);
            return null;
        }

        if (nodes.isEmpty()) {
            System.err.println("️ No nodes registered in coordinator");
            return Collections.emptyList();
        }

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

        if (operation.equals("read")) {
            if (user.getRole().equals("manager")) {
                return user.hasPermission(operation);
            } else {
                return user.hasPermission(operation) && user.getDepartment().equals(department);
            }
        } else if (operation.equals("write")) {
            if (user.getRole().equals("manager")) {
                return user.hasPermission(operation);
            } else {
                return user.hasPermission(operation) && user.getDepartment().equals(department);
            }
        } else if (operation.equals("delete")) {
            if (!user.getDepartment().equals(department)) {
                return false;
            }
        }

        return user.hasPermission(operation);
    }

    private String generateToken(String username) {

        return UUID.randomUUID().toString();
    }

}