package node;// NodeImpl.java
import interfaces.NodeInterface;

import java.io.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class NodeImpl extends UnicastRemoteObject implements NodeInterface {
    private final String nodeId;
    private final String baseStoragePath;
    private final Map<String, ReadWriteLock> fileLocks = new HashMap<>();

    public NodeImpl(String nodeId, String storagePath) throws RemoteException {
        super();
        this.nodeId = nodeId;
        this.baseStoragePath = storagePath;

        createDepartmentFolders();
    }

    private void createDepartmentFolders() {
        String[] departments = {"IT", "HR", "Marketing", "Finance"};
        for (String dept : departments) {
            File deptFolder = new File(baseStoragePath + File.separator + dept);
            if (!deptFolder.exists()) {
                deptFolder.mkdirs();
            }
        }
    }

    @Override
    public boolean storeFile(String department, String fileName, byte[] data) throws RemoteException {
        String filePath = getFilePath(department, fileName);

        ReadWriteLock lock = getFileLock(department, fileName);
        lock.writeLock().lock();

        try {
            File file = new File(filePath);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
                return true;
            } catch (IOException e) {
                System.err.println("Error storing file: " + e.getMessage());
                return false;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public byte[] retrieveFile(String department, String fileName) throws RemoteException {
        String filePath = getFilePath(department, fileName);

        ReadWriteLock lock = getFileLock(department, fileName);
        lock.readLock().lock();

        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return null;
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] data = new byte[(int) file.length()];
                fis.read(data);
                return data;
            } catch (IOException e) {
                System.err.println("Error retrieving file: " + e.getMessage());
                return null;
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean deleteFile(String department, String fileName) throws RemoteException {
        String filePath = getFilePath(department, fileName);

        ReadWriteLock lock = getFileLock(department, fileName);
        lock.writeLock().lock();
        try {
            File file = new File(filePath);
            if (file.exists()) {
                return file.delete();
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean synchronize() throws RemoteException {
        System.out.println(" Synchronization triggered...");
        return true;
    }


    @Override
    public boolean isAlive() throws RemoteException {
        return true;
    }

    @Override
    public boolean updateFile(String department, String fileName, byte[] newData) throws RemoteException {
        String filePath = getFilePath(department, fileName);

        ReadWriteLock lock = getFileLock(department, fileName);
        lock.writeLock().lock();

        try {
            File file = new File(filePath);
            
            if (!file.exists()) {
                System.err.println("Cannot update file: " + fileName + " does not exist in " + department);
                return false;
            }

            File backupFile = new File(filePath + ".backup");
            try {
                if (file.exists()) {
                    Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                System.err.println("Warning: Could not create backup for " + fileName + ": " + e.getMessage());
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(newData);
                
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                
                System.out.println("File updated successfully: " + department + "/" + fileName);
                return true;
                
            } catch (IOException e) {
                System.err.println("Error updating file: " + e.getMessage());
                
                try {
                    if (backupFile.exists()) {
                        Files.copy(backupFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        backupFile.delete();
                        System.out.println("File restored from backup after update failure");
                    }
                } catch (IOException restoreError) {
                    System.err.println("Critical: Could not restore file from backup: " + restoreError.getMessage());
                }
                
                return false;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private synchronized ReadWriteLock getFileLock(String department, String fileName) {
        String key = department + ":" + fileName;
        fileLocks.putIfAbsent(key, new ReentrantReadWriteLock());
        return fileLocks.get(key);
    }

    private String getFilePath(String department, String fileName) {
        return baseStoragePath + File.separator + department + File.separator + fileName;
    }
    @Override
    public List<String> listFiles(String department) throws RemoteException {
        File deptFolder = new File(baseStoragePath + File.separator + department);

        if (!deptFolder.exists() || !deptFolder.isDirectory()) {
            return Collections.emptyList();
        }

        String[] fileNames = deptFolder.list();
        if (fileNames == null) {
            return Collections.emptyList();
        }

        return Arrays.asList(fileNames);
    }


}