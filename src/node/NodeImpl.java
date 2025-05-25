package node;// NodeImpl.java
import interfaces.NodeInterface;

import java.io.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NodeImpl extends UnicastRemoteObject implements NodeInterface {
    private final String nodeId;
    private final String baseStoragePath;
    private final Map<String, ReadWriteLock> fileLocks = new HashMap<>();

    public NodeImpl(String nodeId, String storagePath) throws RemoteException {
        super();
        this.nodeId = nodeId;
        this.baseStoragePath = storagePath;

        // إنشاء مجلدات الأقسام  
        createDepartmentFolders();
    }

    private void createDepartmentFolders() {
        // إنشاء المجلدات الرئيسية للأقسام  
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

        // استخدام قفل الكتابة لمنع القراءة المتزامنة  
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

        // استخدام قفل القراءة (يسمح بقراءات متعددة متزامنة)  
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
        lock.writeLock().lock();  // نحتاج قفل كتابة لأننا نحذف

        try {
            File file = new File(filePath);
            if (file.exists()) {
                return file.delete();  // يرجع true إذا حذف بنجاح
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean synchronize() throws RemoteException {
        System.out.println("🔄 Synchronization triggered...");
        // مكان الكود الفعلي للمزامنة مع عقد أخرى
        return true;
    }


    @Override
    public boolean isAlive() throws RemoteException {
        return true;
    }

    // الحصول على قفل للملف (إنشاء قفل جديد إذا لم يكن موجودًا)  
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


    // باقي التنفيذ...  
}